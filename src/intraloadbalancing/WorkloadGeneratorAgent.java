/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

/**
 * @author octavio
 */
public class WorkloadGeneratorAgent extends Agent {

    @Override
    protected void setup() {

        Utilities utils = new Utilities();

        // Searching for an allocator agent
        String[] searchResults = utils.searchForAgents(this, "AllocatorAgent", 1);
        String allocatorAgent = searchResults[0];
        double averageDelay = 0;

        // Creating behaviours -> delay - requestVM - delay - requestVM - delay - RequestVM .... 
        SequentialBehaviour setOfvirtualMachineRequests = new SequentialBehaviour(this);
        int virtualMachinesRequested = 0;
        while (virtualMachinesRequested < Consts.NUMBER_OF_VMS) {

            long delay = (long) (Consts.AVG_INTERARRIVAL_TIME * (-Math.log(Math.random()))); //  Arrival process is Poisson-distributed
            averageDelay = averageDelay + delay;
            setOfvirtualMachineRequests.addSubBehaviour(new DelayBehaviour(this, (long) delay));

            int[] VMspecs = Consts.VM_OPTIONS[(new Random()).nextInt(Consts.VM_OPTIONS.length)];
            VirtualMachineDescription vm = new VirtualMachineDescription(
                    "VirtualMachineAgent" + String.valueOf(virtualMachinesRequested + 1), // id
                    VMspecs[0], // random number of cores
                    VMspecs[1]); // random memory size

            vm.setCPUProfile((new Random()).nextInt(3)); // 0 low CPU, 1 medium CPU, 2 high CPU
            vm.setMemoryProfile((new Random()).nextInt(3)); /// 0 low memory, 1 medium memory, 2 high memory

            setOfvirtualMachineRequests.addSubBehaviour(new RequestVirtualMachine(allocatorAgent, vm));
            virtualMachinesRequested++;
        }

        addBehaviour(setOfvirtualMachineRequests);

    }

    private class RequestVirtualMachine extends OneShotBehaviour {

        VirtualMachineDescription vm;
        String allocatorAgent;

        RequestVirtualMachine(String allocatorAgent, VirtualMachineDescription vm) {
            this.allocatorAgent = allocatorAgent;
            this.vm = vm;
        }

        @Override
        public void action() {
            try {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID(allocatorAgent, AID.ISGUID));
                msg.setConversationId(Consts.CONVERSATION_INITIAL_VM_REQUEST);
                msg.setContentObject((java.io.Serializable) vm);
                send(msg);

            } catch (Exception ex) {
                if (!Consts.LOG) System.out.println(ex);
            }

        }

    }

    private class DelayBehaviour extends SimpleBehaviour {

        private long timeout;
        private long wakeupTime;
        private boolean finished = false;

        public DelayBehaviour(Agent a, long timeout) {
            super(a);
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
                finished = true;
                handleElapsedTimeout();
            } else {
                block(dt);
            }
        }

        protected void handleElapsedTimeout() { // by default do nothing
        }

        @Override
        public boolean done() {
            return finished;
        }
    }
}
