
package eniac;

import datastructures.NodeDescriptor;
import gui.CountdownPanel;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import network.MainServer;

/**
 * This class contains the code of initialization of the ENIAC calculations.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class Main {
    
    public static final String MAIN_SERVER_ADDRESS = "localhost";
    public static final int MAIN_SERVER_PORT = 30303;
    private static final long TIME_TO_WAIT_FOR_REAL_NODES = 10000;  // milliseconds
    public static long timer;                                      // initialized at MainServer
    public static volatile boolean countdownFinished = false;
    public static volatile int numberOfRealNodes = 0;
    
    private static final int SIZE_X=19, SIZE_Y=16;
    private static float[][] z0;
    private static float[][] lat;
    private static float[][] lon;
    private static final NodeDescriptor[][] nodeDescriptors = new NodeDescriptor[SIZE_Y][SIZE_X];
    private static final Node[][] simulatedNodes = new Node[SIZE_Y][SIZE_X];
    
    
    /**
     * TCP client request types.
     */
    public static enum TCPRequestTypes {
        GET_MY_XY_AND_GRIDSIZE,
        GET_NODE_DESCRIPTOR
    }
    
    
    /**
     * Main application for ENIAC calculations.
     * @param args  unused
     */
    public static void main(String[] args) {
        
        /* Read z0, lat, lon values from files. */
        z0 = readGridValues("Case1-1949010503.z00");
        lat = readGridValues("LAT1.txt");
        lon = readGridValues("LON1.txt");
        
        /* Start Main server. */
        final MainServer mainServer = new MainServer(MAIN_SERVER_PORT);
        final ExecutorService mainServerExecutor = Executors.newSingleThreadExecutor();
        mainServerExecutor.execute(mainServer);
        
        /* Show countdown panel, start countdown. */
        final CountdownPanel cdp = new CountdownPanel(TIME_TO_WAIT_FOR_REAL_NODES);
        cdp.setVisible(true);
        cdp.startCountdown();
        
        /* Countdown finished, dispose panel. */
        countdownFinished = true;
        cdp.dispose();
        
        /* Start simulated Node threads. */   
        final ExecutorService simulatedNodeExecutor = Executors.newFixedThreadPool(SIZE_X*SIZE_Y);  
        for (int y=0; y<SIZE_Y; y++) {
            for (int x=0; x<SIZE_X; x++) {
                if (nodeDescriptors[y][x] == null) {
                    try {                    
                        simulatedNodes[y][x] = new Node(lat[y][x], lon[y][x], z0[y][x]);
                    } catch (SocketException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    simulatedNodeExecutor.execute(simulatedNodes[y][x]);
                }
            }
        }
        
        /* Wait for threads to finish.
           Meanwhile, print out the current value of z in every 10 seconds. */
        simulatedNodeExecutor.shutdown();        
        while (!simulatedNodeExecutor.isTerminated()) {
            getMap();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                System.err.println("EniacGrid: " + ex.getMessage());
            }
            int i=1;
        }
        System.out.println("ALL NODES ENDED");
        getMap(); 
        
        /* Stop main server. */
        mainServer.stop();
        mainServerExecutor.shutdown();
        while (!mainServerExecutor.isTerminated()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }      
        
        /* Exit application. */
        System.exit(0);
    }        
    
    
    /**
     * Reads in grid data from a text file.
     * 
     * @param filename  the name of the text file
     * @return          a 2D array containing the grid data
     */
    private static float[][] readGridValues(String filename)
    {
        final float[][] gridValues = new float[SIZE_Y][SIZE_X];
        int x,y;
        
        x=0;
        try (Scanner sc = new Scanner(new File(filename))) {
            while(sc.hasNextLine())
            {
                Scanner rowReader = new Scanner(sc.nextLine());
                String[] numbers = rowReader.nextLine().split("   ");
                y=0;
                for (String s : numbers)
                {
                    if (s.isEmpty())
                        continue;                
                    gridValues[y][x] = Float.valueOf(s);
                    y++;
                }
                x++;
            }         
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        return gridValues;
    }
    
    
    /**
     * Prints out the current z values of EniacNodes.
     */    
    private static void getMap() {            
        System.out.println("\nMap of z values:");
        System.out.print("---------------------------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------------------------");
        
        for (int y=SIZE_Y-1; y>=0; y--) {
            for(int x=0; x<SIZE_X; x++) {
                if (simulatedNodes[y][x] == null)
                    System.out.print("RealNode ");
                else
                    System.out.print(simulatedNodes[y][x].getValue(Node.DataTypes.Z,simulatedNodes[y][x].z.getStep()) + " ");
            }
            System.out.println();
        }
        System.out.print("---------------------------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------------------------\n");
    }        
    
    
    /**
     * Returns the width and height of the grid.
     * @return  reference to a Dimension with size (SIZE_X, SIZE_Y)
     */
    public static Dimension getGridSize(){
        return new Dimension(SIZE_X, SIZE_Y);
    }
    
    
    /**
     * Returns (x,y) coordinates of a node at a given geographical position.
     * @param latitude      geographical latitude of the node
     * @param longitude     geographical longitude of the node
     * @return              2-element array containing (x,y) coordinates of the node
     */
    public static int[] getNodeXYCoordinates(float latitude, float longitude){
        
        float latDiff = Float.MAX_VALUE,
              lonDiff = Float.MAX_VALUE;
        
        int xTmp=-1,
            yTmp=-1;
                
        /* Find the nearest grid point. */
        for (int y=0; y<SIZE_Y; y++){
            for (int x=0; x<SIZE_X; x++){
                if ( (Math.abs(lat[y][x]-latitude) <= latDiff) && (Math.abs(lon[y][x]-longitude) <= lonDiff) ){
                    latDiff = Math.abs(lat[y][x]-latitude);
                    lonDiff = Math.abs(lon[y][x]-longitude);
                    xTmp = x;
                    yTmp = y;
                }
            }
        }
        return new int[]{xTmp, yTmp};
    }                
    
    
    /**
     * Returns the descriptor of a given node.
     * 
     * @param x  x coordinate of the node
     * @param y  y coordinate of the node
     * @return   the descriptor of the node at (x,y)
     */    
    public static NodeDescriptor getNodeDescriptor(int x, int y) {
        return nodeDescriptors[y][x];
    }
    
    
    /**
     * Adds a new node descriptor (if it doesn't exist already).
     * @param x     x coordinate of the new node
     * @param y     y coordinate of the new node
     * @param nd    node descriptor
     */
    public static void addNodeDescriptor(int x, int y, NodeDescriptor nd) {
        if (nodeDescriptors[y][x] == null) {
            nodeDescriptors[y][x] = nd;            
            if (!countdownFinished)
                numberOfRealNodes++;
        }
        else
            System.err.println("Error in addNodeDescriptor(): node already exits.");
    }
}
