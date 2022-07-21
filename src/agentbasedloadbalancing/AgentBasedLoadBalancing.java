/*
 * Agent-based testbed described and evaluated in
 * J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers",
 * Journal of Parallel and Distributed Computing, 2022.
 */
package agentbasedloadbalancing;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.LogManager;

import com.tinkerpop.blueprints.Graph;

/**
 * @author J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado
 */
public class AgentBasedLoadBalancing {

    private static ExperimentRunConfiguration configuration;

    public static void main(String[] args) {

        try {
            // Logging experiment's output in a file.

            // args 0 : experiment run
            // args 1 : datacenter filename
            // args 2 : target std dev
            // args 3 : number of vms
            // args 4 : load balancing type
            // args 5 : BALANCING_ONLY_ONE_COALITION_AT_A_TIME
            // args 6 : Heuristics
            // args 7 : input directory
            // args 8 : output directory
            // args 9 : VMWARE_MAX_MIGRATIONS

            String XML_FILE = "./fat_trees/big_fat_tree_datacenterCoalitions8_1.XML";
            String fileSufix = "";
            String outputDirectory = "./";
            int initialPort = 30000;

            if (args != null) {
                if (args.length > 0) {
                    configuration = new ExperimentRunConfiguration(Integer.valueOf(args[2]), Integer.valueOf(args[3]), args[4], Integer.valueOf(args[5]), 0, Integer.valueOf(args[2]), 0, Integer.valueOf(args[2]), args[6], Integer.valueOf(args[9]));
                    if (configuration.getLOAD_BALANCING_TYPE() == Consts.DISTRIBUTED_FIXED_COALITIONS) {
                        fileSufix = args[0] + "_" + args[1] + "_" + args[2] + "_" + args[3] + "_" + args[4] + "_" + args[5] + "_" + args[6] + "_" + args[7] + "_" + args[8];
                    } else {
                        fileSufix = args[0] + "_" + args[1] + "_" + args[2] + "_" + args[3] + "_" + args[4] + "_" + args[6] + "_" + args[7] + "_" + args[8] + "_" + args[9];
                    }
                    XML_FILE = "./" + args[7] + "/" + args[1];
                    outputDirectory = "./" + args[8] + "/";
                }
            }
            if (args != null) { // default values to be executed for testing purposes
                if (args.length == 0) {
                    String[] defaultArgs = new String[10];
                    defaultArgs[0] = "0";
                    defaultArgs[1] = "big_fat_tree_datacenterCoalitions4_4.xml";
                    defaultArgs[2] = "7";
                    defaultArgs[3] = "480"; // NUMBER_OF_VMS
                    defaultArgs[4] = "INTRA"; // INTRA, VMWARE
                    defaultArgs[5] = "1";
                    defaultArgs[6] = "EXHAUSTIVE";
                    defaultArgs[7] = "fat_trees";
                    defaultArgs[8] = "results";
                    defaultArgs[9] = "0"; // 0, 1, 10000

                    configuration = new ExperimentRunConfiguration(Integer.valueOf(defaultArgs[2]), Integer.valueOf(defaultArgs[3]), defaultArgs[4], Integer.valueOf(defaultArgs[5]), 0, Integer.valueOf(defaultArgs[2]), 0, Integer.valueOf(defaultArgs[2]), defaultArgs[6], Integer.valueOf(defaultArgs[9]));

                    if (configuration.getLOAD_BALANCING_TYPE() == Consts.DISTRIBUTED_FIXED_COALITIONS) {
                        fileSufix = defaultArgs[0] + "_" + defaultArgs[1] + "_" + defaultArgs[2] + "_" + defaultArgs[3] + "_" + defaultArgs[4] + "_" + defaultArgs[5] + "_" + defaultArgs[6] + "_" + defaultArgs[7] + "_" + defaultArgs[8];
                    } else {
                        fileSufix = defaultArgs[0] + "_" + defaultArgs[1] + "_" + defaultArgs[2] + "_" + defaultArgs[3] + "_" + defaultArgs[4] + "_" + defaultArgs[6] + "_" + defaultArgs[7] + "_" + defaultArgs[8] + "_" + defaultArgs[9];
                    }
                    XML_FILE = "./" + defaultArgs[7] + "/" + defaultArgs[1];
                    outputDirectory = "./" + defaultArgs[8] + "/";
                }
            }

            if (Consts.LOG_TO_FILE) {
                PrintStream outputFile = new PrintStream(outputDirectory + "output" + fileSufix + ".txt"); // I should customize filename so as to we can automize experiments
                System.setOut(outputFile);
            }
            final int STARTING_PORT = initialPort;
            final int MAIN_BASIC_SERVICES_CONTAINER_PORT = STARTING_PORT + 0;
            final int ALLOCATOR_CONTAINER_PORT = STARTING_PORT + 1;
            final int WORKLOAD_GENERATOR_CONTAINER_PORT = STARTING_PORT + 2;
            final int STARTING_PORT_NUMBER_FOR_HOSTS = STARTING_PORT + 3;
            LogManager.getLogManager().reset();
            ArrayList<HostAndNeighbors> dataCenterStructure;
            Graph G;
            Graph2Host graphStructure = new Graph2Host(XML_FILE, configuration);
            G = graphStructure.readGraph();
            graphStructure.setCoalition2HashTable(G); //determine coalition IDs for Hosts
            graphStructure.createHosts(G);
            ArrayList<HashSet<String>> setCoalitions = graphStructure.getCoalitions();
            //System.out.println("Number of coalitions: " + graphStructure.getLeaders().size() + " Total number of hosts: " + graphStructure.getHostsAndNeighbors().size());
            jade.wrapper.AgentContainer mainBasicServicesContainer;
            jade.wrapper.AgentContainer allocatorContainer;
            jade.wrapper.AgentContainer workloadGeneratorContainer;
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
            mainBasicServicesProfile = new jade.core.ProfileImpl("localhost", MAIN_BASIC_SERVICES_CONTAINER_PORT, "Testbed", true);
            mainBasicServicesProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so container can handle a lot of messages
            allocatorContainerProfile = new jade.core.ProfileImpl("localhost", ALLOCATOR_CONTAINER_PORT, "Testbed", false);
            allocatorContainerProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so container can handle a lot of messages
            workloadGeneratorProfile = new jade.core.ProfileImpl("localhost", WORKLOAD_GENERATOR_CONTAINER_PORT, "Testbed", false);
            workloadGeneratorProfile.setParameter("jade _core_messaging_MessageManager_maxqueuesize", "90000000");  // so the container can handle a lot of messages
            // creating containers' profiles
            for (int i = 0; i < graphStructure.getHosts().size(); i++) {
                hostProfiles[i] = new jade.core.ProfileImpl("localhost", STARTING_PORT_NUMBER_FOR_HOSTS + i, "Testbed", false);
            }
            mainBasicServicesContainer = mainBasicServicesContainerRuntime.createMainContainer(mainBasicServicesProfile);
            allocatorContainer = allocatorContainerRuntime.createAgentContainer(allocatorContainerProfile);
            workloadGeneratorContainer = workloadGeneratorContainerRuntime.createAgentContainer(workloadGeneratorProfile);
            for (int i = 0; i < graphStructure.getHosts().size(); i++) {
                hostContainers[i] = serviceContainerRuntime[i].createAgentContainer(hostProfiles[i]);
            }
            // Initializing host descriptions
            ArrayList<HostDescription> hostDescriptions = new ArrayList<HostDescription>();

            //If needed, start basic agents for debugging
            //mainBasicServicesContainer.createNewAgent("sniffer", "jade.tools.sniffer.Sniffer", null);
            //mainBasicServicesContainer.getAgent("sniffer").start();
            //mainBasicServicesContainer.createNewAgent("RMA", "jade.tools.rma.rma", null);
            //mainBasicServicesContainer.getAgent("RMA").start();

            dataCenterStructure = graphStructure.getHostsAndNeighbors();
            hostDescriptions = graphStructure.getHosts();
            HashSet<weightEdge> listEdges = graphStructure.getListEdges();
            Object[] allocatorAgentParams = new Object[3];
            ArrayList<String> leaders = graphStructure.getLeaders();
            allocatorAgentParams[0] = hostDescriptions;
            allocatorAgentParams[1] = leaders; // leaders identifies the coalition members of its own coalition
            allocatorAgentParams[2] = configuration;
            // Starting allocator agent
            allocatorContainer.createNewAgent("AllocatorAgent", "agentbasedloadbalancing.AllocatorAgent", allocatorAgentParams);
            allocatorContainer.getAgent("AllocatorAgent").start();
            // Starting host agents
            for (int i = 0; i < dataCenterStructure.size(); i++) {
                Object[] hostAgentParams = new Object[6];
                HostDescription aHost = dataCenterStructure.get(i).getHostDescription();
                hostAgentParams[0] = aHost; // Leader is not passed but it is set in the HostAgent Constructor
                hostAgentParams[1] = leaders; // include all leaders
                hostAgentParams[2] = listEdges;
                hostAgentParams[3] = configuration;
                hostAgentParams[4] = 0.05; //failureProbability TBD
                hostAgentParams[5] = setCoalitions; //allHostsAgents TBD
                hostContainers[i].createNewAgent(aHost.getId(), "agentbasedloadbalancing.HostAgent", hostAgentParams);
                hostContainers[i].getAgent(aHost.getId()).start();
            }

            // Starting workload generator
            if (Consts.WORKLOAD_GENERATOR_AGENT_GUI) {
                Object[] workloadGeneratorContainerParams = new Object[1];
                workloadGeneratorContainerParams[0] = configuration;
                new WorkloadGeneratorGUI(workloadGeneratorContainer, workloadGeneratorContainerParams).setVisible(true); // to manually start the simulation
            } else {
                Object[] workloadGeneratorContainerParams = new Object[1];
                workloadGeneratorContainerParams[0] = configuration;
                workloadGeneratorContainer.createNewAgent("WorkloadGeneratorAgent", "agentbasedloadbalancing.WorkloadGeneratorAgent", workloadGeneratorContainerParams);
                workloadGeneratorContainer.getAgent("WorkloadGeneratorAgent").start();
            }
        } catch (Exception ex) {
            if (Consts.EXCEPTIONS) {
                ex.printStackTrace();
            }
        }
    }
}