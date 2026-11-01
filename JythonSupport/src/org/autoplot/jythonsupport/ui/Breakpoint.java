
package org.autoplot.jythonsupport.ui;

/**
 *
 * @author jbf
 */
public class Breakpoint implements Comparable {
    
    public Breakpoint(int line) {
        this.line = line;
    }

    public Breakpoint(String filename, int line) {
        this.filename = filename;
        this.line = line;
    }
    int line;
    String filename = null; // This will need more work, for now one file is assumed.
    boolean enabled = true;
    String expr = "";

    @Override
    public int compareTo(Object o) {
        Breakpoint obp = (Breakpoint) o;
        return obp.line - this.line;
    }
    
}
