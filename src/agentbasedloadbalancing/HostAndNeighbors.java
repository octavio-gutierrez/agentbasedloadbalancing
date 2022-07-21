/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class HostAndNeighbors {

    private HostDescription Host;
    private Hashtable neighborsDistance;
    private ArrayList<String> membersOfCoalition;

    public HostAndNeighbors(HostDescription xHost, Hashtable xneighbors, ArrayList<String> xMembersCoalition) {
        this.Host = xHost;
        this.neighborsDistance = xneighbors;
        this.membersOfCoalition = new ArrayList<String>();
        this.membersOfCoalition = xMembersCoalition;
    }

    public HostDescription getHostDescription() {
        return this.Host;
    }

    public void addMember2Coalition(String xMember) {
        membersOfCoalition.add(xMember);
    }

    public void addNeighborDistance(String strHost, long xDistance) {
        this.neighborsDistance.put(strHost, xDistance);
    }

    public Hashtable getNeighbors() {
        return this.neighborsDistance;
    }

    public ArrayList<String> getMembersOfCoalition() {
        return this.membersOfCoalition;
    }

}
