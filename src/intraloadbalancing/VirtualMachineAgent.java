/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import static intraloadbalancing.Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS;

import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * @author octavio
 */
public class VirtualMachineAgent extends Agent {

    private VirtualMachineDescription virtualMachineDescription;
    private boolean reportToOwner;

    private final double[] muCPUUsageLongTermMean = {Consts.LOW_LONGTERM_MEAN_FOR_CPU_USAGE,
            Consts.MEDIUM_LONGTERM_MEAN_FOR_CPU_USAGE,
            Consts.HIGH_LONGTERM_MEAN_FOR_CPU_USAGE};

    private final double[] muMemoryUsageyLongTermMean = {Consts.LOW_LONGTERM_MEAN_FOR_MEMORY_USAGE,
            Consts.MEDIUM_LONGTERM_MEAN_FOR_MEMORY_USAGE,
            Consts.HIGH_LONGTERM_MEAN_FOR_MEMORY_USAGE};

    private double currentCPUUsage;

    private double currentMemoryUsage;

    @Override
    protected void setup() {
        Object[] args = getArguments();

        virtualMachineDescription = (VirtualMachineDescription) args[0];
        reportToOwner = true;
        currentCPUUsage = muCPUUsageLongTermMean[virtualMachineDescription.getCPUProfile()];
        currentMemoryUsage = muMemoryUsageyLongTermMean[virtualMachineDescription.getMemoryProfile()];

        addBehaviour(new PerformanceReporter(this, Consts.VM_REPORTING_RATE));
        addBehaviour(new ListenForMigrationRequests(this));

    }

    @Override
    protected void beforeMove() {
        //virtualMachineDescription.setPreviousOwnerId(MSG_QUEUE_CLASS);
        if (!Consts.LOG) System.out.println(getLocalName() + " BEFORE migration");
        reportToOwner = false;
    }

    @Override
    protected void afterMove() {
        if (!Consts.LOG) System.out.println(getLocalName() + " AFTER migration");
        reportToOwner = true;
        addBehaviour(new RegisterWithNewOwner(this));
    }

    protected void setVirtualMachineDescripton(VirtualMachineDescription virtualMachine) {
        this.virtualMachineDescription = virtualMachine;
    }

    protected VirtualMachineDescription getVirtualMachineDescription() {
        return this.virtualMachineDescription;
    }

    private void updateVirtualMachineResourceConsumption() { // This was extracted from "Black-box and Gray-box Strategies for Virtual Machine Migration"
        double sigmaCPU = 3;
        double rhoCPU = 0.995; // corelation degree 0 - 1 
        double rn = Math.random();
        double z = (Math.pow(rn, 0.135) - Math.pow((1 - rn), 0.135)) / 0.1975;
        double eCPU = z * sigmaCPU;
        currentCPUUsage = muCPUUsageLongTermMean[virtualMachineDescription.getCPUProfile()] + rhoCPU * (currentCPUUsage - muCPUUsageLongTermMean[virtualMachineDescription.getCPUProfile()]) + eCPU;

        double sigmaMemory = 3;
        double rhoMemory = 0.995; // corelation degree 0 - 1 
        rn = Math.random();
        z = (Math.pow(rn, 0.135) - Math.pow((1 - rn), 0.135)) / 0.1975;
        double eMemory = z * sigmaMemory;
        currentMemoryUsage = muMemoryUsageyLongTermMean[virtualMachineDescription.getMemoryProfile()] + rhoMemory * (currentMemoryUsage - muMemoryUsageyLongTermMean[virtualMachineDescription.getMemoryProfile()]) + eMemory;

        if (currentCPUUsage >= 100) {
            currentCPUUsage = 100;
        } else if (currentCPUUsage <= 0) {
            currentCPUUsage = 0;
        }

        if (currentMemoryUsage >= 100) {
            currentMemoryUsage = 100;
        } else if (currentMemoryUsage <= 0) {
            currentMemoryUsage = 0;
        }

        virtualMachineDescription.setCPUUsage(currentCPUUsage);
        virtualMachineDescription.setMemoryUsage(currentMemoryUsage);

    }

    ///////// AGENT BEHAVIOURS' IMPLEMENTATIONS ///////////

    private class PerformanceReporter extends TickerBehaviour {

        public PerformanceReporter(Agent a, long period) {
            super(a, period);
        }

        @Override
        public void onTick() {
            updateVirtualMachineResourceConsumption();

            try {
                if (reportToOwner) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(virtualMachineDescription.getOwnerId(), AID.ISLOCALNAME));
                    msg.setConversationId(Consts.CONVERSATION_MONITOR_VM);
                    msg.setContentObject((java.io.Serializable) virtualMachineDescription);
                    send(msg);
                }
            } catch (Exception e) {
                if (!Consts.EXCEPTIONS) System.out.println(e);
            }
        }

    }

    private class RegisterWithNewOwner extends OneShotBehaviour {

        private Agent agt;

        public RegisterWithNewOwner(Agent agt) {
            this.agt = agt;
        }

        @Override
        public void action() {
            updateVirtualMachineResourceConsumption();

            try {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID(virtualMachineDescription.getOwnerId(), AID.ISLOCALNAME));
                msg.setConversationId(Consts.CONVERSATION_REGISTRATION_DUE_TO_VM_MIGRATION);
                msg.setContentObject((java.io.Serializable) virtualMachineDescription);
                send(msg);
                if (!Consts.LOG)
                    System.out.println(agt.getAID().getLocalName() + " sent message to " + virtualMachineDescription.getOwnerId() + " for registration");
            } catch (Exception e) {
                if (!Consts.EXCEPTIONS) System.out.println(e);
            }

        }

    }

    private class ListenForMigrationRequests extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public ListenForMigrationRequests(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_MIGRATE);
        }

        @Override
        public synchronized void action() {

            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                VirtualMachineDescription vm = (VirtualMachineDescription) msg.getContentObject();
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    virtualMachineDescription.setPreviousOwnerId(vm.getPreviousOwnerId());
                    virtualMachineDescription.setOwnerId(vm.getOwnerId());
                    if (!Consts.LOG)
                        System.out.println("NEW VMA's MIGRATION CODE to " + vm.getContainerName().trim() + " with " + vm.getOwnerId());
//                    System.out.println("NEW VMA's MIGRATION CODE to " + vm.getContainerName().trim() + " with " + vm.getOwnerId());
                    virtualMachineDescription.setMigrationType(vm.getMigrationType());

                    agt.doMove(new ContainerID(vm.getContainerName().trim(), null));
                }
            } catch (Exception ex) {
                if (!Consts.EXCEPTIONS) System.out.println(ex);
            }
        }
    }

}
