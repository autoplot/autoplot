/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import javax.swing.JPanel;

/**
 * similar to DataSourceEditorPanel, this one is arguments for output.
 * @author jbf
 */
public interface DataSourceFormatEditorPanel {
    /**
     * return the editor
     * @return
     */
    JPanel getPanel();

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


}
