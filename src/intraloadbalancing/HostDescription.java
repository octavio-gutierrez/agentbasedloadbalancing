package intraloadbalancing;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * @author JGUTIERRGARC
 */
class HostDescription implements java.io.Serializable {

    private String id = "";
    private boolean inProgress = false;
    private int coalition = -1;
    private double memoryUsage = 0;
    private double CPUUsage = 0;
    private double memory = 0;
    private double memoryUsed = 0;
    private double lockedMemory = 0;
    private int numberOfVirtualCores = 0;
    private int numberOfVirtualCoresUsed = 0;
    private int numberOfLockedVirtualCores = 0;
    private int lowMigrationThresholdForCPU = 0;
    private int highMigrationThresholdForCPU = 0;
    private int lowMigrationThresholdForMemory = 0;
    private int highMigrationThresholdForMemory = 0;
    private boolean willingToParticipateInCNP = false;
    private boolean leader = false;
    private int CPUMigrationHeuristicId = 0;
    private int memoryMigrationHeuristicId = 0; // TBD potentially a random heuristic to avoid bias
    private String containerName = "";
    private ArrayList<VirtualMachineDescription> virtualMachinesHosted = new ArrayList<VirtualMachineDescription>();
    ;
    private String allocatorId = "";
    private String myLeader = "";

    public HostDescription() {
        this.willingToParticipateInCNP = true;
        this.virtualMachinesHosted = new ArrayList<VirtualMachineDescription>();
    }


    public HostDescription(boolean leader, String id, int coalition, double memoryUsage, double CPUUsage, double memory, double memoryUsed, int numberOfVirtualCores, int numberOfVirtualCoresUsed, int lowMigrationThresholdForCPU, int highMigrationThresholdForCPU, int lowMigrationThresholdForMemory, int highMigrationThresholdForMemory, int CPUMigrationHeuristicId, int memoryMigrationHeuristicId, String allocatorId, String containerName, String myLeader) {
        this.leader = leader;
        this.id = id;
        this.coalition = coalition;
        this.memoryUsage = memoryUsage;
        this.CPUUsage = CPUUsage;
        this.memory = memory;
        this.memoryUsed = memoryUsed;
        this.numberOfVirtualCores = numberOfVirtualCores;
        this.numberOfVirtualCoresUsed = numberOfVirtualCoresUsed;
        this.lowMigrationThresholdForCPU = lowMigrationThresholdForCPU;
        this.highMigrationThresholdForCPU = highMigrationThresholdForCPU;
        this.lowMigrationThresholdForMemory = lowMigrationThresholdForMemory;
        this.highMigrationThresholdForMemory = highMigrationThresholdForMemory;
        this.CPUMigrationHeuristicId = CPUMigrationHeuristicId;
        this.memoryMigrationHeuristicId = memoryMigrationHeuristicId;
        this.virtualMachinesHosted = new ArrayList<VirtualMachineDescription>();
        this.containerName = containerName;
        this.allocatorId = allocatorId;
        this.lockedMemory = 0.0;
        this.numberOfLockedVirtualCores = 0;
        this.myLeader = myLeader;
        this.willingToParticipateInCNP = true;
        this.inProgress = false;
    }

    public void setVirtualMachinesHosted(ArrayList<VirtualMachineDescription> virtualMachinesHosted) {
        this.virtualMachinesHosted = virtualMachinesHosted;
    }

    public void setAllocatorId(String allocatorId) {
        this.allocatorId = allocatorId;
    }

    public void setMyLeader(String myLeader) {
        this.myLeader = myLeader;
    }

    public boolean isWillingToParticipateInCNP() {
        return willingToParticipateInCNP;
    }

    public void setWillingToParticipateInCNP(boolean willingToParticipateInCNP) {
        this.willingToParticipateInCNP = willingToParticipateInCNP;
    }

    public ArrayList<VirtualMachineDescription> getVirtualMachinesHosted() {
        return virtualMachinesHosted;
    }

    public boolean isVirtualMachineHosted(String id) {
        ArrayList<VirtualMachineDescription> availableVMs = new ArrayList<>(virtualMachinesHosted);
        Predicate<VirtualMachineDescription> condition = vmDescription -> !vmDescription.getId().equals(id);
        availableVMs.removeIf(condition);
        return !availableVMs.isEmpty();
    }


    public String getMyLeader() {
        return myLeader;
    }

    public boolean isLeader() {
        return leader;
    }

    public void setLeader(boolean leader) {
        this.leader = leader;
    }

    public int getCoalition() {
        return coalition;
    }

    public void setCoalition(int coalition) {
        this.coalition = coalition;
    }

    public int getAvailableVirtualCores() {
        return numberOfVirtualCores - numberOfVirtualCoresUsed;
    }

    public double getAvailableMemory() {
        return memory - memoryUsed;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getCPUUsage() {
        return CPUUsage;
    }

    public void setCPUUsage(double CPUUsage) {
        this.CPUUsage = CPUUsage;
    }

    public double getMemory() {
        return memory;
    }

    public double getLockedMemory() {
        return lockedMemory;
    }

    public void setMemory(double memory) {
        this.memory = memory;
    }

    public void setLockedMemory(double lockedMemory) {
        this.lockedMemory = lockedMemory;
    }

    public double getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(double memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public int getNumberOfVirtualCores() {
        return numberOfVirtualCores;
    }

    public void setNumberOfVirtualCores(int numberOfVirtualCores) {
        this.numberOfVirtualCores = numberOfVirtualCores;
    }

    public int getNumberOfVirtualCoresUsed() {
        return numberOfVirtualCoresUsed;
    }

    public int getNumberOfLockedVirtualCores() {
        return numberOfLockedVirtualCores;
    }

    public void setNumberOfLockedVirtualCores(int numberOfLockedVirtualCores) {
        this.numberOfLockedVirtualCores = numberOfLockedVirtualCores;
    }

    public void setNumberOfVirtualCoresUsed(int numberOfVirtualCoresUsed) {
        this.numberOfVirtualCoresUsed = numberOfVirtualCoresUsed;
    }

    public int getLowMigrationThresholdForCPU() {
        return lowMigrationThresholdForCPU;
    }

    public void setLowMigrationThresholdForCPU(int lowMigrationThresholdForCPU) {
        this.lowMigrationThresholdForCPU = lowMigrationThresholdForCPU;
    }

    public int getHighMigrationThresholdForCPU() {
        return highMigrationThresholdForCPU;
    }

    public void setHighMigrationThresholdForCPU(int highMigrationThresholdForCPU) {
        this.highMigrationThresholdForCPU = highMigrationThresholdForCPU;
    }

    public int getLowMigrationThresholdForMemory() {
        return lowMigrationThresholdForMemory;
    }

    public void setLowMigrationThresholdForMemory(int lowMigrationThresholdForMemory) {
        this.lowMigrationThresholdForMemory = lowMigrationThresholdForMemory;
    }

    public int getHighMigrationThresholdForMemory() {
        return highMigrationThresholdForMemory;
    }

    public void setHighMigrationThresholdForMemory(int highMigrationThresholdForMemory) {
        this.highMigrationThresholdForMemory = highMigrationThresholdForMemory;
    }

    public int getCPUMigrationHeuristicId() {
        return CPUMigrationHeuristicId;
    }

    public void setCPUMigrationHeuristicId(int CPUMigrationHeuristicId) {
        this.CPUMigrationHeuristicId = CPUMigrationHeuristicId;
    }

    public int getMemoryMigrationHeuristicId() {
        return memoryMigrationHeuristicId;
    }

    public String getAllocatorId() {
        return allocatorId;
    }

    public void setMemoryMigrationHeuristicId(int memoryMigrationHeuristicId) {
        this.memoryMigrationHeuristicId = memoryMigrationHeuristicId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HostDescription) {
            HostDescription hostDescription = (HostDescription) obj;
            return this.id.equals(hostDescription.id);
        } else {
            return false;
        }
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    @Override
    public String toString() {
        return "InProgress=" + inProgress
                + ", id=" + id
                + ", coalition=" + coalition
                + ", Leader=" + leader
                + ", containerName=" + containerName
                + ", nCores=" + numberOfVirtualCores
                + ", nCoresUsed=" + numberOfVirtualCoresUsed
                + ", nLockedCores=" + numberOfLockedVirtualCores
                + ", CPUUsage=" + String.format("%.2f", CPUUsage)
                + ", mem=" + memory
                + ", memUsed=" + memoryUsed
                + ", memUsage=" + String.format("%.2f", memoryUsage)
                + ", lockedMemory=" + String.format("%.2f", lockedMemory)
                + ", lowThresCPU=" + lowMigrationThresholdForCPU
                + ", highThresCPU=" + highMigrationThresholdForCPU
                + ", lowThresMem=" + lowMigrationThresholdForMemory
                + ", highThresMem=" + highMigrationThresholdForMemory
                + ", myLeader=" + myLeader
                + ", numberOfVMs=" + virtualMachinesHosted.size();
    }

}
