
package network;

import eniac.Node;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a TCP client which queries the main server for
 * information about the grid and the requester node's (x,y) coordinates.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeTCPClientThread0 implements Runnable {
    
    private final int SEND_BUFFER_SIZE = (2*Float.SIZE) / 8;       // lat(4), lon(4)
    private final int RECEIVE_BUFFER_SIZE = (4*Integer.SIZE) / 8;  // x(4), y(4), grid width(4), grid height(4)
    
    private final Node callerNode;
    private final float lat,lon;
    private final int udpListeningPort;
    private final Socket requesterSocket;
    private final DataOutputStream out;
    
    
    /**
     * Class constructor.
     *
     * @param callerNode        the node that created this TCP client
     * @param lat               geographical latitude of the caller node
     * @param lon               geographical longitude of the caller node
     * @param udpListeningPort  listening port of the UDP server  
     * @param requesterSocket   client socket
     * @param out               DataOutputStream of requesterSocket
     */      
    public NodeTCPClientThread0(Node callerNode, float lat, float lon, int udpListeningPort, Socket requesterSocket, DataOutputStream out) {
        this.callerNode = callerNode;
        this.lat = lat;
        this.lon = lon;
        this.udpListeningPort = udpListeningPort;
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
            buf.putFloat(lat);
            buf.putFloat(lon);
            out.write(buf.array());
            out.writeShort(udpListeningPort);

            /* Read in bytes from the server */
            final byte[] receiveBuffer = new byte[RECEIVE_BUFFER_SIZE];
            in.readFully(receiveBuffer);
            buf = ByteBuffer.wrap(receiveBuffer).order(ByteOrder.LITTLE_ENDIAN);
            final int width = buf.getInt();
            final int height = buf.getInt();
            final int x = buf.getInt();
            final int y = buf.getInt();
            
            /* Set grid size and (x,y) coordinates on the node. */
            callerNode.setGridSize(width, height);
            callerNode.setX(x);
            callerNode.setY(y);            
            
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
