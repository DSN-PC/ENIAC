
package datastructures;

import eniac.Node;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * This class implements a data structure for dz/dt.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class DataStructDZDT extends DataStruct {
    
    public static final int NUM_STEPS = Node.NUM_ITERATIONS+1;
    private boolean denyNextStepRequests;
    
    /**
     * Class constructor.
     */    
    public DataStructDZDT() {
        dataArray = new AtomicIntegerArray(NUM_STEPS);
    }           
    
    /**
     * Gets the data value in a given step.
     * @param step  the step of the requested data value
     * @return      the data value in the given step
     *              (or NaN if the requested data is not available yet)
     */
    @Override
    public float getData(int step) {
        /********************************************************
         * Return NaN if:                                       *
         *      1. the requested data is unavailable.           *
         *      2. the previous Poisson iteration has finished, *
         *         but the next one hasn't started yet,         *
         *         and someone asks for dzdt[0] (next step).    *
         * This is for synchronization purposes.                *
         ********************************************************/
        if ( (step > getStep()) || (step==0 && denyNextStepRequests) )
            return Float.NaN;            
        return Float.intBitsToFloat(dataArray.get(step));            
    }        
    
    
    /**
     * Denies requests for data which belong to the next step.
     */
    public void denyNextStepRequests() {
        denyNextStepRequests = true;
    }
    
    
    /**
     * Allows requests for data which belong to the next step.
     */
    public void allowNextStepRequests() {
        denyNextStepRequests = false;
    }
}
