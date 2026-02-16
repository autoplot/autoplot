
package org.autoplot.scriptconsole;

import java.io.File;
import java.io.IOException;
import org.autoplot.jythonsupport.ui.EditorAnnotationsSupport;

/**
 * Provide backward-compatibility to old scripts like 
 * https://github.com/autoplot/dev/blob/master/demos/tools/editor/commit.jy
 * 
 * This should be deleted after 2025.
 * @author jbf
 */
public class ScriptPanelSupport {
    /**
     * @deprecated 
     * @see AppScriptPanelSupport#markChanges(org.autoplot.jythonsupport.ui.EditorAnnotationsSupport, java.io.File) 
     * @param support
     * @param fln
     * @return
     * @throws IOException
     * @throws InterruptedException 
     */
    public static int markChanges( EditorAnnotationsSupport support, File fln ) throws IOException, InterruptedException {
        return AppScriptPanelSupport.markChanges(support, fln);
    }
}
