package gui;

import eniac.Main;
import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * This class displays a countdown panel waiting for real nodes to connect.
 * @author Vas Ádám (vas.adam@inbox.com)
 */
public class CountdownPanel extends JFrame {
    
    private final long timeout;       // milliseconds
    private JPanel textPanel1, textPanel2;
    private JLabel textLabel1, textLabel2;
    private boolean complete;
    
    /**
     * Class constructor.
     * @param timeout   the waiting timeout in milliseconds
     */
    
    public CountdownPanel(long timeout) {   
        this.timeout = timeout;
        this.init();
    }
    
    /**
     * Initializes GUI elements.
     */
    private void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);
        textPanel1 = new JPanel();        
        textPanel2 = new JPanel(); 
        
        textLabel1 = new JLabel("Waiting for real nodes: " + timeout/1000 + " s");
        textLabel2 = new JLabel("Number of real nodes: " + Main.numberOfRealNodes);
        
        textPanel1.add(textLabel1);        
        textPanel2.add(textLabel2);
        
        add(textPanel1, BorderLayout.NORTH);        
        add(textPanel2, BorderLayout.SOUTH);
        pack();       
    }


    public void startCountdown() {
        
        long timeElapsed;
        
        /* Wait for timer to expire. timer is initialized at MainServer */
        while( (timeElapsed=(System.currentTimeMillis()-Main.timer)-999) < timeout ) {
            textLabel1.setText("Waiting for real nodes: " + (long)((timeout-timeElapsed)/1000) + " s");
            textLabel2.setText("Number of real nodes: " + Main.numberOfRealNodes);
                    
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(CountdownPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Returns a boolean value indicating whether data input has finished.
     * @return  a boolean value indicating whether data input has finished 
     */    
    public boolean isComplete() {
        return complete;
    }
}
