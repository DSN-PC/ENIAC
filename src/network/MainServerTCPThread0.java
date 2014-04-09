
package network;

import datastructures.NodeDescriptor;
import eniac.Main;
import java.awt.Dimension;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class implements a TCP server thread for MainServer.
 * It provides neighbor descriptors for the Eniac nodes.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class MainServerTCPThread0 implements Runnable {
        
    private static final int RECEIVE_BUFFER_SIZE = (2*Float.SIZE) / 8;    // lat4), lon(4)
    private static final int SEND_BUFFER_SIZE = (4*Integer.SIZE) / 8;     // x(4), y(4), grid width(4), grid height(4)
    
    private final Socket requesterSocket; 
    private final DataInputStream in;
    
    
    /**
     * Class constructor
     *
     * @param requesterSocket   client socket
     * @param in                DataInputStream of requesterSocket
     */    
    public MainServerTCPThread0(Socket requesterSocket, DataInputStream in){
        this.requesterSocket = requesterSocket;
        this.in = in;
    }
    

    /**
     * Contains the code of the implementation of the TCP server thread.
     */
    @Override
    public void run() {
                        
        try (DataOutputStream out = new DataOutputStream(requesterSocket.getOutputStream())) {
            final byte[] receiveBuffer = new byte[RECEIVE_BUFFER_SIZE];
            in.readFully(receiveBuffer);
            ByteBuffer buf = ByteBuffer.wrap(receiveBuffer).order(ByteOrder.LITTLE_ENDIAN);
            final float lat = buf.getFloat();
            final float lon = buf.getFloat();            
            final int nodeListeningPort = in.readUnsignedShort();

            /* Prepare and fill buffer. */
            if (buf.capacity() < SEND_BUFFER_SIZE)
                buf = ByteBuffer.allocate(SEND_BUFFER_SIZE);
            else
                buf.clear();      
            buf.order(ByteOrder.LITTLE_ENDIAN);
            final Dimension gridSize = Main.getGridSize();
            final int[] xy = Main.getNodeXYCoordinates(lat, lon);
            buf.putInt(gridSize.width);
            buf.putInt(gridSize.height);            
            buf.putInt(xy[0]);
            buf.putInt(xy[1]);

            /* Send out (x,y) coordinates and grid size to the client */            
            out.write(buf.array());
            
            /* Get the address of the node.
               If it is a loopback address (simulated node),
               store the local host address instead to avoid problems
               when sending this address to a real node.
               (TCP request type GET_NEIGHBOR_ADDRESS_AND_PORT) */
            InetAddress nodeAddress = requesterSocket.getInetAddress();
            
            /* Create and add new node descriptor. */
            Main.addNodeDescriptor(xy[0], xy[1], new NodeDescriptor(nodeAddress, nodeListeningPort));
            
        } catch(IOException e) {
            Logger.getLogger(NodeUDPServer.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            try {
                in.close();
                requesterSocket.close();
            } catch (IOException ex) {
                Logger.getLogger(MainServer.class.getName()).log(Level.SEVERE, null, ex);
            }              
        }
    }
}
