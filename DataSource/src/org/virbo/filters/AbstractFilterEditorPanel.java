/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.filters;

import java.util.logging.Logger;
import javax.swing.JPanel;
import org.das2.util.LoggerManager;
import org.virbo.dataset.QDataSet;

/**
 * Implements the typical filter, where we don't care about the input data and
 * the filter itself implements the GUI.
 * @author jbf
 */
public abstract class AbstractFilterEditorPanel extends JPanel implements FilterEditorPanel {
    
    protected static final Logger logger= LoggerManager.getLogger("apdss.filters");
    
    @Override
    public abstract void setFilter( String filter );

    @Override
    public abstract String getFilter( );

    @Override
    public void setInput( QDataSet ds ) {
        // do nothing, ignore input
    }
    
    @Override
    public JPanel getPanel() {
        return this;
    }
}
