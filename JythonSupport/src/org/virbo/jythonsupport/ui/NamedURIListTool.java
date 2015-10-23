/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
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
    
    public String makeupName( List<String> names ) {
        int max= 0;
        for (String n : names) {
            if ( n.startsWith("ds" ) ) {
                try {
                    int j= Integer.parseInt(n.substring(2) );
                    max= Math.max(max,j);
                } catch ( NumberFormatException ex ) {
                }
            }
        }
        return "ds"+(max+1);
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
                    String newName= makeupName( ids );
                    ids.add(newName);
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
            final int ffi= fi;
            subDelete.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    //TODO: delete URI
                    Container parent= sub.getParent();
                    parent.remove(sub);
                    parent.validate();
                    uris.remove(ffi);
                    ids.remove(ffi);
                    refresh();
                }
            } );
            sub.add( subDelete, BorderLayout.EAST );
        }

        if ( fi>=0 ) {
            final DataSetSelector dss= new DataSetSelector();
            dss.setPlotItButtonVisible(false);
            dss.setValue( uris.get(fi) );
            try{
                dss.setRecent( DataSetSelector.getDefaultRecent() );
            } catch ( IllegalArgumentException ex ) {
                
            }
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
        JLabel c= new JLabel( "Parameter name:" );
        
        c.setAlignmentX( Component.LEFT_ALIGNMENT );
        p.add(  c );
        JTextField tf= new JTextField(currentName);
        tf.setAlignmentX( Component.LEFT_ALIGNMENT );
        p.add( tf );
        p.add( Box.createVerticalStrut( p.getFont().getSize() ) );
        DataSourceEditorPanel edit=null;
        try {
            edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel( DataSetURI.getURIValid( uris.get(fi) ) );
            if ( edit==null ) {
                logger.warning("can't get editor for #"+fi);
            } else {
                String uri= uris.get(fi);
                try {
                    if ( !edit.reject( uri ) ) {
                        edit.prepare( uri, null, new NullProgressMonitor() );
                        edit.setURI( uri );
                        JPanel editPanel= edit.getPanel();
                        editPanel.setAlignmentX( Component.LEFT_ALIGNMENT );
                        p.add( editPanel );
                    }
                } catch ( Exception ex ) {
                    ex.printStackTrace(); //TODO
                }
            }
            
        } catch (URISyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( scrollPane, p, "Rename parameter and dataset editor", JOptionPane.OK_CANCEL_OPTION ) ) {
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

    /**
     * return the jython code that gets these.
     * @return 
     */
    String getAsJython() {
        StringBuilder b= new StringBuilder();
        for ( int i=0; i<this.uris.size(); i++ ) {
            b.append( this.ids.get(i) ).append( "=" ).append( "getDataSet('").append( this.uris.get(i) ).append("')\n");
        }
        return b.toString();
    }

    /**
     * return the jython code that gets these, to prefix vap+inline:...
     * @return 
     */
    String getAsJythonInline() {
        StringBuilder b= new StringBuilder();
        for ( int i=0; i<this.uris.size(); i++ ) {
            b.append( this.ids.get(i) ).append( "=" ).append( "getDataSet('").append( this.uris.get(i) ).append("')&");
        }
        return b.toString();
    }
    
    /**
     * return null if nothing is selected, the URI otherwise.
     * @return null if nothing is selected, the URI otherwise.
     */
    public String selectDataId( String id ) {
        JPanel dsSelector= new JPanel();
        dsSelector.setLayout( new BoxLayout(dsSelector,BoxLayout.Y_AXIS ) );
        ButtonGroup bg= new ButtonGroup();
        JCheckBox[] butts= new JCheckBox[this.uris.size()];
        GridBagLayout layout= new GridBagLayout();
        dsSelector.setLayout(layout);
        GridBagConstraints c= new GridBagConstraints();
        for ( int i=0; i<this.uris.size(); i++ ) {
            JCheckBox cb= new JCheckBox( this.ids.get(i) );
            if ( this.ids.get(i).equals(id) ) cb.setSelected(true);
            butts[i]= cb;
            c.gridx= 1;
            c.gridy= i;
            dsSelector.add( cb, c );
            c.gridx= 2;
            c.anchor= GridBagConstraints.WEST;
            dsSelector.add( new JLabel( this.uris.get(i) ), c );
            bg.add(cb);
        }
        if ( JOptionPane.showConfirmDialog( this, dsSelector, "Select Variable", JOptionPane.OK_CANCEL_OPTION ) ==JOptionPane.OK_OPTION ) {
            for ( int i=0; i<this.uris.size(); i++ ) {
                if ( butts[i].isSelected() ) {
                    return this.ids.get(i);
                }
            }
            return null;
        } else {
            return null;
        }
        
    }
}
