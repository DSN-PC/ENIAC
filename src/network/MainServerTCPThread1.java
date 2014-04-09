
package network;

import datastructures.NodeDescriptor;
import eniac.Main;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
public class MainServerTCPThread1 implements Runnable {
    
    private static final int RECEIVE_BUFFER_SIZE = (2*Integer.SIZE) / 8;  // x(4), y(4)
    
    private final Socket requesterSocket; 
    private final DataInputStream in;
    
    
    /**
     * Class constructor
     *
     * @param requesterSocket   client socket
     * @param in                DataInputStream of requesterSocket
     */    
    public MainServerTCPThread1(Socket requesterSocket, DataInputStream in){
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
            final int x = buf.getInt();
            final int y = buf.getInt();  

            NodeDescriptor nd;
            /* If the requested node descriptor is not available yet,
               send "255.255.255.255" as node address. */
            while ( (nd = Main.getNodeDescriptor(x,y)) == null ) {
                out.write(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255});
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MainServerTCPThread1.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            /* Send node address and port number to the client (no need for endian conversion) */
            if (!requesterSocket.getInetAddress().isLoopbackAddress() && nd.address.isLoopbackAddress())
                out.write(requesterSocket.getLocalAddress().getAddress());
            else            
                out.write(nd.address.getAddress());
            out.writeShort(nd.port);
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
