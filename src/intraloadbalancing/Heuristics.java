package intraloadbalancing;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Heuristics {
    private HostDescription hostDescription;
    private Vector responses;
    private HostDescription selectedHost;
    private VirtualMachineDescription selectedVM;
    private double valuationValue;
    private String resource;
    private String protocolType;


    public Heuristics(HostDescription hostDescription, int loadBalancingCause, Vector responses) {
        this.hostDescription = hostDescription;
        this.responses = responses;
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

    // returns the usage of hostA
    // if vm and hostB are not null, it considers a new virtual machine 'vm' into hostA
    private double getUsage(HostDescription hostA, HostDescription hostB, VirtualMachineDescription vm) {
        double sumCPUUsage = 0;  // percentage
        double sumMemoryUsage = 0;  // percentage
        double CPUUsage;
        double memoryUsage;
        int size = 0;
        //if(hostA != null && hostA.getVirtualMachinesHosted() != null)
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

        for (VirtualMachineDescription vmDescription : hostA.getVirtualMachinesHosted()) { // it emulates that the VM is inside in hostB
            if (vm == null || !vmDescription.getId().equals(vm.getId())) { // without considering the selected vm
                sumCPUUsage = sumCPUUsage + ((vmDescription.getCPUUsage() / 100) * vmDescription.getNumberOfVirtualCores());
                sumMemoryUsage = sumMemoryUsage + (vmDescription.getMemoryUsage() / 100) * vmDescription.getMemory();
            }
        }

        if (size > 0) {
            memoryUsage = (100 * sumMemoryUsage) / hostA.getMemory();
            if (memoryUsage > 100)
                memoryUsage = 100;
            CPUUsage = (100 * sumCPUUsage) / hostA.getNumberOfVirtualCores();
            if (CPUUsage > 100)
                CPUUsage = 100;
        } else {
            memoryUsage = 0;
            CPUUsage = 0;
        }

        if (resource.toLowerCase().equals("cpu")) {
            return CPUUsage;
        }
        return memoryUsage;
    }

    // Mean usage of resources per coalition, considering the resources reserved by the virtual machines
    private double mean(HostDescription hostB, VirtualMachineDescription vm) {
        double sum;
        sum = getUsage(hostDescription, hostB, vm); // This is to take into account the resource usage of the INITIATOR host agent
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null)
                    continue;
                sum += getUsage(participantHost, hostB, vm);
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return sum / (responses.size() + 1);
    }

    // Standard deviation of usage of resources per coalition, considering the resources reserved by the virtual machines
    private double stdDev(HostDescription hostB, VirtualMachineDescription vm) {
        double mean = mean(hostB, vm);
        double sum = Math.pow(getUsage(hostDescription, hostB, vm) - mean, 2);
        Enumeration participantHosts = responses.elements();  // responses from all the other (PARTICIPANTS) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null)
                    continue;
                sum += Math.pow(getUsage(participantHost, hostB, vm) - mean, 2);
            } catch (UnreadableException ex) {
                Logger.getLogger(HostAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return Math.sqrt(sum / (responses.size() + 1));
    }

    // The individual utility of the host
    private double preimputation() {
        return preimputation(null, null);
    }

    // The individual utility of another host
    private double preimputation(HostDescription hostB, VirtualMachineDescription vm) {
        double coalition_value = Math.abs(1 - stdDev(hostB, vm) / 50);  // the coalition value

        double sum = 0.0;
        if (resource.toLowerCase().equals("cpu")) {
            sum += hostDescription.getNumberOfVirtualCores();
        } else if (resource.toLowerCase().equals("memory")) {
            sum += hostDescription.getMemory();
        }
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null)
                    continue;
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


    private double valuation_function(HostDescription hostB, VirtualMachineDescription vm) {
        return preimputation(hostB, vm) - preimputation(); // pi = preimputation(t+1) - preimputation(t)
    }


    // heuristics
    public void heuristic_exhaustive() {
        double val = 0.0;
        double max_val = val;
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null) // ¿porqué a veces ocurre esto?
                    continue;
                if (protocolType.equals("AtoB")) {
                    if (hostDescription.getVirtualMachinesHosted() != null && hostDescription.getVirtualMachinesHosted().size() > 0) {
                        for (VirtualMachineDescription vmDescription : hostDescription.getVirtualMachinesHosted()) {
                            if ((vmDescription.getNumberOfVirtualCores() <= participantHost.getAvailableVirtualCores()) && (vmDescription.getMemory() <= participantHost.getAvailableMemory())) // is the vm fit in the proposed host?
                            {
                                val = valuation_function(participantHost, vmDescription);
                                if (val > max_val) {
                                    this.selectedHost = participantHost;
                                    this.selectedVM = vmDescription;
                                    max_val = val;
                                    this.valuationValue = max_val;
                                }
                            }
                        }
                    }
                } else if (protocolType.equals("BtoA")) {
                    if (participantHost.getVirtualMachinesHosted() != null && participantHost.getVirtualMachinesHosted().size() > 0) {
                        for (VirtualMachineDescription vmDescription : participantHost.getVirtualMachinesHosted()) {
                            if ((vmDescription.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (vmDescription.getMemory() <= hostDescription.getAvailableMemory())) // is the proposed vm fit in the host?
                            {
                                val = valuation_function(hostDescription, vmDescription);
                                if (val > max_val) {
                                    this.selectedHost = participantHost;
                                    this.selectedVM = vmDescription;
                                    max_val = val;
                                    this.valuationValue = max_val;
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
        double val, max_val;
        max_val = val = 0.0;
        double min_val = Double.MAX_VALUE;
        HostDescription participantHost = null;
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        while (participantHosts.hasMoreElements()) {
            try {
                participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null) // ¿porqué a veces ocurre esto?
                    continue;
                if (resource.toLowerCase().equals("cpu")) {
                    val = participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    val = participantHost.getMemoryUsage();
                }
                if (protocolType.equals("AtoB") && val > max_val) {
                    this.selectedHost = participantHost;
                    max_val = val;
                } else if (protocolType.equals("BtoA") && val < min_val) {
                    this.selectedHost = participantHost;
                    min_val = val;
                }
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
        max_val = 0.0;
        if (protocolType.equals("AtoB")) {
            if (hostDescription.getVirtualMachinesHosted() != null && hostDescription.getVirtualMachinesHosted().size() > 0) {
                for (VirtualMachineDescription vmDescription : hostDescription.getVirtualMachinesHosted()) {
                    if ((vmDescription.getNumberOfVirtualCores() <= participantHost.getAvailableVirtualCores()) && (vmDescription.getMemory() <= participantHost.getAvailableMemory())) { // is the vm fit in the proposed host?
                        val = valuation_function(participantHost, vmDescription);
                        if (val > max_val) {
                            this.selectedHost = participantHost;
                            this.selectedVM = vmDescription;
                            max_val = val;
                            this.valuationValue = max_val;
                        }
                    }
                }
            }
        } else if (protocolType.equals("BtoA")) {
            if (participantHost.getVirtualMachinesHosted() != null && participantHost.getVirtualMachinesHosted().size() > 0) {
                for (VirtualMachineDescription vmDescription : participantHost.getVirtualMachinesHosted()) {
                    if ((vmDescription.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (vmDescription.getMemory() <= hostDescription.getAvailableMemory())) { // is the proposed vm fit in the host?
                        val = valuation_function(hostDescription, vmDescription);
                        if (val > max_val) {
                            this.selectedHost = participantHost;
                            this.selectedVM = vmDescription;
                            max_val = val;
                            this.valuationValue = max_val;
                        }
                    }
                }
            }
        }
    }


    public void sort(final double usage, Vector<HostDescription> itemLocationList) {
        Collections.sort(itemLocationList, new Comparator<HostDescription>() {
            @Override
            public int compare(HostDescription o1, HostDescription o2) {
                return 0;
            }
        });
    }

    public void heuristic_sortedHostUsage() {
        double val, max_val;
        max_val = val = 0.0;
        double min_val = Double.MAX_VALUE;
        HostDescription participantHost = null;
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        //responses.sort();

    }


    private double GetRandomNumber(double minimum, double maximum) {
        Random random = new Random();
        return random.nextDouble() * (maximum - minimum) + minimum;
    }

    public void heuristic_roulette_wheel() {
        double total_sum = 0.0;
        Enumeration participantHosts = responses.elements(); // responses from all the other (PARTICIPANT) hosts
        try {
            while (participantHosts.hasMoreElements()) {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null)
                    continue;
                if (resource.toLowerCase().equals("cpu")) {
                    total_sum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    total_sum += participantHost.getMemoryUsage();
                }

            }
            double rand = GetRandomNumber(0, total_sum);
            double partialSum = 0;
            while (participantHosts.hasMoreElements()) {
                HostDescription participantHost = (HostDescription) ((ACLMessage) participantHosts.nextElement()).getContentObject();
                if (participantHost == null)
                    continue;
                if (resource.toLowerCase().equals("cpu")) {
                    partialSum += participantHost.getCPUUsage();
                } else if (resource.toLowerCase().equals("memory")) {
                    partialSum += participantHost.getMemoryUsage();
                }
                if (partialSum >= rand) {
                    this.selectedHost = participantHost;
                }
            }

            double val, max_val = 0.0;
            HostDescription participantHost = this.selectedHost;
            if (protocolType.equals("AtoB")) {
                if (hostDescription.getVirtualMachinesHosted() != null && hostDescription.getVirtualMachinesHosted().size() > 0) {
                    for (VirtualMachineDescription vmDescription : hostDescription.getVirtualMachinesHosted()) {
                        if ((vmDescription.getNumberOfVirtualCores() <= participantHost.getAvailableVirtualCores()) && (vmDescription.getMemory() <= participantHost.getAvailableMemory())) { // is the vm fit in the proposed host?
                            val = valuation_function(participantHost, vmDescription);
                            if (val > max_val) {
                                this.selectedHost = participantHost;
                                this.selectedVM = vmDescription;
                                max_val = val;
                                this.valuationValue = max_val;
                            }
                        }
                    }
                }
            } else if (protocolType.equals("BtoA")) {
                if (participantHost.getVirtualMachinesHosted() != null && participantHost.getVirtualMachinesHosted().size() > 0) {
                    for (VirtualMachineDescription vmDescription : participantHost.getVirtualMachinesHosted()) {
                        if ((vmDescription.getNumberOfVirtualCores() <= hostDescription.getAvailableVirtualCores()) && (vmDescription.getMemory() <= hostDescription.getAvailableMemory())) { // is the proposed vm fit in the host?
                            val = valuation_function(hostDescription, vmDescription);
                            if (val > max_val) {
                                this.selectedHost = participantHost;
                                this.selectedVM = vmDescription;
                                max_val = val;
                                this.valuationValue = max_val;
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
