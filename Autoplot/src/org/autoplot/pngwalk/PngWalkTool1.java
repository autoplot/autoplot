/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.Window;

/**
 * Launcher for PngWalkTool to support old name in legacy codes.  The "1" in 
 * PngWalkTool1 was to indicate it was provisional.  Now it's official.
 * @deprecated see {@link org.autoplot.pngwalk.PngWalkTool}
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
