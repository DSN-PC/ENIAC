package network;

import eniac.Node;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a UDP client for an ENIAC node.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeUDPClient implements Runnable {
    
    private static final int SEND_BUFFER_SIZE = (2*Integer.SIZE) / 8;
    private static final int RECEIVE_BUFFER_SIZE = Float.SIZE / 8;
    private final Node en;
    private final Node.Neighbors neighbor;
    private final Node.DataTypes dataType;
    private final int step;
    private final InetAddress serverAddress;
    private final int serverPort;
    
    
    /**
     * Class constructor.
     *
     * @param en            the node object which this server belongs to
     * @param neighbor      neighbor to query
     * @param dataType      type of the requested data
     * @param step          step of the requested data
     * @param serverAddress IP address of the neighbor
     * @param serverPort    port of the neighbor
     */    
    public NodeUDPClient(Node en, Node.Neighbors neighbor, Node.DataTypes dataType, int step, InetAddress serverAddress, int serverPort) {
        this.en = en;
        this.neighbor = neighbor;
        this.dataType = dataType;
        this.step = step;        
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }
    
    /**
     * Contains the code of the implementation of the UDP client.
     */
    @Override
    public void run() {
        
        try (DatagramSocket requesterSocket = new DatagramSocket()) {
            
            requesterSocket.setSoTimeout(3000);             
            
            /* Send request to the server */ 
            ByteBuffer buf = ByteBuffer.allocate(SEND_BUFFER_SIZE);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(dataType.ordinal());
            buf.putInt(step);
            DatagramPacket sendPacket = new DatagramPacket(buf.array(), buf.array().length, serverAddress, serverPort);
            requesterSocket.send(sendPacket);
            /**********************************************************
             * Awaiting response from the server                      *
             * If no answer in a given timeout period, resend request *
             **********************************************************/
            byte[] receiveBuffer = new byte[RECEIVE_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);            
            float dataReceived;
            while (true) { 
                while (true) {
                    try {
                        requesterSocket.receive(receivePacket);
                    } catch (SocketTimeoutException ex) {
                        System.err.println("Resending UDP request." + " " + dataType + " " + step + " to " + serverAddress + ":" + serverPort + ", x=" + en.x + " y=" + en.y + ", source port:" + requesterSocket.getLocalPort());
                        requesterSocket.send(sendPacket);
                        continue;
                    }
                    break;
                }
                dataReceived = ByteBuffer.wrap(receivePacket.getData()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                
                /***********************************************
                 * If we received NaN, wait and resend request *
                 ***********************************************/
                if (Float.isNaN(dataReceived)) {
                    try {
                        if (dataType == Node.DataTypes.DZDT)
                            Thread.sleep(100);
                        else
                            Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(NodeUDPClient.class.getName()).log(Level.SEVERE, null, ex);
                    }                    
                    requesterSocket.send(sendPacket);
                    continue;
                }
                break;
            }            
            /* Received valid data */
            en.setNeighborValue(neighbor, dataReceived);            
        } catch (IOException e) {
            Logger.getLogger(NodeUDPClient.class.getName()).log(Level.SEVERE, null, e);         
        }            
    }
}
