/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

/**
 * @author octavio
 */

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.tinkerpop.blueprints.Graph;

/**
 *
 * @author octavio
 */
public class IntraLoadBalancing {

    // Creating coalitions
    private static ArrayList<String> createCoalitionFor(String hostId, ArrayList<HashSet<String>> setCoalitions) {
        hostId = hostId.replaceAll("HostAgent", "");
        ArrayList<String> coalition = new ArrayList<String>();
        int hostCoalitionNumber = 0;
        for (int coalitionNumber = 0; coalitionNumber < setCoalitions.size(); coalitionNumber++) {
            HashSet<String> s = setCoalitions.get(coalitionNumber);
            Iterator<String> i = s.iterator();
            while (i.hasNext()) {
                if (hostId.equals(i.next())) {
                    hostCoalitionNumber = coalitionNumber;
                }
            }
        }
        HashSet<String> s = setCoalitions.get(hostCoalitionNumber);
        Iterator<String> i = s.iterator();
        while (i.hasNext()) {
            String host = "HostAgent" + i.next();
            coalition.add(host.trim());
        }
        //System.out.println(coalition);
        return coalition;
    }

    public static void main(String[] args) {


        try {

            // Logging experiment's output in a file.
            // Logging experiment's output in a file.
            // Logging experiment's output in a file.
            String fileSufix = "";
            if (args != null) {
                if (args.length > 0) {
                    fileSufix = args[0];
                }
            }
            if (Consts.LOG_TO_FILE) {
                PrintStream outputFile = new PrintStream("./output" + fileSufix + ".txt"); // I should customize filename so as to we can automize experiments 
                System.setOut(outputFile);
            }

            LogManager.getLogManager().reset();

            // Reading datacenter's structure from a xml file
            // Reading datacenter's structure from a xml file
            // Reading datacenter's structure from a xml file


            ArrayList<HostAndNeighbors> dataCenterStructure;
            String XML_FILE = "DCellCoalitionTest.XML";

            Graph G;
            Graph2Host graphStructure = new Graph2Host(XML_FILE);

            G = graphStructure.readGraph();
            graphStructure.setCoalition2HashTable(G);//determine coalition IDs for Hosts
            graphStructure.createHosts(G);
            ArrayList<HashSet<String>> setCoalitions = graphStructure.getCoalitions();

//            for (int member = 0; member < setCoalitions.size(); member++) {
//                HashSet<String> s = setCoalitions.get(member);
//                System.out.print("COALITION:[");
//                Iterator<String> i = s.iterator();
//                while (i.hasNext()) {
//                    System.out.print(i.next() + ",");
//                }
//                System.out.println("]");
//            }


            // mainBasicServicesContainer is the main container and contains the agent DirectoryFacilitator among other services
            jade.wrapper.AgentContainer mainBasicServicesContainer;
            jade.wrapper.AgentContainer allocatorContainer;
            jade.wrapper.AgentContainer workloadGeneratorContainer;

            // creating a container for each host
            jade.wrapper.AgentContainer hostContainers[] = new jade.wrapper.AgentContainer[graphStructure.getHosts().size()];

            jade.core.Runtime serviceContainerRuntime[] = new jade.core.Runtime[graphStructure.getHosts().size()];
            jade.core.Runtime workloadGeneratorContainerRuntime;

            // creating JADE runtime environments 
            for (int i = 0; i < graphStructure.getHosts().size(); i++) {
                serviceContainerRuntime[i] = jade.core.Runtime.instance();
            }

            jade.core.Runtime mainBasicServicesContainerRuntime;
            mainBasicServicesContainerRuntime = jade.core.Runtime.instance();

            jade.core.Runtime allocatorContainerRuntime;
            allocatorContainerRuntime = jade.core.Runtime.instance();
            workloadGeneratorContainerRuntime = jade.core.Runtime.instance();

            jade.core.ProfileImpl hostProfiles[] = new jade.core.ProfileImpl[graphStructure.getHosts().size()];
            jade.core.ProfileImpl mainBasicServicesProfile;
            jade.core.ProfileImpl allocatorContainerProfile;
            jade.core.ProfileImpl workloadGeneratorProfile;

            // creating JADE runtimes' profiles
            mainBasicServicesProfile = new jade.core.ProfileImpl("localhost", Consts.MAIN_BASIC_SERVICES_CONTAINER_PORT, "Testbed", true);
            mainBasicServicesProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so container can handle a lot of messages                                   

            allocatorContainerProfile = new jade.core.ProfileImpl("localhost", Consts.ALLOCATOR_CONTAINER_PORT, "Testbed", false);
            allocatorContainerProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so container can handle a lot of messages                  

            workloadGeneratorProfile = new jade.core.ProfileImpl("localhost", Consts.WORKLOAD_GENERATOR_CONTAINER_PORT, "Testbed", false);
            workloadGeneratorProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so the container can handle a lot of messages                  

            // creating containers' profiles 
            for (int i = 0; i < graphStructure.getHosts().size(); i++) {
                //hostProfiles[i] = new jade.core.ProfileImpl("localhost", Consts.STARTING_PORT_NUMER_FOR_HOSTS + i, "HostContainer" + String.valueOf(i), false);
                hostProfiles[i] = new jade.core.ProfileImpl("localhost", Consts.STARTING_PORT_NUMER_FOR_HOSTS + i, "Testbed", false);
                hostProfiles[i].setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so the container can handle a lot of messages                  
            }

            mainBasicServicesContainer = mainBasicServicesContainerRuntime.createMainContainer(mainBasicServicesProfile);
            allocatorContainer = allocatorContainerRuntime.createAgentContainer(allocatorContainerProfile);
            workloadGeneratorContainer = workloadGeneratorContainerRuntime.createAgentContainer(workloadGeneratorProfile);

            for (int i = 0; i < graphStructure.getHosts().size(); i++) {
                hostContainers[i] = serviceContainerRuntime[i].createAgentContainer(hostProfiles[i]);
            }

            // Initializing host descriptions 
            ArrayList<HostDescription> hostDescriptions = new ArrayList<HostDescription>();

            //Starting basic agents for debugging 

            //mainBasicServicesContainer.createNewAgent("sniffer", "jade.tools.sniffer.Sniffer", null);
            //mainBasicServicesContainer.getAgent("sniffer").start();
            //mainBasicServicesContainer.createNewAgent("RMA", "jade.tools.rma.rma", null);
            //mainBasicServicesContainer.getAgent("RMA").start();


            dataCenterStructure = graphStructure.getHostsAndNeighbors();
            hostDescriptions = graphStructure.getHosts();
            HashSet<weightEdge> listEdges = graphStructure.getListEdges();

            Object[] allocatorAgentParams = new Object[2];
            ArrayList<HostDescription> xLeaders = graphStructure.getLeaders();

            allocatorAgentParams[0] = hostDescriptions;
            allocatorAgentParams[1] = xLeaders; // leaders identifies the coalition members of its own coalition
            // Starting allocator agent
            allocatorContainer.createNewAgent("AllocatorAgent", "intraloadbalancing.AllocatorAgent", allocatorAgentParams);
            allocatorContainer.getAgent("AllocatorAgent").start();

            // Starting host agents
            for (int i = 0; i < dataCenterStructure.size(); i++) {
                Object[] hostAgentParams = new Object[6];
                HostDescription xHost = dataCenterStructure.get(i).getHostDescription();
                Hashtable neighborsDistance = dataCenterStructure.get(i).getNeighbors();//Weights of neighbors 
                ArrayList<String> membersOfCoalition = dataCenterStructure.get(i).getMembersOfCoalition();
                hostAgentParams[0] = xHost;
                hostAgentParams[1] = createCoalitionFor(xHost.getId(), setCoalitions); // include all the members of a host's coalition
                hostAgentParams[2] = xLeaders; // include all leaders
                String xHostId = xHost.getId().replace("HostAgent", "");
                int posCoalition = graphStructure.getPosCoalition(xHostId);
                hostAgentParams[3] = setCoalitions.get(posCoalition);
                hostAgentParams[4] = listEdges;
                hostAgentParams[5] = neighborsDistance;

                hostContainers[i].createNewAgent(xHost.getId(), "intraloadbalancing.HostAgent", hostAgentParams);
                hostContainers[i].getAgent(xHost.getId()).start();
            }


            // Starting workload generator
            if (Consts.WORKLOAD_GENERATOR_AGENT_GUI) {
                new WorkloadGeneratorGUI(workloadGeneratorContainer).setVisible(true); // to manually start the simulation
            } else {
                workloadGeneratorContainer.createNewAgent("WorkloadGeneratorAgent", "intraloadbalancing.WorkloadGeneratorAgent", null);
                workloadGeneratorContainer.getAgent("WorkloadGeneratorAgent").start();
            }

        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                Logger.getLogger(IntraLoadBalancing.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
