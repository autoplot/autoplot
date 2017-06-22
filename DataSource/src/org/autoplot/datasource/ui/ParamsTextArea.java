/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource.ui;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import org.das2.datum.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSourceFactory;

/**
 * Attempt to make a general-purpose way to make a GUI for a data
 * source.  This should probably be evaluated, because there's another
 * automatic gui-creator that is more effective.
 * 
 * @author jbf
 */
public class ParamsTextArea extends JTextArea {

    private static final Logger logger= LoggerManager.getLogger("apdss.gui");
    
    DataSourceFactory dsf= null;
    List<String> excludeParams= new ArrayList();

    JPopupMenu popup;

    public ParamsTextArea() {
        super();
        
        addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

        });
    }
    
    public void showPopup( MouseEvent e ) {
        final int pos= this.viewToModel( e.getPoint() );
        this.setCaretPosition(pos);
        showPopup(e.getX(),e.getY());

	}
	
	/**
	 * show the completions popup at the given point.
	 * @param x in the component coordinates.
	 * @param y in the component coordinates.
	 */
    public void showPopup( int x, int y ) {
        try {
			final int pos= this.getCaretPosition();
			
            int row= this.getLineOfOffset(pos);
            int linePos= this.getLineStartOffset(row);
            int col= pos - linePos;
            String line= this.getText( linePos, col );

            popup = new JPopupMenu();

            CompletionContext cc = new CompletionContext();

            if ( line.trim().endsWith("=") ) {
                cc.context = CompletionContext.CONTEXT_PARAMETER_VALUE;
                cc.completable= "?"+line;
                cc.surl= cc.completable;
                cc.completablepos = cc.completable.length();
            } else {
                cc.context = CompletionContext.CONTEXT_PARAMETER_NAME;
                cc.completable= "?"+line;
            }
            
            List<CompletionContext> ccs = dsf.getCompletions(cc, new NullProgressMonitor());
            for ( int i=0; i<ccs.size(); i++ ) {
                final CompletionContext acc= ccs.get(i);
                JMenuItem mi= new JMenuItem( new AbstractAction( acc.label) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);
                        insert( acc.completable, pos );
                    }
                } );
                mi.setToolTipText(acc.doc);
                popup.add( mi );
            }
            popup.show(this, x,y  );
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

     public Map<String,String> getParams() {
        LinkedHashMap<String,String> map= new LinkedHashMap();
        String readerParams= getText();
        String[] ss= readerParams.split("\n");
        for ( int i=0; i<ss.length; i++ ) {
            String s= ss[i].trim();
            if ( s.length()==0 ) continue;
            String[] ss2= s.split("=",-2);
            if ( ss2.length==1 ) {
                map.put( ss2[0],"" );
            } else {
                map.put( ss2[0], ss2[1] );
            }
        }
        return map;
     }

     public void setParams( Map<String,String> params ) {
        StringBuilder paramsStr= new StringBuilder();
        for ( Entry<String,String> e: params.entrySet() ) {
            paramsStr.append(e.getKey()).append("=").append(e.getValue()).append("\n");
        }
        setText(paramsStr.toString());
     }

     /**
      * set the data source factory that is used to generate parameter lists
      * from the completion context.
      *
      * @param factory
      * @param excludeParams
      */
     public void setFactory( DataSourceFactory factory, List<String> excludeParams ) {
         this.dsf= factory;
     }

}
