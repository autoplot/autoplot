
package org.autoplot.jythonsupport.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.das2.datum.DatumRange;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.DataSourceEditorPanelUtil;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.das2.qds.filters.FiltersChainPanel;

/**
 * GUI for creating a list of URIs and variables associated with them.
 * @author jbf
 */
public class NamedURIListTool extends JPanel {
    
    private static final Logger logger= LoggerManager.getLogger("jython.dashup");
    private static final String CLASS_NAME = NamedURIListTool.class.getName();    
    
    protected static final String PROP_URIS= "uris";
    
    /**
     * see code use, use is not typical.
     */
    protected static final String PROP_ID="id";
        
    JScrollPane scrollPane;
    
    /**
     * list of URIs.
     */
    List<String> uris=null;
    
    /**
     * list of Java identifiers, one for each URI.
     */
    List<String> ids=null;
    
    List<Boolean> isAuto= null;

    DataMashUp dataMashUp;
    
    public NamedURIListTool( ) {
        scrollPane = new javax.swing.JScrollPane();
        
        ids= Collections.emptyList();
        uris= Collections.emptyList();
        isAuto= Collections.emptyList();
        
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));
        add(scrollPane);
        refresh();
    }
    
    /**
     * rebuild the GUI based on the uris.
     */
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
    
    /**
     * return the uris.
     * @return the uris.
     */
    public String[] getUris() {
        assert ids.size()==uris.size();
        return uris.toArray( new String[uris.size()] );
    }
    
    /**
     * return the id for each URI.
     * @return the ids
     */
    public String[] getIds() {
        assert ids.size()==uris.size();
        return ids.toArray( new String[ids.size()] );
    }
    
    /**
     * return the URI for the name, something that when resolved will result in
     * the dataset.
     * @param name
     * @return the URI or null if the name is not found.
     */
    public String getUriForId( String name ) {
        String suri=null;
        for ( int i=0; i<ids.size(); i++ ) {
            if ( ids.get(i).equals(name) ) {
                suri= uris.get(i);
            }
        }
        if ( suri!=null ) {
            if ( this.timeRange!=null ) {
                String stimeRange= this.timeRange.toString().replaceAll("\\ ","+");
                suri= "vap+inline:getDataSet(\'"+suri+"\',\'"+stimeRange+"\')";
            }
            return suri;
        } else {
            return null;
        }
    }
    
    /**
     * set the DataMashUp tool so we can handle variable rename.
     * @param dmu 
     */
    public void setDataMashUp( DataMashUp dmu ) {
        this.dataMashUp= dmu;
    }
    
    /** make up a name that does not exist in the list of names.
     * 
     * @param names names that must be unique with the new name
     * @return the new name
     */
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
    
    private void bindTimeRange( DataSetSelector dss ) {
        AutoBinding binding;
        binding = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, this, BeanProperty.create("timeRange"), dss, BeanProperty.create("timeRange"));
        binding.bind();
    }
    
    private DatumRange timeRange;

    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(DatumRange timeRange) {
        DatumRange oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
        firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);
    }
    
    private boolean showIds = true;

    public static final String PROP_SHOWIDS = "showIds";

    public boolean isShowIds() {
        return showIds;
    }

    public void setShowIds(boolean showIds) {
        boolean oldShowIds = this.showIds;
        this.showIds = showIds;
        firePropertyChange(PROP_SHOWIDS, oldShowIds, showIds);
    }
    

    /**
     * return the panel with the add and remove icons.
     * @param fi the position 
     * @return one panel  ( +  panel GUI  - )
     */
    private JPanel onePanel( final int fi ) {
        logger.entering( CLASS_NAME, "onePanel", fi );
        final JPanel sub= new JPanel( new BorderLayout() );
        sub.setName("sub"+fi);
        
        Dimension limit= new Dimension(100,24);
        if ( !showIds ) {
            limit= new Dimension(24,24);
        }
        Dimension dim= new Dimension(24,24);
        
        if ( fi>=0 ) {
            if ( showIds ) {
                JButton name= new JButton( ids.get(fi) + "=" );
                name.setMaximumSize( limit );
                name.setPreferredSize( limit );
                name.setToolTipText( "press to rename " );
                name.addActionListener(new ActionListener() {
                 @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);                    
                        String oldName= ids.get(fi);
                        rename(fi);
                        String newName= ids.get(fi);
                        firePropertyChange( PROP_ID + "Name_"+fi, oldName, newName );
                    }
                } );
                sub.add( name, BorderLayout.WEST );
            } else {
                JButton subAdd= new JButton( new ImageIcon( FiltersChainPanel.class.getResource("/resources/add.png") ) );
                subAdd.setMaximumSize( limit );
                subAdd.setPreferredSize( limit );            
                subAdd.setToolTipText( "add new URI" );        
                subAdd.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);
                        List<String> ids= new ArrayList<>(NamedURIListTool.this.ids);
                        List<String> uris= new ArrayList<>(NamedURIListTool.this.uris);
                        String newName= makeupName( ids );
                        ids.add(fi,newName);
                        uris.add(fi,"");
                        setIds(ids);
                        setUris(uris);
                    }
                } );
           
                sub.add( subAdd, BorderLayout.WEST );
                
            }
        } else {
        
           JButton subAdd= new JButton( new ImageIcon( FiltersChainPanel.class.getResource("/resources/add.png") ) );
           subAdd.setMaximumSize( limit );
           subAdd.setPreferredSize( limit );            
           subAdd.setToolTipText( "add new URI" );        
           subAdd.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    List<String> ids= new ArrayList<>(NamedURIListTool.this.ids);
                    List<String> uris= new ArrayList<>(NamedURIListTool.this.uris);
                    List<Boolean> isAuto= new ArrayList<>(NamedURIListTool.this.isAuto);
                    String newName= makeupName( ids );
                    ids.add(newName);
                    uris.add("");
                    isAuto.add(true);
                    setIds(ids);
                    setUris(uris);
                    setIsAuto(isAuto);
                }
            } );
           
            sub.add( subAdd, BorderLayout.WEST );
        }

        if ( fi>=0 ) {
            JButton subDelete= new JButton( new ImageIcon( FiltersChainPanel.class.getResource("/resources/subtract.png") ) );
            subDelete.setMaximumSize( limit );
            subDelete.setPreferredSize( dim );
            
            subDelete.setToolTipText( "remove uri " );
            final int ffi= fi;
            subDelete.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    //TODO: delete URI
                    Container parent= sub.getParent();
                    parent.remove(sub);
                    parent.validate();
                    uris.remove(ffi);
                    ids.remove(ffi);
                    isAuto.remove(ffi);
                    refresh();
                }
            } );
            JPanel p= new JPanel();
            p.setLayout( new BoxLayout( p, BoxLayout.X_AXIS ) );
            p.add( Box.createHorizontalStrut(11) );
            p.add( subDelete );
            sub.add( p, BorderLayout.EAST );
        }

        if ( fi>=0 ) {
            final DataSetSelector dss= new DataSetSelector();
            dss.setPlotItButtonVisible(false);
            dss.setPlayButton(false);
            dss.setValue( uris.get(fi) );
            bindTimeRange(dss);
            
            try{
                List<String> recent= DataSetSelector.getDefaultRecent();
                List<String> recentSansInline= new ArrayList<>();
                for ( String s: recent ) {
                    if ( s.startsWith("vap+inline:") ) {
                        if ( s.contains("getDataSet") ) {
                            logger.log(Level.FINEST, "skipping {0}", s);
                            continue;  // don't include mash-ups in the list of things to mash-up.
                        }
                    }
                    URISplit split= URISplit.parse(s);
                    if ( ".jy".equals(split.ext) ) {
                        logger.log(Level.FINEST, "skipping {0}", s);
                        continue;
                    }
                    if ( !".vap".equals(split.ext) ) {
                        recentSansInline.add(s);
                    }
                }
                dss.setRecent( recentSansInline );
            } catch ( IllegalArgumentException ex ) {
                
            }
            dss.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String newName= null;
                    String currentName= null;
                    if ( uris.get(fi).trim().length()==0 ) {
                        List<String> nids= new ArrayList<>(ids);
                        List<String> nuris= new ArrayList<>(uris);
                        nids.remove(fi);
                        nuris.remove(fi);
                        newName= DataSourceUtil.guessNameFor( dss.getValue(), nuris, nids );
                        if ( isValidIdentifier(newName) ) { 
                            currentName= ids.get( fi );
                        }
                    }
                    String uri= dss.getValue();
                    String uri2= DataSetURI.blurTsbUri(uri);
                    uris.set( fi,uri2 );
                    if ( !uri.equals(uri2) ) {
                        dss.setValue(uri2);
                    }
                    if (dataMashUp!=null ) dataMashUp.refresh();
                    if ( currentName!=null && newName!=null ) {
                        doVariableRename( fi, currentName, newName );
                    }
                }
            });
            dss.getEditor().addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    uris.set( fi,dss.getValue());
                    if (dataMashUp!=null ) dataMashUp.refresh();
                }
            }) ;
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
    
    private void doVariableRename( int fi, String oldName, String newName ) {
        ids.set( fi, newName );
        refresh();
        if ( dataMashUp!=null ) dataMashUp.rename( oldName, newName );
    }
    
    /**
     * returns true if the string is a valid Java (and Python) identifier.
     * @param n
     * @return 
     */
    private boolean isValidIdentifier( String n ) {
        boolean s= n.length()>0 && Character.isJavaIdentifierStart(n.charAt(0) );
        for ( int i=1; s && i<n.length(); i++ ) {
            s= s && Character.isJavaIdentifierPart(n.charAt(i) );
        }
        return s;
    }
    
    private void rename( int fi ) {
        String currentName= ids.get(fi);
        boolean autoName= isAuto.get(fi);
        
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
        //JLabel c= new JLabel( "Parameter name (a name with no spaces, made of letters, numbers and underscores):" );
        
        //c.setAlignmentX( Component.LEFT_ALIGNMENT );
        //p.add( c );

        final JCheckBox cb= new JCheckBox("Manually set parameter name (a name with no spaces, made of letters, numbers and underscores):");
        cb.setToolTipText("checked indicates variable name will be picked automatically");
        cb.setSelected(!autoName);
        p.add( cb );
        
        int em=  p.getFont().getSize();
        JPanel p1= new JPanel();
        p1.setLayout( new BoxLayout( p1, BoxLayout.X_AXIS ) );
        final JTextField tf= new JTextField(currentName);
        tf.setMaximumSize( new Dimension( em*50, em*2 ) );
        tf.setPreferredSize( new Dimension( em*50, em*2 ) );
        tf.setEnabled( cb.isSelected() );
        cb.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tf.setEnabled( cb.isSelected() ); 
            }
        });
        p1.add( Box.createHorizontalStrut( 3*em ) );
        p1.add( tf );
        p1.add( Box.createGlue() );
        p1.setAlignmentX( Component.LEFT_ALIGNMENT );
        
        p.add( p1 );
        p.add( Box.createVerticalStrut( em ) );
        p.add( Box.createGlue() );
        
        DataSourceEditorPanel edit=null;
        try {
            String uri= uris.get(fi);
            edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel( p, uri );
        } catch ( IllegalArgumentException ex ) {
            logger.log(Level.SEVERE, "can't get editor for #{0}", fi);
        }
        String title= edit!=null ? "Rename parameter and dataset editor" : "Rename parameter"; // this is so the position and size are remembered separately.
        while ( JOptionPane.OK_OPTION==WindowManager.showConfirmDialog( scrollPane, p, title, JOptionPane.OK_CANCEL_OPTION ) ) {
            String newName= tf.getText();
            if ( !cb.isSelected() && edit!=null ) {
                newName= DataSourceUtil.guessNameFor(edit.getURI(),uris,ids);
            }
            if ( isValidIdentifier(newName) ) {
                doVariableRename( fi, currentName, newName );
                isAuto.set( fi, !cb.isSelected() );
                if ( edit!=null ) {
                    uris.set( fi, edit.getURI() );
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
                break;
            }
        }
    }

    public void setIds( List<String> ids ) {
        this.ids= new ArrayList<>(ids);
        if ( uris.size()==ids.size() ) refresh();
    }
    
    public void setUris( List<String> uris ) {
        this.uris= new ArrayList<>(uris);
        if ( uris.size()==ids.size() ) refresh();
    }
    
    public void setIsAuto( List<Boolean> isAuto ) {
        this.isAuto= new ArrayList<>(isAuto);
        if ( isAuto.size()==isAuto.size() ) refresh();
    }
    
    /**
     * return the Jython code that gets these.
     * @return the Jython code that gets these.
     */
    protected String getAsJython() {
        StringBuilder b= new StringBuilder();
        for ( int i=0; i<this.uris.size(); i++ ) {
            b.append( this.ids.get(i) ).append( "=" ).append( "getDataSet('").append( this.uris.get(i) ).append("')\n");
        }
        return b.toString();
    }

    /**
     * return the Jython code that gets these, to prefix vap+inline:...
     * Note, IDs with empty URIs are ignored.
     * @return jython code for loading each URI into a variable.
     */
    protected String getAsJythonInline() {
        StringBuilder b= new StringBuilder();
        for ( int i=0; i<this.uris.size(); i++ ) {
            String uri= this.uris.get(i);
            if ( uri.trim().length()>0 ) {
                String s= this.uris.get(i);
                if ( s.contains("'") ) {
                    logger.info("removing single quotes from URI, hope that doesn't break anything.");
                    b.append( this.ids.get(i) ).append( "=" ).append( "getDataSet('").append( s.replaceAll("'","") ).append("\')&");
                } else {
                    b.append( this.ids.get(i) ).append( "=" ).append( "getDataSet('").append( s ).append("')&");
                }
            }
        }
        return b.toString();
    }
    
    /**
     * return null if nothing is selected, the URI otherwise.
     * @param id the current selection, which can be an identifier, QDataSet.UNITS, or 10.0.
     * @return null if nothing is selected, the URI otherwise.
     */
    public String selectDataId( String id ) {
        JPanel dsSelector1= new JPanel();
        JPanel dsSelector= new JPanel();
        dsSelector1.add( new JScrollPane( dsSelector, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS ) );
        dsSelector1.setPreferredSize( new Dimension(600,500 ) );
        dsSelector1.setMaximumSize( new Dimension(600,500 ) );
        
        dsSelector.setLayout( new BoxLayout(dsSelector,BoxLayout.Y_AXIS ) );
        ButtonGroup bg= new ButtonGroup();
        JCheckBox[] butts= new JCheckBox[this.uris.size()+2];
        GridBagLayout layout= new GridBagLayout();
        dsSelector.setLayout(layout);
        GridBagConstraints c= new GridBagConstraints();
        c.anchor= GridBagConstraints.WEST;
        c.weighty= 0.0;
        int i;
        for ( i=0; i<this.uris.size(); i++ ) {
            final JCheckBox cb= new JCheckBox( this.ids.get(i) );
            if ( this.ids.get(i).equals(id) ) cb.setSelected(true);
            butts[i]= cb;
            c.gridy= i;
            c.gridx= 1;
            c.weightx= 0.0;
            dsSelector.add( cb, c );
            
            JLabel label=  new JLabel( this.uris.get(i) );
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    cb.setSelected(true);
                }
            });
            c.gridx= 2;            
            c.weightx= 1.0;
            dsSelector.add( label, c );
            bg.add(cb);
        }
        
        // ------------------------------------------------
        
        final JCheckBox cb= new JCheckBox( "Literal: " );
        final int ilit= i++;
        butts[ilit]= cb;
        cb.setToolTipText("enter a literal like 0.0");
        c.gridy= this.uris.size();
        c.gridx= 1;
        c.weightx= 0.0;
        dsSelector.add( cb, c );
        bg.add(cb);
        
        final JTextField literalTF= new JTextField("0.0");
        literalTF.setMinimumSize( new Dimension(120,literalTF.getFont().getSize()*2) );
        literalTF.setPreferredSize( new Dimension(120,literalTF.getFont().getSize()*2) );
        
        c.gridx= 2;
        c.weightx= 1.0;
        literalTF.addFocusListener(new FocusListener() {
            String orig=null;
            @Override
            public void focusGained(FocusEvent e) {
                orig= literalTF.getText();
            }
            @Override
            public void focusLost(FocusEvent e) {
                if ( !literalTF.getText().equals(orig) ) {
                    cb.setSelected(true);
                }
            }
        } );
        literalTF.addKeyListener( new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                cb.setSelected(true);
            }
        });
        dsSelector.add( literalTF, c );
        
        // ------------------------------------------------
        
        final JCheckBox cb2= new JCheckBox( "Expression: " );
        final int iexpr= i++;
        butts[iexpr]= cb2;
        cb2.setToolTipText("enter an expression");
        c.gridy++;
        c.gridx= 1;
        c.weightx= 0.0;
        dsSelector.add( cb2, c );
        bg.add(cb2);
        
        final JTextField exprTF= new JTextField(expression);
        exprTF.setMinimumSize( new Dimension(120,exprTF.getFont().getSize()*2) );
        exprTF.setMaximumSize( new Dimension(600,exprTF.getFont().getSize()*2) );
        exprTF.setPreferredSize( new Dimension(600,exprTF.getFont().getSize()*2) );
        
        c.gridx= 2;
        c.weightx= 1.0;
        exprTF.addFocusListener(new FocusListener() {
            String orig=null;
            @Override
            public void focusGained(FocusEvent e) {
                orig= exprTF.getText();
            }
            @Override
            public void focusLost(FocusEvent e) {
                if ( !exprTF.getText().equals(orig) ) {
                    cb2.setSelected(true);
                }
            }
        } );
        
        if ( !isValidIdentifier(id) ) {
            if ( id.startsWith("'") || id.startsWith("\"") ) { // string literals
                literalTF.setText( id );
                butts[ilit].setSelected(true);
            } else {
                try {
                    Double.parseDouble(id);
                    if ( id.length()<20 ) {
                        id= String.format( "%s", id );
                    }
                    literalTF.setText( id );
                    butts[ilit].setSelected(true);
                } catch ( NumberFormatException ex ) {
                    exprTF.setText( id );
                    butts[iexpr].setSelected(true);
                }
            }
        }
        dsSelector.add( exprTF, c );
        
        // -------------------------------------------------------
        
        JPanel p= new JPanel();
        c.gridy++;
        c.weighty= 1.0;
        dsSelector.add( p, c );
        
        bg.add(cb);
        if ( JOptionPane.OK_OPTION == WindowManager.showConfirmDialog( this, dsSelector, "Select Variable", JOptionPane.OK_CANCEL_OPTION )  ) {
            for ( i=0; i<this.uris.size(); i++ ) {
                if ( butts[i].isSelected() ) {
                    return this.ids.get(i);
                }
            }
            if ( butts[this.uris.size()].isSelected() ) {
                return literalTF.getText().trim();
            } else if ( butts[this.uris.size()+1].isSelected() ) {
                return exprTF.getText().trim();
            } else {
                return null;
            }
        } else {
            return null;
        }
        
    }

    private String expression="";
    
    /** 
     * set the expression which will appear in the list of names, constants and expressions.
     * @param expr 
     */
    void setExpression(String expr) {
        this.expression= expr;
    }
}
