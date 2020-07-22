/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intraloadbalancing;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.io.File;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author octavio
 */
public class Utilities {

    public Utilities() {

    }

    public void unpublishService(Agent a) {
        try {
            DFService.deregister(a);
        } catch (FIPAException e) {
            System.out.println(e);
        }
    }

    public void publishService(Agent a, String serviceType) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName((jade.core.AID) a.getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(a.getAID().getName());
        dfd.addServices(sd);
        try {
            DFService.register(a, dfd);
        } catch (FIPAException fe) {
            System.out.println(fe);
        }

    }

    public String[] searchForAgents(Agent searchingAgent, String agentType, long maxResults) {// array of Strings that contains addresses of agents which will satisfy a given requirement
        try {
            boolean selfIncluded = false;
            int numberOfProviders = 0;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription templateSd = new ServiceDescription();
            SearchConstraints sc = new SearchConstraints();
            templateSd.setType(agentType);
            template.addServices(templateSd);
            sc.setMaxResults(maxResults);
            DFAgentDescription[] results = DFService.search(searchingAgent, template, sc);
            if (results.length > 0) {
                String[] providers = new String[results.length];
                for (int i = 0; i < results.length; ++i) {
                    DFAgentDescription dfd = results[i];
                    // The same agent may provide several services; we are only interested in the agentType 
                    Iterator it = dfd.getAllServices();
                    while (it.hasNext()) {
                        ServiceDescription sd = (ServiceDescription) it.next();
                        if (sd.getType().equals(agentType)) {
                            providers[numberOfProviders] = sd.getName();
                            if (providers[numberOfProviders].equals(searchingAgent.getName())) {
                                selfIncluded = true;
                            }
                            numberOfProviders = numberOfProviders + 1;
                        }
                    }
                }
                if (selfIncluded) {
                    String[] providersWithoutRequestingAgent = new String[providers.length - 1];
                    int counter = 0;
                    for (int i = 0; i < providers.length; i++) {
                        if (!providers[i].equals(searchingAgent.getName())) {
                            providersWithoutRequestingAgent[counter] = providers[i];
                            counter++;
                        }
                    }
                    return providersWithoutRequestingAgent;
                } else {
                    return providers;
                }

            } else {
                System.out.println("Agent " + searchingAgent.getLocalName() + " did not find any " + agentType + " service");
            }

        } catch (FIPAException fe) {
            System.out.println(fe);
        }

        return null;
    }

//    public Graph<String, DefaultEdge> importGMLGraph(String graphPathFile) {
//        try {
//            File file = new File(graphPathFile);
//            Graph<String, DefaultEdge> g = new SimpleGraph<>(DefaultEdge.class);
//            VertexProvider<String> vp = (id, attributes) -> id + "-" + attributes;
//            EdgeProvider<String, DefaultEdge> ep = (from, to, label, attributes) -> g.getEdgeSupplier().get();
//            GmlImporter<String, DefaultEdge> importer = new GmlImporter<>(vp, ep);
//            importer.importGraph(g, file);
//            return g;
//
//        } catch (ImportException ex) {
//            Logger.getLogger(Utilities.class.getName()).log(Level.SEVERE, null, ex);
//            return null;
//        }
//    }

}
