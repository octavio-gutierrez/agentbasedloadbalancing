package intraloadbalancing;

import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import jade.wrapper.ControllerException;
import java.io.Serializable;

/**
 *
 * @author JGUTIERRGARC
 */
public class HostAgent extends Agent {

    private HostDescription hostDescription;
    private int conversationId;

    private double[] lastCPUUsages;
    private double[] lastMemoryUsages;

    private ArrayList<String> coalitionMembers;
    private ArrayList<HostDescription> coalitionLeaders;
    private int thresholdViolationCounterForHighCPU;  // This is counting how many times thresholds of high cpu have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForHighMemory;  // This is counting how many times thresholds of high memory have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForLowCPU;  // This is counting how many times thresholds of low cpu have been violated, the in/decreasing rate depends on the SMA's report frequency
    private int thresholdViolationCounterForLowMemory;  // This is counting how many times thresholds of low memory have been violated, the in/decreasing rate depends on the SMA's report frequency

    private boolean highCPUThresholdViolated;       // I DO NOT USE THEM. However, they might be used to include additional information.
    private boolean highMemoryThresholdViolated;
    private boolean lowCPUThresholdViolated;
    private boolean lowMemoryThresholdViolated;

    private HashSet<weightEdge> edges;
    private Utilities utils;
    transient protected HostAgentGUI hostAgentGUI; // Reference to the gui    

    public HostAgent() {
        lastCPUUsages = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
        lastMemoryUsages = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];

        hostDescription = new HostDescription();
        hostDescription.setInProgress(false);
        utils = new Utilities();
        coalitionMembers = new ArrayList<String>();
        coalitionLeaders = new ArrayList<HostDescription>();
        edges = new HashSet<weightEdge>();
        resetCounters();
        resetThresholds();
        conversationId = 0;
    }

    double getDistance(String source, String destination){
        if (source.equals(destination)){
            return 0;
        }
        Iterator<weightEdge> i = edges.iterator(); 
        while (i.hasNext()) {
                weightEdge edge = i.next();
                String in = "HostAgent"+edge.getInNode();
                String out = "HostAgent"+edge.getOutNode();
                if ( (in.equals(source) && out.equals(destination)) ||
                   (in.equals(destination) && out.equals(source)) )
                    return edge.getDistance();
        }
        return -1;
    }
    @Override
    protected void setup() {
        try {

            Object[] args = getArguments();
            hostDescription = (HostDescription) args[0];

            hostDescription.setContainerName(getContainerController().getContainerName());
            //System.out.println("Mem "+ hostDescription.getAvailableMemory());
            coalitionMembers = (ArrayList<String>) args[1];
            coalitionLeaders = (ArrayList<HostDescription>) args[2];
            edges = (HashSet)(args[4]);            
            
//            for (int i=0; i<coalitionMembers.size(); i++){
//                System.out.println(hostDescription.getId()+"-"+coalitionMembers.get(i)+ ":" + getDistance(hostDescription.getId(),coalitionMembers.get(i)));
//            }
     
//            System.out.println(hostDescription.getId()+" - NeighborsDistance -> ");            
//            Hashtable neighborsDistance = (Hashtable) args[5];
//            Enumeration<String> enumeration = neighborsDistance.keys();
//            while (enumeration.hasMoreElements()) {
//                String key = enumeration.nextElement();
//                System.out.println("Neighbor : "  + key+" Distance:"+neighborsDistance.get(key));
//            }
//            System.out.println(hostDescription.getId()+" "+hostDescription.getContainerName());

//            utils.publishService(this, "HostAgent");
            if (!Consts.LOG) {
                System.out.println(this.getLocalName() + "'s container is " + this.getContainerController().getContainerName());
            }

            hostAgentGUI = new HostAgentGUI(hostDescription);
            hostAgentGUI.setTitle(getLocalName());
            hostAgentGUI.updateResourceConsumption();
            if (Consts.HOST_AGENT_GUI) {
                hostAgentGUI.setVisible(true);
            }

            if (Consts.LOAD_BALANCING_TYPE == Consts.INTRA_DISTRIBUTED_FIXED_COALITIONS) {
                addBehaviour(new CNPParticipantForIntraLoadBalancingAtoB(this)); // the agent always listens for potential requests for Intra Load Balancing from A (this source host) to B (a destination host).
                addBehaviour(new CNPParticipantForIntraLoadBalancingBtoA(this)); // the agent always listens for potential requests for Intra Load Balancing from B (an external host) to A (this destination host).
            } else if (Consts.LOAD_BALANCING_TYPE == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                addBehaviour(new VMWARE_RemoveAndMigrateVM(this));
                addBehaviour(new VMWARE_LockVM(this));
                addBehaviour(new VMWARE_UnlockVM(this));
                addBehaviour(new VMWARE_LockResources(this));
                addBehaviour(new VMWARE_UnlockResources(this));
                addBehaviour(new VMWARE_ListenerForVMMigrations(this));
                // Add Host Agent's behaviours for centralized load balancing with no coalitions if any.
            }

            // Behaviours required for any balancing type.
            addBehaviour(new ListenerForVMMigrations(this));
            addBehaviour(new RequestsReceiver(this));
            addBehaviour(new PerformanceReporterAndThresholdMonitoring(this, Consts.HOST_REPORTING_RATE + new Random().nextInt((int) Consts.RANGE_OF_RANDOM_TICKS))); // Added a random number to prevent colitions among host agents when enacting interactions protocols.              
            addBehaviour(new VirtualMachineKiller(this, (long) (Consts.AVG_INTERDEPARTURE_TIME * (-Math.log(Math.random())))));
            addBehaviour(new MonitorListener(this));

//            if (Consts.INTER_LOAD_BALANCING_ENABLED) {
//                // add behaviours for interloadbalancing - TBD
//            }
//            System.out.println(this.hostDescription);
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println("It is here 8 "+ ex);
            }
        }
    }

    private class VMWARE_ListenerForVMMigrations extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_ListenerForVMMigrations(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_CONFIRM_MIGRATION);
        }

        @Override
        public void action() {
            if (hostDescription.isInProgress()) {
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {
                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        operationOverVM(vm, "removeAndMigrate", "AtoB");
                        //System.out.println("HERE He is 7");
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 9"+ex);
                    }
                }
            }
        }
    }

    private class VMWARE_LockResources extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_LockResources(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    hostDescription.setInProgress(false);
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {
                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (vm.getMemory() <= hostDescription.getAvailableMemory() && vm.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) { // if the host has sufficient resources to allocate the VM
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.CONFIRM);
                            acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Success in locking resources for VM migration");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Success in locking resources for VM migration");
                            }
                            //System.out.println("HERE He is 4");
                        } else {
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                            acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Failed to lock resources for VM migration due to insufficient resources");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Failed to lock resources for VM migration due to insufficient resources");
                            }
                            hostDescription.setInProgress(false);
                            //System.out.println("HERE He is 5");
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 10"+ex);
                    }
                    hostDescription.setInProgress(false);
                }
            }
        }
    }

    private class VMWARE_LockVM extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_LockVM(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    hostDescription.setInProgress(false);
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {

                        VirtualMachineDescription vm = (VirtualMachineDescription) content;

                        if (hostDescription.isVirtualMachineHosted(vm.getId())) {
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.CONFIRM);
                            acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Success in locking the VM");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Success in locking the VM");
                            }
                            //System.out.println("HERE He is 1");
                        } else {
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                            acknowledgementMsg.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Failed to lock the VM. The VM is not here");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Failed to lock the VM. The VM is not here");
                            }
                            //System.out.println("HERE He is 2");
                            hostDescription.setInProgress(false);
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 11"+ex);
                    }
                    hostDescription.setInProgress(false);
                }

            }

        }
    }

    private class VMWARE_UnlockVM extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_UnlockVM(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_VM);
        }

        @Override
        public void action() {
            if (hostDescription.isInProgress()) {
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {
                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (!Consts.LOG) {
                            System.out.println("VM " + vm.getId() + " unlocked");
                        }
                        hostDescription.setInProgress(false);
                        //System.out.println("HERE He is 3");
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 12"+ex);
                    }
                }
            }
        }
    }

    private class VMWARE_UnlockResources extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_UnlockResources(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_RESOURCES);
        }

        @Override
        public void action() {
            if (hostDescription.isInProgress()) {
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if (msg.getPerformative() == ACLMessage.REQUEST) {
                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (!Consts.LOG) {
                            System.out.println("Resources for VM " + vm.getId() + " unlocked");
                        }
                        hostDescription.setInProgress(false);
                        //System.out.println("HERE He is 33");
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 13"+ex);
                    }
                }
            }
        }
    }

    private void resetAverageUsages() {
        for (int i = 0; i < lastCPUUsages.length; i++) {
            lastCPUUsages[i] = 0;
            lastMemoryUsages[i] = 0;
        }
    }

    private void resetThresholds() {
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

        public ListenerForVMMigrations(Agent a) {
            this.agt = a;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_REGISTRATION_DUE_TO_VM_MIGRATION);
        }

        @Override
        public void action() {
            //if (hostDescription.isInProgress()) {
            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            } else {

                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {
                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (operationOverVM(vm, "migration", null)) { // if it can host the VM
                            if (!Consts.LOG) {
                                System.out.println(hostDescription.getId() + " succesful migration of VM: " + vm);
                            }
                            if (Consts.LOG) {
                                //if (Consts.LOAD_BALANCING_TYPE==Consts.INTRA_DISTRIBUTED_FIXED_COALITIONS){
                                System.out.println("{\"coalition\":" + hostDescription.getCoalition()
                                        + ", \"migrationType\":\"" + vm.getMigrationType() + "\""
                                        + ", \"origin\":\"" + vm.getPreviousOwnerId() + "\""
                                        + ", \"destination\":\"" + vm.getOwnerId() + "\""
                                        + //                                                                    ", \"who prints\":\""+ hostDescription.getId()+"\"" +                                                                            
                                        ", \"vmid\":\"" + vm.getId() + "\""
                                        + ", \"distance\":" + getDistance(vm.getPreviousOwnerId(),vm.getOwnerId())
                                        + ", \"time\":" + System.currentTimeMillis()
                                        + "}");
                                //}
                            }
                        } else { // it cannot host the vm
                            if (!Consts.LOG) {
                                System.out.println(hostDescription.getId() + " failed to migrate VM: " + vm);
                            }
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 14" +ex);
                    }
                }
                resetCounters();
                resetThresholds();
                hostDescription.setInProgress(false);
            }
            //}

        }
    }

    private class RequestsReceiver extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public RequestsReceiver(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_VM_ALLOCATION);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    hostDescription.setInProgress(false);
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {

                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (operationOverVM(vm, "initialAllocation", null)) { // if it can host the VM
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.INFORM);
                            acknowledgementMsg.setConversationId(vm.getConversationId());
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Successful allocation");
                            if (!Consts.LOG) {
                                System.out.println("Successful allocation " + vm.getVirtualMachineId());
                            }
                            agt.send(acknowledgementMsg);
                            //Create VM agent;    
                            Object[] vmAgentParams = new Object[1];
                            vm.setOwnerId(hostDescription.getId());
                            vmAgentParams[0] = vm;
                            getContainerController().createNewAgent(vm.getVirtualMachineId(), "intraloadbalancing.VirtualMachineAgent", vmAgentParams);
                            getContainerController().getAgent(String.valueOf(vm.getVirtualMachineId())).start();
                        } else { // it cannot host the vm
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                            acknowledgementMsg.setConversationId(vm.getConversationId());
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Failed allocation. The server cannot host the VM");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Failed allocation. The server cannot host the VM");
                            }
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 16"+ex);
                    }
                }
                hostDescription.setInProgress(false);
            }

        }
    }

    private class VMWARE_RemoveAndMigrateVM extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public VMWARE_RemoveAndMigrateVM(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_VM_MIGRATION);
        }

        @Override
        public void action() {
            if (!hostDescription.isInProgress()) {
                hostDescription.setInProgress(true);
                ACLMessage msg = receive(mt);
                if (msg == null) {
                    block();
                    hostDescription.setInProgress(false);
                    return;
                }
                try {
                    Object content = msg.getContentObject();
                    if ((msg.getPerformative() == ACLMessage.REQUEST) && (content instanceof VirtualMachineDescription)) {

                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        if (operationOverVM(vm, "initialAllocation", null)) { // if it can host the VM
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.INFORM);
                            acknowledgementMsg.setConversationId(vm.getConversationId());
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Successful allocation");
                            if (!Consts.LOG) {
                                System.out.println("Successful allocation " + vm.getVirtualMachineId());
                            }
                            agt.send(acknowledgementMsg);
                            //Create VM agent;    
                            Object[] vmAgentParams = new Object[2];
                            vm.setOwnerId(hostDescription.getId());
                            vmAgentParams[0] = vm;
                            getContainerController().createNewAgent(vm.getVirtualMachineId(), "intraloadbalancing.VirtualMachineAgent", vmAgentParams);
                            getContainerController().getAgent(String.valueOf(vm.getVirtualMachineId())).start();
                        } else { // it cannot host the vm
                            ACLMessage acknowledgementMsg = new ACLMessage(ACLMessage.FAILURE);
                            acknowledgementMsg.setConversationId(vm.getConversationId());
                            acknowledgementMsg.addReceiver(msg.getSender());
                            acknowledgementMsg.setContent("Failed allocation. The server cannot host the VM");
                            agt.send(acknowledgementMsg);
                            if (!Consts.LOG) {
                                System.out.println("Failed allocation. The server cannot host the VM");
                            }
                        }
                    }
                } catch (Exception ex) {
                    if (Consts.EXCEPTIONS) {
                        System.out.println("It is here 17"+ex);
                    }
                }
                hostDescription.setInProgress(false);
            }

        }
    }

    private class MonitorListener extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public MonitorListener(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_MONITOR_VM);
        }

        @Override
        public synchronized void action() {

            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                Object content = msg.getContentObject();
                if ((msg.getPerformative() == ACLMessage.INFORM) && (content instanceof VirtualMachineDescription)) {
                    VirtualMachineDescription vm = (VirtualMachineDescription) content;
                    updateVirtualMachineResourceConsumption(vm);
                    updateHostResourceConsumption();
                }
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here 18"+ex);
                }
            }

        }
    }

    private void updateVirtualMachineResourceConsumption(VirtualMachineDescription vmDescriptionToBeUpdated) {
        try {
            for (VirtualMachineDescription vmDescription : hostDescription.getVirtualMachinesHosted()) {
                if (vmDescription.equals(vmDescriptionToBeUpdated)) {
                    vmDescription.setCPUUsage(vmDescriptionToBeUpdated.getCPUUsage());
                    vmDescription.setMemoryUsage(vmDescriptionToBeUpdated.getMemoryUsage());
                    break;
                }
            }

        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println("It is here 19"+ex);
            }
        }
    }

    private void updateHostResourceConsumption() {
        double sumMemoryUsage = 0;  // percentage
        double sumCPUUsage = 0;  // percentage
        double memoryUsage = 0;
        double CPUUsage = 0;
        for (VirtualMachineDescription vmDescription : hostDescription.getVirtualMachinesHosted()) {
            sumCPUUsage = sumCPUUsage + ((vmDescription.getCPUUsage() / 100) * vmDescription.getNumberOfVirtualCores());
            sumMemoryUsage = sumMemoryUsage + (vmDescription.getMemoryUsage() / 100) * vmDescription.getMemory();
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
                if (hostDescription.getVirtualMachinesHosted().size() > 0) {
                    operationOverVM(null, "randomDeparture", null);
                }
                terminated = true;
                resetCounters();
                resetThresholds();
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

    private boolean operationOverVM(VirtualMachineDescription vm, String operation, String type) { // This methods is only executed when inProgress is set to False. This prevents datarace conditions due to behaviours' concurrent access to VMs
        switch (operation) {

            case "initialAllocation":
                if (vm.getMemory() <= hostDescription.getAvailableMemory() && vm.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) { // if the host has sufficient resources to allocate the VM
                    hostDescription.setMemoryUsed(hostDescription.getMemoryUsed() + vm.getMemory());
                    hostDescription.setNumberOfVirtualCoresUsed(hostDescription.getNumberOfVirtualCoresUsed() + vm.getNumberOfVirtualCores());
                    try {
                        vm.setContainerName(this.getContainerController().getContainerName());
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 20" +ex);
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
                            System.out.println("It is here 21"+e);
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
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 22"+e);
                        }
                    }
                }
                return false; // failed 
            case "removeAndMigrate":
                if (!Consts.LOG) {
                    System.out.println("VM ready to be deleted and migrated " + vm.getId());
                }
//                System.out.println("VM ready to be deleted and migrated " + vm.getId()+ " from "+ vm.getPreviousOwnerId() + " to "+ vm.getOwnerId()+ " who printed "+ hostDescription.getId());
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
                        resetCounters();
                        resetThresholds();
                        hostDescription.setInProgress(false);
                        return true; // success
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 23"+e);
                        }
                    }
                } else {
                    if (!Consts.LOG) {
                        System.out.println("Error: failure to remove VM prior to migrate it to other host");
                    }
                    resetCounters();
                    resetThresholds();
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
        private int currentTick; // this is to keep track of the time window when Consts.MIGRATION_TRIGGER_TYPE == Consts.MIGRATION_TRIGGER_BASED_ON_AVERAGE_USAGE

        public PerformanceReporterAndThresholdMonitoring(Agent agt, long period) {
            super(agt, period);
            this.agt = agt;
            currentTick = -1;
        }

        @Override
        protected void onTick() {

            updateHostResourceConsumption();
            try {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(hostDescription.getAllocatorId(), AID.ISLOCALNAME));
                msg.setConversationId(Consts.CONVERSATION_MONITOR_HOST);
                msg.setContentObject((java.io.Serializable) hostDescription);
                send(msg);

                if (Consts.LOAD_BALANCING_TYPE == Consts.INTRA_DISTRIBUTED_FIXED_COALITIONS) {

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

                        // verifying wheather any counter cause a vm migration from this host agent or other host agent from the same coalition
                        if ((thresholdViolationCounterForHighCPU >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_HIGH_CPU) && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                            resetCounters();
                            highCPUThresholdViolated = true;
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_CPU));
                        } else if ((thresholdViolationCounterForHighMemory >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_HIGH_MEMORY) && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                            resetCounters();
                            highMemoryThresholdViolated = true;
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_MEMORY));
                        } else if ((thresholdViolationCounterForLowCPU >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_LOW_CPU) && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                            resetCounters();
                            lowCPUThresholdViolated = true;
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_CPU));
                        } else if ((thresholdViolationCounterForLowMemory >= Consts.MAX_THRESHOLD_VIOLATION_COUNTER_FOR_LOW_MEMORY) && (!hostDescription.isInProgress())) {
                            hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                            resetCounters();
                            lowMemoryThresholdViolated = true;
                            agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_MEMORY));
                        }

                    } else if (Consts.MIGRATION_TRIGGER_TYPE == Consts.MIGRATION_TRIGGER_BASED_ON_AVERAGE_USAGE) {

                        currentTick++;

                        lastCPUUsages[currentTick] = hostDescription.getCPUUsage();
                        lastMemoryUsages[currentTick] = hostDescription.getMemoryUsage();

                        if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {

                            double totalCPUUsage = 0;
                            double totalMemoryUsage = 0;

                            for (int i = 0; i < Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE; i++) {
                                totalCPUUsage += lastCPUUsages[i];
                                totalMemoryUsage += lastMemoryUsages[i];
                            }

                            double averageCPUUsage = totalCPUUsage / (double) lastCPUUsages.length; // average CPU usage within a time window
                            double averageMemoryUsage = totalMemoryUsage / (double) lastMemoryUsages.length; // average Memory usage within a time window
                            //System.out.println("*********** "+ averageCPUUsage+ " "+ hostDescription.getHighMigrationThresholdForCPU());                            
                            if ((averageCPUUsage > hostDescription.getHighMigrationThresholdForCPU()) && (hostDescription.getVirtualMachinesHosted().size() > 0) && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                                currentTick = -1;
                                resetCounters();// unique
                                highCPUThresholdViolated = true;
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_CPU));
                            } else if ((averageMemoryUsage > hostDescription.getHighMigrationThresholdForMemory()) && (hostDescription.getVirtualMachinesHosted().size() > 0) && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                                currentTick = -1;
                                resetCounters();// unique
                                highMemoryThresholdViolated = true;
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingAtoB(agt, Consts.MIGRATION_CAUSE_HIGH_MEMORY));
                            } else if ((averageCPUUsage < hostDescription.getLowMigrationThresholdForCPU()) && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                                currentTick = -1;
                                resetCounters();// unique
                                lowCPUThresholdViolated = true;
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_CPU));
                            } else if ((averageMemoryUsage < hostDescription.getLowMigrationThresholdForMemory()) && (!hostDescription.isInProgress())) {
                                hostDescription.setInProgress(true); // to be released once the load balancing algorithm has been executed.
                                currentTick = -1;
                                resetCounters();// unique
                                lowMemoryThresholdViolated = true;
                                agt.addBehaviour(new CNPInitiatorForIntraLoadBalancingBtoA(agt, Consts.MIGRATION_CAUSE_LOW_MEMORY));
                            }

                            if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {
                                currentTick = -1;
                            }
                        }
                    }

                    if (hostDescription.isInProgress()) {
                        if (!Consts.LOG) {
                            System.out.println("Agent " + agt.getLocalName() + " In progress");
                        }
                    }

                }

            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here 24"+ex);
                }
            }

        }

    }

    // Vector responses is a vector of <ACLMessage>. It can be iterated using an enumeration object -> Enumeration e = responses.elements()
    // Each ACLMessage contains a host description including its virtual machines. To access message content:
    // ACLMessage msg = (ACLMessage) e.nextElement();
    // (HostDescription)msg.getContentObject()
    private double mean(Vector responses, String resource) {
        Object copy_responses = (Vector) responses.clone();
        ((Vector) copy_responses).add(null);
        double sum = 0.0;

        if (resource.toLowerCase().equals("cpu")) { // This is to take into account the resource usage of the INITIATOR host agent 
            sum = hostDescription.getCPUUsage();
        } else if (resource.toLowerCase().equals("memory")) {
            sum = hostDescription.getMemoryUsage();
        }

        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts

        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (resource.toLowerCase().equals("cpu")) {
                    sum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    sum += participantHost.getMemoryUsage();
                }
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here 25"+ex);
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

        Enumeration participantHosts = responses.elements();  // responses from all the other (PARTICIPANTS) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (resource.toLowerCase().equals("cpu")) {
                    summatory += Math.pow(participantHost.getCPUUsage() - mean, 2);
                } else if (resource.toLowerCase().equals("memory")) {
                    summatory += Math.pow(participantHost.getMemoryUsage() - mean, 2);
                }
            } catch (UnreadableException ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here 26"+ex);
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

        thresholds[0] = (int) Math.round(mean(responses, "CPU")) - Consts.TARGET_STD_DEV;
        thresholds[1] = (int) Math.round(mean(responses, "CPU")) + Consts.TARGET_STD_DEV;

        thresholds[2] = (int) Math.round(mean(responses, "Memory")) - Consts.TARGET_STD_DEV;
        thresholds[3] = (int) Math.round(mean(responses, "Memory")) + Consts.TARGET_STD_DEV;

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

/// BEGIN WORKING ON DANIEL
    /// aquí modifica Daniel
// Vector responses is a vector of <ACLMessage>. It can be iterated using an enumeration object -> Enumeration e = responses.elements()
// Each ACLMessage contains a host description including its virtual machines. To access message content:
// ACLMessage msg = (ACLMessage) e.nextElement();
// (HostDescription)msg.getContentObject()
    private Decision selectHostAgentBasedOnCoalitionUtility(Vector responses, int loadBalancingCause) {
        Heuristics heuristics = new Heuristics(hostDescription, loadBalancingCause, responses);
        HostDescription selectedHost = null;
        VirtualMachineDescription selectedVM = null;

        /*
    System.out.println("I am host "+hostDescription.getId() + " with " +
            hostDescription.getNumberOfVirtualCores() + " cores and " +
            hostDescription.getMemory() + " GB of memory." +
            " Reserved: " + hostDescription.getNumberOfVirtualCoresUsed() + " cores and " +
            hostDescription.getMemoryUsed() + "GB; " +
            "CPUUsage=" + hostDescription.getCPUUsage() + "% and MemoryUsage=" + hostDescription.getMemoryUsage()+"%");
    System.out.println("Valuating the neighborhood: ");
         */
        try {
            // the heuristics
            heuristics.heuristic_exhaustive();
            //heuristics.heuristic_maxMinHostUsage();
            //heuristics.heuristic_roulette_wheel();

            selectedHost = heuristics.getSelectedHost();
            selectedVM = heuristics.getSelectedVM();

            // perform the migration
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
                            System.out.println("WARNING. (AtoB) failed migration FROM " + selectedHost.getId() + " TO " + hostDescription.getId() + " THE VM " + selectedVM.getId() + " and a valuation of " + heuristics.getValuationValue());
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
        return null; // if no agent was selected for any reason return a null host description. This will reject all the participant agents' proposals. 
    }
/// END     WORKING ON DANIEL
/*
    private Decision selectHostAgentBasedOnCoalitionUtility(Vector responses, int loadBalancingCause) {
        try {

            switch (loadBalancingCause) {

                case Consts.MIGRATION_CAUSE_HIGH_CPU: // select a recipient host agent at random and select one of this host agent's virtual machines at random.
                    ACLMessage msgOfSelectedHostAgentHighCPU = (ACLMessage) responses.get((new Random()).nextInt(responses.size())); // select a host agent at random.
                    HostDescription selectedHostHighCPU = (HostDescription) msgOfSelectedHostAgentHighCPU.getContentObject();
                    VirtualMachineDescription selectedVMHighCPU = hostDescription.getVirtualMachinesHosted().get((new Random()).nextInt(hostDescription.getVirtualMachinesHosted().size()));// select one of my VMs at random.                        
                    if (selectedVMHighCPU != null && selectedHostHighCPU != null) {
                        if ((selectedVMHighCPU.getNumberOfVirtualCores() <= selectedHostHighCPU.getAvailableVirtualCores()) && (selectedVMHighCPU.getMemory() <= selectedHostHighCPU.getAvailableMemory())) {
                            return new Decision(this.hostDescription, selectedHostHighCPU, selectedVMHighCPU, Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                        } else {
                            return new Decision(new HostDescription(), new HostDescription(), selectedVMHighCPU, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }

                    } else {
                        if (!Consts.LOG) System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_HIGH_CPU");
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                case Consts.MIGRATION_CAUSE_LOW_CPU: // select an external host agent at random and select one of his virtual machines at random.
                    ArrayList<HostDescription> possibleCPUHosts = new ArrayList<>();
                    Enumeration eCPU = responses.elements();
                    while (eCPU.hasMoreElements()) {
                        HostDescription host = (HostDescription) ((ACLMessage) eCPU.nextElement()).getContentObject();
                        if (host != null) {
                            if (host.getVirtualMachinesHosted() != null) {
                                if (host.getVirtualMachinesHosted().size() > 0) // if the host has VMs add it to the pool of possible hosts.
                                {
                                    possibleCPUHosts.add(host);
                                }
                            }
                        }
                    }
                    HostDescription selectedHostLowCPU;
                    VirtualMachineDescription selectedVMLowCPU;
                    if (possibleCPUHosts.size() > 0) {
                        selectedHostLowCPU = possibleCPUHosts.get((new Random()).nextInt(possibleCPUHosts.size()));
                        selectedVMLowCPU = selectedHostLowCPU.getVirtualMachinesHosted().get((new Random()).nextInt(selectedHostLowCPU.getVirtualMachinesHosted().size()));// select one of his VMs at random.                            
                        if ((selectedVMLowCPU.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (selectedVMLowCPU.getMemory() <= hostDescription.getAvailableMemory())) {
                            return new Decision(selectedHostLowCPU, this.hostDescription, selectedVMLowCPU, Consts.DECISION_TYPE_MIGRATE_FROM_B_TO_A);
                        } else {
                            return new Decision(new HostDescription(), new HostDescription(), selectedVMLowCPU, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }
                    } else { // External host agents do not have VMs to migrate, so no load balancing is possible.
                        if (!Consts.LOG) System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_LOW_CPU");
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                case Consts.MIGRATION_CAUSE_HIGH_MEMORY: // select a recipient host agent at random and select one of this host agent's virtual machines at random.
                    ACLMessage msgOfSelectedHostAgentHighMemory = (ACLMessage) responses.get((new Random()).nextInt(responses.size())); // select a host agent at random.
                    HostDescription selectedHostHighMemory = (HostDescription) msgOfSelectedHostAgentHighMemory.getContentObject();
                    VirtualMachineDescription selectedVMHighMemory = hostDescription.getVirtualMachinesHosted().get((new Random()).nextInt(hostDescription.getVirtualMachinesHosted().size()));// select one of my VMs at random.                        
                    if (selectedVMHighMemory != null && selectedHostHighMemory != null) {
                        if ((selectedVMHighMemory.getNumberOfVirtualCores() <= selectedHostHighMemory.getAvailableVirtualCores()) && (selectedVMHighMemory.getMemory() <= selectedHostHighMemory.getAvailableMemory())) {
                            return new Decision(this.hostDescription, selectedHostHighMemory, selectedVMHighMemory, Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                        } else {
                            return new Decision(new HostDescription(), new HostDescription(), selectedVMHighMemory, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }

                    } else {
                        if (!Consts.LOG) System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_HIGH_MEMORY");
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                case Consts.MIGRATION_CAUSE_LOW_MEMORY:
                    ArrayList<HostDescription> possibleMemoryHosts = new ArrayList<>();
                    Enumeration eMemory = responses.elements();
                    while (eMemory.hasMoreElements()) {
                        HostDescription host = (HostDescription) ((ACLMessage) eMemory.nextElement()).getContentObject();
                        if (host != null) {
                            if (host.getVirtualMachinesHosted() != null) {
                                if (host.getVirtualMachinesHosted().size() > 0) // if the host has VMs add it to the pool of possible hosts.
                                {
                                    possibleMemoryHosts.add(host);
                                }
                            }
                        }
                    }
                    HostDescription selectedHostLowMemory;
                    VirtualMachineDescription selectedVMLowMemory;
                    if (possibleMemoryHosts.size() > 0) {
                        selectedHostLowMemory = possibleMemoryHosts.get((new Random()).nextInt(possibleMemoryHosts.size()));
                        selectedVMLowMemory = selectedHostLowMemory.getVirtualMachinesHosted().get((new Random()).nextInt(selectedHostLowMemory.getVirtualMachinesHosted().size()));// select one of his VMs at random.                            
                        if ((selectedVMLowMemory.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (selectedVMLowMemory.getMemory() <= hostDescription.getAvailableMemory())) {
                            return new Decision(selectedHostLowMemory, this.hostDescription, selectedVMLowMemory, Consts.DECISION_TYPE_MIGRATE_FROM_B_TO_A);
                        } else {
                            return new Decision(new HostDescription(), new HostDescription(), selectedVMLowMemory, Consts.DECISION_TYPE_DONT_MIGRATE);
                        }
                    } else { // External host agents do not have VMs to migrate, so no load balancing is possible.
                        if (!Consts.LOG) System.out.println("ERROR: No host agent was selected because no one replied - Consts.MIGRATION_CAUSE_LOW_MEMORY");
                        return new Decision(new HostDescription(), new HostDescription(), new VirtualMachineDescription(), Consts.DECISION_TYPE_DONT_MIGRATE);
                    }

                default:
                    if (!Consts.LOG) System.out.println("Error: Unknown load balancing cause");
                    return null;
            }
        } catch (UnreadableException ex) {
            if (Consts.EXCEPTIONS) System.out.println(ex);
        }
        if (!Consts.LOG) System.out.println("Error: For some reason, no agent was selected");
        return null; // if no agent was selected for any reason return a null host description. This will reject all the participant agents' proposals. 
    }
     */
    private class CNPInitiatorForIntraLoadBalancingAtoB extends OneShotBehaviour { // Initiator of Contract Net Protocol for VM Migration

        private Agent agt;
        private Decision decision;
        private int loadBalancingCause;
        private int numberOfPotentialRespondents;

        public CNPInitiatorForIntraLoadBalancingAtoB(Agent agt, int loadBalancingCause) {
            super(null);
            this.loadBalancingCause = loadBalancingCause;
            this.agt = agt;
            this.decision = new Decision();
            this.numberOfPotentialRespondents = coalitionMembers.size() - 1;
        }

        @Override
        public void action() {
            try {

                ACLMessage callForProposalsForLoadBalancing = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < coalitionMembers.size(); i++) {
                    if (!coalitionMembers.get(i).equals(hostDescription.getId())) {
                        callForProposalsForLoadBalancing.addReceiver(new AID(coalitionMembers.get(i), AID.ISLOCALNAME));
                    }
                }

                

                callForProposalsForLoadBalancing.setSender(agt.getAID());
                callForProposalsForLoadBalancing.setConversationId(Consts.CONVERSATION_INTRA_LOAD_BALANCING_A_TO_B);
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
                    protected void handleRefuse(ACLMessage refuse) {
                        if (!Consts.LOG) {
                            System.out.println(refuse.getSender().getName() + " refused to participate for some reason");
                        }
                    }

                    @Override
                    protected void handleFailure(ACLMessage failure) {
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
                                //////////////////// Simple and random selection of hostAgents. This should be done based on the coalition's utility
                                decision = selectHostAgentBasedOnCoalitionUtility(responses, loadBalancingCause);
                                if (decision != null) {

                                    // Updating thresholds based on current load
                                    int[] thresholds = calculateNewThresholds(responses);
                                    // thresholds[0]  low CPU 
                                    // thresholds[1]  high CPU 
                                    // thresholds[2]  low Memory 
                                    // thresholds[3]  high Memory                                    
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

                                                    //reply.setContent(agt.getContainerController().getContainerName());
                                                    reply.setContentObject(decision);
                                                    acceptances.addElement(reply);
                                                    proposalAccepted = true;
                                                    if (!Consts.LOG) {
                                                        System.out.println("ACCEPT - " + msg.getSender().getLocalName() + " = " + decision.getDestinationHost().getId());
                                                    }
                                                } catch (Exception ex) {
                                                    if (Consts.EXCEPTIONS) {
                                                        System.out.println("It is here 27"+ex);
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
                                                        System.out.println("Is it here 2"+ex);
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

                                if (Consts.INTER_LOAD_BALANCING_ENABLED && (decision.getDecision() == Consts.DECISION_TYPE_DONT_MIGRATE)) { // enact inter load balancing protocol 
                                    // enact inter load balancing protocol 
                                } else { //if inter load balancing is not enabled then just clean up and reset thresholds
                                    resetCounters();
                                    resetThresholds();
                                    hostDescription.setInProgress(false);
                                }
                            }
                        } else { // if no agent replied to the cfp, unlock vm 
                            if (!Consts.LOG) {
                                System.out.println("No agent replied to cfp. Load balancing cause " + loadBalancingCause);
                            }
                            resetCounters();
                            resetThresholds();
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
                            operationOverVM(decision.getSelectedVM(), "removeAndMigrate", "AtoB");
                        } else {
                            if (!Consts.LOG) {
                                System.out.println("ERROR: Unknown load balancing cause");
                            }
                            resetCounters();
                            resetThresholds();
                            hostDescription.setInProgress(false);
                        }

                    }

                });

            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here"+ex);
                }
            }

        }

    }

    private class CNPInitiatorForIntraLoadBalancingBtoA extends OneShotBehaviour { // Initiator of Contract Net Protocol for VM Migration

        private Agent agt;
        private Decision decision;
        private int loadBalancingCause;
        private int numberOfPotentialRespondents;

        public CNPInitiatorForIntraLoadBalancingBtoA(Agent agt, int loadBalancingCause) {
            super(null);
            this.loadBalancingCause = loadBalancingCause;
            this.agt = agt;
            this.decision = new Decision();
            this.numberOfPotentialRespondents = coalitionMembers.size() - 1;
        }

        @Override
        public void action() {
            try {

                ACLMessage callForProposalsForLoadBalancing = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < coalitionMembers.size(); i++) {
                    if (!coalitionMembers.get(i).equals(hostDescription.getId())) {
                        callForProposalsForLoadBalancing.addReceiver(new AID(coalitionMembers.get(i), AID.ISLOCALNAME));
                    }
                }
                callForProposalsForLoadBalancing.setSender(agt.getAID());
                callForProposalsForLoadBalancing.setConversationId(Consts.CONVERSATION_INTRA_LOAD_BALANCING_B_TO_A);
                callForProposalsForLoadBalancing.setContent(String.valueOf(loadBalancingCause));

                callForProposalsForLoadBalancing.setReplyWith(String.valueOf(agt.getLocalName() + "-" + String.valueOf(conversationId)));
                callForProposalsForLoadBalancing.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                callForProposalsForLoadBalancing.setReplyByDate(new Date(System.currentTimeMillis() + Consts.TIMEOUT_FOR_CNP_INITIATOR_FOR_LOAD_BALANCING));
                if (!Consts.LOG) {
                    System.out.println("****** Initiator of CNP for load balancing from B to A " + agt.getLocalName());
                }
                conversationId++;

                addBehaviour(new ContractNetInitiator(agt, callForProposalsForLoadBalancing) {

                    @Override
                    protected void handleRefuse(ACLMessage refuse) {
                        if (!Consts.LOG) {
                            System.out.println(refuse.getSender().getName() + " refused to participate for some reason");
                        }
                    }

                    @Override
                    protected void handleFailure(ACLMessage failure) {
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

                                //////////////////// Simple and random selection of hostAgents. This should be done based on the coalition's utility
                                decision = selectHostAgentBasedOnCoalitionUtility(responses, loadBalancingCause);

                                if (decision != null) {

                                    // Updating thresholds based on current load
                                    int[] thresholds = calculateNewThresholds(responses);
                                    // thresholds[0]  low CPU 
                                    // thresholds[1]  high CPU 
                                    // thresholds[2]  low Memory 
                                    // thresholds[3]  high Memory                                    
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
                                                        System.out.println("It is here 28"+ex);
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
                                                        System.out.println("It is here 29"+ex);
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
                                //Once the VM has been received or migrated inProgress will be set to false
                            } else { // if the VM was not accepted for any member of coalition, unlock it
                                if (!Consts.LOG) {
                                    System.out.println("No agent was selected for Load Balancing from B to A. Load balancing cause " + loadBalancingCause);
                                }
                                if (!Consts.LOG) {
                                    System.out.println("The decision was " + decision.getDecision());
                                }
                                resetCounters();
                                resetThresholds();
                                hostDescription.setInProgress(false);
                            }
                        } else { // if no agent replied to the cfp, unlock vm 
                            if (!Consts.LOG) {
                                System.out.println("No agent replied to cfp. Load balancing cause " + loadBalancingCause);
                            }
                            resetCounters();
                            resetThresholds();
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
                            resetCounters();
                            resetThresholds();
                            hostDescription.setInProgress(false);
                        }

                    }

                });

            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println("It is here 30"+ex);
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

            MessageTemplate subtemplate = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
                    MessageTemplate.MatchConversationId(Consts.CONVERSATION_INTRA_LOAD_BALANCING_B_TO_A));
            MessageTemplate template = MessageTemplate.and(subtemplate,
                    MessageTemplate.MatchPerformative(ACLMessage.CFP));

            addBehaviour(new ContractNetResponder(agt, template) {
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
                        //decision.getSelectedVM().setContainerName(agt.getContainerController().getContainerName());
                        decision.getSelectedVM().setPreviousOwnerId(hostDescription.getId());
                        decision.getSelectedVM().setOwnerId(accept.getSender().getLocalName());
                        operationOverVM(decision.getSelectedVM(), "removeAndMigrate", "BtoA");

                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of acceptance BtoA");
                        }
                        hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                        hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                        hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                        hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());

                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 31"+ex);
                        }
                    }
                    return inform;
                }

                @Override
                protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
                    try {
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of rejection BtoA");
                        }
                        Decision decision = (Decision) reject.getContentObject();
                        hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                        hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                        hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                        hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());

                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " got proposal rejected");
                        }
                        resetCounters();
                        resetThresholds();
                        hostDescription.setInProgress(false); // if proposal rejected release the agent so it can participate in other CNPs
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 32"+ex);
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

            MessageTemplate subtemplate = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
                    MessageTemplate.MatchConversationId(Consts.CONVERSATION_INTRA_LOAD_BALANCING_A_TO_B));
            MessageTemplate template = MessageTemplate.and(subtemplate,
                    MessageTemplate.MatchPerformative(ACLMessage.CFP));

            addBehaviour(new ContractNetResponder(agt, template) {
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

                        hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                        hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                        hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                        hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of acceptance AtoB");
                        }
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 4: "+ex);
                        }
                    }
                    return inform;
                }

                @Override
                protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
                    try {
                        Decision decision = (Decision) reject.getContentObject();
                        hostDescription.setLowMigrationThresholdForCPU(decision.getLowMigrationThresholdForCPU());
                        hostDescription.setHighMigrationThresholdForCPU(decision.getHighMigrationThresholdForCPU());
                        hostDescription.setLowMigrationThresholdForMemory(decision.getLowMigrationThresholdForMemory());
                        hostDescription.setHighMigrationThresholdForMemory(decision.getHighMigrationThresholdForMemory());
                        if (!Consts.LOG) {
                            System.out.println("I " + agt.getAID() + " updated his migration thresholds because of rejection AtoB");
                        }
                        if (!Consts.LOG) {
                            System.out.println("Agent " + getLocalName() + " got proposal rejected");
                        }
                        resetCounters();
                        resetThresholds();
                        hostDescription.setInProgress(false); // if proposal rejected release the agent so it can participate in other CNPs
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println("It is here 5: "+ex);
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
            } else if (hostDescription.isInProgress()) { // If there are already some locked/compromised resources, simply refuse to participate in CFPs.
                hostDescription.setWillingToParticipateInCNP(false);
            }
            result.setContentObject(hostDescription);
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println("Is it here 6"+ex);
            }
        }
        return result;
    }
}
