# ProjektSKJ
SKJProject_2021/2022_sem3

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
 */ -------------------------------------------------------------
