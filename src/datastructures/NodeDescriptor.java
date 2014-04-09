
package datastructures;

import java.net.InetAddress;

/**
 * This class implements a node descriptor.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class NodeDescriptor {
    
    public final InetAddress address;   
    public final int port;

    /**
     * Class constructor.
     * @param address   address of the neighbor node
     * @param port      listening port of the neighbor node
     */
    public NodeDescriptor(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }
}
