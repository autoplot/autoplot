/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.Window;

/**
 * Launcher for PngWalkTool1.  The "1" in PngWalkTool1 was to indicate it was provisional.  Now it's official.
 * @author jbf
 */
public class PngWalkTool {
    
    public static PngWalkTool start( String template, final Window parent ) {
        PngWalkTool1.start(template, parent);
        return null;
    }

    public static void main( String[] args ) {
        PngWalkTool1.main(args);
    }

}
