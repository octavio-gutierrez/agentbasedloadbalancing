package intraloadbalancing;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

public class Graph2Host {

    private String graphString;
    private Graph myMLGraph;
    private ArrayList<HostDescription> hosts;
    private ArrayList<HashSet<String>> SetCoalitions;
    private ArrayList<String> membersOfCoalition;
    private Hashtable neighborsCoalition; //String idHostneighbor, int coalition of its hostNeighbor
    private Hashtable neighborsDistance; // String idHostneighbor, int distance to its neighbor
    private ArrayList<HostAndNeighbors> theHosts;
    private ArrayList<HostDescription> leaders;
    private HashSet<weightEdge> listEdges;

    public Graph2Host(String xFile) {
        graphString = xFile;
        hosts = new ArrayList<HostDescription>();
        neighborsCoalition = new Hashtable();//This can be a <HostDescription,distance>
        neighborsDistance = new Hashtable();
        theHosts = new ArrayList<HostAndNeighbors>();
        membersOfCoalition = new ArrayList<String>();
        this.SetCoalitions = new ArrayList<HashSet<String>>();
        this.leaders = new ArrayList<HostDescription>();
        this.listEdges = new HashSet<weightEdge>();
    }

    //Added by Joel 2020-05-02
    public ArrayList<HostDescription> getHosts() {
        return this.hosts;
    }

    //Added by Joel 2020-05-04
    public void addNeighborCoalition(String strHost, int xCoalition) {
        //This hashtable set the CoalitionID for each host

        neighborsCoalition.put(strHost, xCoalition);
    }

    //Defined by Joel 2020-06-10
    public int getPosCoalition(String idHost) {
        int retPos = 0;
        for (int counter = 0; counter < SetCoalitions.size(); counter++) {
            HashSet<String> s = SetCoalitions.get(counter);
            if (s.contains(idHost)) {
                return counter;
            }
        }
        return retPos;
    }

    public ArrayList<HostDescription> getLeaders() {
        return this.leaders;
    }

    public void addLeader(HostDescription xHost) {
        this.leaders.add(xHost);
    }

    //Defined by Joel 2020-06-10
    public void addNeighborDistance(String OutHost, String inHost, long xDistance) {
        this.neighborsDistance.put(inHost, xDistance);
        weightEdge myOutWeightedEdge = new weightEdge(OutHost, inHost, xDistance);
        weightEdge myInWeightedEdge = new weightEdge(inHost, OutHost, xDistance);
        weightEdge xEdge = myOutWeightedEdge;
        // System.out.println("Added=("+xEdge.getOutNode()+","+xEdge.getInNode()+","+xEdge.getDistance()+")");
        this.listEdges.add(myOutWeightedEdge);
        this.listEdges.add(myInWeightedEdge);
    }

    //this function creates the set that contains the coalitions
    //Only leaders of coalitions create such a set
    public void createSetCoalition(String idLeader) {
        HashSet<String> auxSet = new HashSet<String>();
        auxSet.add(idLeader);
        SetCoalitions.add(auxSet);
    }

    //This function add a coalition member to a coalition whose
    //leader is leaderOfCoalition
    public void add2Coalition(String coalitionMember, String leaderOfCoalition) {
        for (int counter = 0; counter < SetCoalitions.size(); counter++) {
            HashSet<String> s = SetCoalitions.get(counter);
            if (s.contains(leaderOfCoalition)) {
                s.add(coalitionMember);
            }
        }
    }

    public void setCoalition2HashTable(Graph graph) {
        Iterable<Vertex> vertices = graph.getVertices();
        //System.out.println(vertices);
        Iterator<Vertex> verticesIterator = vertices.iterator();
        while (verticesIterator.hasNext()) {
            Vertex myVertex = verticesIterator.next();
            Iterable<Edge> edges = myVertex.getEdges(Direction.IN);
            Iterator<Edge> edgesIterator = edges.iterator();
            String xCoalition = (String) myVertex.getProperty("Coalition");
            int xxCoalition = Integer.parseInt(xCoalition);
            long xLeader = (Long) myVertex.getProperty("Leader");
            long xCPU = (Long) myVertex.getProperty("CPU");
            long xMEM = (Long) myVertex.getProperty("memory");
            int xxCPU = (int) xCPU;
            int xxMEM = (int) xMEM;
            ///Revisar property id, it is not correct
            String strID = (String) myVertex.getProperty("identifier");
            if (strID.equals(xCoalition)) {//This is a leader
                this.createSetCoalition(strID);
            }
            addNeighborCoalition(strID, xxCoalition);
        }
    }

    /*
     * Function to create the hosts traversing the graph 3rd Jun 2020
     * **/
    public ArrayList<HashSet<String>> getCoalitions() {
        return this.SetCoalitions;
    }

    public void createHosts(Graph graph) {
        Iterable<Vertex> vertices = graph.getVertices();
        //System.out.println(vertices);
        boolean isLeader = false;
        Iterator<Vertex> verticesIterator = vertices.iterator();
        HostAndNeighbors completeHost;

        while (verticesIterator.hasNext()) {
            neighborsDistance.clear();//remove all elements of HashTable
            membersOfCoalition.clear();//remove all members of coalition members
            Vertex myVertex = verticesIterator.next();
            Iterable<Edge> edges = myVertex.getEdges(Direction.OUT);
            Iterator<Edge> edgesIterator = edges.iterator();
            String xCoalition = (String) myVertex.getProperty("Coalition");
            int xxCoalition = Integer.parseInt(xCoalition);
            long xLeader = (Long) myVertex.getProperty("Leader");
            long xCPU = (Long) myVertex.getProperty("CPU");
            long xMEM = (Long) myVertex.getProperty("memory");
            int xxCPU = (int) xCPU;
            int xxMEM = (int) xMEM;
            ///Revisar property id, it is not correct
            String strID = (String) myVertex.getProperty("identifier");
            if (strID.equals(xCoalition)) {
                //System.out.println("I am a leader:" + strID);
                isLeader = true;
            }
            //System.out.println("Host:");
            HostAgent xHost;
            // leader= true if coalition is equal to itself verify


            double randomNumber = new Random().nextGaussian() * (Consts.HOST_OPTIONS.length / 2 - 1) + (Consts.HOST_OPTIONS.length / 2 - 1);

            if (randomNumber < 0) {
                randomNumber = 0;
            } else if (randomNumber > (Consts.HOST_OPTIONS.length - 1)) {
                randomNumber = Consts.HOST_OPTIONS.length - 1;
            }
            int hostOption = (int) Math.round(randomNumber);
            int[] hostSpecs = Consts.HOST_OPTIONS[hostOption];
            HostDescription H = new HostDescription(isLeader, "HostAgent" + strID,
                    xxCoalition,
                    0.0,
                    0.0,
                    hostSpecs[1],//xxMEM,
                    0.0,
                    hostSpecs[0],//xxCPU,
                    0,
                    Consts.MIGRATION_THRESHOLD_FOR_LOW_CPU, // - int value from 0 to 100
                    Consts.MIGRATION_THRESHOLD_FOR_HIGH_CPU, // - int value from 0 to 100 but greater than > lowMigrationThresholdForCPU
                    Consts.MIGRATION_THRESHOLD_FOR_LOW_MEMORY, // - int value from 0 to 100
                    Consts.MIGRATION_THRESHOLD_FOR_HIGH_MEMORY, // - int value from 0 to 100 but greater than > lowMigrationThresholdForMemory
                    0,
                    0,
                    "AllocatorAgent",
                    "aContainerName",
                    String.valueOf(xLeader));
            if (isLeader) {
                this.addLeader(H);
            }
            isLeader = false;
            hosts.add(H);
            add2Coalition(strID, xCoalition);
            while (edgesIterator.hasNext()) {
                Edge edge = edgesIterator.next();
                Vertex InOutVertex = edge.getVertex(Direction.IN);
                String StrOutVertexID = (String) InOutVertex.getProperty("identifier");
                //System.out.println("Host "+strID+" neighbor "+StrOutVertexID);
                int InOutVertexID = Integer.valueOf(StrOutVertexID);
                long xWeight = (Long) edge.getProperty("weight");
                int yyCoalition = (Integer) neighborsCoalition.get(StrOutVertexID);

                if (xxCoalition == yyCoalition) {
                    //addNeighborDistance(StrOutVertexID,xWeight); //In case only the distance to neighbors of the same coalition are required
                    this.membersOfCoalition.add(StrOutVertexID);
                }
                addNeighborDistance(strID, StrOutVertexID, xWeight);//In case we requiere information of all the neighbors, not only from coalition
            }

            completeHost = new HostAndNeighbors(H, neighborsDistance, membersOfCoalition);
            theHosts.add(completeHost);
        }
    }

    public HashSet<weightEdge> getListEdges() {
        return this.listEdges;
    }

    public ArrayList<HostAndNeighbors> getHostsAndNeighbors() {
        return this.theHosts;
    }

    public Graph readGraph() throws Exception {
        Graph graph = new TinkerGraph();
        GraphMLReader reader = new GraphMLReader(graph);
        InputStream is = new BufferedInputStream(new FileInputStream(graphString));
        reader.inputGraph(is);
        return graph;
    }

}
