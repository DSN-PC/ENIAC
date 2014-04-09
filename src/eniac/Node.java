
package eniac;

import datastructures.*;
import network.NodeUDPClient;
import network.NodeUDPServer;
import java.awt.Dimension;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import network.NodeTCPClient;


/**
 * This class implements a simulated ENIAC node.
 * @version 0.1
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class Node implements Runnable  {

    /**
     * x coordinate of the node
     */    
    public int x;
    /**
     * y coordinate of the node
     */    
    public int y;
    
    /**
     * size of the grid
     */
    private Dimension gridSize;
    
    /*
     * variables indicating the position of the node in the grid
     */
    private boolean isInner, isNorthern, isEastern, isSouthern, isWestern;
    
    /**
     * on a border node this variable indicates whether the fluid is entering or leaving the area
     */
    private boolean isFluidLeaving;    
   
    /**
     * geographical latitude 
     */
    private final float lat;
    /**
     * geographical longitude 
     */
    private final float lon;
    
    /*
     * some geographical parameters
     */
    private final float m,                                              // map projection magnification factor
                        f,                                              // Coriolis parameter
                        h;                                              // defined as g*(m^2)/f
    private static final int DS = 736000;                               // grid interval (736 km)
    private static final float GRAV = (float)9.80665;                   // gravitational constant
    private static final float OMEGA = (float)(2*Math.PI/(24*60*60));   // angular velocity of Earth’s rotation    
    
    /*
     * variables used in calculations
     */
    private float dzdx, dzdy, detadx, detady, zNew, xiNew;    
    
    /**
     * types of exchanged data
     */
    public static enum DataTypes {
        Z,                      // geopotential height of the 500 mbar level
        DZDT,                   // dz/dt
        XI,                     // Laplace(z)
        DXIDT,                  // dxi/dt
        ETA                     // absolute vorticity
    }        
    
    /**
     * data objects for calculation data
     */
    public final DataStruct z; 
    public final DataStructDZDT dzdt;    
    public final DataStructXI xi; 
    public final DataStructDXIDT dxidt; 
    public final DataStructETA eta; 
    
    /**
     * directions to close neighbors 
     */
    public static enum Neighbors {
        NORTH, EAST, SOUTH, WEST
    }    
    
    /**
     * neighbors' descriptors 
     */
    private final EnumMap<Neighbors, NodeDescriptor> neighborDescriptors;
    
    /**
     * neighbors' data (temporary buffer used during data exchange between neighbors)
     */
    private final EnumMap<Neighbors, AtomicInteger> neighborData;    
    
    /** 
     * UDP server and its executor 
     */
    private final NodeUDPServer udpServer;
    private final ExecutorService udpServerExecutor;    
    
    /**
     * some constants common to all nodes 
     */
    public static final int HOURS = 24;           // forecast duration in hours
    private static final int DT_SEC = 3600;       // duration of one forecast step in seconds
    public static final int NUM_ITERATIONS = 200; // number of iterations of the solution method of the Poisson-equation
    
    
    /**
     * Class constructor.
     * Node is initialized here.
     *
     * @param lat           geographical latitude in radians
     * @param lon           geographical longitude in radians
     * @param z0            initial value of z
     * @throws              java.net.SocketException
     */    
    public Node(float lat, float lon, float z0) throws SocketException {       
        
        this.lat = lat;
        this.lon = lon;
        
        this.z = new DataStructZ();
        setValue(DataTypes.Z, 0, z0);       
        this.dzdt = new DataStructDZDT();
        this.xi = new DataStructXI();
        this.dxidt = new DataStructDXIDT();
        this.eta = new DataStructETA();        
          
        this.neighborDescriptors = new EnumMap<>(Neighbors.class);
        this.neighborData = new EnumMap<>(Neighbors.class);
        
        this.udpServer = new NodeUDPServer(this);
        this.udpServerExecutor = Executors.newSingleThreadExecutor();
                
        /* Initialize geographical parameters */  
        this.m = (float)(2/(1+Math.sin(lat)));
        this.f = (float)(2 * OMEGA * Math.sin(lat));
        this.h = GRAV * this.m*this.m / this.f; 
    }   
    
    
    /**
     * Contains the code to be executed for each node. 
     * ENIAC calculations are implemented here.
     */
    @Override
    public void run() {           
        
        /* Start UDP server. */
        udpServerExecutor.execute(udpServer);
        
        /* Initialize node. */
        initNode();
          
        /*************************** 
         *      INNER NODES:       *
         * Calculate xi=Laplace(z) *
         ***************************/
        if (isInner) {    
            /* Get z from neighbors */
            getDataFromNeighborsUDP(DataTypes.Z, 0, Neighbors.values());

            /* xi(i,j)=(z(i+1,j)+z(i-1,j)+z(i,j+1)+z(i,j-1)-4*z)/(DS^2) */
            float sum=0;
            for (Neighbors nb : Neighbors.values())
                sum += getNeighborValue(nb);
            setValue(DataTypes.XI, 0, (sum-4*getValue(DataTypes.Z, 0)) / DS / DS);  
        }     

        /***************************************** 
         *             BORDER NODES:             *        
         * Calculate xi by extrapolation,        *
         * check if fluid is entering or leaving *
         *****************************************/   
        else {                 
            /* dz/dt=0 at border nodes (initialized automatically), but *
             * setting its step to max is required so that it doesn't   *
             * return NaN when an inner node asks for it during the     *
             * solution method of the Poisson equation                  */
            dzdt.setStep(DataStructDZDT.NUM_STEPS-1);
            
            /* 
             * Western border node 
             */
            if (isWestern) {
                /* Get z from northern and southern neighbors. */
                getDataFromNeighborsUDP(DataTypes.Z, 0, Neighbors.NORTH, Neighbors.SOUTH);   

                /* Two cases:
                   1. z(i,j+1) >= z(i,j-1) ---> fluid is leaving
                   2. otherwise ---> fluid is entering */
                if (getNeighborValue(Neighbors.NORTH) >= getNeighborValue(Neighbors.SOUTH)) 
                    isFluidLeaving = true;                        

                /* Get xi from close and distant eastern neighbors */
                getDataFromNeighborsUDP(DataTypes.XI, 0, Neighbors.EAST, Neighbors.WEST);    

                /* xi(i,j)= 2*xi(i,j+1) - xi(i,j+2) */
                setValue(DataTypes.XI, 0, 2*(getNeighborValue(Neighbors.EAST)) - getNeighborValue(Neighbors.WEST));
            }
            /*
             * Eastern border node 
             */
            else if (isEastern) {
                /* Get z from southern and northern neighbors */
                getDataFromNeighborsUDP(DataTypes.Z, 0, Neighbors.SOUTH, Neighbors.NORTH);   

                /* Two cases:
                   1. z(i,j-1) >= z(i,j+1) ---> fluid is leaving
                   2. otherwise ---> fluid is entering */
                if (getNeighborValue(Neighbors.SOUTH) >= getNeighborValue(Neighbors.NORTH))
                    isFluidLeaving = true;


                /* Get xi from close and distant western neighbors. */
                getDataFromNeighborsUDP(DataTypes.XI, 0, Neighbors.WEST, Neighbors.EAST);           

                /* xi(i,j)= 2*xi(i,j-1) - xi(i,j-2) */
                setValue(DataTypes.XI, 0, 2*(getNeighborValue(Neighbors.WEST)) - getNeighborValue(Neighbors.EAST));
            }

            /*
             * Southern border node 
             */
            else if (isSouthern) {
                /* Get z from western and eastern neighbors. */
                getDataFromNeighborsUDP(DataTypes.Z, 0, Neighbors.WEST, Neighbors.EAST);   

                /* Two cases:
                   1. z(i-1,j) >= z(i+1,j) ---> fluid is leaving
                   2. otherwise ---> fluid is entering */
                if (getNeighborValue(Neighbors.WEST) >= getNeighborValue(Neighbors.EAST))
                    isFluidLeaving = true;

                /* Get xi from close and distant northern neighbors. */
                getDataFromNeighborsUDP(DataTypes.XI, 0, Neighbors.NORTH, Neighbors.SOUTH);       

                /* xi(i,j)= 2*xi(i+1,j) - xi(i+2,j) */
                setValue(DataTypes.XI, 0, 2*(getNeighborValue(Neighbors.NORTH)) - getNeighborValue(Neighbors.SOUTH));
            }

            /*
             * Northern border node 
             */
            else if (isNorthern) {
                /* Get z from eastern and western neighbors. */
                getDataFromNeighborsUDP(DataTypes.Z, 0, Neighbors.EAST, Neighbors.WEST);   

                /* Two cases:
                   1. z(i+1,j) >= z(i-1,j) ---> fluid is leaving
                   2. otherwise ---> fluid is entering */
                if (getNeighborValue(Neighbors.EAST) >= getNeighborValue(Neighbors.WEST))
                    isFluidLeaving = true;

                /* Get xi from close and distant southern neighbors. */
                getDataFromNeighborsUDP(DataTypes.XI, 0, Neighbors.SOUTH, Neighbors.NORTH);     

                /* xi(i,j)= 2*xi(i-1,j) - xi(i-2,j) */
                setValue(DataTypes.XI, 0, 2*(getNeighborValue(Neighbors.SOUTH)) - getNeighborValue(Neighbors.NORTH));
            } 
            
            /* dxi/dt=0 at border nodes (initialized automatically) where *
             * fluid is entering the area, but setting its step to max    *
             * is required so that it doesn't return NaN when a neighbour *
             * node asks for it during the calculation of eta.            */                
            if (!isFluidLeaving)
                dxidt.setStep(DataStructDXIDT.NUM_STEPS-1);
        }         
        
        /****************************************************
         **************************************************** 
         ************** MAIN LOOP STARTS HERE ***************
         ****************************************************
         ****************************************************/        
        for (int step=0; step<HOURS; step++) {
            
            System.out.println("node (" + x + "," + y + ") step " + (step+1));
            
            /********************************
             * Calculate absolute vorticity *
             ********************************/
            setValue(DataTypes.ETA, step, h*getValue(DataTypes.XI, step) + f);            

            /*************************************
             *            INNER NODES:           *
             * Calculate dxi/dt = Jacobi(eta,z), *
             * solve the Poisson equation        *
             *************************************/
            if (isInner) {
                getDataFromNeighborsUDP(DataTypes.Z, step, Neighbors.values());
                /* dz/dx(i,j) = (z(i+1,j)-z(i-1,j))/(2*DS) */
                dzdx = (getNeighborValue(Neighbors.EAST) - getNeighborValue(Neighbors.WEST)) / (2*DS);     
                /* dz/dy(i,j) = (z(i,j+1)-z(i,j-1))/(2*DS) */
                dzdy = (getNeighborValue(Neighbors.NORTH) - getNeighborValue(Neighbors.SOUTH)) / (2*DS);
                                                
                getDataFromNeighborsUDP(DataTypes.ETA, step, Neighbors.values());
                /* deta/dx(i,j) = (eta(i+1,j)-eta(i-1,j))/(2*DS) */
                detadx = (getNeighborValue(Neighbors.EAST) - getNeighborValue(Neighbors.WEST)) / (2*DS);     
                /* deta/dy(i,j) = (eta(i,j+1)-eta(i,j-1))/(2*DS) */
                detady = (getNeighborValue(Neighbors.NORTH) - getNeighborValue(Neighbors.SOUTH)) / (2*DS);    

                /* dxi/dt(i,j) = Jacobi(i,j) = (deta/dx * dz/dy - deta/dy * dz/dx) */
                setValue(DataTypes.DXIDT, step, detadx*dzdy - detady*dzdx);
                
                /******************************************************************
                 * Solve the Laplace(dz/dt) = dxi/dt Poisson equation iteratively *
                 ******************************************************************/  
                dzdt.setStep(0);
                dzdt.allowNextStepRequests();
                for (int it_step=0; it_step<NUM_ITERATIONS; it_step++) {
                    /* Get dz/dt from neighbors. */
                    getDataFromNeighborsUDP(DataTypes.DZDT, it_step, Neighbors.values());

                    /* dz/dt(i,j) = (1/4)*(dz/dt(i+1,j) + dz/dt(i-1,j) + dz/dt(i,j+1) + dz/dt(i,j-1) - Jacobi(i,j)*(DS^2))) */
                    float sum = 0;
                    for (Neighbors nb : Neighbors.values())
                        sum += getNeighborValue(nb);
                    setValue(DataTypes.DZDT, it_step+1, (sum - getValue(DataTypes.DXIDT, step)*DS*DS) / 4); 
                    Thread.yield(); 
                }
                dzdt.denyNextStepRequests();
            }    
            

            /****************************************************
             *                  BORDER NODES:                   *
             * Fluid leaving: Calculate dxi/dt by extrapolation *
             * Fluid entering: dxi/dt=0 (set before)            *
             ****************************************************/
            else if (isFluidLeaving) {                
                /*********************** 
                 * Western border node *
                 ***********************/
                if (isWestern)
                {
                    /* Get dxi/dt from close and distant eastern neighbors. */
                    getDataFromNeighborsUDP(DataTypes.DXIDT, step, Neighbors.EAST, Neighbors.WEST);  
                    
                    /* dxi/dt(i,j) = 2*dxi/dt(i+1,j) - dxi/dt(i+2,j) */
                    setValue(DataTypes.DXIDT, step, (2*getNeighborValue(Neighbors.EAST)) - getNeighborValue(Neighbors.WEST));
                }
                /***********************
                 * Eastern border node *
                 ***********************/
                else if (isEastern)
                {
                    /* Get dxi/dt from close and distant western neighbors. */
                    getDataFromNeighborsUDP(DataTypes.DXIDT, step, Neighbors.WEST, Neighbors.EAST);  

                    /* dxi/dt(i,j) = 2*dxi/dt(i-1,j) - dxi/dt(i-2,j) */
                    setValue(DataTypes.DXIDT, step, (2*getNeighborValue(Neighbors.WEST)) - getNeighborValue(Neighbors.EAST));
                }
                /************************
                 * Southern border node *
                 ************************/
                else if (isSouthern)
                {
                    /* Get dxi/dt from close and distant northern neighbors. */
                    getDataFromNeighborsUDP(DataTypes.DXIDT, step, Neighbors.NORTH, Neighbors.SOUTH);  

                    /* dxi/dt(i,j) = 2*dxi/dt(i,j+1) - dxi/dt(i,j+2) */
                    setValue(DataTypes.DXIDT, step, (2*getNeighborValue(Neighbors.NORTH)) - getNeighborValue(Neighbors.SOUTH));
                }        
                /************************
                 * Northern border node *
                 ************************/
                else if (isNorthern)
                {
                    /* Get dxi/dt from close and distant southern neighbors. */
                    getDataFromNeighborsUDP(DataTypes.DXIDT, step, Neighbors.SOUTH, Neighbors.NORTH);  

                    /* dxi/dt(i,j) = 2*dxi/dt(i,j-1) - dxi/dt(i,j-2) */
                    setValue(DataTypes.DXIDT, step, (2*getNeighborValue(Neighbors.SOUTH)) - getNeighborValue(Neighbors.NORTH));
                }
            }
            
            /*********************************************************************
             * Step forward xi and z based on dxi/dt and dz/dt                   *
             * Note: dz/dt=0 at border nodes (initial value of AtomicInteger),   *
             * dxi/dt=0 at border nodes where fluid is entering (set explicitly) *
             *********************************************************************/            
            /* First step: forward differences */
            if (step==0) {         
                xiNew = getValue(DataTypes.XI, step) + DT_SEC*getValue(DataTypes.DXIDT, step);
                zNew = getValue(DataTypes.Z, step) + DT_SEC*getValue(DataTypes.DZDT, NUM_ITERATIONS);
            }
            /* Next steps: central differences */
            else {
                xiNew = getValue(DataTypes.XI, step-1) + 2*DT_SEC*getValue(DataTypes.DXIDT, step);
                zNew = getValue(DataTypes.Z, step-1) + 2*DT_SEC*getValue(DataTypes.DZDT, NUM_ITERATIONS);
            }
            
            setValue(DataTypes.XI, step+1, xiNew);
            setValue(DataTypes.Z, step+1, zNew);
        }
        /***************************************************
         *************************************************** 
         *************** MAIN LOOP ENDS HERE ***************
         ***************************************************
         ***************************************************/
        
        
        /* Stop UDP server. */  
        udpServer.stop();        
        udpServerExecutor.shutdown();
        try {
            udpServerExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
    }     
    
    
    /**
     * Initializes the node (grid size, x,y coordinates, neighbors' descriptors)
     */
    private void initNode() {
        
        /* Get grid size and (x,y) coordinates from main server. */
        getGridSizeAndPositionTCP();        
        
        /********************************************************************************************************
         * Check node position: inner/border node                                                               *
         * Set neighbors' port numbers                                                                          *
         * If this is a northern border node, then let the distant southern neighbor be the "northern neighbor" *
         * (This method is applied to eastern, western and southern border nodes, too)                          *
         * This is required because the border nodes calculate their xi and dxi/dt values by extrapolation      *
         ********************************************************************************************************/       
        /*******************************************
         * Check if this is a northern border node *
         * Get northern neighbor's descriptor      *
         *******************************************/ 
        if (y<(gridSize.height-1)) {
            isNorthern = false;
            getNeighborDescriptorTCP(Neighbors.NORTH, x, y+1);
        }
        else {
            isNorthern = true;
            getNeighborDescriptorTCP(Neighbors.NORTH, x, y-2);
        }
        
        /********************************************
         * Check if this is a eastern border node.  *
         * Get eastern neighbor's descriptor.       *
         ********************************************/          
        if (x<(gridSize.width-1)) {
            isEastern = false;
            getNeighborDescriptorTCP(Neighbors.EAST, x+1, y);
        }
        else {
            isEastern = true;
            getNeighborDescriptorTCP(Neighbors.EAST, x-2, y);
        }
        
        /********************************************
         * Check if this is a southern border node. *
         * Get southern neighbor's descriptor.      *
         ********************************************/  
        if (y>0) {
            isSouthern = false;
            getNeighborDescriptorTCP(Neighbors.SOUTH, x, y-1);
        }
        else {
            isSouthern = true;
            getNeighborDescriptorTCP(Neighbors.SOUTH, x, y+2);
        }     
        
        /********************************************
         * Check if this is a western border node.  *
         * Get western neighbor's descriptor.       *
         ********************************************/  
        if (x>0) {
            isWestern = false;
            getNeighborDescriptorTCP(Neighbors.WEST, x-1, y);
        }
        else {
            isWestern = true;
            getNeighborDescriptorTCP(Neighbors.WEST, x+2, y);
        }          
        
        /* Check if this is an inner node */
        isInner = !isNorthern && !isEastern && !isSouthern && !isWestern;        
    }
    
        
    /**
     * Queries neighbor(s) for data.
     * 
     * Starts UDP client thread(s) to get data from neighbor(s).
     * 
     * @param dataType  the type of data to be queried from the neighbor(s)
     * @param step      the step of the queried data
     * @param neighbors which neighbor(s) to request the data from
     */
    private void getDataFromNeighborsUDP(DataTypes dataType, int step, Neighbors... neighbors) {
        
        ExecutorService udpClientExecutor = Executors.newFixedThreadPool(neighbors.length);
        for (Neighbors n: neighbors) {
            udpClientExecutor.execute(new NodeUDPClient(this, n, dataType, step, neighborDescriptors.get(n).address, neighborDescriptors.get(n).port));
        }             
        
        udpClientExecutor.shutdown();        
        try {
            udpClientExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    /**
     * Queries main server for grid data and coordinates of this node.     * 
     * Starts a TCP client thread to get the grid size and (x,y) coordinates from main server.     *
     */            
    private void getGridSizeAndPositionTCP() {
        
        ExecutorService tcpClientExecutor = Executors.newSingleThreadExecutor();
        try {
            tcpClientExecutor.execute(new NodeTCPClient(this, lat, lon, udpServer.getListeningPort(), InetAddress.getByName(Main.MAIN_SERVER_ADDRESS), Main.MAIN_SERVER_PORT));
        } catch (UnknownHostException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        tcpClientExecutor.shutdown();
        try {
            tcpClientExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    
    

    /**
     * Queries main server for node descriptor data.
     * 
     * Starts a TCP client thread to get the descriptor of node at (x,y) from main server.
     * 
     * @param x     x coordinate of the neighbor node
     * @param y     y coordinate of the neighbor node
     */    
    private void getNeighborDescriptorTCP(Neighbors neighbor, int x, int y) {
        
        ExecutorService tcpClientExecutor = Executors.newSingleThreadExecutor();
        try {
            tcpClientExecutor.execute(new NodeTCPClient(this, neighbor, x, y, InetAddress.getByName(Main.MAIN_SERVER_ADDRESS), Main.MAIN_SERVER_PORT));
        } catch (UnknownHostException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        tcpClientExecutor.shutdown();
        try {
            tcpClientExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    
    /**
     * Returns the value of a data field.
     *  
     * @param dataType  the type of data
     * @param step      the step of the requested data
     * @return          the value of the requested data field
     */
    public float getValue(DataTypes dataType, int step) {        
        switch (dataType) {
            case Z:
                return z.getData(step);
            case DZDT:
                return dzdt.getData(step);                
            case XI:
                return xi.getData(step);                
            case DXIDT:
                return dxidt.getData(step);                
            case ETA:
                return eta.getData(step);                   
            default:
                System.err.println("Error in getData(): invalid dataType");
                return Float.NaN;
        }
    }
    
    
    /**
     * Sets the value of a data field.
     * 
     * @param dataType
     * @param step
     * @param data 
     */
    private void setValue(DataTypes dataType, int step, float data) {        
        switch (dataType) {
            case Z:
                z.setData(step, data);
                break;
            case DZDT:
                dzdt.setData(step, data);
                break;
            case XI:
                xi.setData(step, data);
                break;
            case DXIDT:
                dxidt.setData(step, data);
                break;
            case ETA:
                eta.setData(step, data);
                break;
            default:
                System.err.println("Error in setData(): invalid dataType");
        }
    }   
    
    
    /**
     * Sets the grid size.
     * @param width     width of the grid
     * @param height    height of the grid
     */
    public void setGridSize(int width, int height){
        this.gridSize = new Dimension(width, height);
    }
    
    
    /**
     * Sets the x coordinate of this node.
     * @param x     new x coordinate
     */
    public void setX(int x) {
        this.x = x;
    }
    
    
    /**
     * Sets the y coordinate of this node.
     * @param y     new y coordinate
     */
    public void setY(int y) {
        this.y = y;
    }
    
    
    /**
     * Returns the current value of a data field of a neighbor.
     * 
     * Uses the neighborData buffer, therefore returns only the last queried value
     * for a neighbor. This method is based on local storage only,
     * and doesn't do any communication with other nodes.
     * 
     * @param n     the neighbor whose data is requested
     * @return      the value of the data field of the neighbor
     */
    private float getNeighborValue(Neighbors n) {
        return Float.intBitsToFloat(neighborData.get(n).get());
    }
        
    
    /**
     * Sets the current value of the data field of a neighbor.
     * 
     * Uses the neighborData buffer, and stores only the last queried value
     * for a particular neighbor. This method is used by NodeUDPClient thread
     * for storing the received neighbor data locally.
     * 
     * @param n     the neighbor whose data is to be set
     * @param data  the value to be set for the neighbor
     */
    public void setNeighborValue(Neighbors n, float data) {
        neighborData.put(n, new AtomicInteger(Float.floatToIntBits(data)));
    } 
    
    
    /**
     * Creates a new descriptor for a neighbor.
     * @param neighbor          the neighbor whose descriptor is to be created
     * @param nodeDescriptor    the new node descriptor
     */
    public void setNeighborDescriptor(Neighbors neighbor, NodeDescriptor nodeDescriptor) {
        neighborDescriptors.put(neighbor, nodeDescriptor);
    }    
}
