/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.DataSourceEditorPanelUtil;
import org.virbo.filters.FiltersChainPanel;

/**
 * GUI for creating a list of URIs.
 * @author jbf
 */
public class NamedURIListTool extends JPanel {
    
    private static final Logger logger= LoggerManager.getLogger("jython.dashup");
    private static final String CLASS_NAME = NamedURIListTool.class.getName();    
    
    JScrollPane scrollPane;
    List<String> uris=null;
    List<String> ids=null;
    
    public NamedURIListTool() {
        scrollPane = new javax.swing.JScrollPane();
        
        ids= Collections.emptyList();
        uris= Collections.emptyList();
        
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));
        add(scrollPane);
        refresh();
    }
    
    final public void refresh() {
        JPanel content= new JPanel();
        BoxLayout lo= new BoxLayout( content, BoxLayout.Y_AXIS );
        content.setLayout( lo );

        for ( int i=0; i<uris.size(); i++ ) {
            content.add( onePanel(i) );
        }
        content.add( onePanel(-1) );
        scrollPane.setViewportView(content);
        
    }
    
    public String[] getUris() {
        String[] result= new String[0];
        return result;
    }
    
    /**
     * return the panel with the add and remove icons.
     * @param fi the position 
     * @return one panel  ( +  panel GUI  - )
     */
    private JPanel onePanel( final int fi ) {
        logger.entering( CLASS_NAME, "onePanel", fi );
        final JPanel sub= new JPanel( new BorderLayout() );
        
        Dimension limit= new Dimension(100,24);
        
        if ( fi>=0 ) {
            JButton name= new JButton( ids.get(fi) + "=" );
            name.setMaximumSize( limit );
            name.setPreferredSize( limit );
            name.setToolTipText( "press to rename " );
            name.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);                    
                    rename(fi);
                }
            } );
            sub.add( name, BorderLayout.WEST );
        } else {
        
           JButton subAdd= new JButton("");
           subAdd.setIcon( new ImageIcon( FiltersChainPanel.class.getResource("/resources/add.png") ) );
           subAdd.setMaximumSize( limit );
           subAdd.setPreferredSize( limit );            
           subAdd.setToolTipText( "add new URI" );        
           subAdd.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    List<String> ids= new ArrayList<String>(NamedURIListTool.this.ids);
                    List<String> uris= new ArrayList<String>(NamedURIListTool.this.uris);
                    ids.add("ds1");
                    uris.add("");
                    setIds(ids);
                    setUris(uris);
                }
            } );
           
            sub.add( subAdd, BorderLayout.WEST );
        }

        if ( fi>=0 ) {
            JButton subDelete= new JButton("");
            subDelete.setIcon( new ImageIcon( FiltersChainPanel.class.getResource("/resources/subtract.png") ) );
            subDelete.setMaximumSize( limit );
            subDelete.setPreferredSize( limit );
            subDelete.setToolTipText( "remove uri " );
            subDelete.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    //TODO: delete URI
                    Container parent= sub.getParent();
                    parent.remove(sub);
                    parent.validate();
                }
            } );
            sub.add( subDelete, BorderLayout.EAST );
        }

        if ( fi>=0 ) {
            final DataSetSelector dss= new DataSetSelector();
            dss.setPlotItButtonVisible(false);
            dss.setValue( uris.get(fi) );
            dss.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    uris.set( fi,dss.getValue());
                }
            });
            sub.add( dss, BorderLayout.CENTER );

        } else {
            final JLabel tf= new JLabel();
            tf.setText("<html><i>&nbsp;(click to add)</i></html>");
            sub.add( tf, BorderLayout.CENTER );

        }

        Dimension maximumSize = sub.getPreferredSize();
        maximumSize.width = Integer.MAX_VALUE;
        sub.setMaximumSize(maximumSize);

        return sub;
    }
    
    private void rename( int fi ) {
        String currentName= ids.get(fi);
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
        JTextField tf= new JTextField(currentName);
        p.add( tf );
        DataSourceEditorPanel edit=null;
        try {
            edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel( DataSetURI.getURIValid( uris.get(fi) ) );
            try {
                edit.prepare( uris.get(fi), null, new NullProgressMonitor() );
                p.add( edit.getPanel() );
            } catch ( Exception ex ) {
                ex.printStackTrace(); //TODO
            }
        } catch (URISyntaxException ex) {
            Logger.getLogger(NamedURIListTool.class.getName()).log(Level.SEVERE, null, ex);
        }
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( scrollPane, p ) ) {
            ids.set( fi,tf.getText() );
            if ( edit!=null ) {
                uris.set( fi, edit.getURI() );
            }
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    refresh();
                }
            });
            
        }
    }
        
    public void setIds( List<String> ids ) {
        this.ids= new ArrayList<String>(ids);
        if ( uris.size()==ids.size() ) refresh();
    }
    
    public void setUris( List<String> uris ) {
        this.uris= new ArrayList<String>(uris);
        if ( uris.size()==ids.size() ) refresh();
    }

}
