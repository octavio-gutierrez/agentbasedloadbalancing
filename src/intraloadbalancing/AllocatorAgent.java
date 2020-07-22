/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * @author octavio
 */
public class AllocatorAgent extends Agent {

    private boolean firstArrival;
    private int conversationId;
    private ArrayList<HostDescription> hosts;
    private ArrayList<HostDescription> possiblyCompromisedHosts;
    private ArrayList<HostDescription> hostLeaders;

    private Utilities utils;
    transient protected AllocatorAgentGUI allocatorAgentGUI; // Reference to the gui    
    private boolean CPUThresholdViolated;       // I DO NOT USE THEM. However, they might be used to include additional information.
    private boolean memoryThresholdViolated;
    private boolean VMWAREkeepMigrating;


    public AllocatorAgent() {
        possiblyCompromisedHosts = new ArrayList<HostDescription>();
        utils = new Utilities();
        conversationId = 0;
        firstArrival = true; // This is to indicate that the simulation has started and that the the logging process should begin. 
        CPUThresholdViolated = false;
        memoryThresholdViolated = false;
        VMWAREkeepMigrating = false;
    }

    @Override
    protected void setup() {

        utils.publishService(this, "AllocatorAgent");

        Object[] args = getArguments();
        hosts = (ArrayList<HostDescription>) args[0];
        hostLeaders = (ArrayList<HostDescription>) args[1];  //Added Joel 2020-06-24
        if (!Consts.LOG) {
            System.out.println("\n" + hosts + "\n");
        }

        allocatorAgentGUI = new AllocatorAgentGUI(hosts);
        if (Consts.ALLOCATOR_AGENT_GUI) allocatorAgentGUI.setVisible(true);

        addBehaviour(new RequestsReceiver(this));
        addBehaviour(new MonitorListener(this));
    }


    private class Logger extends TickerBehaviour {

        private Agent agt;
        private int currentTick; // this is to keep track of the time window 
        private int finalCountDown = 0; // in ticks
        private double[] lastCPUStdDev;
        private double[] lastMemoryStdDev;
        private int numberOfContinuousMigrations;

        public Logger(Agent agt) {
            super(agt, Consts.LOGGING_RATE);
            this.agt = agt;
            currentTick = -1;
            numberOfContinuousMigrations = 0;
            lastCPUStdDev = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
            lastMemoryStdDev = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
        }

        @Override
        protected void onTick() {

            //System.out.println("At TICKER -> \n"+possiblyCompromisedHosts);                    
            long time = System.currentTimeMillis();
            DecimalFormat df = new DecimalFormat("0.##");
            String balancing_metric = "";
            balancing_metric += "{\"dataCenterCPUMean\":" + df.format(mean("cpu", hosts, -1)) + ", ";
            balancing_metric += "\"dataCenterCPUStdDev\":" + df.format(stdDev("cpu", hosts, -1)) + ", ";
            balancing_metric += "\"dataCenterMemoryMean\":" + df.format(mean("memory", hosts, -1)) + ", ";
            balancing_metric += "\"dataCenterMemoryStdDev\":" + df.format(stdDev("memory", hosts, -1)) + ", ";
            balancing_metric += "\"coalitions\": [";

//            for (int i = 0; i < Consts.NUMBER_OF_COALITIONS; i++) {
//                balancing_metric += "{\"id\":" + i + ", ";
//                balancing_metric += "\"CPUMean\":" + df.format(mean("cpu", hosts, i)) + ", ";
//                balancing_metric += "\"CPUStdDev\":" + df.format(stdDev("cpu", hosts, i)) + ", ";
//                balancing_metric += "\"memoryMean\":" + df.format(mean("memory", hosts, i)) + ", ";
//                balancing_metric += "\"memoryStdDev\":" + df.format(stdDev("memory", hosts, i)) + "}";
//                if ((i + 1) < Consts.NUMBER_OF_COALITIONS) {
//                    balancing_metric += ", ";
//                }
//            }

            for (int i = 0; i < hostLeaders.size(); i++) {
                balancing_metric += "{\"id\":" + hostLeaders.get(i).getCoalition() + ", ";
                balancing_metric += "\"CPUMean\":" + df.format(mean("cpu", hosts, hostLeaders.get(i).getCoalition())) + ", ";
                balancing_metric += "\"CPUStdDev\":" + df.format(stdDev("cpu", hosts, hostLeaders.get(i).getCoalition())) + ", ";
                balancing_metric += "\"memoryMean\":" + df.format(mean("memory", hosts, hostLeaders.get(i).getCoalition())) + ", ";
                balancing_metric += "\"memoryStdDev\":" + df.format(stdDev("memory", hosts, hostLeaders.get(i).getCoalition())) + "}";
                if ((i + 1) < hostLeaders.size()) {
                    balancing_metric += ", ";
                }
            }


            balancing_metric += "], \"time\":" + time + "}";

            if ((mean("cpu", hosts, -1) != 0) && (mean("memory", hosts, -1) != 0)) { // if there is load, then log it.
                finalCountDown = 0;
                if (Consts.LOG) {
                    System.out.println(balancing_metric);
                }

                if (Consts.LOAD_BALANCING_TYPE == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    currentTick++;
                    lastCPUStdDev[currentTick] = stdDev("cpu", hosts, -1);
                    lastMemoryStdDev[currentTick] = stdDev("memory", hosts, -1);
                    if ((currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) || (VMWAREkeepMigrating)) {
                        //System.out.println("Check whether I need to migrate");
                        double totalCPUStdDev = 0;
                        double totalMemoryStdDev = 0;

                        for (int i = 0; i < Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE; i++) {
                            totalCPUStdDev += lastCPUStdDev[i];
                            totalMemoryStdDev += lastMemoryStdDev[i];
                        }

                        double avgCPUStdDev = totalCPUStdDev / (double) lastCPUStdDev.length; // average CPU Std dev within a time window
                        double avgMemoryStdDev = totalMemoryStdDev / (double) lastMemoryStdDev.length; // average Memory Std dev within a time window
                        //System.out.println("numberOfContinuousMigrations "+ numberOfContinuousMigrations);
                        if (numberOfContinuousMigrations >= Consts.VMWARE_MAX_MIGRATIONS) {
                            numberOfContinuousMigrations = 0;
                            VMWAREkeepMigrating = false;
                            //currentTick = -1;
                        } else if (((avgCPUStdDev <= Consts.TARGET_STD_DEV) && VMWAREkeepMigrating && Consts.VMWARE_BALANCE_CPU_LOAD) ||
                                ((avgMemoryStdDev <= Consts.TARGET_STD_DEV) && VMWAREkeepMigrating && Consts.VMWARE_BALANCE_MEMORY_LOAD)) {
                            numberOfContinuousMigrations = 0;
                            VMWAREkeepMigrating = false;
                            //currentTick = -1;
                        } else if ((avgCPUStdDev > Consts.TARGET_STD_DEV) && (Consts.VMWARE_BALANCE_CPU_LOAD)) {
                            //currentTick = -1;
                            CPUThresholdViolated = true;
                            VMWAREkeepMigrating = true;
                            numberOfContinuousMigrations++;
                            if (!Consts.LOG) {
                                System.out.println("I NEED TO BALANCE CPU");
                            }
                            //System.out.println("I NEED TO BALANCE CPU");
                            agt.addBehaviour(new VMWARE_LoadBalancing(agt, Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU, avgCPUStdDev));
                        } else if ((avgMemoryStdDev > Consts.TARGET_STD_DEV) && (Consts.VMWARE_BALANCE_MEMORY_LOAD)) {
                            //currentTick = -1;
                            memoryThresholdViolated = true;
                            VMWAREkeepMigrating = true;
                            numberOfContinuousMigrations++;
                            if (!Consts.LOG) {
                                System.out.println("I NEED TO BALANCE MEMORY");
                            }
                            //System.out.println("I NEED TO BALANCE MEMORY");
                            agt.addBehaviour(new VMWARE_LoadBalancing(agt, Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY, avgMemoryStdDev));
                        }


                        if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {
                            currentTick = -1;
                        }
                    }

                }
            } else { // if there is no load, then close the program.
                finalCountDown++;
                if (finalCountDown > 20 /*ticks*/) {
                    System.exit(0);
                }
            }
        }
    }

    private double getNumberOfElements(int coalition) {// coalition -1 indicates that all the host should be taken into account
        double n = 0;
        if (coalition == -1) {
            n = hosts.size();
        } else {
            for (HostDescription host : hosts) {
                if (host.getCoalition() == coalition) {
                    n = n + 1;
                }
            }
        }
        return n;
    }

    private double mean(String resource, ArrayList<HostDescription> hosts, int coalition) { // coalition -1 indicates that all the hosts should be taken into account
        double sum = 0.0;
        double n = getNumberOfElements(coalition);
        for (HostDescription host : hosts) {
            if (resource.toLowerCase().equals("cpu")) {
                if (coalition == -1) {
                    sum += host.getCPUUsage();
                } else if (host.getCoalition() == coalition) {
                    sum += host.getCPUUsage();
                }
            } else if (resource.toLowerCase().equals("memory")) {
                if (coalition == -1) {
                    sum += host.getMemoryUsage();
                } else if (host.getCoalition() == coalition) {
                    sum += host.getMemoryUsage();
                }
            }
        }

        return sum / n;
    }

    private double stdDev(String resource, ArrayList<HostDescription> hosts, int coalition) { // coalition -1 indicates that all the hosts should be taken into account
        double summatory = 0.0;
        double n = getNumberOfElements(coalition);
        double mean = mean(resource, hosts, coalition);
        for (HostDescription host : hosts) {
            if (resource.toLowerCase().equals("cpu")) {
                if (coalition == -1) {
                    summatory += Math.pow(host.getCPUUsage() - mean, 2);
                } else if (host.getCoalition() == coalition) {
                    summatory += Math.pow(host.getCPUUsage() - mean, 2);
                }
            } else if (resource.toLowerCase().equals("memory")) {
                if (coalition == -1) {
                    summatory += Math.pow(host.getMemoryUsage() - mean, 2);
                } else if (host.getCoalition() == coalition) {
                    summatory += Math.pow(host.getMemoryUsage() - mean, 2);
                }
            }
        }
        return Math.sqrt(summatory / n);
    }

    private static VirtualMachineDescription deepCopyVM(VirtualMachineDescription vm) {
        VirtualMachineDescription aDeepCopyOfVM = new VirtualMachineDescription();
        aDeepCopyOfVM.setId(vm.getId());
        aDeepCopyOfVM.setSortingField(vm.getSortingField());
        aDeepCopyOfVM.setMigrationCause(vm.getMigrationCause());
        aDeepCopyOfVM.setPreviousOwnerId(vm.getPreviousOwnerId());
        aDeepCopyOfVM.setMigrationType(vm.getMigrationType());
        aDeepCopyOfVM.setOwnerId(vm.getOwnerId());
        aDeepCopyOfVM.setContainerName(vm.getContainerName());
        aDeepCopyOfVM.setCPUUsage(vm.getCPUUsage());
        aDeepCopyOfVM.setCPUProfile(vm.getCPUProfile());
        aDeepCopyOfVM.setMemoryProfile(vm.getMemoryProfile());
        aDeepCopyOfVM.setMemoryUsage(vm.getMemoryUsage());
        aDeepCopyOfVM.setVirtualMachineId(vm.getId());
        aDeepCopyOfVM.setNumberOfVirtualCores(vm.getNumberOfVirtualCores());
        aDeepCopyOfVM.setMemory(vm.getMemory());
        aDeepCopyOfVM.setSortingField(vm.getSortingField());
        aDeepCopyOfVM.setConversationId(vm.getConversationId());
        aDeepCopyOfVM.setLock(vm.isLock());
        return aDeepCopyOfVM;
    }

    private static HostDescription deepCopyHost(HostDescription host) {
        HostDescription aDeepCopyOfHost = new HostDescription();
        aDeepCopyOfHost.setAllocatorId(host.getAllocatorId());
        aDeepCopyOfHost.setMyLeader(host.getMyLeader());
        aDeepCopyOfHost.setWillingToParticipateInCNP(host.isWillingToParticipateInCNP());
        aDeepCopyOfHost.setLeader(host.isLeader());
        aDeepCopyOfHost.setCoalition(host.getCoalition());
        aDeepCopyOfHost.setId(host.getId());
        aDeepCopyOfHost.setMemoryUsage(host.getMemoryUsage());
        aDeepCopyOfHost.setCPUUsage(host.getCPUUsage());
        aDeepCopyOfHost.setMemory(host.getMemory());
        aDeepCopyOfHost.setLockedMemory(host.getLockedMemory());
        aDeepCopyOfHost.setMemoryUsed(host.getMemoryUsed());
        aDeepCopyOfHost.setNumberOfVirtualCores(host.getNumberOfVirtualCores());
        aDeepCopyOfHost.setNumberOfLockedVirtualCores(host.getNumberOfLockedVirtualCores());
        aDeepCopyOfHost.setNumberOfVirtualCoresUsed(host.getNumberOfVirtualCoresUsed());
        aDeepCopyOfHost.setLowMigrationThresholdForCPU(host.getLowMigrationThresholdForCPU());
        aDeepCopyOfHost.setHighMigrationThresholdForCPU(host.getHighMigrationThresholdForCPU());
        aDeepCopyOfHost.setLowMigrationThresholdForMemory(host.getLowMigrationThresholdForMemory());
        aDeepCopyOfHost.setHighMigrationThresholdForMemory(host.getHighMigrationThresholdForMemory());
        aDeepCopyOfHost.setCPUMigrationHeuristicId(host.getCPUMigrationHeuristicId());
        aDeepCopyOfHost.setMemoryMigrationHeuristicId(host.getMemoryMigrationHeuristicId());
        aDeepCopyOfHost.setContainerName(host.getContainerName());
        aDeepCopyOfHost.setInProgress(host.isInProgress());
        if (host.getVirtualMachinesHosted() != null) {
            if (host.getVirtualMachinesHosted().size() > 0) {
                for (int i = 0; i < host.getVirtualMachinesHosted().size(); i++) {
                    try {
                        if (host.getVirtualMachinesHosted().get(i) != null)
                            aDeepCopyOfHost.getVirtualMachinesHosted().add(deepCopyVM(host.getVirtualMachinesHosted().get(i)));
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS)
                            e.printStackTrace();
                    }
                }
//                for (VirtualMachineDescription vm : host.getVirtualMachinesHosted()) {  
//                    aDeepCopyOfHost.getVirtualMachinesHosted().add(deepCopyVM(vm));
//                }
            }
        }


        return aDeepCopyOfHost;
    }


    private static ArrayList<HostDescription> getUpdatedCopyOfDatacenter(ArrayList<HostDescription> hosts, HostDescription updatedSourceHost, HostDescription updatedDestinationHost) {
        ArrayList<HostDescription> updatedListOfHosts = new ArrayList<HostDescription>();
        for (int i = 0; i < hosts.size(); i++) {
            if (!(hosts.get(i).getId().equals(updatedSourceHost.getId()) || hosts.get(i).getId().equals(updatedDestinationHost.getId()))) {
                updatedListOfHosts.add(deepCopyHost(hosts.get(i)));
            }
        }

//        for (HostDescription host : hosts) {
//            if (!(host.getId().equals(updatedSourceHost.getId()) || host.getId().equals(updatedDestinationHost.getId()))){
//                updatedListOfHosts.add(deepCopyHost(host));
//            }
//        }        
        updatedListOfHosts.add(updatedSourceHost);
        updatedListOfHosts.add(updatedDestinationHost);
        return updatedListOfHosts;
    }

    private static HostDescription removeVMfromHost(HostDescription host, String VMIdtoBeRemoved) {
        HostDescription updatedHost = deepCopyHost(host);
        updatedHost.getVirtualMachinesHosted().clear();
        for (VirtualMachineDescription vm : host.getVirtualMachinesHosted()) {
            if (!vm.getId().equals(VMIdtoBeRemoved)) {
                updatedHost.getVirtualMachinesHosted().add(deepCopyVM(vm));
            }
        }
        return updatedHost;
    }

    private static HostDescription getDeepCopyOfHost(ArrayList<HostDescription> hosts, String hostId) {
        for (HostDescription host : hosts) {
            if (host.getId().equals(hostId)) {
                return deepCopyHost(host);
            }
        }
        return null;
    }

    private Decision VMWAREDynamicResourceSchedulerAlgorithm(int loadBalancingCause, double currentStdDev) {
        boolean aBestDecisionFound = false;
        Decision bestDecision = new Decision();
        ArrayList<HostDescription> safeHosts = new ArrayList<HostDescription>();
        ArrayList<VirtualMachineDescription> currentVMs = new ArrayList<VirtualMachineDescription>();

        for (int i = 0; i < hosts.size(); i++) {
            if (!possiblyCompromisedHosts.contains(hosts.get(i))) {
                safeHosts.add(deepCopyHost(hosts.get(i)));
            }
        }
//        for (HostDescription potentialHost : hosts) {
//            if (!possiblyCompromisedHosts.contains(potentialHost)) {
//                safeHosts.add(deepCopyHost(potentialHost));
//            }
//        }


        for (int i = 0; i < safeHosts.size(); i++) {
            for (int j = 0; j < safeHosts.get(i).getVirtualMachinesHosted().size(); j++) {
                currentVMs.add(deepCopyVM(safeHosts.get(i).getVirtualMachinesHosted().get(j)));
            }
        }

//        for (HostDescription safeHost : safeHosts) {
//            for (VirtualMachineDescription vm : safeHost.getVirtualMachinesHosted()) {
//                currentVMs.add(deepCopyVM(vm));
//            }
//        }

        if ((safeHosts.size() <= 1) || (currentVMs.size() < 1)) {
            bestDecision.setDecision(-1); // meaning do not migrate         
            return bestDecision;
        } else {
            double bestStdDev = currentStdDev;
            for (int i = 0; i < currentVMs.size(); i++) {
//            for (VirtualMachineDescription vm : currentVMs) {
                for (int j = 0; j < safeHosts.size(); j++) {
//                for (HostDescription host : safeHosts) {
                    if ((currentVMs.get(i).getNumberOfVirtualCores() <= safeHosts.get(j).getAvailableVirtualCores())
                            && (currentVMs.get(i).getMemory() <= safeHosts.get(j).getAvailableMemory())
                            && (!currentVMs.get(i).getOwnerId().trim().equals(safeHosts.get(j).getId().trim()))) {


                        HostDescription possiblySelectedDestinationHost = deepCopyHost(safeHosts.get(j));
//                        System.out.println("possiblySelectedDestinationHost before "+ possiblySelectedDestinationHost);
                        possiblySelectedDestinationHost.getVirtualMachinesHosted().add(deepCopyVM(currentVMs.get(i)));
//                        System.out.println("possiblySelectedDestinationHost after "+ possiblySelectedDestinationHost);
//                        System.out.println("vm added " + vm);

                        HostDescription aHost = getDeepCopyOfHost(safeHosts, currentVMs.get(i).getOwnerId());
//                        System.out.println("possiblySelectedSourceHost before "+ aHost);                        
                        HostDescription possiblySelectedSourceHost = removeVMfromHost(aHost, currentVMs.get(i).getId());
//                        System.out.println("possiblySelectedSourceHost after"+ possiblySelectedSourceHost);                        

//                        System.out.println("Datacenter before "+ safeHosts);                        
                        ArrayList<HostDescription> possiblyNewDatacenter = getUpdatedCopyOfDatacenter(hosts, possiblySelectedSourceHost, possiblySelectedDestinationHost);

//                        System.out.println("Datacenter after "+ possiblyNewDatacenter);                        

                        double newStdDev = 0;

                        if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                            newStdDev = stdDev("memory", possiblyNewDatacenter, -1);
                        } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                            newStdDev = stdDev("cpu", possiblyNewDatacenter, -1);
                        }
                        //System.out.println("old std " + bestStdDev + " new std "+ newStdDev );
                        if ((newStdDev < bestStdDev) && (!possiblySelectedSourceHost.getId().equals(possiblySelectedDestinationHost.getId()))) {
                            //System.out.println(possiblySelectedSourceHost.getId()+ "==" +possiblySelectedDestinationHost.getId());
                            aBestDecisionFound = true;
                            bestStdDev = newStdDev;

                            //  public Decision(HostDescription sourceHost, HostDescription destinationHost, VirtualMachineDescription selectedVM, int decision) {
                            bestDecision = new Decision(deepCopyHost(possiblySelectedSourceHost), deepCopyHost(possiblySelectedDestinationHost), deepCopyVM(currentVMs.get(i)), Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                            bestDecision.getSelectedVM().setContainerName(possiblySelectedDestinationHost.getContainerName());
                            bestDecision.getSelectedVM().setPreviousOwnerId(possiblySelectedSourceHost.getId());
                            bestDecision.getSelectedVM().setOwnerId(possiblySelectedDestinationHost.getId());
                            bestDecision.getSelectedVM().setCoalition(possiblySelectedDestinationHost.getCoalition());
                            if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                bestDecision.getSelectedVM().setMigrationCause(Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY);
                            } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                bestDecision.getSelectedVM().setMigrationCause(Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU);
                            }
                        }
                    }
                }
            }
        }
        if (aBestDecisionFound) {
            //System.out.println(bestDecision);
            return bestDecision;
        } else {
            //System.out.println("No decision");
            Decision dontMigrate = new Decision();
            dontMigrate.setDecision(-1);
            return dontMigrate;
        }
    }

    private Decision makeCentralizedLoadBalancingDecision(int loadBalancingCause) {
        Decision decision = new Decision();
        HostDescription sourceHost = new HostDescription();//most loaded            
        HostDescription destinationHost = new HostDescription();//least loaded
        VirtualMachineDescription selectedVM = new VirtualMachineDescription();
        ArrayList<HostDescription> safeHosts = new ArrayList<HostDescription>();

        //System.out.println("ERROR 1 " +hosts);
        //System.out.println("ERROR 1.5 " +possiblyCompromisedHosts);
        for (HostDescription potentialHost : hosts) {
            if (!possiblyCompromisedHosts.contains(potentialHost)) {
                safeHosts.add(potentialHost);
            }
        }
        //System.out.println("ERROR 2");
        switch (loadBalancingCause) {
            case Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU:
                //System.out.println("ERROR 3 " +safeHosts);
                if (safeHosts.size() > 0) {
                    double minCPUUsage = 101;
                    double maxCPUUsage = -1;
                    for (HostDescription host : safeHosts) {
                        if (host.getCPUUsage() < minCPUUsage) {
                            minCPUUsage = host.getCPUUsage();
                            destinationHost = host;
                        }
                        if (host.getCPUUsage() >= maxCPUUsage) {
                            maxCPUUsage = host.getCPUUsage();
                            sourceHost = host;
                        }
                    }
                    //System.out.println("ERROR 4 " + sourceHost);
                    selectedVM = sourceHost.getVirtualMachinesHosted().get((new Random()).nextInt(sourceHost.getVirtualMachinesHosted().size()));// select a sourceHost's VM at random.                        
                    //System.out.println("ERROR 4.5 " + sourceHost);
                    if (((selectedVM.getNumberOfVirtualCores() <= destinationHost.getAvailableVirtualCores()) && (selectedVM.getMemory() <= destinationHost.getAvailableMemory())) && (!sourceHost.getId().equals(destinationHost.getId()))) {
                        decision = new Decision(sourceHost, destinationHost, selectedVM, Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                        decision.getSelectedVM().setContainerName(destinationHost.getContainerName());
                        decision.getSelectedVM().setPreviousOwnerId(sourceHost.getId());
                        decision.getSelectedVM().setOwnerId(destinationHost.getId());
                        decision.getSelectedVM().setMigrationCause(Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU);
                    } else {
                        decision.setDecision(-1); // meaning do not migrate
                    }
                } else {
                    decision.setDecision(-1); // meaning do not migrate                
                }

                //System.out.println("ERROR 5");
                break;

            case Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY:
                if (safeHosts.size() > 0) {
                    double minMemoryUsage = 101;
                    double maxMemoryUsage = -1;
                    //System.out.println("ERROR 6 " + safeHosts);
                    for (HostDescription host : safeHosts) {
                        if (host.getMemoryUsage() < minMemoryUsage) {
                            minMemoryUsage = host.getMemoryUsage();
                            destinationHost = host;
                        }
                        if (host.getMemoryUsage() >= maxMemoryUsage) {
                            maxMemoryUsage = host.getMemoryUsage();
                            sourceHost = host;
                        }
                    }
                    //System.out.println("ERROR 7 " + sourceHost);
                    selectedVM = sourceHost.getVirtualMachinesHosted().get((new Random()).nextInt(sourceHost.getVirtualMachinesHosted().size()));// select a sourceHost's VM at random.                        
                    //System.out.println("ERROR 7.5 " + sourceHost);
                    if (((selectedVM.getNumberOfVirtualCores() <= destinationHost.getAvailableVirtualCores()) && (selectedVM.getMemory() <= destinationHost.getAvailableMemory())) && (!sourceHost.getId().equals(destinationHost.getId()))) {
                        decision = new Decision(sourceHost, destinationHost, selectedVM, Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B);
                        decision.getSelectedVM().setContainerName(destinationHost.getContainerName());
                        decision.getSelectedVM().setPreviousOwnerId(sourceHost.getId());
                        decision.getSelectedVM().setOwnerId(destinationHost.getId());
                        decision.getSelectedVM().setMigrationCause(Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY);
                    } else {
                        decision.setDecision(-1); // meaning do not migrate
                    }
                } else {
                    decision.setDecision(-1); // meaning do not migrate                
                }

                //System.out.println("ERROR 8");
                break;
        }

        return decision;
    }

    private class VMWARE_LoadBalancing extends SimpleBehaviour {

        private int state = 1;
        private Agent agt;
        private long wakeupTime;
        private boolean finished;
        private int loadBalancingCause;
        private double standardDeviation;
        private Decision decision;

        public VMWARE_LoadBalancing(Agent agt, int loadBalancingCause, double standardDeviation) {
            super(agt);
            this.agt = agt;
            this.loadBalancingCause = loadBalancingCause;
            this.standardDeviation = standardDeviation;
            this.wakeupTime = (Consts.VMWARE_TIMEOUT_FOR_LOAD_BALANCING < 0
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + Consts.VMWARE_TIMEOUT_FOR_LOAD_BALANCING);
            decision = new Decision();
        }

        @Override
        public boolean done() {
            return finished;
        }

        @Override
        public void action() {

            switch (state) {
                case 1:
                    try {
                        ACLMessage msgRequestLockVM = new ACLMessage(ACLMessage.REQUEST);
                        //System.out.println("This is before the problem ");
                        decision = VMWAREDynamicResourceSchedulerAlgorithm(loadBalancingCause, standardDeviation);
                        //decision = makeCentralizedLoadBalancingDecision(loadBalancingCause);
                        //System.out.println("Decision" +decision);
                        //System.out.println("The decision "+ decision);
                        if (decision.getDecision() != -1) {
                            AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                            msgRequestLockVM.setSender(agt.getAID());
                            msgRequestLockVM.addReceiver(to);
                            msgRequestLockVM.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                            msgRequestLockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestLockVM);
                            state++;
                        }
                        //System.out.println("HERE I AM 1");
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            e.printStackTrace();
                        }
                    }
                    break;

                case 2:
                    ACLMessage msgResultLockVM = agt.receive(MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM));
                    if (msgResultLockVM != null) { // if the message is received on time
                        if (msgResultLockVM.getPerformative() == ACLMessage.CONFIRM) {
                            try {
                                ACLMessage msgRequestLockResources = new ACLMessage(ACLMessage.REQUEST);
                                AID to = new AID(decision.getDestinationHost().getId(), AID.ISLOCALNAME);
                                msgRequestLockResources.setSender(agt.getAID());
                                msgRequestLockResources.addReceiver(to);
                                msgRequestLockResources.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES);
                                msgRequestLockResources.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestLockResources);
                                //System.out.println("HERE I AM 2");                                
                                state++;
                            } catch (Exception e) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(e);
                                }
                            }

                        } else if (msgResultLockVM.getPerformative() == ACLMessage.FAILURE) {
                            finished = true;
                            //System.out.println("HERE I AM 3");                                                            
                            if (!Consts.LOG) {
                                System.out.println("Unable to load balance because source host could not lock the VM selected");
                            }

                            if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                CPUThresholdViolated = false;
                            } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                memoryThresholdViolated = false;
                            }
                            // I have to unlock the VM at source host
                            try {
                                ACLMessage msgRequestUnlockVM = new ACLMessage(ACLMessage.REQUEST);
                                AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                                msgRequestUnlockVM.setSender(agt.getAID());
                                msgRequestUnlockVM.addReceiver(to);
                                msgRequestUnlockVM.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_VM);
                                msgRequestUnlockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestUnlockVM);
                            } catch (IOException ex) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(ex);
                                }
                            }
                        }
                    }
                    long dt = wakeupTime - System.currentTimeMillis();
                    if (dt > 0) {
                        block(dt);
                    } else { /// The was not received on time. Then the timeout is triggered
                        finished = true;
                        if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                            CPUThresholdViolated = false;
                        } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                            memoryThresholdViolated = false;
                        }
                        if (!Consts.LOG) {
                            System.out.println("The source host agent did not respond and we have to call off the VM migration");
                        }

                        try {
                            ACLMessage msgRequestUnlockVM = new ACLMessage(ACLMessage.REQUEST);
                            AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                            msgRequestUnlockVM.setSender(agt.getAID());
                            msgRequestUnlockVM.addReceiver(to);
                            msgRequestUnlockVM.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_VM);
                            msgRequestUnlockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestUnlockVM);

                            // the destination host may have not beee able to respond however it was lock so I had to unlock it.
                            ACLMessage msgRequestUnlockResources = new ACLMessage(ACLMessage.REQUEST);
                            to = new AID(decision.getDestinationHost().getId(), AID.ISLOCALNAME);
                            msgRequestUnlockResources.setSender(agt.getAID());
                            msgRequestUnlockResources.addReceiver(to);
                            msgRequestUnlockResources.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_RESOURCES);
                            msgRequestUnlockResources.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestUnlockResources);
                            //System.out.println("HERE I AM 7");

                        } catch (IOException ex) {
                            if (Consts.EXCEPTIONS) {
                                System.out.println(ex);
                            }
                        }

                        //System.out.println("HERE I AM 4");                                                        
                    }
                    break;

                case 3:
                    ACLMessage msgResultLockResources = agt.receive(MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES));
                    if (msgResultLockResources != null) { // if the message is received on time
                        if (msgResultLockResources.getPerformative() == ACLMessage.CONFIRM) {
                            try {
                                ACLMessage msgMigratieVM = new ACLMessage(ACLMessage.REQUEST);
                                AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                                msgMigratieVM.setSender(agt.getAID());
                                msgMigratieVM.addReceiver(to);
                                msgMigratieVM.setConversationId(Consts.VMWARE_CONVERSATION_CONFIRM_MIGRATION);
                                msgMigratieVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgMigratieVM);
                                //System.out.println("HERE I AM 5");                                        
                                if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                    CPUThresholdViolated = false;
                                } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                    memoryThresholdViolated = false;
                                }
                            } catch (Exception e) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(e);
                                }
                            }
                        } else if (msgResultLockResources.getPerformative() == ACLMessage.FAILURE) {
                            finished = true;
                            if (!Consts.LOG) {
                                System.out.println("Unable to load balance because destination host could not lock sufficient resources");
                            }
                            if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                CPUThresholdViolated = false;
                            } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                memoryThresholdViolated = false;
                            }
                            // I have to unlock the VM at source host
                            try {
                                ACLMessage msgRequestUnlockVM = new ACLMessage(ACLMessage.REQUEST);
                                AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                                msgRequestUnlockVM.setSender(agt.getAID());
                                msgRequestUnlockVM.addReceiver(to);
                                msgRequestUnlockVM.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_VM);
                                msgRequestUnlockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestUnlockVM);
                                //System.out.println("HERE I AM 6");                                                                                                                        
                            } catch (IOException ex) {
                                if (Consts.EXCEPTIONS) {
                                    java.util.logging.Logger.getLogger(AllocatorAgent.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                        }
                        finished = true; // finish the behaviour either way (failure or confirm)
                    }
                    dt = wakeupTime - System.currentTimeMillis();
                    if (dt > 0) {
                        block(dt);
                    } else { /// The was not received on time. Then the timeout is triggered
                        finished = true;
                        if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                            CPUThresholdViolated = false;
                        } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                            memoryThresholdViolated = false;
                        }
                        if (!Consts.LOG) {
                            System.out.println("The source destination host did not respond and we have to call off the VM migration");
                        }
                        try {

                            // I have to unlock both Resources at the destionatio host and the vm at the source host.
                            ACLMessage msgRequestUnlockResources = new ACLMessage(ACLMessage.REQUEST);
                            AID to = new AID(decision.getDestinationHost().getId(), AID.ISLOCALNAME);
                            msgRequestUnlockResources.setSender(agt.getAID());
                            msgRequestUnlockResources.addReceiver(to);
                            msgRequestUnlockResources.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_RESOURCES);
                            msgRequestUnlockResources.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestUnlockResources);
                            //System.out.println("HERE I AM 7");      

                            ACLMessage msgRequestUnlockVM = new ACLMessage(ACLMessage.REQUEST);
                            to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                            msgRequestUnlockVM.setSender(agt.getAID());
                            msgRequestUnlockVM.addReceiver(to);
                            msgRequestUnlockVM.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK_VM);
                            msgRequestUnlockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestUnlockVM);
                            //System.out.println("HERE I AM 6");                                                                                                                        


                        } catch (IOException ex) {
                            if (Consts.EXCEPTIONS) {
                                java.util.logging.Logger.getLogger(AllocatorAgent.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                    }
                    break;
            }
        }
    }

    private class RequestsReceiver extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public RequestsReceiver(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_INITIAL_VM_REQUEST);
        }

        @Override
        public void action() {
            ACLMessage msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                Object content = msg.getContentObject();
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    if (content instanceof VirtualMachineDescription) {

                        VirtualMachineDescription vm = (VirtualMachineDescription) content;
                        conversationId++;

                        if (firstArrival) {
                            firstArrival = false;
                            if (Consts.LOG) {
                                addBehaviour(new Logger(agt));
                            }
                        }

                        vm.setConversationId(agt.getLocalName() + String.valueOf(conversationId));
                        //System.out.println(vm);

                        // Verifying whether there is a host with available cores to host the new VM                    
                        ArrayList<HostDescription> availableHosts = new ArrayList<>(hosts);
                        Predicate<HostDescription> condition = hostDescription -> vm.getMemory() > hostDescription.getAvailableMemory() || vm.getNumberOfVirtualCores() > hostDescription.getAvailableVirtualCores();
                        availableHosts.removeIf(condition);

                        if ((availableHosts.size() > 0)) { // If the VM can be hosted in the Datacenter
                            HostDescription randomlySelectedHost = availableHosts.get((new Random()).nextInt(availableHosts.size()));
                            //System.out.println(randomlySelectedHost.getId());
                            agt.addBehaviour(new virtualMachineAllocator(agt, randomlySelectedHost, vm)); // Allocate VM to a host selected at random.
                            //agt.addBehaviour(new virtualMachineAllocator(agt, "HostAgent0", vm)); // Allocate VM to a host selected at random.
                        } else {
                            // behavior that keeps trying to allocate the VM
                            agt.addBehaviour(new PeriodicallyAttemptingToAllocateVM(agt, Consts.PERIOD_FOR_PERIODICALLY_ATTEMPTING_TO_ALLOCATE_VM, vm));
                        }

                    }
                }
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private class virtualMachineAllocator extends OneShotBehaviour {

        private Agent agt;
        private VirtualMachineDescription vm;
        private HostDescription selectedHost;

        public virtualMachineAllocator(Agent agt, HostDescription selectedHost, VirtualMachineDescription vm) {
            super(null);
            this.agt = agt;
            this.vm = vm;
            this.selectedHost = selectedHost;
        }

        @Override
        public void action() {

            try {

                if (Consts.LOAD_BALANCING_TYPE == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    if (!possiblyCompromisedHosts.contains(selectedHost))
                        possiblyCompromisedHosts.add(selectedHost); // add the selectedHost to the possibly compromised hosts that are not available to host a VM because of a concurrent initial VM allocation 
//                    System.out.println("At virtualMachineAllocator -> \n"+possiblyCompromisedHosts);                    
                }

                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                AID to = new AID(selectedHost.getId(), AID.ISLOCALNAME);
                msg.setSender(agt.getAID());
                msg.addReceiver(to);
                msg.setConversationId(Consts.CONVERSATION_VM_ALLOCATION);
                //vm.setPreviousOwnerId("");
                vm.setOwnerId(selectedHost.getId());
                msg.setContentObject((java.io.Serializable) vm);
                agt.send(msg);
                agt.addBehaviour(new ReceiverAck(agt, vm)); // I added the conversation id to listen for that specific message
            } catch (IOException ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }

        }
    }

    private class ReceiverAck extends SimpleBehaviour {

        private Agent agt;
        private VirtualMachineDescription vm;
        MessageTemplate mt;
        private boolean terminated = false;

        public ReceiverAck(Agent agt, VirtualMachineDescription vm) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(vm.getConversationId());
            this.vm = vm;
        }

        @Override
        public void action() {
            ACLMessage msg = receive(mt);
            if (msg != null) {

                ////////////////////// WORKING ON //////////////////////  BEGIN
                ////////////////////// WORKING ON //////////////////////  BEGIN
                ////////////////////// WORKING ON //////////////////////  BEGIN
                if (Consts.LOAD_BALANCING_TYPE == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    Predicate<HostDescription> condition = hostDescription -> hostDescription.getId().equals(msg.getSender().getLocalName());
                    possiblyCompromisedHosts.removeIf(condition);
//                    System.out.println("At receiverAck -> \n"+possiblyCompromisedHosts);
                    //remove the host from the possibly compromised hosts that are not available to host a VM because of a concurrent initial VM allocation 
                }

                ////////////////////// WORKING ON //////////////////////  END
                ////////////////////// WORKING ON //////////////////////  END
                ////////////////////// WORKING ON //////////////////////  END
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (!Consts.LOG) {
                        System.out.println(agt.getLocalName() + " received a successful response about VM allocation from agent " + msg.getSender().getName());
                    }
                } else if (msg.getPerformative() == ACLMessage.FAILURE) {
                    if (!Consts.LOG) {
                        System.out.println(agt.getLocalName() + " received a failure response about VM allocation from agent " + msg.getSender().getName());
                    }
                    agt.addBehaviour(new PeriodicallyAttemptingToAllocateVM(agt, Consts.PERIOD_FOR_PERIODICALLY_ATTEMPTING_TO_ALLOCATE_VM, vm));
                }
                terminated = true;
            }
            block();

        }

        @Override
        public boolean done() {
            return terminated;
        }

    }

    private class PeriodicallyAttemptingToAllocateVM extends TickerBehaviour {

        private Agent agt;
        private VirtualMachineDescription vm;

        public PeriodicallyAttemptingToAllocateVM(Agent agt, long period, VirtualMachineDescription vm) {
            super(agt, period);
            this.agt = agt;
            this.vm = vm;
        }

        @Override
        protected void onTick() {
            // Verifying whether there is a host with available cores to host the new VM                    

            ArrayList<HostDescription> availableHosts = new ArrayList<>(hosts);
            Predicate<HostDescription> condition = hostDescription -> vm.getMemory() >= hostDescription.getAvailableMemory() || vm.getNumberOfVirtualCores() >= hostDescription.getAvailableVirtualCores();
            availableHosts.removeIf(condition);

            if (availableHosts.size() > 0) { // If the VM can be hosted in the Datacenter
                HostDescription randomlySelectedHost = availableHosts.get((new Random()).nextInt(availableHosts.size()));
                agt.addBehaviour(new virtualMachineAllocator(agt, randomlySelectedHost, vm)); // Allocate VM to a host selected at random.
                //agt.addBehaviour(new virtualMachineAllocator(agt, "HostAgent0", vm)); // Allocate VM to a host selected at random.
                stop(); // terminate ticker behavior
            }

        }

    }

    private class MonitorListener extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;

        public MonitorListener(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_MONITOR_HOST);
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
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (content instanceof HostDescription) {
                        HostDescription hostDescription = (HostDescription) content;
                        updateHostsResourceConsumption(hostDescription);
                        allocatorAgentGUI.updateServersMonitorList();
                    }
                }

            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }

        }
    }

    private void updateHostsResourceConsumption(HostDescription hostDescriptionToBeUpdated) {
        try {
            for (HostDescription hostDescription : hosts) {
                if (hostDescription.equals(hostDescriptionToBeUpdated)) {
                    hostDescription = hostDescriptionToBeUpdated;
                    break;
                }
            }
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                System.out.println(ex);
            }
        }
    }

}
