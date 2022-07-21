/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */

package agentbasedloadbalancing;

import java.util.function.Predicate;
import java.util.*;
import java.io.IOException;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.*;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPANames;
import jade.proto.ContractNetInitiator;
import jade.proto.ContractNetResponder;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class HostAgent extends Agent {

    private HostDescription hostDescription;
    private int conversationId;
    private double[] lastCPUUsages;
    private double[] lastMemoryUsages;
    private ArrayList<String> coalitionLeaders;
    private Map<String, ArrayList<String>> coalitionToHostAgents; // coalition id, members
    private Map<String, String> hostAgentToCoalition; // member id to coalition
    private int thresholdViolationCounterForHighCPU;  // This is counting how many times thresholds of high cpu have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForHighMemory;  // This is counting how many times thresholds of high memory have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForLowCPU;  // This is counting how many times thresholds of low cpu have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForLowMemory;  // This is counting how many times thresholds of low memory have been violated, the in/decreasing rate depends on the SMA's report frequency
    private boolean highCPUThresholdViolated;
    private boolean highMemoryThresholdViolated;
    private boolean lowCPUThresholdViolated;
    private boolean lowMemoryThresholdViolated;
    private HashSet<weightEdge> edges;
    private Utilities utils;
    transient protected HostAgentGUI hostAgentGUI;
    private int currentTick;
    private static ExperimentRunConfiguration configuration;

    public HostAgent() {
        coalitionToHostAgents = new HashMap<String, ArrayList<String>>();
        hostAgentToCoalition = new HashMap<String, String>();
        lastCPUUsages = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
        lastMemoryUsages = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
        hostDescription = new HostDescription();
        hostDescription.setInProgress(false);
        utils = new Utilities();
        coalitionLeaders = new ArrayList<String>();
        edges = new HashSet<weightEdge>();
        resetAverageUsages();
        resetCounters();
        resetThresholdFlags();
        conversationId = 0;
    }

    @Override
    protected void setup() {
        try {
            Object[] args = getArguments();
            hostDescription = (HostDescription) args[0];
            hostDescription.setContainerName(getContainerController().getContainerName());
            coalitionLeaders = (ArrayList<String>) args[1];
            edges = (HashSet) (args[2]);
            configuration = (ExperimentRunConfiguration) args[3];
            coalitionToHostAgents = getCoalitions((ArrayList<HashSet<String>>) args[5]);
            // Creating a new dictionary including the relationship between hostAgents and coalitions.
            for (String coalitionId : coalitionToHostAgents.keySet()) {
                for (String hostAgent : coalitionToHostAgents.get(coalitionId)) {
                    hostAgentToCoalition.put(hostAgent, coalitionId);
                }
            }
            hostDescription.setMyLeader("HostAgent" + hostDescription.getCoalition());
            if (!Consts.LOG) {
                System.out.println(this.getLocalName() + "'s container is " + this.getContainerController().getContainerName());
            }
            hostAgentGUI = new HostAgentGUI(hostDescription);
            hostAgentGUI.setTitle(getLocalName());
            hostAgentGUI.updateResourceConsumption();
            if (Consts.HOST_AGENT_GUI) {
                hostAgentGUI.setVisible(true);
            }
            if (configuration.getLOAD_BALANCING_TYPE() == Consts.DISTRIBUTED_FIXED_COALITIONS) {
                addBehaviour(new CNPParticipantForIntraLoadBalancingAtoB(this)); // the agent always listens for potential requests for Intra Load Balancing from A (this source host) to B (a destination host).
                addBehaviour(new CNPParticipantForIntraLoadBalancingBtoA(this)); // the agent always listens for potential requests for Intra Load Balancing from B (an external host) to A (this destination host).
                if (configuration.isBALANCING_ONLY_ONE_COALITION_AT_A_TIME()) {
                    if (hostDescription.isLeader()) addBehaviour(new LeaderListenerForCounterReset(this));
                    else addBehaviour(new MemberListenerForCounterReset(this));
                }
            } else if (configuration.getLOAD_BALANCING_TYPE() == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                addBehaviour(new VMWARE_RemoveAndMigrateVM(this));
                addBehaviour(new VMWARE_LockVM(this));
                addBehaviour(new VMWARE_LockResources(this));
                addBehaviour(new VMWARE_Unlock(this));
                addBehaviour(new VMWARE_ListenerForVMMigrations(this));
            }
            // Behaviours required for any balancing type.
            addBehaviour(new ListenerForVMMigrations(this));
            addBehaviour(new RequestsReceiver(this));
            addBehaviour(new PerformanceReporterAndThresholdMonitoring(this, Consts.HOST_REPORTING_RATE + new Random().nextInt((int) Consts.RANGE_OF_RANDOM_TICKS))); // Added a random number to prevent colitions among host agents when enacting interactions protocols.
            addBehaviour(new VirtualMachineKiller(this, (long) (Consts.AVG_INTERDEPARTURE_TIME * (-Math.log(Math.random())))));
            addBehaviour(new MonitorListener(this));
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println(ex);
            }
        }
    }

    private String selectCoalitionForLoadbalancing() {
        return coalitionLeaders.get((new Random()).nextInt(coalitionLeaders.size()));
    }

    private Map<String, ArrayList<String>> getCoalitions(ArrayList<HashSet<String>> setCoalitions) {
        Map<String, ArrayList<String>> tmpCoalitionToHosts = new HashMap<String, ArrayList<String>>();
        ArrayList<String> coalition = new ArrayList<String>();
        for (int coalitionNumber = 0; coalitionNumber < setCoalitions.size(); coalitionNumber++) {
            HashSet<String> set = setCoalitions.get(coalitionNumber);
            Iterator<String> iterator = set.iterator();
            ArrayList<String> hostAgents = new ArrayList<String>();
            ArrayList<Integer> hostAgentIDs = new ArrayList<Integer>();
            int minIdentifier = Integer.MAX_VALUE;
            while (iterator.hasNext()) {
                String anAgentID = (String) iterator.next();
                hostAgentIDs.add(Integer.valueOf(anAgentID));
                hostAgents.add("HostAgent" + anAgentID);
                if (Integer.valueOf(anAgentID) < minIdentifier) {
                    minIdentifier = Integer.valueOf(anAgentID);
                }
            }
            tmpCoalitionToHosts.put("HostAgent" + String.valueOf(minIdentifier), hostAgents);
        }
        return tmpCoalitionToHosts;
    }

    double getDistance(String source, String destination) {
        if (source.equals(destination)) {
            return 0;
        }
        Iterator<weightEdge> i = edges.iterator();
        while (i.hasNext()) {
            weightEdge edge = i.next();
            String in = "HostAgent" + edge.getInNode();
            String out = "HostAgent" + edge.getOutNode();
            if ((in.equals(source) && out.equals(destination)) || (in.equals(destination) && out.equals(source)))
                return edge.getDistance();
        }
        return -1;
    }

    private class LeaderListenerForCounterReset extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage initialMsg;
        private ACLMessage finalMsg;
        private AID to;

        public LeaderListenerForCounterReset(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_A_COALITION_WAS_JUST_BALANCED);
        }

        @Override
        public void action() {
            initialMsg = receive(mt);
            if (initialMsg == null) {
                block();
                return;
            }
            try {
                resetAverageUsages();
                resetCounters();
                // send a message to all coalitions members
                finalMsg = new ACLMessage(ACLMessage.REQUEST);
                to = new AID(hostDescription.getId(), AID.ISLOCALNAME);
                finalMsg.setSender(agt.getAID());
                ArrayList<String> coalitionMembers = coalitionToHostAgents.get(hostDescription.getMyLeader());
                for (int i = 0; i < coalitionMembers.size(); i++) { // notify all the coalition members (except me) that a coalition has been balanced
                    if (!coalitionMembers.get(i).equals(hostDescription.getId())) {
                        finalMsg.addReceiver(new AID(coalitionMembers.get(i), AID.ISLOCALNAME));
                    }
                }
                finalMsg.setConversationId(Consts.CONVERSATION_RESET_COUNTERS);
                finalMsg.setContent("nothing relevant");
                agt.send(finalMsg);
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("Hey 1" + ex);
                }
            }
        }
    }

    private class MemberListenerForCounterReset extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;

        public MemberListenerForCounterReset(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_RESET_COUNTERS);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                resetAverageUsages();
                resetCounters();
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("Hey 2 " + ex);
                }
            }
        }
    }

    private class ResetDatacenterLoadBalancingCounters extends OneShotBehaviour {

        private Agent agt;

        public ResetDatacenterLoadBalancingCounters(Agent agt) {
            super(null);
            this.agt = agt;
        }

        @Override
        public void action() {
            try {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                AID to = new AID(hostDescription.getId(), AID.ISLOCALNAME);
                msg.setSender(agt.getAID());
                for (int i = 0; i < coalitionLeaders.size(); i++) { // notify all the leaders (except my own leader) that a coalition has been balanced
                    if (!coalitionLeaders.get(i).equals("HostAgent" + hostDescription.getCoalition())) {
                        msg.addReceiver(new AID(coalitionLeaders.get(i), AID.ISLOCALNAME));
                    }
                }
                msg.setConversationId(Consts.CONVERSATION_A_COALITION_WAS_JUST_BALANCED);
                msg.setContent("nothing relevant");
                agt.send(msg);
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("Hey 4" + ex);
                }
            }
        }
    }

    private class VMWARE_ListenerForVMMigrations extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;

        public VMWARE_ListenerForVMMigrations(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_CONFIRM_MIGRATION);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                vm = (VirtualMachineDescription) (msg.getContentObject());
                operationOverVM(vm, "removeAndMigrate", "AtoB", null, null);
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
            hostDescription.setInProgress(false);
        }
    }

    private class VMWARE_LockResources extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;
        private ACLMessage acknowledgementMsg;

        public VMWARE_LockResources(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                hostDescription.setInProgress(false);
                block();
                return;
            }
            try {
                if (!hostDescription.isInProgress()) {
                    hostDescription.setInProgress(true);
                    vm = (VirtualMachineDescription) (msg.getContentObject());
                    if ((vm.getMemory() <= hostDescription.getAvailableMemory()) && (vm.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores())) { // if the host has sufficient resources to allocate the VM
                        acknowledgementMsg = new ACLMessage(ACLMessage.CONFIRM);
                        acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Success in locking resources for VM migration");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Success in locking resources for VM migration");
                        }
                    } else {
                        hostDescription.setInProgress(false);
                        acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                        acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Failed to lock resources for VM migration due to insufficient resources");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Failed to lock resources for VM migration due to insufficient resources");
                        }
                    }
                } else { //if it is busy
                    acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                    acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                    acknowledgementMsg.addReceiver(msg.getSender());
                    acknowledgementMsg.setContent("Failed to lock resources for VM migration because I'm busy");
                    agt.send(acknowledgementMsg);
                }
            } catch (Exception ex) {
                hostDescription.setInProgress(false);
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private class VMWARE_LockVM extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;
        private ACLMessage acknowledgementMsg;

        public VMWARE_LockVM(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                hostDescription.setInProgress(false);
                block();
                return;
            }
            try {
                if (!hostDescription.isInProgress()) {
                    hostDescription.setInProgress(true);
                    vm = (VirtualMachineDescription) (msg.getContentObject());
                    if (hostDescription.isVirtualMachineHosted(vm.getId())) {
                        acknowledgementMsg = new ACLMessage(ACLMessage.CONFIRM);
                        acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Success in locking the VM");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Success in locking the VM");
                        }
                    } else {
                        hostDescription.setInProgress(false);
                        acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                        acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Failed to lock the VM. The VM is not here");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Failed to lock the VM. The VM is not here");
                        }
                    }

                } else { // if in progress, cancel protocol
                    acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                    acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                    acknowledgementMsg.addReceiver(msg.getSender());
                    acknowledgementMsg.setContent("I'm busy");
                    agt.send(acknowledgementMsg);
                }
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
                hostDescription.setInProgress(false);
            }
        }
    }

    private class VMWARE_Unlock extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;

        public VMWARE_Unlock(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_UNLOCK);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            hostDescription.setInProgress(false);
        }
    }

    private void resetAverageUsages() {
        currentTick = -1;
        for (int i = 0; i < lastCPUUsages.length; i++) {
            lastCPUUsages[i] = 0;
            lastMemoryUsages[i] = 0;
        }
    }

    private void resetThresholdFlags() {
        highCPUThresholdViolated = false;
        lowCPUThresholdViolated = false;
        highMemoryThresholdViolated = false;
        lowMemoryThresholdViolated = false;
    }

    private void resetCounters() {
        thresholdViolationCounterForHighMemory = 0;
        thresholdViolationCounterForHighCPU = 0;
        thresholdViolationCounterForLowMemory = 0;
        thresholdViolationCounterForLowCPU = 0;
    }

    private class ListenerForVMMigrations extends CyclicBehaviour { // This behaviour is involved in both VMWARE load balancing and Distributed Load Balancing

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;

        public ListenerForVMMigrations(Agent a) {
            this.agt = a;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_REGISTRATION_DUE_TO_VM_MIGRATION);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            } else {

                try {
                    vm = (VirtualMachineDescription) (msg.getContentObject());
                    if (operationOverVM(vm, "migration", null, null, null)) { // if it can host the VM
                        if (Consts.LOG) {
                            int sourceCoalition = Integer.valueOf(hostAgentToCoalition.get(vm.getPreviousOwnerId()).replace("HostAgent", ""));
                            int destinationCoalition = Integer.valueOf(hostAgentToCoalition.get(vm.getOwnerId()).replace("HostAgent", ""));
                            System.out.println("{\"source_coalition\":" + String.valueOf(sourceCoalition) + ", \"destination_coalition\":" + String.valueOf(destinationCoalition) + ", \"migrationType\":\"" + vm.getMigrationType() + "\"" + ", \"origin\":\"" + vm.getPreviousOwnerId() + "\"" + ", \"destination\":\"" + vm.getOwnerId() + "\"" + ", \"vmid\":\"" + vm.getId() + "\"" + ", \"distance\":" + getDistance(vm.getPreviousOwnerId(), vm.getOwnerId()) + ", \"time\":" + System.currentTimeMillis() + "}");
                        }
                    } else { // it cannot host the vm
                        if (!Consts.LOG) {
                            System.out.println(hostDescription.getId() + " failed to migrate VM: " + vm);
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println(ex);
                    }
                }
                resetAverageUsages();
                resetCounters();
                resetThresholdFlags();
                hostDescription.setInProgress(false);
            }
        }
    }

    private class RequestsReceiver extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;
        private ACLMessage acknowledgementMsg;
        private Object[] vmAgentParams;

        public RequestsReceiver(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_VM_ALLOCATION);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                msg = receive(mt);
                if (msg == null) {
                    hostDescription.setInProgress(false);
                    block();
                    return;
                }
                try {
                    hostDescription.setInProgress(true);
                    vm = (VirtualMachineDescription) (msg.getContentObject());
                    if (operationOverVM(vm, "initialAllocation", null, null, null)) { // if it can host the VM
                        acknowledgementMsg = new ACLMessage(ACLMessage.INFORM);
                        acknowledgementMsg.setConversationId(vm.getConversationId());
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Successful allocation");
                        if (!Consts.LOG) {
                            System.out.println("Successful allocation " + vm.getVirtualMachineId());
                        }
                        agt.send(acknowledgementMsg);
                        //Create VM agent;
                        vmAgentParams = new Object[1];
                        vm.setOwnerId(hostDescription.getId());
                        vmAgentParams[0] = vm;
                        getContainerController().createNewAgent(vm.getVirtualMachineId(), "agentbasedloadbalancing.VirtualMachineAgent", vmAgentParams);
                        getContainerController().getAgent(String.valueOf(vm.getVirtualMachineId())).start();
                    } else { // it cannot host the vm
                        acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                        acknowledgementMsg.setConversationId(vm.getConversationId());
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Failed allocation. The server cannot host the VM");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Failed allocation. The server cannot host the VM");
                        }
                    }
                } catch (Exception ex) {
                    hostDescription.setInProgress(false);
                    if (Consts.EXCEPTIONS) {
                        System.out.println(ex);
                    }
                }
                hostDescription.setInProgress(false);
            }
        }
    }

    private class VMWARE_RemoveAndMigrateVM extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;
        private ACLMessage acknowledgementMsg;
        private Object[] vmAgentParams;

        public VMWARE_RemoveAndMigrateVM(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_VM_MIGRATION);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                msg = receive(mt);
                if (msg == null) {
                    block();
                    hostDescription.setInProgress(false);
                    return;
                }
                try {
                    hostDescription.setInProgress(true);
                    vm = (VirtualMachineDescription) (msg.getContentObject());
                    if (operationOverVM(vm, "initialAllocation", null, null, null)) { // if it can host the VM
                        acknowledgementMsg = new ACLMessage(ACLMessage.INFORM);
                        acknowledgementMsg.setConversationId(vm.getConversationId());
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Successful allocation");
                        if (!Consts.LOG) {
                            System.out.println("Successful allocation " + vm.getVirtualMachineId());
                        }
                        agt.send(acknowledgementMsg);
                        vmAgentParams = new Object[2];
                        vm.setOwnerId(hostDescription.getId());
                        vmAgentParams[0] = vm;
                        getContainerController().createNewAgent(vm.getVirtualMachineId(), "agentbasedloadbalancing.VirtualMachineAgent", vmAgentParams);
                        getContainerController().getAgent(String.valueOf(vm.getVirtualMachineId())).start();
                    } else { // it cannot host the vm
                        acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                        acknowledgementMsg.setConversationId(vm.getConversationId());
                        acknowledgementMsg.addReceiver(msg.getSender());
                        acknowledgementMsg.setContent("Failed allocation. The server cannot host the VM");
                        agt.send(acknowledgementMsg);
                        if (!Consts.LOG) {
                            System.out.println("Failed allocation. The server cannot host the VM");
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println(ex);
                    }
                }
                hostDescription.setInProgress(false);
            }
        }
    }

    private class MonitorListener extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private VirtualMachineDescription vm;

        public MonitorListener(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_MONITOR_VM);
        }

        @Override
        public synchronized void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                vm = (VirtualMachineDescription) (msg.getContentObject());
                updateVirtualMachineResourceConsumption(vm);
                updateHostResourceConsumption();
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private void updateVirtualMachineResourceConsumption(VirtualMachineDescription vmDescriptionToBeUpdated) {
        try {
            for (int i = 0; i < hostDescription.getVirtualMachinesHosted().size(); i++) {
                if (hostDescription.getVirtualMachinesHosted().get(i).getId().equals(vmDescriptionToBeUpdated.getId())) {
                    hostDescription.getVirtualMachinesHosted().get(i).setCPUUsage(vmDescriptionToBeUpdated.getCPUUsage());
                    hostDescription.getVirtualMachinesHosted().get(i).setMemoryUsage(vmDescriptionToBeUpdated.getMemoryUsage());
                    break;
                }
            }
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println(ex);
            }
        }
    }

    private void updateHostResourceConsumption() {
        double sumMemoryUsage = 0;  // percentage
        double sumCPUUsage = 0;  // percentage
        double memoryUsage = 0;
        double CPUUsage = 0;
        for (int i = 0; i < hostDescription.getVirtualMachinesHosted().size(); i++) {
            sumCPUUsage = sumCPUUsage + ((hostDescription.getVirtualMachinesHosted().get(i).getCPUUsage() / 100) * hostDescription.getVirtualMachinesHosted().get(i).getNumberOfVirtualCores());
            sumMemoryUsage = sumMemoryUsage + (hostDescription.getVirtualMachinesHosted().get(i).getMemoryUsage() / 100) * hostDescription.getVirtualMachinesHosted().get(i).getMemory();
        }
        if (hostDescription.getVirtualMachinesHosted().size() > 0) {
            memoryUsage = (100 * sumMemoryUsage) / hostDescription.getMemory();
            if (memoryUsage > 100) {
                memoryUsage = 100;
            }
            hostDescription.setMemoryUsage(memoryUsage);
            CPUUsage = (100 * sumCPUUsage) / hostDescription.getNumberOfVirtualCores();
            if (CPUUsage > 100) {
                CPUUsage = 100;
            }
            hostDescription.setCPUUsage(CPUUsage);
        } else {
            hostDescription.setMemoryUsage(0);
            hostDescription.setCPUUsage(0);
        }
        hostAgentGUI.updateResourceConsumption();
    }

    public class VirtualMachineKiller extends SimpleBehaviour {

        private long timeout;
        private long wakeupTime;
        private boolean terminated = false;
        private Agent agt;

        public VirtualMachineKiller(Agent agt, long timeout) {
            this.agt = agt;
            this.timeout = timeout;
        }

        @Override
        public void onStart() {
            wakeupTime = System.currentTimeMillis() + timeout;
        }

        @Override
        public void action() {
            long dt = wakeupTime - System.currentTimeMillis();
            if (dt <= 0) {
                handleElapsedTimeout();
            } else {
                block(dt);
            }
        }

        protected void handleElapsedTimeout() {
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                resetAverageUsages();
                resetCounters();
                resetThresholdFlags();
                terminated = true;
                if (hostDescription.getVirtualMachinesHosted().size() > 0) {
                    operationOverVM(null, "randomDeparture", null, null, null);
                }
                hostDescription.setInProgress(false);
            }
        }

        @Override
        public boolean done() {
            if (terminated) {
                long delay = (long) (Consts.AVG_INTERDEPARTURE_TIME * (-Math.log(Math.random()))); //  Departure process is Poisson Distributed
                agt.addBehaviour(new VirtualMachineKiller(agt, delay));
            }
            return terminated;
        }
    }

    private VirtualMachineDescription randomlySelectVMForMigration() {
        ArrayList<VirtualMachineDescription> availableVMs = new ArrayList<>(hostDescription.getVirtualMachinesHosted());
        Predicate<VirtualMachineDescription> condition = virtualMachineDescription -> virtualMachineDescription.isLock() == true;
        availableVMs.removeIf(condition);

        if (availableVMs.size() > 0) {
            return availableVMs.get((new Random()).nextInt(availableVMs.size()));// If a VM can be migrated
        } else {
            return null;
        }
    }

    private boolean operationOverVM(VirtualMachineDescription vm, String operation, String type, String source, String destination) { // This methods is only executed when inProgress is set to False. This prevents datarace conditions due to behaviours' concurrent access to VMs
        switch (operation) {
            case "initialAllocation":
                if (vm.getMemory() <= hostDescription.getAvailableMemory() && vm.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) { // if the host has sufficient resources to allocate the VM
                    hostDescription.setMemoryUsed(hostDescription.getMemoryUsed() + vm.getMemory());
                    hostDescription.setNumberOfVirtualCoresUsed(hostDescription.getNumberOfVirtualCoresUsed() + vm.getNumberOfVirtualCores());
                    try {
                        vm.setContainerName(this.getContainerController().getContainerName());
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(ex);
                        }
                    }
                    vm.setPreviousOwnerId(hostDescription.getId());
                    vm.setOwnerId(hostDescription.getId());
                    hostDescription.getVirtualMachinesHosted().add(vm);
                    updateHostResourceConsumption();
                    return true; // success
                }
                return false; // failed

            case "migration":
                if (vm.getMemory() <= hostDescription.getAvailableMemory() && vm.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) { // if the host has sufficient resources to allocate the VM
                    hostDescription.setMemoryUsed(hostDescription.getMemoryUsed() + vm.getMemory());
                    hostDescription.setNumberOfVirtualCoresUsed(hostDescription.getNumberOfVirtualCoresUsed() + vm.getNumberOfVirtualCores());
                    try {
                        vm.setContainerName(this.getContainerController().getContainerName());
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(e);
                        }
                    }
                    hostDescription.getVirtualMachinesHosted().add(vm);
                    updateHostResourceConsumption();
                    return true; // success
                } else {
                    if (!Consts.LOG) {
                        System.out.println("ERROR to allocate VM: insufficient resources");
                    }
                }
                return false; // failed

            case "randomDeparture":
                if (hostDescription.getVirtualMachinesHosted().size() > 0) { // If a VM can be removed
                    VirtualMachineDescription randomlySelectedVM = hostDescription.getVirtualMachinesHosted().get((new Random()).nextInt(hostDescription.getVirtualMachinesHosted().size()));
                    hostDescription.getVirtualMachinesHosted().remove(randomlySelectedVM);
                    try {
                        getContainerController().getAgent(randomlySelectedVM.getVirtualMachineId()).suspend();
                        getContainerController().getAgent(randomlySelectedVM.getVirtualMachineId()).kill();
                        if (!Consts.LOG) {
                            System.out.println(hostDescription.getId() + " killed " + randomlySelectedVM.getVirtualMachineId());
                        }
                        hostDescription.setMemoryUsed(hostDescription.getMemoryUsed() - randomlySelectedVM.getMemory());
                        hostDescription.setNumberOfVirtualCoresUsed(hostDescription.getNumberOfVirtualCoresUsed() - randomlySelectedVM.getNumberOfVirtualCores());
                        updateHostResourceConsumption();
                        return true; // success
                    } catch (jade.wrapper.ControllerException e) {
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(e);
                        }
                    }
                }
                return false;
            case "removeAndMigrate":
                Predicate<VirtualMachineDescription> conditionForRemoval = virtualMachineDescription -> virtualMachineDescription.getId().equals(vm.getId());
                if (hostDescription.getVirtualMachinesHosted().removeIf(conditionForRemoval)) { // If the VM was found and can be removed.
                    try {
                        hostDescription.setMemoryUsed(hostDescription.getMemoryUsed() - vm.getMemory());
                        hostDescription.setNumberOfVirtualCoresUsed(hostDescription.getNumberOfVirtualCoresUsed() - vm.getNumberOfVirtualCores());
                        updateHostResourceConsumption();

                        ACLMessage migrationRequest = new ACLMessage(ACLMessage.REQUEST);
                        migrationRequest.setConversationId(Consts.CONVERSATION_MIGRATE);
                        migrationRequest.addReceiver(new AID(vm.getVirtualMachineId(), AID.ISLOCALNAME));
                        vm.setMigrationType(type);
                        migrationRequest.setContentObject((VirtualMachineDescription) vm);
                        send(migrationRequest);
                        if (!Consts.LOG) {
                            System.out.println(hostDescription.getId() + " at " + this.getContainerController().getContainerName() + " migrated " + vm.getVirtualMachineId() + " to " + vm.getOwnerId() + " at " + vm.getContainerName());
                        }
                        resetAverageUsages();
                        resetCounters();
                        resetThresholdFlags();
                        hostDescription.setInProgress(false);
                        return true; // success
                    } catch (Exception e) {
                        hostDescription.setInProgress(false);
                        if (Consts.EXCEPTIONS) {
                            System.out.println(e);
                        }
                    }
                } else {
                    if (!Consts.LOG) {
                        System.out.println("Error: failure to remove VM prior to migrate it to other host");
                    }
                    resetAverageUsages();
                    resetCounters();
                    resetThresholdFlags();
                    hostDescription.setInProgress(false);
                }
                return false; // failed

            default:
                if (!Consts.LOG) {
                    System.out.println("ERROR: unknown operation type over VM.");
                }
        }
        return false; // failure
    }

    private class PerformanceReporterAndThresholdMonitoring extends TickerBehaviour {

        private Agent agt;
        private ACLMessage msg;
        private double totalCPUUsage = 0;
        private double totalMemoryUsage = 0;

        public PerformanceReporterAndThresholdMonitoring(Agent agt, long period) {
            super(agt, period);
            this.agt = agt;
            currentTick = -1;
        }

        @Override
        protected void onTick() {
            try {
                updateHostResourceConsumption();
                msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(hostDescription.getAllocatorId(), AID.ISLOCALNAME));
                msg.setConversationId(Consts.CONVERSATION_MONITOR_HOST);
                msg.setContentObject((java.io.Serializable) hostDescription);
                send(msg);
                if ((configuration.getLOAD_BALANCING_TYPE() == Consts.DISTRIBUTED_FIXED_COALITIONS)) {
                    if (Consts.MIGRATION_TRIGGER_TYPE == Consts.MIGRATION_TRIGGER_BASED_ON_COUNTERS) {
                        if (hostDescription.getCPUUsage() > hostDescription.getHighMigrationThresholdForCPU()) {
                            thresholdViolationCounterForHighCPU++;
                        } else if (thresholdViolationCounterForHighCPU > 0) {
                            thresholdViolationCounterForHighCPU--;
                        }
                        if (hostDescription.getMemoryUsage() > hostDescription.getHighMigrationThresholdForMemory()) {
                            thresholdViolationCounterForHighMemory++;
                        } else if (thresholdViolationCounterForHighMemory > 0) {
                            thresholdViolationCounterForHighMemory--;
                        }
                        if (hostDescription.getCPUUsage() < hostDescription.getLowMigrationThresholdForCPU()) {
                            thresholdViolationCounterForLowCPU++;
                        } else if (thresholdViolationCounterForLowCPU > 0) {
                            thresholdViolationCounterForLowCPU--;
                        }
                        if (hostDescription.getMemoryUsage() < hostDescription.getLowMigrationThresholdForMemory()) {
                            thresholdViolationCounterForLowMemory++;
                        } else if (thresholdViolationCounterForLowMemory > 0) {
                            thresholdViolationCounterForLowMemory--;
                        }
                        // verifying whether any counter cause a vm migration from this host agent or other host agent from the same coalition
                        if ((thresholdViolationCounterForHighCPU >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_HIGH_CPU) && Consts.BALANCE_CPU && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true);
                            resetCounters();
                            resetAverageUsages();
                            highCPUThresholdViolated = true;
                            String coalitionIdForLoadBalancing = "";
                            if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                            } else {
                                coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                            }
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_CPU, coalitionIdForLoadBalancing));

                        } else if ((thresholdViolationCounterForHighMemory >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_HIGH_MEMORY) && Consts.BALANCE_MEMORY && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true);
                            resetCounters();
                            resetAverageUsages();
                            highMemoryThresholdViolated = true;
                            String coalitionIdForLoadBalancing = "";
                            if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                            } else {
                                coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                            }
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_MEMORY, coalitionIdForLoadBalancing));
                        } else if ((thresholdViolationCounterForLowCPU >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_LOW_CPU) && Consts.BALANCE_CPU && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true);
                            resetCounters();
                            resetAverageUsages();
                            lowCPUThresholdViolated = true;
                            String coalitionIdForLoadBalancing = "";
                            if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                            } else {
                                coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                            }
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_CPU, coalitionIdForLoadBalancing));
                        } else if ((thresholdViolationCounterForLowMemory >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_LOW_MEMORY) && Consts.BALANCE_MEMORY && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true);
                            resetCounters();
                            resetAverageUsages();
                            lowMemoryThresholdViolated = true;
                            String coalitionIdForLoadBalancing = "";
                            if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                            } else {
                                coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                            }
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_MEMORY, coalitionIdForLoadBalancing));
                        }
                    } else if (Consts.MIGRATION_TRIGGER_TYPE == Consts.MIGRATION_TRIGGER_BASED_ON_AVERAGE_USAGE) {
                        currentTick++;
                        lastCPUUsages[currentTick] = hostDescription.getCPUUsage();
                        lastMemoryUsages[currentTick] = hostDescription.getMemoryUsage();
                        if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {
                            totalCPUUsage = 0;
                            totalMemoryUsage = 0;
                            for (int i = 0; i < Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE; i++) {
                                totalCPUUsage += lastCPUUsages[i];
                                totalMemoryUsage += lastMemoryUsages[i];
                            }
                            double averageCPUUsage = totalCPUUsage / (double) lastCPUUsages.length; // average CPU usage within a time window
                            double averageMemoryUsage = totalMemoryUsage / (double) lastMemoryUsages.length; // average Memory usage within a time window
                            if ((averageCPUUsage > hostDescription.getHighMigrationThresholdForCPU()) && (hostDescription.getVirtualMachinesHosted().size() > 0) && Consts.BALANCE_CPU && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true);
                                currentTick = -1;
                                resetCounters();
                                resetAverageUsages();
                                highCPUThresholdViolated = true;
                                String coalitionIdForLoadBalancing = "";
                                if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                    coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                                } else {
                                    coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                                }
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_CPU, coalitionIdForLoadBalancing));
                            } else if ((averageMemoryUsage > hostDescription.getHighMigrationThresholdForMemory()) && Consts.BALANCE_MEMORY && (hostDescription.getVirtualMachinesHosted().size() > 0) && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true);
                                currentTick = -1;
                                resetCounters();
                                resetAverageUsages();
                                highMemoryThresholdViolated = true;
                                String coalitionIdForLoadBalancing = "";
                                if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                    coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                                } else {
                                    coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                                }
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_MEMORY, coalitionIdForLoadBalancing));

                            } else if ((averageCPUUsage < hostDescription.getLowMigrationThresholdForCPU()) && Consts.BALANCE_CPU && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true);
                                currentTick = -1;
                                resetCounters();
                                resetAverageUsages();
                                lowCPUThresholdViolated = true;
                                String coalitionIdForLoadBalancing = "";
                                if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                    coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                                } else {
                                    coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                                }
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_CPU, coalitionIdForLoadBalancing));
                            } else if ((averageMemoryUsage < hostDescription.getLowMigrationThresholdForMemory()) && Consts.BALANCE_MEMORY && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true);
                                currentTick = -1;
                                resetCounters();
                                resetAverageUsages();
                                lowMemoryThresholdViolated = true;
                                String coalitionIdForLoadBalancing = "";
                                if (Consts.ONLY_INTRA_LOAD_BALANCING) {
                                    coalitionIdForLoadBalancing = hostDescription.getMyLeader();
                                } else {
                                    coalitionIdForLoadBalancing = selectCoalitionForLoadbalancing();
                                }
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_MEMORY, coalitionIdForLoadBalancing));
                            }
                            if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {
                                currentTick = -1;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                hostDescription.setInProgress(false);
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }

        }

    }

    private double mean(Vector responses, String resource) {
        double sum = 0.0;
        if (resource.toLowerCase().equals("cpu")) { // This is to take into account the resource usage of the INITIATOR host agent
            sum = hostDescription.getCPUUsage();
        } else if (resource.toLowerCase().equals("memory")) {
            sum = hostDescription.getMemoryUsage();
        }
        HostDescription participantHost;
        for (int i = 0; i < responses.size(); i++) {
            try {
                participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (resource.toLowerCase().equals("cpu")) {
                    sum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    sum += participantHost.getMemoryUsage();
                }
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
        return sum / (responses.size() + 1);
    }

    private double stdDev(Vector responses, String resource) {
        double summatory = 0.0;
        double mean = mean(responses, resource);
        if (resource.toLowerCase().equals("cpu")) { // This is to take into account the resource usage of the INITIATOR host agent
            summatory = Math.pow(hostDescription.getCPUUsage() - mean, 2);
        } else if (resource.toLowerCase().equals("memory")) {
            summatory = Math.pow(hostDescription.getMemoryUsage() - mean, 2);
        }
        HostDescription participantHost;
        for (int i = 0; i < responses.size(); i++) {
            try {
                participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (resource.toLowerCase().equals("cpu")) {
                    summatory += Math.pow(participantHost.getCPUUsage() - mean, 2);
                } else if (resource.toLowerCase().equals("memory")) {
                    summatory += Math.pow(participantHost.getMemoryUsage() - mean, 2);
                }
            } catch (UnreadableException ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
        return Math.sqrt(summatory / (responses.size() + 1));
    }

    private int[] calculateNewThresholds(Vector responses) {
        int[] thresholds = new int[4];
        // thresholds[0]  low CPU 
        // thresholds[1]  high CPU 
        // thresholds[2]  low Memory 
        // thresholds[3]  high Memory
        thresholds[0] = (int) Math.round(mean(responses, "CPU")) - configuration.getTARGET_STD_DEV();
        thresholds[1] = (int) Math.round(mean(responses, "CPU")) + configuration.getTARGET_STD_DEV();
        thresholds[2] = (int) Math.round(mean(responses, "Memory")) - configuration.getTARGET_STD_DEV();
        thresholds[3] = (int) Math.round(mean(responses, "Memory")) + configuration.getTARGET_STD_DEV();
        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] > 100) {
                thresholds[i] = 100;
            }
            if (thresholds[i] < 0) {
                thresholds[i] = 0;
            }
        }
        return thresholds;
    }

    private Decision selectHostAgentBasedOnCoalitionUtility(Vector responses, int loadBalancingCause) {
        Heuristics heuristics = new Heuristics(hostDescription, loadBalancingCause, responses, edges, configuration.getHEURISTIC());
        HostDescription selectedHost = heuristics.getSelectedHost();
        VirtualMachineDescription selectedVM = heuristics.getSelectedVM();
        try {
            switch (loadBalancingCause) {
                case Consts.MIGRATION_CAUSE_HIGH_CPU:
                case Consts.MIGRATION_CAUSE_HIGH_MEMORY:
                    if (selectedVM != null && selectedHost != null) {
                        if ((selectedVM.getNumberOfVirtualCores() <= selectedHost.getAvailableVirtualCores()) && (selectedVM.getMemory() <= selectedHost.getAvailableMemory())) {
                            return new Decision(this.hostDescription, selectedHost, selectedVM, Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                        } else {
                            System.out.println("WARNING. (AtoB) failed migration FROM " + hostDescription.getId() + " TO " + selectedHost.getId() + " WITH VM " + selectedVM.getId() + " and a valuation of " + heuristics.getValuationValue());
                            return new Decision(new HostDescription(), new HostDescription(), selectedVM, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }
                    } else {
                        if (!Consts.LOG) {
                            System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_ = " + loadBalancingCause);
                        }
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                case Consts.MIGRATION_CAUSE_LOW_CPU:
                case Consts.MIGRATION_CAUSE_LOW_MEMORY:
                    if (selectedVM != null && selectedHost != null) {
                        if ((selectedVM.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (selectedVM.getMemory() <= hostDescription.getAvailableMemory())) {
                            return new Decision(selectedHost, this.hostDescription, selectedVM, Consts.DECISION_TYPE_MIGRATE_FROM_B_TO_A);
                        } else {
                            System.out.println("WARNING. (BtoA) failed migration FROM " + selectedHost.getId() + " TO " + hostDescription.getId() + " THE VM " + selectedVM.getId() + " and a valuation of " + heuristics.getValuationValue());
                            return new Decision(new HostDescription(), new HostDescription(), selectedVM, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }
                    } else { // External host agents do not have VMs to migrate, so no load balancing is possible.
                        if (!Consts.LOG) {
                            System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_ = " + loadBalancingCause);
                        }
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                default:
                    if (!Consts.LOG) {
                        System.out.println("Error: Unknown load balancing cause");
                    }
                    return null;
            }
        } catch (Exception ex) {
            if (!Consts.LOG) {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }
            ex.printStackTrace();
        }
        if (!Consts.LOG) {
            System.out.println("Error: For some reason, no agent was selected. Load balancing cause " + loadBalancingCause);
        }
        return null;
    }


    private class CNPInitiatorForIntraLoadBalancingAtoB extends OneShotBehaviour { // Initiator of Contract Net Protocol for VM Migration

        private Agent agt;
        private Decision decision;
        private int loadBalancingCause;
        private int numberOfPotentialRespondents;
        private ACLMessage callForProposalsForLoadBalancing;
        private String coalitionIdForLoadBalancing;

        public CNPInitiatorForIntraLoadBalancingAtoB(Agent agt, int loadBalancingCause, String coalitionIdForLoadBalancing) {
            super(null);
            this.loadBalancingCause = loadBalancingCause;
            this.agt = agt;
            this.decision = new Decision();
            this.coalitionIdForLoadBalancing = coalitionIdForLoadBalancing;
        }

        @Override
        public void action() {
            try {
                ArrayList<String> coalitionMembers = coalitionToHostAgents.get(coalitionIdForLoadBalancing);
                callForProposalsForLoadBalancing = new ACLMessage(ACLMessage.CFP);
                numberOfPotentialRespondents = 0;
                for (int i = 0; i < coalitionMembers.size(); i++) {
                    if (!coalitionMembers.get(i).equals(hostDescription.getId())) {
                        callForProposalsForLoadBalancing.addReceiver(new AID(coalitionMembers.get(i), AID.ISLOCALNAME));
                        numberOfPotentialRespondents++;
                    }
                }
                callForProposalsForLoadBalancing.setSender(agt.getAID());
                callForProposalsForLoadBalancing.setConversationId(Consts.CONVERSATION_LOAD_BALANCING_A_TO_B);
                callForProposalsForLoadBalancing.setContent(String.valueOf(loadBalancingCause));
                callForProposalsForLoadBalancing.setReplyWith(String.valueOf(agt.getLocalName() + "-" + String.valueOf(conversationId)));
                callForProposalsForLoadBalancing.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                callForProposalsForLoadBalancing.setReplyByDate(new Date(System.currentTimeMillis() + Consts.TIMEOUT_FOR_CNP_INITIATOR_FOR_LOAD_BALANCING));
                if (!Consts.LOG) {
                    System.out.println("****** Initiator of CNP for intra load balancing from A to B " + agt.getLocalName());
                }
                conversationId++;
                addBehaviour(new ContractNetInitiator(agt, callForProposalsForLoadBalancing) {
                    @Override
                    protected void handleNotUnderstood(ACLMessage notUnderstood) {
                        hostDescription.setInProgress(false);
                    }

                    @Override
                    protected void handleOutOfSequence(ACLMessage notUnderstood) {
                        hostDescription.setInProgress(false);
                    }

                    @Override
                    protected void handleRefuse(ACLMessage refuse) {
                        if (!Consts.LOG) {
                            System.out.println(refuse.getSender().getName() + " refused to participate for some reason");
                        }
                    }

                    @Override
                    protected void handleFailure(ACLMessage failure) {
                        hostDescription.setInProgress(false);
                        if (failure.getSender().equals(myAgent.getAMS())) {
                            if (!Consts.LOG) {
                                System.out.println("Respondent does not exist");
                            }
                        } else {
                            if (!Consts.LOG) {
                                System.out.println(failure.getSender().getName() + " failed");
                            }
                        }
                        numberOfPotentialRespondents--;
                    }

                    @Override
                    protected void handleAllResponses(Vector responses, Vector acceptances) {
                        if (responses.size() < numberOfPotentialRespondents) {
                            if (!Consts.LOG) {
                                System.out.println(agt.getName() + " - Timeout expired: missing " + (numberOfPotentialRespondents - responses.size()) + " responses");
                            }
                        }

                        if (responses.size() > 0) {
                            boolean proposalAccepted = false;
                            Enumeration e = responses.elements();

                            if (responses.size() > 0) {
                                decision = selectHostAgentBasedOnCoalitionUtility(responses, loadBalancingCause);
                                if (decision != null) {
                                    // Updating thresholds based on current load
                                    int[] thresholds = calculateNewThresholds(responses);
                                    decision.setLowMigrationThresholdForCPU(thresholds[0]);
                                    decision.setHighMigrationThresholdForCPU(thresholds[1]);
                                    decision.setLowMigrationThresholdForMemory(thresholds[2]);
                                    decision.setHighMigrationThresholdForMemory(thresholds[3]);
                                    hostDescription.setLowMigrationThresholdForCPU(thresholds[0]);
                                    hostDescription.setHighMigrationThresholdForCPU(thresholds[1]);
                                    hostDescription.setLowMigrationThresholdForMemory(thresholds[2]);
                                    hostDescription.setHighMigrationThresholdForMemory(thresholds[3]);
                                    if (!Consts.LOG) {
                                        System.out.println("I " + agt.getAID() + " updated his migration thresholds because it initiated A to B CNP ");
                                    }
                                    while (e.hasMoreElements()) {
                                        ACLMessage msg = (ACLMessage) e.nextElement();
                                        if (msg.getPerformative() == ACLMessage.PROPOSE) {
                                            ACLMessage reply = msg.createReply();
                                            if (msg.getSender().getLocalName().equals(decision.getDestinationHost().getId())) {
                                                try {
                                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                                    reply.setContentObject(decision);
                                                    acceptances.addElement(reply);
                                                    proposalAccepted = true;
                                                    if (!Consts.LOG) {
                                                        System.out.println("ACCEPT - " + msg.getSender().getLocalName() + " = " + decision.getDestinationHost().getId());
                                                    }
                                                } catch (Exception ex) {
                                                    if (Consts.EXCEPTIONS) {
                                                        System.out.println(ex);
                                                    }
                                                }
                                            } else {
                                                try {
                                                    if (!Consts.LOG) {
                                                        System.out.println("REJECT - " + msg.getSender().getLocalName() + " = " + decision.getDestinationHost().getId());
                                                    }
                                                    reply.setContentObject(decision);
                                                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                                                    acceptances.addElement(reply);
                                                } catch (IOException ex) {
                                                    if (Consts.EXCEPTIONS) {
                                                        System.out.println(ex);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (proposalAccepted) {
                                if (!Consts.LOG) {
                                    System.out.println("Agent " + decision.getDestinationHost().getId() + " was selected for Load Balancing from A to B. Load balancing cause " + loadBalancingCause);
                                }

                            } else { // if the VM was not accepted for any member of coalition, unlock it and start inter_load balancing if enabled.
                                if (!Consts.LOG) {
                                    System.out.println("No agent was selected for Intra Load Balancing from A to B. Load balancing cause " + loadBalancingCause);
                                }
                                if (!Consts.LOG) {
                                    System.out.println("The decision was " + decision.getDecision());
                                }
                                resetCounters();
                                resetThresholdFlags();
                                hostDescription.setInProgress(false);
                            }
                        } else { // if no agent replied to the cfp, unlock vm
                            if (!Consts.LOG) {
                                System.out.println("No agent replied to cfp. Load balancing cause " + loadBalancingCause);
                            }
                            resetCounters();
                            resetThresholdFlags();
                            hostDescription.setInProgress(false);
                        }
                    }

                    @Override
                    protected void handleInform(ACLMessage inform) { // I'll use this as an acknowledge from the selected hostAgent, once it acknowledges that the VM has been accepted and that there are sufficient resources to host it, I can remove the vm
                        if ((loadBalancingCause == Consts.MIGRATION_CAUSE_HIGH_CPU) || (loadBalancingCause == Consts.MIGRATION_CAUSE_HIGH_MEMORY)) { // it means this HostAgent will migrate one of his VMs to other HostAgents
                            if (!Consts.LOG) {
                                System.out.println("Agent " + inform.getSender().getName() + " confirms that it will host the VM and that sufficient resources have been allocated");
                            }
                            decision.getSelectedVM().setContainerName(inform.getContent());
                            decision.getSelectedVM().setPreviousOwnerId(hostDescription.getId());
                            decision.getSelectedVM().setOwnerId(inform.getSender().getLocalName());
                            operationOverVM(decision.getSelectedVM(), "removeAndMigrate", "AtoB", null, null);
                            if (configuration.isBALANCING_ONLY_ONE_COALITION_AT_A_TIME())
                                agt.addBehaviour(new ResetDatacenterLoadBalancingCounters(agt));
                            resetAverageUsages();
                            resetCounters();
                            resetThresholdFlags();
                        } else {
                            if (!Consts.LOG) {
                                System.out.println("ERROR: Unknown load balancing cause");
                            }
                            resetAverageUsages();
                            resetCounters();
                            resetThresholdFlags();
                            hostDescription.setInProgress(false);
                        }
                    }
                });
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private class CNPInitiatorForIntraLoadBalancingBtoA extends OneShotBehaviour { // Initiator of Contract Net Protocol for VM Migration

        private Agent agt;
        private Decision decision;
        private int loadBalancingCause;
        private int numberOfPotentialRespondents;
        private String coalitionIdForLoadBalancing;

        public CNPInitiatorForIntraLoadBalancingBtoA(Agent agt, int loadBalancingCause, String coalitionIdForLoadBalancing) {
            super(null);
            this.loadBalancingCause = loadBalancingCause;
            this.agt = agt;
            this.decision = new Decision();
            this.coalitionIdForLoadBalancing = coalitionIdForLoadBalancing;
        }

        @Override
        public void action() {
            try {
                ArrayList<String> coalitionMembers = coalitionToHostAgents.get(coalitionIdForLoadBalancing);
                ACLMessage callForProposalsForLoadBalancing = new ACLMessage(ACLMessage.CFP);
                numberOfPotentialRespondents = 0;
                for (int i = 0; i < coalitionMembers.size(); i++) {
                    if (!coalitionMembers.get(i).equals(hostDescription.getId())) {
                        callForProposalsForLoadBalancing.addReceiver(new AID(coalitionMembers.get(i), AID.ISLOCALNAME));
                        numberOfPotentialRespondents++;
                    }
                }
                callForProposalsForLoadBalancing.setSender(agt.getAID());
                callForProposalsForLoadBalancing.setConversationId(Consts.CONVERSATION_LOAD_BALANCING_B_TO_A);
                callForProposalsForLoadBalancing.setContent(String.valueOf(loadBalancingCause));
                callForProposalsForLoadBalancing.setReplyWith(String.valueOf(agt.getLocalName() + "-" + String.valueOf(conversationId)));
                callForProposalsForLoadBalancing.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                callForProposalsForLoadBalancing.setReplyByDate(new Date(System.currentTimeMillis() + Consts.TIMEOUT_FOR_CNP_INITIATOR_FOR_LOAD_BALANCING));
                conversationId++;
                addBehaviour(new ContractNetInitiator(agt, callForProposalsForLoadBalancing) {
                    @Override
                    protected void handleNotUnderstood(ACLMessage notUnderstood) {
                        hostDescription.setInProgress(false);
                    }

                    @Override
                    protected void handleOutOfSequence(ACLMessage notUnderstood) {
                        hostDescription.setInProgress(false);
                    }

                    @Override
                    protected void handleRefuse(ACLMessage refuse) {
                        if (!Consts.LOG) {
                            System.out.println(refuse.getSender().getName() + " refused to participate for some reason");
                        }
                    }

                    @Override
                    protected void handleFailure(ACLMessage failure) {
                        hostDescription.setInProgress(false);
                        if (failure.getSender().equals(myAgent.getAMS())) {
                            if (!Consts.LOG) {
                                System.out.println("Respondent does not exist");
                            }
                        } else {
                            if (!Consts.LOG) {
                                System.out.println(failure.getSender().getName() + " failed");
                            }
                        }
                        numberOfPotentialRespondents--;
                    }

                    @Override
                    protected void handleAllResponses(Vector responses, Vector acceptances) {
                        if (responses.size() < numberOfPotentialRespondents) {
                            if (!Consts.LOG) {
                                System.out.println(agt.getName() + " - Timeout expired: missing " + (numberOfPotentialRespondents - responses.size()) + " responses");
                            }
                        }

                        if (responses.size() > 0) {
                            boolean proposalAccepted = false;
                            Enumeration e = responses.elements();

                            if (responses.size() > 0) {
                                decision = selectHostAgentBasedOnCoalitionUtility(responses, loadBalancingCause);
                                if (decision != null) {
                                    // Updating thresholds based on current load
                                    int[] thresholds = calculateNewThresholds(responses);
                                    decision.setLowMigrationThresholdForCPU(thresholds[0]);
                                    decision.setHighMigrationThresholdForCPU(thresholds[1]);
                                    decision.setLowMigrationThresholdForMemory(thresholds[2]);
                                    decision.setHighMigrationThresholdForMemory(thresholds[3]);
                                    hostDescription.setLowMigrationThresholdForCPU(thresholds[0]);
                                    hostDescription.setHighMigrationThresholdForCPU(thresholds[1]);
                                    hostDescription.setLowMigrationThresholdForMemory(thresholds[2]);
                                    hostDescription.setHighMigrationThresholdForMemory(thresholds[3]);
                                    if (!Consts.LOG) {
                                        if (!Consts.LOG) {
                                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because it initiated B to A CNP ");
                                        }
                                    }
                                    while (e.hasMoreElements()) {
                                        ACLMessage msg = (ACLMessage) e.nextElement();
                                        if (msg.getPerformative() == ACLMessage.PROPOSE) {
                                            ACLMessage reply = msg.createReply();
                                            if (msg.getSender().getLocalName().equals(decision.getSourceHost().getId())) {
                                                try {
                                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                                    reply.setContentObject(decision);
                                                    acceptances.addElement(reply);
                                                    proposalAccepted = true;
                                                    if (!Consts.LOG) {
                                                        System.out.println("ACCEPT - " + msg.getSender().getLocalName() + " = " + decision.getSourceHost().getId());
                                                    }
                                                } catch (IOException ex) {
                                                    if (Consts.EXCEPTIONS) {
                                                        System.out.println(ex);
                                                    }
                                                }
                                            } else {
                                                try {
                                                    if (!Consts.LOG) {
                                                        System.out.println("REJECT - " + msg.getSender().getLocalName() + " = " + decision.getSourceHost().getId());
                                                    }
                                                    reply.setContentObject(decision);
                                                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                                                    acceptances.addElement(reply);
                                                } catch (Exception ex) {
                                                    if (Consts.EXCEPTIONS) {
                                                        System.out.println(ex);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (proposalAccepted) {
                                if (!Consts.LOG) {
                                    System.out.println("Agent " + decision.getSourceHost().getId() + " was selected for Load Balancing from B to A. Load balancing cause " + loadBalancingCause);
                                }
                            } else { // if the VM was not accepted for any member of coalition, unlock it
                                if (!Consts.LOG) {
                                    System.out.println("No agent was selected for Load Balancing from B to A. Load balancing cause " + loadBalancingCause);
                                }
                                if (!Consts.LOG) {
                                    System.out.println("The decision was " + decision.getDecision());
                                }
                                resetCounters();
                                resetThresholdFlags();
                                hostDescription.setInProgress(false);
                            }
                        } else { // if no agent replied to the cfp, unlock vm
                            if (!Consts.LOG) {
                                System.out.println("No agent replied to cfp. Load balancing cause " + loadBalancingCause);
                            }
                            resetCounters();
                            resetThresholdFlags();
                            hostDescription.setInProgress(false);
                        }
                    }

                    @Override
                    protected void handleInform(ACLMessage inform) { // I'll use this as an acknowledge from the selected hostAgent, once it acknowledges that the VM has been accepted and that there are sufficient resources to host it, I can remove the vm
                        if ((loadBalancingCause == Consts.MIGRATION_CAUSE_LOW_CPU) || (loadBalancingCause == Consts.MIGRATION_CAUSE_LOW_MEMORY)) {
                            if (!Consts.LOG) {
                                System.out.println("Agent " + inform.getSender().getName() + " confirms that it will send the VM");
                            }
                        } else {
                            if (!Consts.LOG) {
                                System.out.println("ERROR: Unknown load balancing cause");
                            }
                            resetAverageUsages();
                            resetCounters();
                            resetThresholdFlags();
                            hostDescription.setInProgress(false);
                        }
                    }
                });
            } catch (Exception ex) {
                hostDescription.setInProgress(false);
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private class CNPParticipantForIntraLoadBalancingBtoA extends OneShotBehaviour {

        private Agent agt;

        public CNPParticipantForIntraLoadBalancingBtoA(Agent agt) {
            this.agt = agt;
        }

        @Override
        public synchronized void action() {
            if (!Consts.LOG) {
                System.out.println("Agent " + getLocalName() + " waiting for CFP for Intra Load Balancing from B to A ...");
            }

            MessageTemplate subtemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET), MessageTemplate.MatchConversationId(Consts.CONVERSATION_LOAD_BALANCING_B_TO_A));
            MessageTemplate template = MessageTemplate.and(subtemplate, MessageTemplate.MatchPerformative(ACLMessage.CFP));

            addBehaviour(new ContractNetResponder(agt, template) {
                @Override
                protected void handleOutOfSequence(ACLMessage msg) {
                    hostDescription.setInProgress(false);
                }

                @Override
                protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                    resetAverageUsages();
                    resetCounters();
                    return handleCallForProposals(agt, cfp);  // handleCallForProposals sets inProgress to true;
                }

                @Override
                protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                    ACLMessage inform = accept.createReply();
                    try {
                        inform.setPerformative(ACLMessage.INFORM);
                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " accept proposal from Agent " + cfp.getSender());
                        }
                        inform.setContent(agt.getContainerController().getContainerName());
                        Decision decision = (Decision) accept.getContentObject();
                        decision.getSelectedVM().setContainerName(decision.getDestinationHost().getContainerName());
                        decision.getSelectedVM().setPreviousOwnerId(hostDescription.getId());
                        decision.getSelectedVM().setOwnerId(accept.getSender().getLocalName());
                        operationOverVM(decision.getSelectedVM(), "removeAndMigrate", "BtoA", null, null);
                        if (configuration.isBALANCING_ONLY_ONE_COALITION_AT_A_TIME())
                            agt.addBehaviour(new ResetDatacenterLoadBalancingCounters(agt));
                        resetAverageUsages();
                        resetCounters();
                        resetThresholdFlags();
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of acceptance BtoA");
                        }
                        if (decision.getSourceHost().getCoalition() == decision.getDestinationHost().getCoalition()) {
                            hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                            hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                            hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                            hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        }
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(ex);
                        }
                    }
                    return inform;
                }

                @Override
                protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
                    try {
                        Decision decision = (Decision) reject.getContentObject();
                        if (decision.getSourceHost().getCoalition() == decision.getDestinationHost().getCoalition()) {
                            hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                            hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                            hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                            hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        }
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of rejection BtoA");
                        }
                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " got proposal rejected");
                        }
                        resetAverageUsages();
                        resetCounters();
                        resetThresholdFlags();
                        hostDescription.setInProgress(false); // if proposal rejected release the agent so it can participate in other CNPs
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(ex);
                        }
                    }
                }
            });
        }
    }

    private class CNPParticipantForIntraLoadBalancingAtoB extends OneShotBehaviour {

        private Agent agt;

        public CNPParticipantForIntraLoadBalancingAtoB(Agent agt) {
            this.agt = agt;
        }

        @Override
        public synchronized void action() {
            if (!Consts.LOG) {
                System.out.println("Agent " + getLocalName() + " waiting for CFP for Intra Load Balancing from A to B ...");
            }
            MessageTemplate subtemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET), MessageTemplate.MatchConversationId(Consts.CONVERSATION_LOAD_BALANCING_A_TO_B));
            MessageTemplate template = MessageTemplate.and(subtemplate, MessageTemplate.MatchPerformative(ACLMessage.CFP));
            addBehaviour(new ContractNetResponder(agt, template) {

                @Override
                protected void handleOutOfSequence(ACLMessage msg) {
                    hostDescription.setInProgress(false);
                }

                @Override
                protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                    resetAverageUsages();
                    resetCounters();
                    return handleCallForProposals(agt, cfp);  // handleCallForProposals sets inProgress to true; 
                }

                @Override
                protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                    ACLMessage inform = accept.createReply();
                    try {
                        Decision decision = (Decision) accept.getContentObject();
                        inform.setPerformative(ACLMessage.INFORM);
                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " accept proposal from Agent " + cfp.getSender());
                        }
                        inform.setContent(agt.getContainerController().getContainerName());
                        if (decision.getSourceHost().getCoalition() == decision.getDestinationHost().getCoalition()) {
                            hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                            hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                            hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                            hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        }
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of acceptance AtoB");
                        }
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(ex);
                        }
                    }
                    return inform;
                }

                @Override
                protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
                    try {
                        Decision decision = (Decision) reject.getContentObject();
                        if (decision.getSourceHost().getCoalition() == decision.getDestinationHost().getCoalition()) {
                            hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                            hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                            hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                            hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        }
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of rejection AtoB");
                        }
                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " got proposal rejected");
                        }
                        resetAverageUsages();
                        resetCounters();
                        resetThresholdFlags();
                        hostDescription.setInProgress(false); // if proposal rejected release the agent so it can participate in other CNPs
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(ex);
                        }
                    }
                }
            });
        }
    }

    private synchronized ACLMessage handleCallForProposals(Agent agt, ACLMessage cfp) {
        ACLMessage result = null;
        try {
            result = cfp.createReply();
            result.setPerformative(ACLMessage.PROPOSE);
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                hostDescription.setWillingToParticipateInCNP(true);
            } else {// If there are already some locked/compromised resources or the host has failed, simply refuse to participate in CFPs.
                hostDescription.setWillingToParticipateInCNP(false);
            }
            result.setContentObject(hostDescription);
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println(ex);
            }
        }
        return result;
    }
}
