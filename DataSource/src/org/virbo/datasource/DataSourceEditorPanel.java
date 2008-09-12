/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import javax.swing.JPanel;

/**
 * Interface for discovering a GUI editor for an URL.
 * @author jbf
 */
public interface DataSourceEditorPanel {
    public JPanel getPanel();
    public void setUrl( String url );
    public String getUrl();
}
