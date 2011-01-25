/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import javax.swing.JPanel;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Interface for discovering a GUI editor for an URL.
 * Note the correct order to use a GUI is:
 *    reject( String uri ) is the URI close enough that we can create an editor for it?  Editors that never reject "allow discovery"
 *    prepare( String uri, Window parent, ProgressMonitor mon )  prepare the GUI, maybe by downloading resources, etc
 *    setURI( String uri ) set the URI for editing.  This is the oldest method and is a bit redundant.
 *    getPanel()           enter the GUI.
 *    getURI()             may be called at any time.  Note this should return a valid URI.  The intent here is that this could be called multiple times.
 * @author jbf
 */
public interface DataSourceEditorPanel {
    public JPanel getPanel();

    /**
     * initialize the editor to edit this URI.  This may be incomplete, and the editor
     * should make it valid so getUri is valid.
     * @param url
     */
    public void setURI( String uri );

    /**
     * return the URI configured by the editor.  This should be the fully-qualified
     * URI, with the "vap+<ext>:" scheme.
     *
     * @return
     */
    public String getURI();

    /**
     * load any needed resources.  Return false if cancel, true to proceed
     * into gui.
     * @param uri partially-completed URI
     * @return true to proceed, false if to cancel.
     */
    public boolean prepare( String uri, java.awt.Window parent, ProgressMonitor mon) throws Exception;

    /**
     * reject the URI, perhaps because we aren't close enough to identify a resource.
     * (e.g. folder containing cdf's is identified, but not the cdf, so use
     * filesystem completion instead.)
     * @param uri
     * @return
     */
    public boolean reject( String uri ) throws Exception;
}
