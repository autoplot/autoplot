package org.autoplot.datasource;

import java.util.List;
import javax.swing.JPanel;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Interface for discovering a GUI editor for an URL.
 * Note the correct order to use a GUI is:
 * <tt>
 *    reject( String uri ) is the URI close enough that we can create an editor for it?  Editors that never reject, "allow discovery"
 *      setExpertMode( boolean ) if available, then expert mode is supported and the options should be restricted to reliable options.
 *    prepare( String uri, Window parent, ProgressMonitor mon )  prepare the GUI, maybe by downloading resources, etc
 *    setURI( String uri ) set the URI for editing.  This is the oldest method and is a bit redundant.
 *    getPanel()           enter the GUI.
 *    getURI()             may be called at any time.  Note this should return a valid URI.  The intent here is that this could be called multiple times.
 * </tt>
 * Data Sources that support discovery will create a DataSourceEditorPanel with
 * no parameters, e.g. "vap+cdaweb:"
 * @author jbf
 */
public interface DataSourceEditorPanel {

    /**
     * reject the URI, perhaps because we aren't close enough to identify a resource.
     * For example, a CDF URI contains the name of the file but not the variable to plot,
     * so we need to enter the editor panel to complete the URI.
     * Leaving the editor should never result in a URI that would reject.
     * @param uri
     * @return true if the URI is not usable.
     * @throws java.lang.Exception
     */
    public boolean reject( String uri ) throws Exception;

    /**
     * load any needed resources.  Return false if cancel, true to proceed into the gui.
     * Throw a FileNotFoundException if needed resources is not found.
     * @param uri partially-completed URI
     * @param parent the parent GUI.
     * @param mon monitor to indicate slow process.
     * @return true to proceed, false if to cancel.
     */
    public boolean prepare( String uri, java.awt.Window parent, ProgressMonitor mon) throws Exception;

    /**
     * initialize the editor to edit this URI.  This may be incomplete, and the editor
     * should make it valid so getUri is valid.  Note also that the URI will be
     * be the same as in prepare.  If exceptions occur here, they must be re-thrown as
     * runtime exceptions, and they should be checked for in prepare().
     * @param uri
     */
    public void setURI( String uri );

    /**
     * mark the problems identified by the data source.  Note the reject method here doesn't provide the list,
     * but instead the DataSourceFactory.reject method.  This is because often data providers intentionally provide a
     * partial URI for the user to complete via the editor.
     * @param problems
     */
    public void markProblems( List<String> problems );

    /**
     * return the GUI to edit the URI.
     * @return
     */
    public JPanel getPanel();

    /**
     * return the URI configured by the editor.  This should be the fully-qualified
     * URI, with the "vap+&lt;ext&gt;:" scheme.
     *
     * @return
     */
    public String getURI();

}
