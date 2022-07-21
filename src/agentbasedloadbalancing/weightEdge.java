/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class weightEdge {
    private String outNode;
    private String inNode;
    private float distance;

    public weightEdge(String xOut, String xIn, float xDistance) {
        this.outNode = xOut;
        this.inNode = xIn;
        this.distance = xDistance;
    }

    public String getOutNode() {
        return this.outNode;
    }

    public String getInNode() {
        return this.inNode;
    }

    public float getDistance() {
        return this.distance;
    }

}

