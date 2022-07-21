/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class AllocatorAgent extends Agent {

    private boolean firstArrival;
    private int conversationId;
    private ArrayList<HostDescription> hosts;
    private ArrayList<HostDescription> possiblyCompromisedHosts;
    private ArrayList<String> hostLeaders;
    private Utilities utils;
    transient protected AllocatorAgentGUI allocatorAgentGUI;
    private boolean CPUThresholdViolated;
    private boolean memoryThresholdViolated;
    private boolean VMWAREkeepMigrating;
    private static ExperimentRunConfiguration configuration;

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
        hostLeaders = (ArrayList<String>) args[1];
        configuration = (ExperimentRunConfiguration) args[2];
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
        private int logRecords;
        private int currentTick; // this is to keep track of the time window 
        private int finalCountDown = 0; // in ticks
        private double[] lastCPUStdDev;
        private double[] lastMemoryStdDev;
        private int numberOfContinuousMigrations;
        private int consecutiveEqualLogs;
        private String previousBalancingMetric;
        private DecimalFormat df;
        private String balancingMetric;
        private long time;

        public Logger(Agent agt) {
            super(agt, Consts.LOGGING_RATE);
            this.agt = agt;
            currentTick = -1;
            numberOfContinuousMigrations = 0;
            lastCPUStdDev = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
            lastMemoryStdDev = new double[Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE];
            consecutiveEqualLogs = 0;
            previousBalancingMetric = "";
            df = new DecimalFormat("0.##");
            balancingMetric = "";
            time = 0;
            logRecords = 0;
        }

        @Override
        public int onEnd() {
            System.exit(1);
            return 0;
        }

        @Override
        protected void onTick() {
            time = System.currentTimeMillis();
            balancingMetric = "";
            balancingMetric += "{\"dataCenterCPUMean\":" + df.format(mean("cpu", hosts, -1)) + ", ";
            balancingMetric += "\"dataCenterCPUStdDev\":" + df.format(stdDev("cpu", hosts, -1)) + ", ";
            balancingMetric += "\"dataCenterMemoryMean\":" + df.format(mean("memory", hosts, -1)) + ", ";
            balancingMetric += "\"dataCenterMemoryStdDev\":" + df.format(stdDev("memory", hosts, -1)) + ", ";
            balancingMetric += "\"coalitions\": [";
            for (int i = 0; i < hostLeaders.size(); i++) {
                int coalitionId = Integer.valueOf(hostLeaders.get(i).replace("HostAgent", ""));
                balancingMetric += "{\"id\":" + coalitionId + ", ";
                balancingMetric += "\"CPUMean\":" + df.format(mean("cpu", hosts, coalitionId)) + ", ";
                balancingMetric += "\"CPUStdDev\":" + df.format(stdDev("cpu", hosts, coalitionId)) + ", ";
                balancingMetric += "\"memoryMean\":" + df.format(mean("memory", hosts, coalitionId)) + ", ";
                balancingMetric += "\"memoryStdDev\":" + df.format(stdDev("memory", hosts, coalitionId)) + "}";
                if ((i + 1) < hostLeaders.size()) {
                    balancingMetric += ", ";
                }
            }
            balancingMetric += "], \"time\":" + time + "}";
            if (!balancingMetric.equals(previousBalancingMetric)) {
                previousBalancingMetric = balancingMetric;
                consecutiveEqualLogs = 0;
            } else {
                consecutiveEqualLogs++;
                if (consecutiveEqualLogs > 300) {
                    System.exit(1);
                }
            }
            if ((mean("cpu", hosts, -1) != 0) || (mean("memory", hosts, -1) != 0)) { // if there is load, then log it.
                finalCountDown = 0;
                if (Consts.LOG) {
                    System.out.println(balancingMetric);
                    logRecords++;
                    if (logRecords >= Consts.NUMBER_OF_LOG_RECORDS) {
                        System.exit(0);
                    }
                }
                if (configuration.getLOAD_BALANCING_TYPE() == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    currentTick++;
                    lastCPUStdDev[currentTick] = stdDev("cpu", hosts, -1);
                    lastMemoryStdDev[currentTick] = stdDev("memory", hosts, -1);
                    if ((currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) || (VMWAREkeepMigrating)) {
                        double totalCPUStdDev = 0;
                        double totalMemoryStdDev = 0;
                        for (int i = 0; i < Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE; i++) {
                            totalCPUStdDev += lastCPUStdDev[i];
                            totalMemoryStdDev += lastMemoryStdDev[i];
                        }
                        double avgCPUStdDev = totalCPUStdDev / (double) lastCPUStdDev.length; // average CPU Std dev within a time window
                        double avgMemoryStdDev = totalMemoryStdDev / (double) lastMemoryStdDev.length; // average Memory Std dev within a time window
                        if (numberOfContinuousMigrations >= configuration.getVMWARE_MAX_MIGRATIONS()) {
                            numberOfContinuousMigrations = 0;
                            VMWAREkeepMigrating = false;
                        } else if (((avgCPUStdDev <= configuration.getTARGET_STD_DEV()) && VMWAREkeepMigrating && Consts.VMWARE_BALANCE_CPU_LOAD) || ((avgMemoryStdDev <= configuration.getTARGET_STD_DEV()) && VMWAREkeepMigrating && Consts.VMWARE_BALANCE_MEMORY_LOAD)) {
                            numberOfContinuousMigrations = 0;
                            VMWAREkeepMigrating = false;
                        } else if ((avgCPUStdDev > configuration.getTARGET_STD_DEV()) && (Consts.VMWARE_BALANCE_CPU_LOAD)) {
                            CPUThresholdViolated = true;
                            VMWAREkeepMigrating = true;
                            numberOfContinuousMigrations++;
                            agt.addBehaviour(new VMWARE_LoadBalancing(agt, Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU, avgCPUStdDev));
                        } else if ((avgMemoryStdDev > configuration.getTARGET_STD_DEV()) && (Consts.VMWARE_BALANCE_MEMORY_LOAD)) {
                            memoryThresholdViolated = true;
                            VMWAREkeepMigrating = true;
                            numberOfContinuousMigrations++;
                            agt.addBehaviour(new VMWARE_LoadBalancing(agt, Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY, avgMemoryStdDev));
                        }
                        if (currentTick == (Consts.TIME_WINDOW_IN_TERMS_OF_REPORTING_RATE - 1)) {
                            currentTick = -1;
                        }
                    }
                }
            } else { // if there is no load, then close the program.
                finalCountDown++;
                if (finalCountDown > 200 /*ticks*/) {
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
            for (int i = 0; i < hosts.size(); i++) {
                if (hosts.get(i).getCoalition() == coalition) {
                    n = n + 1;
                }
            }
        }
        return n;
    }

    private double mean(String resource, ArrayList<HostDescription> hosts, int coalition) { // coalition -1 indicates that all the hosts should be taken into account
        double sum = 0.0;
        double n = getNumberOfElements(coalition);
        for (int i = 0; i < hosts.size(); i++) {
            if (resource.toLowerCase().equals("cpu")) {
                if (coalition == -1) {
                    sum += hosts.get(i).getCPUUsage();
                } else if (hosts.get(i).getCoalition() == coalition) {
                    sum += hosts.get(i).getCPUUsage();
                }
            } else if (resource.toLowerCase().equals("memory")) {
                if (coalition == -1) {
                    sum += hosts.get(i).getMemoryUsage();
                } else if (hosts.get(i).getCoalition() == coalition) {
                    sum += hosts.get(i).getMemoryUsage();
                }
            }
        }
        return sum / n;
    }

    private double stdDev(String resource, ArrayList<HostDescription> hosts, int coalition) { // coalition -1 indicates that all the hosts should be taken into account
        double summatory = 0.0;
        double n = getNumberOfElements(coalition);
        double mean = mean(resource, hosts, coalition);
        for (int i = 0; i < hosts.size(); i++) {
            if (resource.toLowerCase().equals("cpu")) {
                if (coalition == -1) {
                    summatory += Math.pow(hosts.get(i).getCPUUsage() - mean, 2);
                } else if (hosts.get(i).getCoalition() == coalition) {
                    summatory += Math.pow(hosts.get(i).getCPUUsage() - mean, 2);
                }
            } else if (resource.toLowerCase().equals("memory")) {
                if (coalition == -1) {
                    summatory += Math.pow(hosts.get(i).getMemoryUsage() - mean, 2);
                } else if (hosts.get(i).getCoalition() == coalition) {
                    summatory += Math.pow(hosts.get(i).getMemoryUsage() - mean, 2);
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
                    } catch (Exception ex) {
                        if (Consts.EXCEPTIONS) System.out.println(ex);
                    }
                }
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
        updatedListOfHosts.add(updatedSourceHost);
        updatedListOfHosts.add(updatedDestinationHost);
        return updatedListOfHosts;
    }

    private static HostDescription removeVMfromHost(HostDescription host, String VMIdtoBeRemoved) {
        HostDescription updatedHost = deepCopyHost(host);
        updatedHost.getVirtualMachinesHosted().clear();
        for (int i = 0; i < host.getVirtualMachinesHosted().size(); i++) {
            if (!host.getVirtualMachinesHosted().get(i).getId().equals(VMIdtoBeRemoved)) {
                updatedHost.getVirtualMachinesHosted().add(deepCopyVM(host.getVirtualMachinesHosted().get(i)));
            }
        }
        return updatedHost;
    }

    private static HostDescription getDeepCopyOfHost(ArrayList<HostDescription> hosts, String hostId) {
        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).getId().equals(hostId)) {
                return deepCopyHost(hosts.get(i));
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
        for (int i = 0; i < safeHosts.size(); i++) {
            for (int j = 0; j < safeHosts.get(i).getVirtualMachinesHosted().size(); j++) {
                currentVMs.add(deepCopyVM(safeHosts.get(i).getVirtualMachinesHosted().get(j)));
            }
        }
        if ((safeHosts.size() <= 1) || (currentVMs.size() < 1)) {
            bestDecision.setDecision(-1); // meaning do not migrate         
            return bestDecision;
        } else {
            double bestStdDev = currentStdDev;
            for (int i = 0; i < currentVMs.size(); i++) {
                for (int j = 0; j < safeHosts.size(); j++) {
                    if ((currentVMs.get(i).getNumberOfVirtualCores() <= safeHosts.get(j).getAvailableVirtualCores()) && (currentVMs.get(i).getMemory() <= safeHosts.get(j).getAvailableMemory()) && (!currentVMs.get(i).getOwnerId().trim().equals(safeHosts.get(j).getId().trim()))) {
                        HostDescription possiblySelectedDestinationHost = deepCopyHost(safeHosts.get(j));
                        possiblySelectedDestinationHost.getVirtualMachinesHosted().add(deepCopyVM(currentVMs.get(i)));
                        HostDescription aHost = getDeepCopyOfHost(safeHosts, currentVMs.get(i).getOwnerId());
                        HostDescription possiblySelectedSourceHost = removeVMfromHost(aHost, currentVMs.get(i).getId());
                        ArrayList<HostDescription> possiblyNewDatacenter = getUpdatedCopyOfDatacenter(hosts, possiblySelectedSourceHost, possiblySelectedDestinationHost);
                        double newStdDev = 0;
                        if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                            newStdDev = stdDev("memory", possiblyNewDatacenter, -1);
                        } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                            newStdDev = stdDev("cpu", possiblyNewDatacenter, -1);
                        }
                        if ((newStdDev < bestStdDev) && (!possiblySelectedSourceHost.getId().equals(possiblySelectedDestinationHost.getId()))) {
                            aBestDecisionFound = true;
                            bestStdDev = newStdDev;
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
            return bestDecision;
        } else {
            Decision dontMigrate = new Decision();
            dontMigrate.setDecision(-1);
            return dontMigrate;
        }
    }

    private Decision makeCentralizedLoadBalancingDecision(int loadBalancingCause) {
        Decision decision = new Decision();
        HostDescription sourceHost = new HostDescription(); // most loaded
        HostDescription destinationHost = new HostDescription(); // least loaded
        VirtualMachineDescription selectedVM = new VirtualMachineDescription();
        ArrayList<HostDescription> safeHosts = new ArrayList<HostDescription>();
        for (int i = 0; i < hosts.size(); i++) {
            if (!possiblyCompromisedHosts.contains(hosts.get(i))) {
                safeHosts.add(hosts.get(i));
            }
        }
        switch (loadBalancingCause) {
            case Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU:
                if (safeHosts.size() > 0) {
                    double minCPUUsage = 101;
                    double maxCPUUsage = -1;
                    for (int i = 0; i < safeHosts.size(); i++) {
                        if (safeHosts.get(i).getCPUUsage() < minCPUUsage) {
                            minCPUUsage = safeHosts.get(i).getCPUUsage();
                            destinationHost = safeHosts.get(i);
                        }
                        if (safeHosts.get(i).getCPUUsage() >= maxCPUUsage) {
                            maxCPUUsage = safeHosts.get(i).getCPUUsage();
                            sourceHost = safeHosts.get(i);
                        }
                    }
                    selectedVM = sourceHost.getVirtualMachinesHosted().get((new Random()).nextInt(sourceHost.getVirtualMachinesHosted().size())); // select a sourceHost's VM at random.
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
                break;

            case Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY:
                if (safeHosts.size() > 0) {
                    double minMemoryUsage = 101;
                    double maxMemoryUsage = -1;
                    for (int i = 0; i < safeHosts.size(); i++) {
                        if (safeHosts.get(i).getMemoryUsage() < minMemoryUsage) {
                            minMemoryUsage = safeHosts.get(i).getMemoryUsage();
                            destinationHost = safeHosts.get(i);
                        }
                        if (safeHosts.get(i).getMemoryUsage() >= maxMemoryUsage) {
                            maxMemoryUsage = safeHosts.get(i).getMemoryUsage();
                            sourceHost = safeHosts.get(i);
                        }
                    }
                    selectedVM = sourceHost.getVirtualMachinesHosted().get((new Random()).nextInt(sourceHost.getVirtualMachinesHosted().size())); // select a sourceHost's VM at random.
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
            this.wakeupTime = System.currentTimeMillis() + Consts.VMWARE_TIMEOUT_FOR_LOAD_BALANCING;
            decision = new Decision();
        }

        @Override
        public boolean done() {
            return finished;
        }

        @Override
        public synchronized void action() {
            switch (state) {
                case 1:
                    try {
                        decision = VMWAREDynamicResourceSchedulerAlgorithm(loadBalancingCause, standardDeviation);
                        if (decision.getDecision() != -1) {
                            ACLMessage msgRequestLockVM = new ACLMessage(ACLMessage.REQUEST);
                            AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                            msgRequestLockVM.setSender(agt.getAID());
                            msgRequestLockVM.addReceiver(to);
                            msgRequestLockVM.setConversationId(Consts.VMWARE_CONVERSATION_LOCK_VM);
                            msgRequestLockVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                            agt.send(msgRequestLockVM);
                            state++;
                            wakeupTime = System.currentTimeMillis() + Consts.VMWARE_TIMEOUT_FOR_LOAD_BALANCING;
                        }
                    } catch (Exception e) {
                        if (Consts.EXCEPTIONS) {
                            System.out.println(e);
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
                                wakeupTime = System.currentTimeMillis() + Consts.VMWARE_TIMEOUT_FOR_LOAD_BALANCING;
                                state++;
                            } catch (Exception e) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(e);
                                }
                            }

                        } else if (msgResultLockVM.getPerformative() == ACLMessage.FAILURE) {
                            finished = true;
                            if (!Consts.LOG) {
                                System.out.println("Unable to load balance because source host could not lock the VM selected or it is busy");
                            }
                            if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                CPUThresholdViolated = false;
                            } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                memoryThresholdViolated = false;
                            }
                        }
                    } else {
                        long dt = wakeupTime - System.currentTimeMillis();
                        if (dt > 0) {
                            block(dt);
                            return;
                        } else {
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
                                ACLMessage msgRequestUnlock = new ACLMessage(ACLMessage.REQUEST);
                                msgRequestUnlock.setSender(agt.getAID());
                                msgRequestUnlock.addReceiver(new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME));
                                msgRequestUnlock.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK);
                                msgRequestUnlock.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestUnlock);
                            } catch (Exception ex) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(ex);
                                }
                            }
                        }
                    }
                    break;

                case 3:
                    ACLMessage msgResultLockResources = agt.receive(MessageTemplate.MatchConversationId(Consts.VMWARE_CONVERSATION_LOCK_RESOURCES));
                    if (msgResultLockResources != null) { // if the message is received on time
                        if (msgResultLockResources.getPerformative() == ACLMessage.CONFIRM) {
                            try {
                                ACLMessage msgMigrateVM = new ACLMessage(ACLMessage.REQUEST);
                                AID to = new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME);
                                msgMigrateVM.setSender(agt.getAID());
                                msgMigrateVM.addReceiver(to);
                                msgMigrateVM.setConversationId(Consts.VMWARE_CONVERSATION_CONFIRM_MIGRATION);
                                msgMigrateVM.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgMigrateVM);
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
                                System.out.println("Unable to load balance because destination host could not lock sufficient resources or it is busy");
                            }
                            if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_CPU) {
                                CPUThresholdViolated = false;
                            } else if (loadBalancingCause == Consts.MIGRATION_CAUSE_VMWARE_JUST_MEMORY) {
                                memoryThresholdViolated = false;
                            }
                            try {
                                ACLMessage msgRequestUnlock = new ACLMessage(ACLMessage.REQUEST);
                                msgRequestUnlock.setSender(agt.getAID());
                                msgRequestUnlock.addReceiver(new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME));
                                msgRequestUnlock.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK);
                                msgRequestUnlock.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestUnlock);
                            } catch (Exception ex) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(ex);
                                }
                            }
                        }
                        finished = true; // finish the behaviour either way (failure or confirm)
                    } else {
                        long dt = wakeupTime - System.currentTimeMillis();
                        if (dt > 0) {
                            block(dt);
                            return;
                        } else {
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
                                ACLMessage msgRequestUnlock = new ACLMessage(ACLMessage.REQUEST);
                                msgRequestUnlock.setSender(agt.getAID());
                                msgRequestUnlock.addReceiver(new AID(decision.getSourceHost().getId(), AID.ISLOCALNAME));
                                msgRequestUnlock.addReceiver(new AID(decision.getDestinationHost().getId(), AID.ISLOCALNAME));
                                msgRequestUnlock.setConversationId(Consts.VMWARE_CONVERSATION_UNLOCK);
                                msgRequestUnlock.setContentObject((java.io.Serializable) decision.getSelectedVM());
                                agt.send(msgRequestUnlock);
                            } catch (Exception ex) {
                                if (Consts.EXCEPTIONS) {
                                    System.out.println(ex);
                                }
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
        private ACLMessage msg;
        private VirtualMachineDescription vm;
        private HostDescription randomlySelectedHost;

        public RequestsReceiver(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_INITIAL_VM_REQUEST);
        }

        @Override
        public void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                vm = (VirtualMachineDescription) msg.getContentObject();
                conversationId++;
                if (firstArrival) {
                    firstArrival = false;
                    if (Consts.LOG) {
                        addBehaviour(new Logger(agt));
                    }
                }
                vm.setConversationId(agt.getLocalName() + String.valueOf(conversationId));
                // Verifying whether there is a host with available cores to host the new VM
                ArrayList<HostDescription> availableHosts = new ArrayList<>(hosts);
                Predicate<HostDescription> condition = hostDescription -> vm.getMemory() > hostDescription.getAvailableMemory() || vm.getNumberOfVirtualCores() > hostDescription.getAvailableVirtualCores();
                availableHosts.removeIf(condition);
                if ((availableHosts.size() > 0)) { // If the VM can be hosted in the Datacenter
                    randomlySelectedHost = availableHosts.get((new Random()).nextInt(availableHosts.size()));
                    agt.addBehaviour(new virtualMachineAllocator(agt, randomlySelectedHost, vm)); // Allocate VM to a host selected at random.
                } else {
                    // behavior that keeps trying to allocate the VM
                    agt.addBehaviour(new PeriodicallyAttemptingToAllocateVM(agt, Consts.PERIOD_FOR_PERIODICALLY_ATTEMPTING_TO_ALLOCATE_VM, vm));
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
        private ACLMessage msg;

        public virtualMachineAllocator(Agent agt, HostDescription selectedHost, VirtualMachineDescription vm) {
            super(null);
            this.agt = agt;
            this.vm = vm;
            this.selectedHost = selectedHost;
        }

        @Override
        public void action() {
            try {
                if (configuration.getLOAD_BALANCING_TYPE() == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    if (!possiblyCompromisedHosts.contains(selectedHost))
                        possiblyCompromisedHosts.add(selectedHost); // add the selectedHost to the possibly compromised hosts that are not available to host a VM because of a concurrent initial VM allocation 
                }
                msg = new ACLMessage(ACLMessage.REQUEST);
                AID to = new AID(selectedHost.getId(), AID.ISLOCALNAME);
                msg.setSender(agt.getAID());
                msg.addReceiver(to);
                msg.setConversationId(Consts.CONVERSATION_VM_ALLOCATION);
                vm.setOwnerId(selectedHost.getId());
                msg.setContentObject((java.io.Serializable) vm);
                agt.send(msg);
                agt.addBehaviour(new ReceiverAck(agt, vm)); // I added the conversation id to listen for that specific message
            } catch (Exception ex) {
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
                if (configuration.getLOAD_BALANCING_TYPE() == Consts.VMWARE_CENTRALIZED_WITH_NO_COALITIONS) {
                    Predicate<HostDescription> condition = hostDescription -> hostDescription.getId().equals(msg.getSender().getLocalName());
                    possiblyCompromisedHosts.removeIf(condition);
                }
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
                stop(); // terminate ticker behavior
            }
        }
    }

    private class MonitorListener extends CyclicBehaviour {

        private Agent agt;
        private MessageTemplate mt;
        private ACLMessage msg;
        private HostDescription hostDescription;

        public MonitorListener(Agent agt) {
            this.agt = agt;
            this.mt = MessageTemplate.MatchConversationId(Consts.CONVERSATION_MONITOR_HOST);
        }

        @Override
        public synchronized void action() {
            msg = receive(mt);
            if (msg == null) {
                block();
                return;
            }
            try {
                hostDescription = (HostDescription) msg.getContentObject();
                updateHostsResourceConsumption(hostDescription);
                allocatorAgentGUI.updateServersMonitorList();
            } catch (Exception ex) {
                if (Consts.EXCEPTIONS) {
                    System.out.println(ex);
                }
            }
        }
    }

    private void updateHostsResourceConsumption(HostDescription hostDescriptionToBeUpdated) {
        try {
            for (int i = 0; i < hosts.size(); i++) {
                if (hosts.get(i).getId().equals(hostDescriptionToBeUpdated.getId())) {
                    hosts.get(i).setMemoryUsed(hostDescriptionToBeUpdated.getMemoryUsed());
                    hosts.get(i).setMemory(hostDescriptionToBeUpdated.getMemory());
                    hosts.get(i).setNumberOfVirtualCoresUsed(hostDescriptionToBeUpdated.getNumberOfVirtualCoresUsed());
                    hosts.get(i).setNumberOfVirtualCores(hostDescriptionToBeUpdated.getNumberOfVirtualCores());
                    hosts.get(i).setCPUUsage(hostDescriptionToBeUpdated.getCPUUsage());
                    hosts.get(i).setMemoryUsage(hostDescriptionToBeUpdated.getMemoryUsage());
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