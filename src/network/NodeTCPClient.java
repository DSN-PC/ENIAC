package network;

import eniac.Node;
import eniac.Node.Neighbors;
import eniac.Main.TCPRequestTypes;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a TCP client which queries the main server for
 * grid or node descriptor information.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeTCPClient implements Runnable {
    
    private final Node callerNode;
    private float lat,lon;
    private int udpListeningPort;
    private final InetAddress mainServerAddress;
    private final int mainServerPort;
    private int x,y;
    private Neighbors neighbor;
    
    private final TCPRequestTypes requestType;

    
    /**
     * Class constructor (for request type GET_MY_XY_AND_GRIDSIZE).
     *
     * @param callerNode            the node that created this TCP client
     * @param lat                   geographical latitude of the caller node
     * @param lon                   geographical longitude of the caller node
     * @param udpListeningPort      listening port of the UDP server
     * @param mainServerAddress     InetAddress of the main server
     * @param mainServerPort        port of the main server
     */    
    public NodeTCPClient(Node callerNode, float lat, float lon, int udpListeningPort, InetAddress mainServerAddress, int mainServerPort) {
        this.callerNode = callerNode;
        this.lat = lat;
        this.lon = lon;
        this.udpListeningPort = udpListeningPort;
        this.mainServerAddress = mainServerAddress;
        this.mainServerPort = mainServerPort;
        
        this.requestType = TCPRequestTypes.GET_MY_XY_AND_GRIDSIZE;
    }   
        
    
    /**
     * Class constructor (for request type GET_NODE_DESCRIPTOR).
     *
     * @param callerNode            the node instance that created this TCP Client
     * @param neighbor              direction of the neighbor node
     * @param x                     x coordinate of the neighbor node
     * @param y                     y coordinate of the neighbor node
     * @param mainServerAddress     InetAddress of the main server
     * @param mainServerPort        port of the main server
     */    
    public NodeTCPClient(Node callerNode, Neighbors neighbor, int x, int y, InetAddress mainServerAddress, int mainServerPort) {        
        this.callerNode = callerNode;
        this.neighbor = neighbor;
        this.x = x;
        this.y = y;
        this.mainServerAddress = mainServerAddress;
        this.mainServerPort = mainServerPort;
        
        this.requestType = TCPRequestTypes.GET_NODE_DESCRIPTOR;
    }
    
    /**
     * Contains the code of the implementation of the TCP client.
     */
    @Override
    public void run() {
        
        final ExecutorService clientThreadExecutor = Executors.newSingleThreadExecutor();
        
        try {
            Socket requesterSocket = new Socket(mainServerAddress, mainServerPort);
            DataOutputStream out = new DataOutputStream(requesterSocket.getOutputStream());
            out.writeByte((byte)requestType.ordinal());
            
            switch (requestType) {
                /**************************************************************************
                 * Request type 0: client asks for x,y based on latitude and longitude    *
                 **************************************************************************/            
                case GET_MY_XY_AND_GRIDSIZE:
                    clientThreadExecutor.execute(new NodeTCPClientThread0(callerNode, lat, lon, udpListeningPort, requesterSocket, out));
                    break;
                    
                /**************************************************************************
                 * Request type 1: client asks for neighbor address and port based on x,y *
                 **************************************************************************/                
                case GET_NODE_DESCRIPTOR:
                    clientThreadExecutor.execute(new NodeTCPClientThread1(callerNode, neighbor, x, y, requesterSocket, out));
                    break;
                    
                default:
                    System.err.println("Error in NodeTCPClient run(): invalid requestType.");
                    requesterSocket.close();
                    out.close();
                    break;                
            }
        } catch (IOException ex) {
            Logger.getLogger(NodeTCPClient.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            /* Shut down executor. */
            clientThreadExecutor.shutdown();
            try {
                clientThreadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(NodeTCPClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
