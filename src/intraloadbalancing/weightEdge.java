package intraloadbalancing;

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

