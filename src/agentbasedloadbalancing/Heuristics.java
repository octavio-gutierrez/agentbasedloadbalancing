/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class Heuristics {
    private HostDescription hostDescription;
    private Vector responses;
    private HostDescription selectedHost;
    private VirtualMachineDescription selectedVM;
    private double valuationValue;
    private String resource;
    private String protocolType;
    private HashSet<weightEdge> edges;
    private double diameterNetwork;

    public Heuristics(HostDescription hostDescription, int loadBalancingCause, Vector responses, HashSet<weightEdge> edges, String heuristic) {
        this.hostDescription = hostDescription;
        this.responses = responses;
        this.edges = (HashSet) (edges);
        this.diameterNetwork = getDiameterNetwork();
        this.selectedHost = null;
        this.selectedVM = null;
        switch (loadBalancingCause) {
            case Consts.MIGRATION_CAUSE_HIGH_CPU:
                this.resource = "cpu";
                this.protocolType = "AtoB";
                break;
            case Consts.MIGRATION_CAUSE_HIGH_MEMORY:
                this.resource = "memory";
                this.protocolType = "AtoB";
                break;
            case Consts.MIGRATION_CAUSE_LOW_CPU:
                this.resource = "cpu";
                this.protocolType = "BtoA";
                break;
            case Consts.MIGRATION_CAUSE_LOW_MEMORY:
                this.resource = "memory";
                this.protocolType = "BtoA";
        }
        if (heuristic.equals(Consts.EXHAUSTIVE)) heuristic_exhaustive();
        else if (heuristic.equals(Consts.MAXMIN)) heuristic_maxMinHostUsage();
        else if (heuristic.equals(Consts.ROULETTE)) heuristic_roulette_wheel();
    }

    double getDiameterNetwork() {
        Iterator<weightEdge> i = edges.iterator();
        double diameter = 0.0;
        while (i.hasNext()) {
            weightEdge edge = i.next();
            double d = edge.getDistance();
            if (d > diameter) {
                diameter = d;
            }
        }
        return diameter;
    }

    public HostDescription getSelectedHost() {
        return selectedHost;
    }

    public VirtualMachineDescription getSelectedVM() {
        return selectedVM;
    }

    public double getValuationValue() {
        return valuationValue;
    }

    private double getUsage(HostDescription hostA, HostDescription hostB, VirtualMachineDescription vm) {
        double sumCPUUsage = 0;  // percentage
        double sumMemoryUsage = 0;  // percentage
        double CPUUsage;
        double memoryUsage;
        int size = 0;
        size = hostA.getVirtualMachinesHosted().size();
        if (protocolType.equals("AtoB")) {
            if (vm != null && hostB != null) {
                if (hostA.getId().equals(hostDescription.getId())) { // it emulates that the VM is not inside in hostA
                    size--;
                } else if (hostA.getId().equals(hostB.getId())) {
                    sumCPUUsage = ((vm.getCPUUsage() / 100) * vm.getNumberOfVirtualCores());
                    sumMemoryUsage = (vm.getMemoryUsage() / 100) * vm.getMemory();
                    size++;
                }
            }
        } else if (protocolType.equals("BtoA")) {
            if (vm != null && hostB != null) {
                if (hostA.getId().equals(hostDescription.getId())) { // it emulates that the VM is inside in hostA
                    sumCPUUsage = ((vm.getCPUUsage() / 100) * vm.getNumberOfVirtualCores());
                    sumMemoryUsage = (vm.getMemoryUsage() / 100) * vm.getMemory();
                    size++;
                } else if (hostA.getId().equals(hostB.getId())) {
                    size--;
                }
            }
        }
        for (int i = 0; i < hostA.getVirtualMachinesHosted().size(); i++) { // it emulates that the VM is inside in hostB
            if (vm == null || !hostA.getVirtualMachinesHosted().get(i).getId().equals(vm.getId())) { // without considering the selected vm
                sumCPUUsage = sumCPUUsage + ((hostA.getVirtualMachinesHosted().get(i).getCPUUsage() / 100) * hostA.getVirtualMachinesHosted().get(i).getNumberOfVirtualCores());
                sumMemoryUsage = sumMemoryUsage + (hostA.getVirtualMachinesHosted().get(i).getMemoryUsage() / 100) * hostA.getVirtualMachinesHosted().get(i).getMemory();
            }
        }
        if (size > 0) {
            memoryUsage = (100 * sumMemoryUsage) / hostA.getMemory();
            if (memoryUsage > 100) memoryUsage = 100;
            CPUUsage = (100 * sumCPUUsage) / hostA.getNumberOfVirtualCores();
            if (CPUUsage > 100) CPUUsage = 100;
        } else {
            memoryUsage = 0;
            CPUUsage = 0;
        }
        if (resource.toLowerCase().equals("cpu")) {
            return CPUUsage;
        }
        return memoryUsage;
    }

    private double mean(HostDescription hostB, VirtualMachineDescription vm) {
        double sum;
        sum = getUsage(hostDescription, hostB, vm); // This is to take into account the resource usage of the INITIATOR host agent
        for (int i = 0; i < responses.size(); i++) { // responses from all the other (PARTICIPANT) hosts
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                sum += getUsage(participantHost, hostB, vm);
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return sum / (responses.size() + 1);
    }

    private double stdDev(HostDescription hostB, VirtualMachineDescription vm) {
        double mean = mean(hostB, vm);
        double sum = Math.pow(getUsage(hostDescription, hostB, vm) - mean, 2);
        for (int i = 0; i < responses.size(); i++) {// responses from all the other (PARTICIPANT) hosts
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                sum += Math.pow(getUsage(participantHost, hostB, vm) - mean, 2);
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return Math.sqrt(sum / (responses.size() + 1));
    }

    private double preimputation() {
        return preimputation(null, null);
    }

    private double preimputation(HostDescription hostB, VirtualMachineDescription vm) {
        double coalition_value = Math.abs(1 - stdDev(hostB, vm) / 50);  // the coalition value
        double sum = 0.0;
        if (resource.toLowerCase().equals("cpu")) {
            sum += hostDescription.getNumberOfVirtualCores();
        } else if (resource.toLowerCase().equals("memory")) {
            sum += hostDescription.getMemory();
        }
        for (int i = 0; i < responses.size(); i++) { // responses from all the other (PARTICIPANT) hosts
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                if (resource.toLowerCase().equals("cpu")) {
                    sum += participantHost.getNumberOfVirtualCores();
                } else if (resource.toLowerCase().equals("memory")) {
                    sum += participantHost.getMemory();
                }
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        double preimputation = coalition_value;
        if (resource.toLowerCase().equals("cpu")) {
            preimputation *= hostDescription.getNumberOfVirtualCores() / sum;
        } else if (resource.toLowerCase().equals("memory")) {
            preimputation *= hostDescription.getMemory() / sum;
        }
        return preimputation;
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

    private double valuation_function(HostDescription hostB, VirtualMachineDescription vm) {
        double tplusOne = preimputation(hostB, vm);
        double t = preimputation();
        double dist = getDistance(hostDescription.getId(), hostB.getId());
        double valuation = (tplusOne - t) / dist;
        return valuation;
    }

    public void heuristic_exhaustive() {
        double val, max_val;
        max_val = val = 0.0;
        for (int i = 0; i < responses.size(); i++) { // responses from all the other (PARTICIPANT) hosts
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                if (protocolType.equals("AtoB")) {
                    if (hostDescription.getVirtualMachinesHosted() != null && hostDescription.getVirtualMachinesHosted().size() > 0) {
                        for (int j = 0; j < hostDescription.getVirtualMachinesHosted().size(); j++) {
                            if ((hostDescription.getVirtualMachinesHosted().get(j).getNumberOfVirtualCores() <= participantHost.getAvailableVirtualCores()) && (hostDescription.getVirtualMachinesHosted().get(j).getMemory() <= participantHost.getAvailableMemory())) // is the vm fit in the proposed host?
                            {
                                val = valuation_function(participantHost, hostDescription.getVirtualMachinesHosted().get(j));
                                if (val > max_val) {
                                    this.selectedHost = participantHost;
                                    this.selectedVM = hostDescription.getVirtualMachinesHosted().get(j);
                                    this.valuationValue = max_val = val;
                                }
                            }
                        }
                    }
                } else if (protocolType.equals("BtoA")) {
                    if (participantHost.getVirtualMachinesHosted() != null && participantHost.getVirtualMachinesHosted().size() > 0) {
                        for (int j = 0; j < participantHost.getVirtualMachinesHosted().size(); j++) {
                            if ((participantHost.getVirtualMachinesHosted().get(j).getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (participantHost.getVirtualMachinesHosted().get(j).getMemory() <= hostDescription.getAvailableMemory())) // is the proposed vm fit in the host?
                            {
                                val = valuation_function(hostDescription, participantHost.getVirtualMachinesHosted().get(j));
                                if (val > max_val) {
                                    this.selectedHost = participantHost;
                                    this.selectedVM = participantHost.getVirtualMachinesHosted().get(j);
                                    this.valuationValue = max_val = val;
                                }
                            }
                        }
                    }
                }
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }


    public void heuristic_maxMinHostUsage() {
        double val, max_val, min_val;
        max_val = val = 0.0;
        min_val = Double.MAX_VALUE;
        this.selectedHost = null;
        HostDescription participantHost = null;
        for (int i = 0; i < responses.size(); i++) { // responses from all the other (PARTICIPANT) hosts
            try {
                participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                if (resource.toLowerCase().equals("cpu")) {
                    val = participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    val = participantHost.getMemoryUsage();
                }
                if (protocolType.equals("AtoB") && val < min_val) {
                    this.selectedHost = participantHost;
                    min_val = val;
                } else if (protocolType.equals("BtoA") && val > max_val) {
                    this.selectedHost = participantHost;
                    max_val = val;
                }
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }

        ArrayList<VirtualMachineDescription> list_vm = null;
        HostDescription target_host = null;
        if (this.selectedHost == null) {
            this.selectedVM = null;
            if (!Consts.LOG) {
                System.out.println("MAXMIN: None of the host agents of the coalition are available");
            }
        } else if (protocolType.equals("AtoB")) {
            list_vm = hostDescription.getVirtualMachinesHosted();
            target_host = this.selectedHost;
        } else if (protocolType.equals("BtoA")) {
            list_vm = this.selectedHost.getVirtualMachinesHosted();
            target_host = hostDescription;
        }

        max_val = 0.0;
        if (list_vm != null && list_vm.size() > 0) {
            for (VirtualMachineDescription vm : list_vm) {
                System.out.println("MAXMIN: vm.getMemory() = " + vm.getMemory() + "; target_host.getAvailableMemory() = " + target_host.getAvailableMemory() + "; vm.getNumberOfVirtualCores() = " + vm.getNumberOfVirtualCores() + "; target_host.getAvailableVirtualCores() = " + target_host.getAvailableVirtualCores());
                if ((vm.getMemory() <= target_host.getAvailableMemory()) && (vm.getNumberOfVirtualCores() <= target_host.getAvailableVirtualCores())) { // if the host has sufficient resources to allocate the VM
                    val = valuation_function(target_host, vm);
                    if (val > max_val) {
                        this.selectedVM = vm;
                        this.valuationValue = max_val = val;
                    }
                }
            }
        }
        if (this.selectedVM == null) {
            this.selectedHost = null;
            if (!Consts.LOG) {
                System.out.println("MAXMIN: None of the virtual machines of the selected host fit in the target host");
            }
        }
    }


    private double GetRandomNumber(double minimum, double maximum) {
        Random random = new Random();
        return random.nextDouble() * (maximum - minimum) + minimum;
    }

    public void heuristic_roulette_wheel() {
        double total_sum = 0.0;
        try {
            for (int i = 0; i < responses.size(); i++) {// responses from all the other (PARTICIPANT) hosts
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                if (resource.toLowerCase().equals("cpu")) {
                    total_sum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    total_sum += participantHost.getMemoryUsage();
                }
            }
        } catch (UnreadableException ex) {
            Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        double rand = GetRandomNumber(0, total_sum);
        double partialSum = 0;
        try {
            for (int i = 0; i < responses.size(); i++) { // responses from all the other (PARTICIPANT) hosts
                HostDescription participantHost = (HostDescription) ((ACLMessage) responses.get(i)).getContentObject();
                if (participantHost == null) continue;
                if (resource.toLowerCase().equals("cpu")) {
                    partialSum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    partialSum += participantHost.getMemoryUsage();
                }
                if (partialSum >= rand) {
                    this.selectedHost = participantHost;
                }
            }
        } catch (UnreadableException ex) {
            Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        ArrayList<VirtualMachineDescription> list_vm = null;
        HostDescription target_host = null;
        if (this.selectedHost == null) {
            this.selectedVM = null;
            if (!Consts.LOG) {
                System.out.println("MAXMIN: None of the host agents of the coalition are available");
            }
        } else if (protocolType.equals("AtoB")) {
            list_vm = hostDescription.getVirtualMachinesHosted();
            target_host = this.selectedHost;
        } else if (protocolType.equals("BtoA")) {
            list_vm = this.selectedHost.getVirtualMachinesHosted();
            target_host = hostDescription;
        }

        double val, max_val = 0.0;
        if (list_vm != null && list_vm.size() > 0) {
            for (VirtualMachineDescription vm : list_vm) {
                System.out.println("ROULETTE_WHEEL: vm.getMemory() = " + vm.getMemory() + "; target_host.getAvailableMemory() = " + target_host.getAvailableMemory() + "; vm.getNumberOfVirtualCores() = " + vm.getNumberOfVirtualCores() + "; target_host.getAvailableVirtualCores() = " + target_host.getAvailableVirtualCores());
                if ((vm.getMemory() <= target_host.getAvailableMemory()) && (vm.getNumberOfVirtualCores() <= target_host.getAvailableVirtualCores())) { // if the host has sufficient resources to allocate the VM
                    val = valuation_function(target_host, vm);
                    if (val > max_val) {
                        this.selectedVM = vm;
                        this.valuationValue = max_val = val;
                    }
                }
            }
        }
        if (this.selectedVM == null) {
            this.selectedHost = null;
            if (!Consts.LOG) {
                System.out.println("ROULETTE_WHEEL: None of the virtual machines of the selected host fit in the target host");
            }
        }
    }
}
