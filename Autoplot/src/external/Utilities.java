
package external;

/**
 * I'm having a heck of a time getting the new ScriptContext to import.
 * Let's try a simpler class.
 * @author jbf
 */
public class Utilities {
    
    public static Utilities getInstance() {
        return new Utilities();
    }
    
    public int getRandom() {
        return (int)( Math.random()*100 );
    }
    
    public void log(String msg) {
        System.err.println(msg);
    }
}
