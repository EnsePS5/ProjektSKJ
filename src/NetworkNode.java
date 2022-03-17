/* ------------------------------------------------------------
 *  The NetworkNode implementation.
 *
 *  -- introduction --
 *
 *  First launching of NetworkNode creates the 'Supervisor Node'[SN] (the main one). After that every new launch
 *  creates node that is in some way subservient to the SN - directly (Has no intermediaries/agents) or indirectly
 *  (via one or more intermediaries/agents). Clients can connect to any node.
 *
 *  The purpose of the program is to allocate resources required by client. All nodes have different resource packs so to
 *  fulfil the requirement, resources might have been allocated in more than one node.
 *
 *  -- Connection between nodes --
 *
 *  The connection between nodes is not constance. New create node just 'pings' the SN to make it aware of new child.
 *  Then, the SN saves in Arraylist unnecessary data (in this case child node port) to make connection possible when it's
 *  needed.
 *
 *  All nodes communicating system is based on TCP. (Personally, it's just easier to use TCP than UDP.
 *  That's why I choose this way)
 *
 *  Nodes send to each other few 'operations', all expressed via String :
 *      1. "NODE"
 *          First connection to the net, 'introduce' itself to the SN.
 *      2. "TERMINATE"
 *          Terminates all nodes. Closes sockets, printWriters etc. Stars from children Nodes (if exists), then SN and
 *  finally shutdowns itself.
 *      3. default (resources to allocate sent by client)
 *          When clients connect to the node, his command goes here. If the first contact node has enough resources, after
 *  allocation and response preparation, client gets the message with certain allocated resources and ipAddress with port.
 *  Else if there is not enough available resources, the "ALLOCATION" operation begins.
 *  After that, client gets the response : FAILED if there were not enough resources or ALLOCATING if there were.
 *      4. "ALLOCATE"
 *  Firstly call children nodes to allocate required resources (starts from first connected child and tan goes on). If
 *  any of them do not have enough resources, calls SN whose then calls his other children nodes (except the one which
 *  called him). It all repeats until the quantity of resources to allocate is 0 or there are not enough of them.
 *
 * !!!
 * URGENT -> unfortunately there are some issues with "ALLOCATE" and whole net allocation does not work properly.
 *           Single node allocation is fine and ready to go! I'm working on it.
 * !!!
 *
 * ProjektSKJ, 2021/2022, Kacper Godlewski s23161.
 */

//
//THE NODE
//

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkNode{

    // parameters needed to create new nodes
    public String ipAddress;

    String gateway = null;
    int identifier = 0;
    int gatewayPort = 0;
    int listeningPort = 0;
    Integer supervisorNodePort = null;
    List<Integer> childNodesPorts;

    Map<String,Integer> resources = new HashMap<>();

    ArrayList<String> resourcesToAllocate = new ArrayList<>();
    ArrayList<Integer> quantityToAllocate = new ArrayList<>();

    ArrayList<String> resourcesToRecover = new ArrayList<>();
    ArrayList<Integer> quantityToRecover = new ArrayList<>();

    public NetworkNode(){
        childNodesPorts = new Vector<>();
        this.ipAddress = "localhost";
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        NetworkNode networkNode = new NetworkNode();

        String command = null;
        String identifier = null;

        // parameter initialization
        for (int i = 0; i < args.length; i++) {
            switch (args[i]){
                case "-ident":
                   identifier = args[++i];
                   networkNode.identifier = Integer.parseInt(identifier);
                    break;
                case "-tcpport":
                    networkNode.listeningPort = Integer.parseInt(args[++i]);
                    break;
                case "-gateway":
                    String[] gatewayArr = args[++i].split(":");
                    networkNode.gateway = gatewayArr[0];
                    networkNode.gatewayPort = Integer.parseInt(gatewayArr[1]);
                    networkNode.supervisorNodePort = networkNode.gatewayPort;
                    break;
                case "terminate":
                    command = "TERMINATE";
                    break;
                default:
                    if (command == null) command = args[i];
                    else if(! "TERMINATE".equals(command))command += " " + args[i];
            }
        }
        // TO DELETE TODO
        System.out.println("I " +identifier);
        System.out.println("TP " + networkNode.listeningPort);
        System.out.println("GP " + networkNode.gateway + " - " + networkNode.gatewayPort);
        System.out.println(command);

        /*
         * NODE CREATION SYSTEM
         */

        //ServerSocket (starts listening for other nodes and clients)
        ServerSocket serverSocket = new ServerSocket(networkNode.listeningPort);

        if (networkNode.supervisorNodePort != null){
            networkNode.connectToSupervisor();
        }

        // Filling up HashMap with resources
        assert command != null;
        String[] tempResArr = command.split(" ");
        for (String s : tempResArr) {
            String[] arrToMap = s.split(":");
            networkNode.resources.put(arrToMap[0], Integer.parseInt(arrToMap[1]));
        }

        while (true){

            ExecutorService threadPool = Executors.newFixedThreadPool(20);
            Socket connectionSocket = serverSocket.accept();

            threadPool.submit(()->{

                try {
                    PrintWriter outMsg = new PrintWriter(connectionSocket.getOutputStream(),true);
                    BufferedReader comingMsg = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

                    String operation = comingMsg.readLine();
                    int childNodePort = 0;

                    if (operation.equals("NODE")){
                        operation = comingMsg.readLine();
                        childNodePort = Integer.parseInt(operation);
                        operation = "NODE";
                    }

                    //------------------
                    // switch -> Brain of nodes operations.
                    //------------------

                    switch (operation){

                        // Making supervisor node aware of its own children
                        case "NODE":
                            networkNode.childNodesPorts.add(childNodePort);

                            System.out.println
                                    ("Node -> " + childNodePort + " added to " + networkNode.listeningPort + " port list successfully!");
                            break;


                        // Terminates whole network
                        case "TERMINATE":

                            networkNode.termination();

                            System.out.println("Node " + networkNode.listeningPort + " terminated");
                            System.exit(0);

                        case "ALLOCATE":
                            // TODO Packing client resources to allocate to string and certain functions initialization.
                        default:

                            String[] tempArr = operation.split(" ");
                            /*networkNode.resourcesToAllocate.add(tempArr[0]);
                            networkNode.quantityToAllocate.add(1);*/

                            for (int i = 1; i < tempArr.length; i++) {
                                String[] res = tempArr[i].split(":");
                                networkNode.resourcesToAllocate.add(res[0]);
                                networkNode.quantityToAllocate.add(Integer.parseInt(res[1]));
                            }

                            ArrayList<String> toClient = networkNode.allocation();


                            if (!toClient.contains("-1")) {
                                outMsg.println("ALLOCATED");

                                for (String s : toClient) {
                                    outMsg.println(s);
                                }
                                outMsg.println();

                                break;
                            }else {
                                outMsg.println("FAILED");
                                outMsg.println();

                                networkNode.recover();
                            }

                    }
                    connectionSocket.close();
                    outMsg.close();
                    comingMsg.close();
                } catch (IOException e) {
                    System.out.println("No connection with " + networkNode.gateway + " or terminated");
                }
            });

        }
    }

    //------------------
    //Methods  V V V
    //------------------

    // Sends message (operation names like "TERMINATE") to all children nodes.

    private void sendMessageToChildNodes(String message) throws IOException {
        for (int i = 0; i < childNodesPorts.size(); i++) {

            Socket sendingSocket = new Socket(ipAddress, childNodesPorts.get(i));

            PrintWriter outMessage = new PrintWriter(sendingSocket.getOutputStream(),true);
            //BufferedReader inMessage = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

            outMessage.println(message);
            outMessage.println();

            sendingSocket.close();
            outMessage.close();
        }
    }

    //--------------------------------
    // Sends message (operation names like "TERMINATE") to supervisor node.

    private void sendMessageToSupervisor(String message) throws IOException {

        Socket sendingSocket = new Socket(ipAddress,this.supervisorNodePort);

        PrintWriter outMessage = new PrintWriter(sendingSocket.getOutputStream(),true);
        //BufferedReader inMessage = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        outMessage.println(message);
        outMessage.println();

        sendingSocket.close();
        outMessage.close();
    }

    //--------------------------------
    // Tries to connect to supervisor node if it exists. In other case, throws an exception.

    private void connectToSupervisor() throws IOException, InterruptedException {
        Socket Socket = new Socket(ipAddress, this.supervisorNodePort);

        PrintWriter outToSupervisor = new PrintWriter(Socket.getOutputStream());
        BufferedReader inFromSupervisor = new BufferedReader(new InputStreamReader(Socket.getInputStream()));

        System.out.println("Connecting to ->  " + this.supervisorNodePort);

        outToSupervisor.println("NODE");
        outToSupervisor.println(this.listeningPort);
        outToSupervisor.println();

        System.out.println("Connected!");

        /*String fromSupervisor = inFromSupervisor.readLine();

        if(!fromSupervisor.contains(String.valueOf(this.listeningPort))) {
            throw new RuntimeException("ERROR! Unable to Connect to Supervisor -> " + this.supervisorNodePort);
        }*/
        //TimeUnit.MILLISECONDS.sleep(250);

        outToSupervisor.close();
        inFromSupervisor.close();
        Socket.close();
    }

    //---------------------------------
    // Terminates all nodes in network

    private void termination(){

        try {
            sendMessageToChildNodes("TERMINATE");

            if (this.supervisorNodePort != null) {
                sendMessageToSupervisor("TERMINATE");
            }
        }catch (IOException ignored){}
    }

    //--------------------------------
    // Sends "ALLOCATE" commands to all children nodes and supervisor (if exists)

    private ArrayList<String> allocation() throws IOException {

        ArrayList<String> responseToClient = new ArrayList<>();

        allocator(this.resources,this.resourcesToAllocate,this.quantityToAllocate);

        boolean isDone = false;

        // Checking if all resources are allocated (counter starts counting from 1 cause first in arraylist is identifier)
        for (int i = 0,counter = 0; i < this.quantityToAllocate.size(); i++) {
            if (this.quantityToAllocate.get(i) == 0){
                counter++;
            }
            if (counter == this.quantityToAllocate.size()){
                isDone = true;
                break;
            }
        }

        // Checking if ALL resources are allocated
        if (isDone){
            for (int i = 0; i < this.resourcesToAllocate.size(); i++) {
                responseToClient.add(this.resourcesToRecover.get(i) + ":" + this.quantityToRecover.get(i) + ":" + this.ipAddress + ":" + this.listeningPort);
            }
            this.resourcesToRecover.clear();
            this.quantityToRecover.clear();
            this.resourcesToAllocate.clear();
            this.quantityToAllocate.clear();
            return responseToClient;
        }else {
            //TODO ALOCATE OPERATION
            /*for (int i = 0; i < this.childNodesPorts.size(); i++) {
                sendMessageToChildNodes("ALLOCATE");
            }*/

            if (!isDone){
                responseToClient.add("-1");
                return responseToClient;
            }
        }

        return responseToClient;
    }

    //-----------------------------------
    // Method responsible for allocating resources

    private void allocator(Map<String,Integer> resources,
                          ArrayList<String> resourcesToAllocate,ArrayList<Integer> quantityToAllocate){
        /*for (int i = 0; i < resourcesToAllocate.size(); i++) {
            System.out.println(resourcesToAllocate.get(i) + " -> " + quantityToAllocate.get(i));
        }*/

        /*System.out.println("res A -> " + resources.get("A"));
        System.out.println("res B -> " + resources.get("B"));
        System.out.println("res C -> " + resources.get("C"));*/



        for (int i = 0; i < resourcesToAllocate.size(); i++) {
            if (resources.containsKey(resourcesToAllocate.get(i)) && quantityToAllocate.get(i) != 0){
                int activeResource = resources.get(resourcesToAllocate.get(i));
                int leftResources;
                if (activeResource >= quantityToAllocate.get(i)){
                    leftResources = activeResource - quantityToAllocate.get(i);

                    resources.replace(resourcesToAllocate.get(i),leftResources);

                    this.resourcesToRecover.add(resourcesToAllocate.get(i));
                    this.quantityToRecover.add(quantityToAllocate.get(i));
                    quantityToAllocate.set(i,0);

                }else if (activeResource < quantityToAllocate.get(i)){
                    leftResources = 0;

                    resources.replace(resourcesToAllocate.get(i),leftResources);

                    this.resourcesToRecover.add(resourcesToAllocate.get(i));
                    this.quantityToRecover.add(activeResource);
                    quantityToAllocate.set(i, quantityToAllocate.get(i) - activeResource);
                }

            }
        }
       /* for (int i = 0; i < resourcesToAllocate.size(); i++) {
            System.out.println(resourcesToAllocate.get(i) + " -> " + quantityToAllocate.get(i));
        }*/

        /*System.out.println("res A -> " + resources.get("A"));
        System.out.println("res B -> " + resources.get("B"));
        System.out.println("res C -> " + resources.get("C"));

        System.out.println("rec res A -> " + resources.get("A"));
        System.out.println("rec res B -> " + resources.get("B"));
        System.out.println("rec res C -> " + resources.get("C"));*/
    }
    //----------------------------------
    // Recovers resources when allocation fails
    public void recover(){
        for (int i = 0; i < resourcesToRecover.size(); i++) {
            if (resources.containsKey(resourcesToRecover.get(i))){

                resources.replace(resourcesToRecover.get(i),
                        resources.get(resourcesToRecover.get(i)) + quantityToRecover.get(i));

                resourcesToRecover.clear();
                quantityToRecover.clear();
            }
        }
    }
}
