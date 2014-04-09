
package datastructures;

import eniac.Node;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * This class implements a data structure for z.
 * @see    DataStruct class
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class DataStructZ extends DataStruct {
    
    public static final int NUM_STEPS = Node.HOURS+1;
    
    /**
     * Class constructor.
     */
    public DataStructZ() {
        dataArray = new AtomicIntegerArray(NUM_STEPS);
    }          
}
