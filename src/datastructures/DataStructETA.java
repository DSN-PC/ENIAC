
package datastructures;

import eniac.Node;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * This class implements a data structure for eta.
 * @see    DataStruct class
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class DataStructETA extends DataStruct {
    
    public static final int NUM_STEPS = Node.HOURS;
    
    /**
     * Class constructor.
     */
    public DataStructETA() {
        dataArray = new AtomicIntegerArray(NUM_STEPS);
    }          
}
