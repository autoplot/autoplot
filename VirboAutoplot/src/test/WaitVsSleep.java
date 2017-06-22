
package test;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.autoplot.AutoplotUI;

/**
 * demonstrate the difference between wait and sleep
 * @author faden@cottagesystems.com
 */
public class WaitVsSleep {
    static double d= 0;
    
    public static void sleepDemo() {
        Runnable run= new Runnable() {
            public void run() {
                while ( true ) {
                    try {
                        Thread.sleep(10);
                        if ( d==1 ) {
                            break;
                        }
                    } catch (InterruptedException ex) {
                        Logger.getLogger(WaitVsSleep.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        };
        new Thread( run,"sleep" ).start();
    }
    
    
    public static void waitDemo() {
        Runnable run= new Runnable() {
            public void run() {
                synchronized ( WaitVsSleep.class ) {
                    while ( true ) {
                        try {
                            WaitVsSleep.class.wait(10);
                            if ( d==1 ) {
                                break;
                            }
                        } catch (InterruptedException ex) {
                            Logger.getLogger(WaitVsSleep.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        };
        new Thread( run,"sleep" ).start();
    }
    
        
    public static void main( String[] args ) {
        waitDemo();
        //sleepDemo();
        String pid= AutoplotUI.getProcessId("???");
        JOptionPane.showMessageDialog( null, "Click Okay to stop loop PID="+pid );
        d=1;
    }
    
}
