
package network;

import eniac.Main;
import eniac.Main.TCPRequestTypes;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a TCP server for ENIAC calculations.
 * It provides grid and node descriptor information for the requester nodes.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class MainServer implements Runnable {
    
    private final int port;
    private volatile boolean stop;
    
    /**
     * Class constructor
     *
     * @param port        the listening port
     */   
    public MainServer(int port) {
        this.port = port;
    }

    /**
     * Contains the code of the implementation of the main server.
     */
    @Override
    public void run() {
                 
        final ExecutorService serverThreadExecutor = Executors.newCachedThreadPool();        
        Socket requesterSocket;
        
        /* Start UDP broadcaster thread */
        MainServerUDPThread udpThread = new MainServerUDPThread(port);
        serverThreadExecutor.execute(udpThread);
        
        try (ServerSocket providerSocket = new ServerSocket(port)) { 
            providerSocket.setSoTimeout(10000);
            
            /* Start countdown timer. */
            Main.timer = System.currentTimeMillis();
            
            while (true) {     
                /********************************************************************
                 * Wait for client connection request and start a new server thread *
                 ********************************************************************/
                try {
                    requesterSocket = providerSocket.accept();                    
                } catch (SocketTimeoutException ex) {
                    if (stop)
                        break;
                    continue;
                }
                requesterSocket.setSoTimeout(10000);
                final DataInputStream in = new DataInputStream(requesterSocket.getInputStream());
                final TCPRequestTypes requestType = TCPRequestTypes.values()[in.readByte()];

                switch (requestType) {
                    /*******************************************************************
                     * Request type 0: client asks for grid size and (x,y) coordinates *
                     * based on latitude and longitude                                 *
                     *******************************************************************/
                    case GET_MY_XY_AND_GRIDSIZE:
                        serverThreadExecutor.execute(new MainServerTCPThread0(requesterSocket, in));
                        break;

                    /******************************************************************
                     * Request type 1: client asks for node descriptor based on (x,y) *
                     ******************************************************************/                            
                    case GET_NODE_DESCRIPTOR:
                        serverThreadExecutor.execute(new MainServerTCPThread1(requesterSocket, in));
                        break;

                    default:
                        System.err.println("Error in MainServer run(): invalid requestType.");
                        break;
                }
            }  
        } catch(IOException e) {
            Logger.getLogger(NodeUDPServer.class.getName()).log(Level.SEVERE, null, e);
        } finally {           
            /* Stop UDP broadcaster thread */
            udpThread.stop();
            /* Shut down executor. */
            serverThreadExecutor.shutdown();
            try {
                serverThreadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(MainServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Sends a stop signal to the server.
     */
    public void stop() {
        stop = true;
    }
}
