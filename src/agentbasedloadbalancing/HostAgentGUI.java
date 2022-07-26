/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */

package agentbasedloadbalancing;

import java.text.DecimalFormat;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class HostAgentGUI extends javax.swing.JFrame {

    private HostDescription hostDescription;

    public HostAgentGUI(HostDescription hostDescription) {
        this.hostDescription = hostDescription;
        initComponents();
    }

    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jRadioButton1 = new javax.swing.JRadioButton();
        buttonGroup2 = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        listVirtualMachines = new javax.swing.JList();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        labelLogicalCores = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        labelMemory = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        labelCPUUsage = new javax.swing.JLabel();
        labelMemoryUsage = new javax.swing.JLabel();
        sliderLowCPUMigrationThreshold = new javax.swing.JSlider();
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        sliderHighCPUMigrationThreshold = new javax.swing.JSlider();
        jLabel8 = new javax.swing.JLabel();
        sliderHighMemoryMigrationThreshold = new javax.swing.JSlider();
        sliderLowMemoryMigrationThreshold = new javax.swing.JSlider();
        jLabel10 = new javax.swing.JLabel();
        labelAvailableLogicalCores = new javax.swing.JLabel();
        labelAvailableMemory = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        labelCoalition = new javax.swing.JLabel();
        jRadioButton1.setText("jRadioButton1");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });
        jScrollPane1.setViewportView(listVirtualMachines);
        listVirtualMachines.getAccessibleContext().setAccessibleDescription("");
        jLabel1.setText("Virtual Machines Hosted");
        jLabel2.setText("Host's information");
        jLabel7.setText("Available cores:");
        labelLogicalCores.setText("0");
        jLabel11.setText("Available memory (GBs):");
        labelMemory.setText("0");
        jLabel13.setText("Overall CPU usage:");
        jLabel14.setText("Overall memory usage:");
        labelCPUUsage.setText("0%");
        labelMemoryUsage.setText("0%");
        sliderLowCPUMigrationThreshold.setPaintTicks(true);
        sliderLowCPUMigrationThreshold.setValue(10);
        sliderLowCPUMigrationThreshold.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliderLowCPUMigrationThresholdStateChanged(evt);
            }
        });
        jLabel4.setText("Low CPU migration threshold = 10%");
        jLabel6.setText("High CPU migration threshold = 90%");
        sliderHighCPUMigrationThreshold.setPaintTicks(true);
        sliderHighCPUMigrationThreshold.setValue(90);
        sliderHighCPUMigrationThreshold.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliderHighCPUMigrationThresholdStateChanged(evt);
            }
        });
        jLabel8.setText("High memory threshold = 90%");
        sliderHighMemoryMigrationThreshold.setPaintTicks(true);
        sliderHighMemoryMigrationThreshold.setValue(90);
        sliderHighMemoryMigrationThreshold.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliderHighMemoryMigrationThresholdStateChanged(evt);
            }
        });
        sliderLowMemoryMigrationThreshold.setPaintTicks(true);
        sliderLowMemoryMigrationThreshold.setValue(10);
        sliderLowMemoryMigrationThreshold.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliderLowMemoryMigrationThresholdStateChanged(evt);
            }
        });
        jLabel10.setText("Low memory migration threshold = 10%");
        labelAvailableLogicalCores.setText("0");
        labelAvailableMemory.setText("0");
        jLabel9.setText("out of");
        jLabel12.setText("out of");
        jLabel3.setText("Coalition: ");
        labelCoalition.setText("0");
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(20, 20, 20)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(jLabel11)
                                                                        .addComponent(jLabel7))
                                                                .addGap(29, 29, 29)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                        .addComponent(labelAvailableLogicalCores, javax.swing.GroupLayout.DEFAULT_SIZE, 73, Short.MAX_VALUE)
                                                                        .addComponent(labelAvailableMemory, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(jLabel12, javax.swing.GroupLayout.Alignment.TRAILING)
                                                                        .addComponent(jLabel9, javax.swing.GroupLayout.Alignment.TRAILING))
                                                                .addGap(34, 34, 34)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                        .addComponent(labelLogicalCores, javax.swing.GroupLayout.DEFAULT_SIZE, 69, Short.MAX_VALUE)
                                                                        .addComponent(labelMemory, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                                .addGap(100, 100, 100)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addComponent(jLabel14)
                                                                                .addGap(18, 18, 18)
                                                                                .addComponent(labelMemoryUsage))
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addComponent(jLabel13)
                                                                                .addGap(45, 45, 45)
                                                                                .addComponent(labelCPUUsage))
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addComponent(jLabel3)
                                                                                .addGap(98, 98, 98)
                                                                                .addComponent(labelCoalition)))
                                                                .addGap(62, 62, 62))
                                                        .addComponent(jScrollPane1))
                                                .addContainerGap())
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(jLabel2)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(sliderLowMemoryMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(42, 42, 42)
                                                                .addComponent(jLabel10))
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(sliderHighMemoryMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(42, 42, 42)
                                                                .addComponent(jLabel8))
                                                        .addComponent(jLabel1)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(sliderHighCPUMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addComponent(sliderLowCPUMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                                .addGap(42, 42, 42)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(jLabel4)
                                                                        .addComponent(jLabel6))))
                                                .addGap(231, 302, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel2)
                                        .addComponent(jLabel3)
                                        .addComponent(labelCoalition))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel13)
                                        .addComponent(labelCPUUsage)
                                        .addComponent(jLabel7)
                                        .addComponent(labelLogicalCores)
                                        .addComponent(labelAvailableLogicalCores)
                                        .addComponent(jLabel9))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel14)
                                        .addComponent(labelMemoryUsage)
                                        .addComponent(jLabel11)
                                        .addComponent(labelMemory)
                                        .addComponent(labelAvailableMemory)
                                        .addComponent(jLabel12))
                                .addGap(40, 40, 40)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(sliderLowCPUMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel4))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel6)
                                        .addComponent(sliderHighCPUMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(sliderLowMemoryMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel10))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel8)
                                        .addComponent(sliderHighMemoryMigrationThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(18, 18, Short.MAX_VALUE)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );
        labelLogicalCores.getAccessibleContext().setAccessibleName("jLLogicalCores");
        labelMemory.getAccessibleContext().setAccessibleName("jLMemory");
        labelCPUUsage.getAccessibleContext().setAccessibleName("jLCPUUsage");
        labelCPUUsage.getAccessibleContext().setAccessibleDescription("");
        labelMemoryUsage.getAccessibleContext().setAccessibleName("jLMemoryUsage");
        sliderLowCPUMigrationThreshold.getAccessibleContext().setAccessibleName("JSCPUMigration");

        pack();
    }

    private void sliderLowCPUMigrationThresholdStateChanged(javax.swing.event.ChangeEvent evt) {
        hostDescription.setLowMigrationThresholdForCPU(sliderLowCPUMigrationThreshold.getValue());
        jLabel4.setText("Low CPU migration threshold = " + hostDescription.getLowMigrationThresholdForCPU() + "%");
    }

    private void sliderHighCPUMigrationThresholdStateChanged(javax.swing.event.ChangeEvent evt) {
        hostDescription.setHighMigrationThresholdForCPU(sliderHighCPUMigrationThreshold.getValue());
        jLabel6.setText("High CPU migration threshold = " + hostDescription.getHighMigrationThresholdForCPU() + "%");
    }

    private void formWindowOpened(java.awt.event.WindowEvent evt) {

    }

    private void sliderHighMemoryMigrationThresholdStateChanged(javax.swing.event.ChangeEvent evt) {
        hostDescription.setHighMigrationThresholdForMemory(sliderHighMemoryMigrationThreshold.getValue());
        jLabel8.setText("High memory migration threshold = " + hostDescription.getHighMigrationThresholdForMemory() + "%");
    }

    private void sliderLowMemoryMigrationThresholdStateChanged(javax.swing.event.ChangeEvent evt) {
        hostDescription.setLowMigrationThresholdForMemory(sliderLowMemoryMigrationThreshold.getValue());
        jLabel10.setText("Low memory migration threshold = " + hostDescription.getLowMigrationThresholdForMemory() + "%");
    }

    protected void updateResourceConsumption() {
        sliderLowCPUMigrationThreshold.setValue(hostDescription.getLowMigrationThresholdForCPU());
        sliderHighCPUMigrationThreshold.setValue(hostDescription.getHighMigrationThresholdForCPU());
        sliderLowMemoryMigrationThreshold.setValue(hostDescription.getLowMigrationThresholdForMemory());
        sliderHighMemoryMigrationThreshold.setValue(hostDescription.getHighMigrationThresholdForMemory());
        labelCoalition.setText(String.valueOf(hostDescription.getCoalition()));
        labelLogicalCores.setText(String.valueOf(hostDescription.getNumberOfVirtualCores()));
        labelMemory.setText(String.valueOf(hostDescription.getMemory()));
        labelAvailableLogicalCores.setText(String.valueOf(hostDescription.getAvailableVirtualCores()));
        labelAvailableMemory.setText(String.valueOf(hostDescription.getAvailableMemory()));
        listVirtualMachines.setListData(hostDescription.getVirtualMachinesHosted().toArray());
        DecimalFormat df = new DecimalFormat("0.##");
        if (hostDescription.getCPUUsage() > 100) {
            labelCPUUsage.setText("100.00%");
        } else {
            labelCPUUsage.setText(df.format(hostDescription.getCPUUsage()) + "%");
        }
        if (hostDescription.getMemoryUsage() > 100) {
            labelMemoryUsage.setText("100.00%");
        } else {
            labelMemoryUsage.setText(df.format(hostDescription.getMemoryUsage()) + "%");
        }
    }

    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel labelAvailableLogicalCores;
    private javax.swing.JLabel labelAvailableMemory;
    private javax.swing.JLabel labelCPUUsage;
    private javax.swing.JLabel labelCoalition;
    private javax.swing.JLabel labelLogicalCores;
    private javax.swing.JLabel labelMemory;
    private javax.swing.JLabel labelMemoryUsage;
    private javax.swing.JList listVirtualMachines;
    private javax.swing.JSlider sliderHighCPUMigrationThreshold;
    private javax.swing.JSlider sliderHighMemoryMigrationThreshold;
    private javax.swing.JSlider sliderLowCPUMigrationThreshold;
    private javax.swing.JSlider sliderLowMemoryMigrationThreshold;
}
