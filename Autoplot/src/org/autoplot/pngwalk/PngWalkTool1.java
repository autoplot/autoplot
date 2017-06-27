/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.Window;

/**
 * Launcher for PngWalkTool1.  The "1" in PngWalkTool1 was to indicate it was provisional.  Now it's official.
 * This is a half-hearted attempt to support Jython scripts that used the PngWalkTool1 name.  Please let us know if this
 * needs attention, at autoplot@groups.google.com
 * 
 * @author jbf
 */
public class PngWalkTool1 {
    
    public static PngWalkTool start( String template, final Window parent ) {
        return PngWalkTool.start(template, parent);
    }

    public static void main( String[] args ) {
        PngWalkTool.main(args);
    }

}
