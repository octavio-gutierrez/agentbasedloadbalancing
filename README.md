# agentbasedloadbalancing

This repository contains the source code of the agent-based testbed for distributed load balancing described and evaluated in J.O. Gutierrez-Garcia, J.A. Trejo-Sánchez, D. Fajardo-Delgado, "Agent Coalitions for Load Balancing in Cloud Data Centers", Journal of Parallel and Distributed Computing, 2022.

The list of input arguments of the agent-based testbed are as follows:

* arg 0: Id of experiment run. Default value: 0
    
* arg 1: Name of a datacenter file in a GraphML file format containing the datacenter’s topology and number of hosts. Default value: big_fat_tree_datacenterCoalitions4_4.xml. 

* arg 2: A target standard deviation. Default value: 7.	

* arg 3: Number of virtual machines. Default value: 480.			

* arg 4: A load balancing type. Possible values are INTRA and VMWARE. Default value: INTRA.

* arg 5: Possible values are 0 and 1. If set to 1, it enables the agent-based interaction protocol to broadcast a VM migration event to all the coalitions. If set to 0, it disables this functionality. Default value: 1.

* arg 6: A load balancing heuristic name. Possible values are EXHAUSTIVE, MAXMIN, and ROULETTE. If the load balancing type is set to VMWARE (arg 4), this input argument is ignored, and the hill-climbing load balancing heuristic of VMware DRS is used. Default value: EXHAUSTIVE.

* arg 7: Name of an input directory where datacenter files are located. Default value: fat_trees.

* arg 8: Name of an output directory. Default value: results.

* arg 9: VMware's maximum number of consecutive VM migrations. Default value: 0.

Many other settings can be configured by modifying consts.java. Among the settings that can be configured in consts.java are: 1) possible host specifications, 2) possible VM specifications, 3) whether the output will be printed to console or a file, and 3) logging frequency in ms.

The simulation output consists of a list of JSON objects. There are two types of JSON objects: datacenter performance and VM migration. 

The datacenter performance object includes the overall resource usage of the datacenter and the resource usage of each coalition as follows:

{"dataCenterCPUMean":16.13, "dataCenterCPUStdDev":20.05, "dataCenterMemoryMean":11.01, "dataCenterMemoryStdDev":17.91, "coalitions": [{"id":24, "CPUMean":20.73, "CPUStdDev":20.32, "memoryMean":24.26, "memoryStdDev":25.46}, {"id":28, "CPUMean":17.87, "CPUStdDev":23.56, "memoryMean":11.08, "memoryStdDev":15.73}, {"id":20, "CPUMean":16.14, "CPUStdDev":20.43, "memoryMean":6.55, "memoryStdDev":10.55}, {"id":32, "CPUMean":9.78, "CPUStdDev":12.57, "memoryMean":2.14, "memoryStdDev":1.34}], "time":1658410902610}

The VM migration object includes information related to a VM migration event as follows:

{"source_coalition":20, "destination_coalition":28, "migrationType":"AtoB", "origin":"HostAgent20", "destination":"HostAgent29", "vmid":"VirtualMachineAgent19", "distance":6.0, "time":1658410910978}

If no input arguments are provided, the agent-based testbed will run a simulation using the default values.

The main class is AgentBasedLoadBalancing.java