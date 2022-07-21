/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class WorkloadGeneratorGUI extends javax.swing.JFrame {

    /**
     * Creates new form WorkloadGeneratorGUI
     */
    private jade.wrapper.AgentContainer workloadGeneratorContainer;
    private Object[] workloadGeneratorContainerParams;


    public WorkloadGeneratorGUI(jade.wrapper.AgentContainer workloadGeneratorContainer, Object[] workloadGeneratorContainerParams) {
        this.workloadGeneratorContainer = workloadGeneratorContainer;
        this.workloadGeneratorContainerParams = workloadGeneratorContainerParams;
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        buttonStart = new javax.swing.JButton();
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Workload Generator");
        setName("workloadGeneratorFrame"); // NOI18N
        buttonStart.setText("Start workload generation");
        buttonStart.setName("startButton"); // NOI18N
        buttonStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonStartActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addGap(29, 29, 29).addComponent(buttonStart).addContainerGap(30, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(layout.createSequentialGroup().addContainerGap().addComponent(buttonStart).addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        pack();
    }

    private void buttonStartActionPerformed(java.awt.event.ActionEvent evt) {
        startSimulationRun();
    }

    private void startSimulationRun() {
        try {
            workloadGeneratorContainer.createNewAgent("WorkloadGeneratorAgent", "agentbasedloadbalancing.WorkloadGeneratorAgent", workloadGeneratorContainerParams);
            workloadGeneratorContainer.getAgent("WorkloadGeneratorAgent").start();
        } catch (Exception e) {
            if (Consts.EXCEPTIONS) e.printStackTrace();
        }
    }

    private javax.swing.JButton buttonStart;
}