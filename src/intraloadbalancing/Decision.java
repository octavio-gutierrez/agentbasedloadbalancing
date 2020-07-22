/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import java.io.Serializable;

/**
 * @author octavio
 */
public class Decision implements Serializable {

    private HostDescription destinationHost;
    private HostDescription sourceHost;
    private VirtualMachineDescription selectedVM;
    private int decision;
    private int lowMigrationThresholdForCPU;
    private int highMigrationThresholdForCPU;
    private int lowMigrationThresholdForMemory;
    private int highMigrationThresholdForMemory;

    // decision = -1 : null value meaning no decision. Consts.DECISION_TYPE_DONT_MIGRATE = -1;
    // decision = 0 : selectedVM should be migrated from this host to the selectedHost.  Consts.DECISION_TYPE_MIGRATE_FROM_A_TO_B = 0;
    // decision = 1 : selectedVM should be migrated from the selectedHost to this host. Consts.DECISION_TYPE_MIGRATE_FROM_B_TO_A = 1;

    public Decision() {
        this.sourceHost = new HostDescription();
        this.destinationHost = new HostDescription();
        this.selectedVM = new VirtualMachineDescription();
        this.decision = -1;
    }

    public Decision(HostDescription sourceHost, HostDescription destinationHost, VirtualMachineDescription selectedVM, int decision) {
        this.sourceHost = sourceHost;
        this.destinationHost = destinationHost;
        this.selectedVM = selectedVM;
        this.decision = decision;
    }


    public HostDescription getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(HostDescription sourceHost) {
        this.sourceHost = sourceHost;
    }

    public HostDescription getDestinationHost() {
        return destinationHost;
    }

    public void setDestinationHost(HostDescription destinationHost) {
        this.destinationHost = destinationHost;
    }

    public VirtualMachineDescription getSelectedVM() {
        return selectedVM;
    }

    public void setSelectedVM(VirtualMachineDescription selectedVM) {
        this.selectedVM = selectedVM;
    }

    public int getDecision() {
        return decision;
    }

    public void setDecision(int decision) {
        this.decision = decision;
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

    @Override
    public String toString() {
        return "Decision{" + "destinationHost=" + destinationHost + ", sourceHost=" + sourceHost + ", selectedVM=" + selectedVM + ", decision=" + decision + ", lowMigrationThresholdForCPU=" + lowMigrationThresholdForCPU + ", highMigrationThresholdForCPU=" + highMigrationThresholdForCPU + ", lowMigrationThresholdForMemory=" + lowMigrationThresholdForMemory + ", highMigrationThresholdForMemory=" + highMigrationThresholdForMemory + '}';
    }


}
