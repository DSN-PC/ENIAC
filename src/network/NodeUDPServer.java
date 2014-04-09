
package network;

import eniac.Node;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class implements a UDP server for an ENIAC node.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeUDPServer implements Runnable {
    
    private static final int SEND_BUFFER_SIZE = Float.SIZE / 8;
    private static final int RECEIVE_BUFFER_SIZE = (2*Integer.SIZE) / 8;    
    private final Node en;
    private volatile boolean stop;
    private DatagramSocket providerSocket;
    
    
    /**
     * Class constructor
     *
     * @param en    the node which this server belongs to
     * @throws java.net.SocketException
     */    
    public NodeUDPServer(Node en) throws SocketException {        
        this.en = en;
        try {
            this.providerSocket = new DatagramSocket();
        } catch (SocketException ex) {
            Logger.getLogger(NodeUDPServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
    /**
     * Contains the code of the implementation of the UDP server.
     */
    @Override
    public void run() {
        byte[] receiveBuffer = new byte[RECEIVE_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length); 
        byte[] sendBuffer;
        DatagramPacket sendPacket;
        int dataTypeOrdinal;
        int step;
        float dataToSend;         
        
        try { 
            this.providerSocket.setSoTimeout(30000);             
            
            while(true) {     
                /*********************************************
                 * Wait for client request and send response *
                 *********************************************/
                try {
                    providerSocket.receive(receivePacket);
                } catch (SocketTimeoutException ex) {
                    System.err.println("UDP Server receive timeout, x=" + en.x + " y=" + en.y);
                    if (stop)
                        break;
                    continue;
                }
                ByteBuffer buf = ByteBuffer.wrap(receivePacket.getData()).order(ByteOrder.LITTLE_ENDIAN);
                dataTypeOrdinal = buf.getInt();
                step = buf.getInt();
                
                dataToSend = en.getValue(Node.DataTypes.values()[dataTypeOrdinal], step);        
                sendBuffer = ByteBuffer.allocate(SEND_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN).putFloat(dataToSend).array();                                
                sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                providerSocket.send(sendPacket);                
            }  
            System.err.println("UDP server thread finished, x=" + en.x + " y=" + en.y);
        } catch(IOException e) {
            System.err.println("UDP server thread finished exception, x=" + en.x + " y=" + en.y);
            Logger.getLogger(NodeUDPServer.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            System.err.println("UDP server thread finished finally, x=" + en.x + " y=" + en.y);
            providerSocket.close();                  
        }
    }
    
    
    /**
     * Returns the listening port of the node UDP server.
     * @return listening port of the node UDP server
     */
    public int getListeningPort() {
        return providerSocket.getLocalPort();
    }
    
    
    /**
     * Sends a stop signal to the server.
     */
    public void stop() {
        stop = true;
    }
}