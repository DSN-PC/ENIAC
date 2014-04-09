
package datastructures;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * This class is an abstract class for data structures of z, dzdt, xi, dxidt and eta.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public abstract class DataStruct {
    protected AtomicInteger step;
    protected AtomicIntegerArray dataArray;
    protected final Object lock;

    /**
     * Class constructor.
     */
    public DataStruct() {
        step = new AtomicInteger(-1);
        lock = new Object();        
    }    

    /**
     * Returns the current step of the data structure.
     * @return the current step of the data structure
     */
    public int getStep() {
        synchronized (lock) {
            return step.get();
        }
    }
    
    /**
     * Sets the current step of the data structure to a given value.
     * This method is usually called by <code>setData()</code>.
     * @param step  the new step value
     */
    public void setStep(int step) {
        synchronized (lock) {
            this.step.set(step);
        }
    }

    /**
     * Gets the data value in a given step.
     * @param step  the step of the requested data value
     * @return      the data value in the given step
     *              (or NaN if the requested data is not available yet)
     */
    public float getData(int step) {
        /* If the requested data is unavailable, return NaN */
        if (step > getStep())
            return Float.NaN;            
        return Float.intBitsToFloat(dataArray.get(step));            
    }    

    /**
     * Sets the data value in a given step.
     * @param step  the step of the data value
     * @param data  the new value
     */
    public void setData(int step, float data) {
        synchronized (lock) {            
            dataArray.set(step, Float.floatToIntBits(data));
            this.step.set(step);
        }
    }        
}
