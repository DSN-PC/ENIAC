package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a UDP server, which sends out broadcast messages on the
 * local network advertising the IP address and port of the main server.
 * It uses UDP port 30303 as defined by Microchip's Discovery protocol.
 * @author ÁK
 */
public class MainServerUDPThread implements Runnable {
    
    private boolean stop;
    private DatagramSocket broadcastSocket;
    private final int port;
    
    public MainServerUDPThread(int port) {
        this.port = port;
    }
    
    /**
     * Contains the code of the UDP broadcaster thread
     */
    @Override
    public void run() {
        try {
            /* Broadcasted string is from Microchip's TCPIP Discovery Tool */
            byte[] sendData = "Discovery: Who is out there?".getBytes();
            Enumeration<NetworkInterface> interfaces;
            while(!stop) {
                interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual())
                        continue;
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        broadcastSocket = new DatagramSocket(port, interfaceAddress.getAddress());
                        broadcastSocket.setBroadcast(true);
                        broadcastSocket.send(new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), port));                        
                        broadcastSocket.close();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MainServerUDPThread.class.getName()).log(Level.SEVERE, null, ex);
                }
            }           
        } catch (IOException ex) {
            Logger.getLogger(MainServerUDPThread.class.getName()).log(Level.SEVERE, null, ex);
        }    
    } 
    
    
    /**
     * Sends a stop signal to the broadcaster thread.
     */
    public void stop() {
        stop = true;
    }
}
