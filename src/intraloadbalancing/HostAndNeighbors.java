package intraloadbalancing;

import java.util.ArrayList;
import java.util.Hashtable;

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
