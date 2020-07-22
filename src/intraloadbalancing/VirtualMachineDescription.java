/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import java.util.Comparator;

/**
 * @author octavio
 */
class VirtualMachineDescription implements java.io.Serializable, Comparable<VirtualMachineDescription> {

    private String id;
    private int numberOfVirtualCores;
    private int memory;                 // in GBs
    private double CPUUsage;            // value from 0 to 100
    private double memoryUsage;         // value from 0 to 100     
    private int CPUProfile;             // 0 Low CPU - 1 Medium CPU - 2 High CPU
    private int memoryProfile;          // 0 Low Memory - 1 Medium Memory - 2 High Memory
    private int sortingField;           // 0 sort by CPU Usage - 0 srot by Memory Usage
    private String conversationId;      // this is a control attribute for allocating the vm.       
    private boolean lock;               // this is to prevent or allow any type of operation over the VM
    private String containerName;
    private String ownerId;
    private String previousOwnerId;
    private String migrationType; //AtoB or BtoA
    private int migrationCause;
    private int coalition;


    public VirtualMachineDescription() {
        this.lock = false;
        this.containerName = "";
        this.migrationType = "";
    }

    public VirtualMachineDescription(String id) {
        this.id = id;
        this.lock = false;
        this.containerName = "";
        this.migrationType = "";
    }

    public VirtualMachineDescription(String id, int numberOfVirtualCores, int memory) {
        this.id = id;
        this.numberOfVirtualCores = numberOfVirtualCores;
        this.memory = memory;
        this.lock = false;
        this.containerName = "";
        this.migrationType = "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCoalition() {
        return coalition;
    }

    public void setCoalition(int coalition) {
        this.coalition = coalition;
    }

    public void setSortingField(int sortingField) {
        this.sortingField = sortingField;
    }

    public int getMigrationCause() {
        return migrationCause;
    }

    public void setMigrationCause(int migrationCause) {
        this.migrationCause = migrationCause;
    }

    public String getPreviousOwnerId() {
        return previousOwnerId;
    }

    public void setPreviousOwnerId(String previousOwnerId) {
        this.previousOwnerId = previousOwnerId;
    }

    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getId() {
        return id;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public void setCPUUsage(double CPUUsage) {
        this.CPUUsage = CPUUsage;
    }

    public int getCPUProfile() {
        return CPUProfile;
    }

    public int getMemoryProfile() {
        return memoryProfile;
    }

    public void setCPUProfile(int CPUProfile) {
        this.CPUProfile = CPUProfile;
    }

    public void setMemoryProfile(int memoryProfile) {
        this.memoryProfile = memoryProfile;
    }

    public double getCPUUsage() {
        return this.CPUUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setVirtualMachineId(String id) {
        this.id = id;
    }

    public String getVirtualMachineId() {
        return this.id;
    }

    public int getNumberOfVirtualCores() {
        return numberOfVirtualCores;
    }

    public void setNumberOfVirtualCores(int numberOfVirtualCores) {
        this.numberOfVirtualCores = numberOfVirtualCores;
    }

    public int getMemory() {
        return this.memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }

    public int getSortingField() {
        return sortingField;
    }

    public void setSortingField(byte sortingField) {
        this.sortingField = sortingField;
    }

    @Override
    public int compareTo(VirtualMachineDescription anotherVMDescription) {
        if (sortingField == 0) { // sort by CPU Usage
            if ((CPUUsage * numberOfVirtualCores) > (anotherVMDescription.CPUUsage * anotherVMDescription.numberOfVirtualCores)) {
                return 1;
            } else if ((CPUUsage * numberOfVirtualCores) < (anotherVMDescription.CPUUsage * anotherVMDescription.numberOfVirtualCores)) {
                return -1;
            } else {
                return 0;
            }
        } else { // sort by Memory Usage
            if ((memoryUsage * memory) > (anotherVMDescription.memoryUsage * anotherVMDescription.memory)) {
                return 1;
            } else if ((memoryUsage * memory) < (anotherVMDescription.memoryUsage * anotherVMDescription.memory)) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VirtualMachineDescription) {
            VirtualMachineDescription virtualMachineDescription = (VirtualMachineDescription) obj;
            return this.id.equals(virtualMachineDescription.id);
        } else {
            return false;
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String toString() {
        return "lock=" + lock
                + ", id=" + id
                + ", virtualCores=" + numberOfVirtualCores
                + ", CPUUsage=" + String.format("%.2f", CPUUsage)
                + ", CPUProfile=" + CPUProfile
                + ", mem=" + memory
                + ", memUsage=" + String.format("%.2f", memoryUsage)
                + ", memProfile=" + memoryProfile
                + ", container=" + containerName;

    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

}
