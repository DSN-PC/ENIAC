package network;

import datastructures.NodeDescriptor;
import eniac.Node;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a TCP client which queries the main server for
 * node descriptor information.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeTCPClientThread1 implements Runnable {
    
    private final int SEND_BUFFER_SIZE = (2*Integer.SIZE) / 8;  // x(4), y(4)
    private final Node callerNode;
    private final Node.Neighbors neighbor;
    private final int x,y;
    private final Socket requesterSocket;
    private final DataOutputStream out;
        
    
    /**
     * Class constructor.
     *
     * @param callerNode            the node instance that created this TCP Client
     * @param neighbor              direction of the neighbor node
     * @param x                     x coordinate of the neighbor node
     * @param y                     y coordinate of the neighbor node
     * @param requesterSocket       client socket
     * @param out                   DataOutputStream of requesterSocket
     */        
    public NodeTCPClientThread1(Node callerNode, Node.Neighbors neighbor, int x, int y, Socket requesterSocket, DataOutputStream out) {        
        this.callerNode = callerNode;
        this.neighbor = neighbor;
        this.x = x;
        this.y = y;
        this.requesterSocket = requesterSocket;
        this.out = out;
    }
    
    /**
     * Contains the code of the implementation of the TCP client.
     */
    @Override
    public void run() {
                
        try (DataInputStream in = new DataInputStream(requesterSocket.getInputStream())) { 
            /* Send query to the main server */
            ByteBuffer buf = ByteBuffer.allocate(SEND_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(x);
            buf.putInt(y);
            out.write(buf.array());

            /* Read in bytes from the server */
            final byte[] addressBytes = new byte[4];
            in.readFully(addressBytes);
            
            /* Keep receiving while address==255.255.255.255 (neighbor node not logged in yet) */
            while (Arrays.equals(addressBytes, new byte[]{(byte)255,(byte)255,(byte)255,(byte)255})){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(NodeTCPClientThread1.class.getName()).log(Level.SEVERE, null, ex);
                }
                in.readFully(addressBytes);
            }
            final InetAddress requestedServerAddress = InetAddress.getByAddress(addressBytes);
            final int requestedServerPort = in.readUnsignedShort();
                        
            /* Set neighbor descriptor on the node. */
            callerNode.setNeighborDescriptor(neighbor, new NodeDescriptor(requestedServerAddress, requestedServerPort));
            
        } catch(IOException e) {
            Logger.getLogger(NodeUDPServer.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            try {
                out.close();
                requesterSocket.close();
            } catch (IOException ex) {
                Logger.getLogger(MainServer.class.getName()).log(Level.SEVERE, null, ex);
            }              
        }
    }
}
