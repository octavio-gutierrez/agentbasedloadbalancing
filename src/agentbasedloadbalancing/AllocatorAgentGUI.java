/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

import java.util.ArrayList;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class AllocatorAgentGUI extends javax.swing.JFrame {

    private ArrayList<HostDescription> activeHosts;

    public AllocatorAgentGUI(ArrayList<HostDescription> activeHosts) {
        this.activeHosts = activeHosts;
        initComponents();
    }

    private void initComponents() {
        jScrollPane1 = new javax.swing.JScrollPane();
        listHostsInformation = new javax.swing.JList();
        jLabel1 = new javax.swing.JLabel();
        comboAllocatorLoadBalancingHeuristic = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Monitoring & Allocator Agent");
        jScrollPane1.setViewportView(listHostsInformation);
        jLabel1.setText("Hosts' information");
        comboAllocatorLoadBalancingHeuristic.setModel(new javax.swing.DefaultComboBoxModel(new String[]{"Random"}));
        comboAllocatorLoadBalancingHeuristic.setName("jCBHeuristicType");
        comboAllocatorLoadBalancingHeuristic.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboAllocatorLoadBalancingHeuristicActionPerformed(evt);
            }
        });
        jLabel2.setText("Allocator's load balancing heuristic");
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addGap(33, 33, 33).addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false).addGroup(layout.createSequentialGroup().addComponent(jLabel1).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addComponent(jLabel2).addGap(18, 18, 18).addComponent(comboAllocatorLoadBalancingHeuristic, javax.swing.GroupLayout.PREFERRED_SIZE, 193, javax.swing.GroupLayout.PREFERRED_SIZE)).addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 760, javax.swing.GroupLayout.PREFERRED_SIZE)).addContainerGap(35, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup().addContainerGap().addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE).addComponent(jLabel1).addComponent(comboAllocatorLoadBalancingHeuristic, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(jLabel2)).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 566, javax.swing.GroupLayout.PREFERRED_SIZE).addContainerGap(34, Short.MAX_VALUE)));
        comboAllocatorLoadBalancingHeuristic.getAccessibleContext().setAccessibleName("jCBHeuristicType");
        pack();
    }

    private void comboAllocatorLoadBalancingHeuristicActionPerformed(java.awt.event.ActionEvent evt) {
    }

    protected void updateServersMonitorList() {
        listHostsInformation.setListData(activeHosts.toArray());
    }

    protected String getAllocatorLoadBalancingHeuristic() {
        return "random";
    }

    private javax.swing.JComboBox comboAllocatorLoadBalancingHeuristic;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList listHostsInformation;
}
