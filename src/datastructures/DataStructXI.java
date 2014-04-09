
package datastructures;

import eniac.Node;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * This class implements a data structure for xi.
 * @see    DataStruct class
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class DataStructXI extends DataStruct {
    
    public static final int NUM_STEPS = Node.HOURS+1;
    
    /**
     * Class constructor.
     */
    public DataStructXI() {
        dataArray = new AtomicIntegerArray(NUM_STEPS);
    }          
}
