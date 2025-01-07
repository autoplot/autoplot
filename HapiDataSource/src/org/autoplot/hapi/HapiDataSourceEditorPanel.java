
package org.autoplot.hapi;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.autoplot.datasource.DataSetURI;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.das2.util.TickleTimer;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.RecentComboBox;
import org.autoplot.datasource.TimeRangeTool;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.ui.PromptComboBoxEditor;
import org.das2.dataset.DataSetUtil;
import org.das2.graph.GraphUtil;
import org.das2.util.ColorUtil;

/**
 * Swing editor for HAPI URIs
 * @author jbf
 */
public final class HapiDataSourceEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    private JSONArray idsJSON;
    private boolean supportsBinary;
    
    private URL defaultServer;
	
    private Datum myValidTime;
    
    private Component firstParameter=null;
    
    private boolean initialized= false;
	
    private List<JCheckBox> parameterCheckboxes= new ArrayList<>();
    
    private static final Icon NULL_ICON;
    
    static { 
        BufferedImage image= new BufferedImage( 16, 16, BufferedImage.TYPE_INT_ARGB );
        Graphics g= image.getGraphics();
        g.setColor(new Color(255,255,255,0) );
        g.fillRect( 0, 0, 16, 16 );
        //g.setColor( Color.LIGHT_GRAY );
        g.drawRect(0,0,15,15);
        NULL_ICON= new ImageIcon(image);
    }
    
    /**
     * return the range of available data. For example, Polar/Hydra data is available
     * from 1996-03-20 to 2008-04-15.
     * @param info
     * @return the range of available data.
     */
    private DatumRange getRange( JSONObject info ) {
        try {
            if ( info.has("firstDate") && info.has("lastDate") ) { // this is deprecated behavior
                String firstDate= info.getString("firstDate");
                String lastDate= info.getString("lastDate");
                if ( firstDate!=null && lastDate!=null ) {
                    Datum t1= Units.us2000.parse(firstDate);
                    Datum t2= Units.us2000.parse(lastDate);
                    if ( t1.le(t2) ) {
                        return new DatumRange( t1, t2 );
                    } else {
                        logger.warning( "firstDate and lastDate are out of order, ignoring.");
                    }
                }
            } else if ( info.has("startDate") ) { // note startDate is required.
                String startDate= info.getString("startDate");
				String stopDate;
				if ( info.has("stopDate") ) {
					stopDate= info.getString("stopDate");
				} else {
					stopDate= null;
				}
                if ( stopDate!=null ) {
                    stopDate= HapiDataSource.parseTime(stopDate).toString();
                }
                if ( startDate!=null ) {
                    Datum t1= Units.us2000.parse(startDate);
                    Datum t2= stopDate==null ? myValidTime : Units.us2000.parse(stopDate);
                    if ( t1.le(t2) ) {
                        return new DatumRange( t1, t2 );
                    } else {
                        logger.warning( "firstDate and lastDate are out of order, ignoring.");
                    }
                }
			}
        } catch ( JSONException | ParseException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        return null;
    }
        
    private String currentParameters= null;
    private URL currentServer= null;
    private DatumRange currentRange= null;
    private String currentId= null;
    private String currentExtra=null;
    private JSONObject currentInfo=null;
    
    private int lastParamIndex= -1; // the index of the last parameter selection.

    /**
     * scientist-provided time range
     */
    private String providedTimeRange= null;
    
    /**
     * Creates new form HapiDataSourceEditorPanel
     */
    public HapiDataSourceEditorPanel() {
		try {
			myValidTime= TimeUtil.create( "2200-01-01T00:00" );
		} catch (ParseException ex) {
			Logger.getLogger(HapiDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
		}
        try {
            List<String> servers= HapiServer.getKnownServers();
            this.defaultServer = new URL(servers.get(servers.size()-1));
        } catch ( MalformedURLException ex ) {
            throw new RuntimeException(ex);
        }
        initComponents();
        
        hapiServerRecentComboBox.setPreferenceNode("hapi.servers");
        PromptComboBoxEditor editor= new PromptComboBoxEditor("search");
        editor.setTooltipText( hapiServerRecentComboBox.getToolTipText() );
        hapiServerRecentComboBox.setEditor( editor );
        ((JTextField)editor.getEditorComponent()).setColumns(10);
        hapiServerRecentComboBox.invalidate();
        hapiServerRecentComboBox.revalidate();
        
        datasetFilterComboBox.setPreferenceNode("hapi.filters");
        editor= new PromptComboBoxEditor("search regex");
        editor.setTooltipText( datasetFilterComboBox.getToolTipText() );
        datasetFilterComboBox.setEditor( editor );
        ((JTextField)editor.getEditorComponent()).setColumns(10);
        datasetFilterComboBox.invalidate();
        datasetFilterComboBox.revalidate();
        
        parameterFilterComboBox.setPreferenceNode("hapi.filters");
        editor= new PromptComboBoxEditor("search");
        editor.setTooltipText( datasetFilterComboBox.getToolTipText() );
        parameterFilterComboBox.setEditor( editor );
        ((JTextField)editor.getEditorComponent()).setColumns(7);
        parameterFilterComboBox.invalidate();
        parameterFilterComboBox.revalidate();

        timeRangeComboBox.setPreferenceNode(RecentComboBox.PREF_NODE_TIMERANGE);
        parametersScrollPane.getVerticalScrollBar().setUnitIncrement( parametersPanel.getFont().getSize() );

        parametersPanel.setLayout( new BoxLayout( parametersPanel, BoxLayout.Y_AXIS ) );

        serversComboBox.setEnabled(false);
        serversComboBox.setModel( new DefaultComboBoxModel<>( HapiServer.getKnownServersArray() ) ); 
        loadKnownServersSoon();
        
        idsList2.addListSelectionListener( new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if ( !e.getValueIsAdjusting() ) {
                    String selectedValue= idsList2.getSelectedValue();
                    if ( selectedValue==null ) {
                        return;
                    }
                    if ( !selectedValue.equals(currentId) ) {
                        currentParameters= null;
                    }
                    if ( currentId!=null && currentId.equals(selectedValue) ) {
                        return;
                    }
                    if ( currentServer!=null ) {
                        currentId= selectedValue;
                    } else {
                        currentId= null;
                    }
                    
                    if ( currentId==null ) {
                        titleLabel.setText(" ");
                        return;
                    }
                    if ( currentId.startsWith("Error:" ) ) {
                        return;
                    }
                    titleLabel.setText("Retrieving info for "+currentId+"...");

                    parametersPanel.removeAll();
                    parametersPanel.revalidate();
                    parametersPanel.repaint();
                    resetVariableTimer.tickle();
                }
            }
        } );
        datasetFilterComboBox.getEditor().getEditorComponent().addKeyListener( new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                Runnable run= new Runnable() {
                    public void run() {
                        resetServerCatalog( currentServer );                        
                    }
                };
                SwingUtilities.invokeLater(run);
            }
        } );
        parameterFilterComboBox.getEditor().getEditorComponent().addKeyListener( new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                Runnable run= new Runnable() {
                    public void run() {
                        try {
                            resetIdImmediately( currentId, currentInfo );
                        } catch ( JSONException ex ) {
                            ex.printStackTrace();
                        }
                    }
                };
                SwingUtilities.invokeLater(run);
            }
        } );
    }

    TickleTimer resetVariableTimer= new TickleTimer( 100, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                String s= currentId;
                if ( s!=null && s.trim().length()>0 ) {
                    resetId(HapiServer.encodeURL( (String)serversComboBox.getSelectedItem() ), s );  
                } else {
                    parametersPanel.removeAll();
                    parametersPanel.add(new JLabel(" "));
//                    JEditorPane p= new JEditorPane();
//                    try {
//                        p.setPage( new URL( (String)serversComboBox.getSelectedItem() ));
//                        parametersPanel.add( p );
//                    } catch (IOException ex) {
//                        Logger.getLogger(HapiDataSourceEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
//                    }

                    titleLabel.setText(" ");

                }
            } catch (MalformedURLException ex) {
                JOptionPane.showMessageDialog( parametersPanel, ex.toString() );
            }
        }
    });
    
    private static String findFavIcon( String hapiString ) {
        try {
            URL hapi= new URL( hapiString );
            return new URL( hapi.getProtocol() + "://" + hapi.getHost() + "/favicon.ico" ).toString();
        } catch (MalformedURLException ex) {
            return null;
        }
    }
    
    private static final Map<String,ImageIcon> icons= Collections.synchronizedMap( new HashMap() );
    
    private static Icon iconFor( Object o, boolean wait ) {
        
        final String faviconUrl;
        faviconUrl= findFavIcon( o.toString() );
        
        ImageIcon result= icons.get( faviconUrl );
        if (result==null && wait ) {
            try {
                long t1= System.currentTimeMillis();
                
                try {
                    File ff= DataSetURI.getFile( faviconUrl, null );
                
                    List<BufferedImage> bbs= net.sf.image4j.codec.ico.ICODecoder.read(ff);
                    BufferedImage useThis= null;
                    for ( BufferedImage bb: bbs ) {
                        if ( bb.getWidth()<20 ) {
                            useThis= bb;
                            break;
                        }
                    }
                    if ( useThis==null ) {
                        BufferedImage im= bbs.get(0);
                        int h= im.getWidth(null);
                        int w= im.getHeight(null);
                        int s= 20;
                        int h1= Math.min(24,s*w/h);
                        BufferedImage bi=  new BufferedImage( s, h1, BufferedImage.TYPE_INT_ARGB);
                        Graphics g= bi.createGraphics();
                        g.drawImage( im, 0, 0, s, h1, null, null );
                        useThis= bi;
                    }
                    result= new ImageIcon(useThis);
                } catch ( IOException ex ) {
                    result= null;
                }
                logger.log(Level.FINE, "time to load icon for {0}: {1} ms", new Object[]{ o, System.currentTimeMillis()-t1});
                icons.put( faviconUrl, result );
                
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } 
        if ( result==null ) {
            return NULL_ICON;
        }
        return result;
    }
    
    private static class IconCellRenderer implements ListCellRenderer {
        DefaultListCellRenderer r= new DefaultListCellRenderer();
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c= r.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            Icon icon= iconFor( value, false );
            ((DefaultListCellRenderer)c).setIcon(icon);
            return c;
        }
    }
    
    /**
     * load the known servers and set the GUI.  This should not be called from
     * the event thread, and a runnable for the event thread will be submitted.
     */
    public void loadKnownServersImmediately() {
        String[] servers1= HapiServer.listHapiServersArray();
        String item = (String)hapiServerRecentComboBox.getSelectedItem();
        if ( item==null ) item = "";
        item = item.trim();
        if ( item.length()>0 ) {
            Pattern p= Pattern.compile(item,Pattern.CASE_INSENSITIVE);
            List<String> newServers= new ArrayList<>();
            for ( int i=0; i<servers1.length; i++ ) {
                if ( p.matcher(servers1[i]).find() ) {
                    newServers.add(servers1[i]);
                }
            }
            servers1= newServers.toArray( new String[newServers.size()] );
        }
        final String[] servers= servers1;
        Runnable run= new Runnable() {
            @Override
            public void run() {
                serversComboBox.setModel( new DefaultComboBoxModel<>( servers ));
                serversComboBox.setRenderer( new IconCellRenderer() );
                try {
                    defaultServer= new URL(servers[0]); //TODO: sometimes server is URL sometimes a string.  How annoying...
                } catch (MalformedURLException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                if ( currentServer!=null ) {
                    serversComboBox.setSelectedItem(currentServer.toString());
                } else {
                    serversComboBox.setSelectedIndex(0);
                }
                serversComboBox.setEnabled(true);
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    /**
     * request that the known servers be displayed.  This will spawn an 
     * asynchronous thread to get the server names, and then will load the GUI 
     * on the event thread.  This can be called from the event thread.
     */
    public void loadKnownServersSoon() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                loadKnownServersImmediately();
            }
        };
        new Thread(run,"loadKnownServers").start();
        run= new Runnable() {
            @Override
            public void run() {
                String[] servers = HapiServer.listHapiServersArray();
                for ( String s: servers ) {
                    Icon i= iconFor( s, true ); // load of icon off the event thread.
                    if ( i!=null ) logger.log(Level.FINER, "iconHeight={0}", i.getIconHeight());
                }
            };
        };
        new Thread(run,"loadKnownServerIcons").start();
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        serversComboBox = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        jPanel3 = new javax.swing.JPanel();
        parametersScrollPane = new javax.swing.JScrollPane();
        parametersPanel = new javax.swing.JPanel();
        clearAllB = new javax.swing.JButton();
        setAllB = new javax.swing.JButton();
        extraInfoButton = new javax.swing.JButton();
        titleLabel = new javax.swing.JLabel();
        cachedFileButton = new javax.swing.JButton();
        parameterFilterComboBox = new org.autoplot.datasource.RecentComboBox();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        idsList2 = new javax.swing.JList<>();
        clearButton = new javax.swing.JButton();
        datasetFilterComboBox = new org.autoplot.datasource.RecentComboBox();
        messagesLabel = new javax.swing.JLabel();
        binaryCB = new javax.swing.JCheckBox();
        timeRangeComboBox = new org.autoplot.datasource.RecentComboBox();
        exampleTimeRangesCB = new javax.swing.JComboBox<>();
        disableCacheCheckBox = new javax.swing.JCheckBox();
        hapiServerRecentComboBox = new org.autoplot.datasource.RecentComboBox();

        jLabel1.setText("HAPI Server:");

        serversComboBox.setEditable(true);
        serversComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "http://tsds.org/get/IMAGE/PT1M/hapi", " " }));
        serversComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serversComboBoxActionPerformed(evt);
            }
        });

        jLabel2.setText("Time Range: ");

        jButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/hapi/calendar.png"))); // NOI18N
        jButton1.setToolTipText("Time Range Tool");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jSplitPane1.setDividerLocation(210);

        parametersPanel.setMinimumSize(new java.awt.Dimension(100, 0));

        javax.swing.GroupLayout parametersPanelLayout = new javax.swing.GroupLayout(parametersPanel);
        parametersPanel.setLayout(parametersPanelLayout);
        parametersPanelLayout.setHorizontalGroup(
            parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 485, Short.MAX_VALUE)
        );
        parametersPanelLayout.setVerticalGroup(
            parametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 195, Short.MAX_VALUE)
        );

        parametersScrollPane.setViewportView(parametersPanel);

        clearAllB.setText("Clear All");
        clearAllB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearAllBActionPerformed(evt);
            }
        });

        setAllB.setText("Set All");
        setAllB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setAllBActionPerformed(evt);
            }
        });

        extraInfoButton.setText("Extra Info");
        extraInfoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extraInfoButtonActionPerformed(evt);
            }
        });

        titleLabel.setText(" ");

        cachedFileButton.setText("Cached Files...");
        cachedFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cachedFileButtonActionPerformed(evt);
            }
        });

        parameterFilterComboBox.setToolTipText("search bar, any parameter or parameter description containing regular expression (.* matches anything) is shown");
        parameterFilterComboBox.setMaximumSize(new java.awt.Dimension(1028, 32767));
        parameterFilterComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                parameterFilterComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(parametersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 497, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(clearAllB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(setAllB)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cachedFileButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(extraInfoButton))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addComponent(titleLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(parameterFilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(4, 4, 4))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(titleLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(parameterFilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(parametersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(clearAllB)
                    .addComponent(setAllB)
                    .addComponent(extraInfoButton)
                    .addComponent(cachedFileButton)))
        );

        jSplitPane1.setRightComponent(jPanel3);

        jScrollPane2.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        jScrollPane2.setMinimumSize(new java.awt.Dimension(100, 22));

        idsList2.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(idsList2);

        clearButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/hapi/clearTextButton.png"))); // NOI18N
        clearButton.setToolTipText("clear search bar");
        clearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearButtonActionPerformed(evt);
            }
        });

        datasetFilterComboBox.setToolTipText("search bar, any id or title containing regular expression (.* matches anything) is shown");
        datasetFilterComboBox.setMaximumSize(new java.awt.Dimension(1028, 32767));
        datasetFilterComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                datasetFilterComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(datasetFilterComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(clearButton))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(clearButton)
                    .addComponent(datasetFilterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 216, Short.MAX_VALUE))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                    .addGap(30, 30, 30)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)))
        );

        jSplitPane1.setLeftComponent(jPanel1);

        messagesLabel.setText("(messages here)");

        binaryCB.setText("Use Binary");
        binaryCB.setToolTipText("Some servers support binary data transfers, and this will use binary to transfer data.");
        binaryCB.setEnabled(false);

        exampleTimeRangesCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Example Time Ranges" }));
        exampleTimeRangesCB.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                exampleTimeRangesCBItemStateChanged(evt);
            }
        });

        disableCacheCheckBox.setText("Disable Cache");

        hapiServerRecentComboBox.setToolTipText("search bar for HAPI server, any server containing regular expression (.* matches anything) is shown");
        hapiServerRecentComboBox.setMaximumSize(new java.awt.Dimension(1028, 32767));
        hapiServerRecentComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hapiServerRecentComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 712, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(serversComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hapiServerRecentComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(timeRangeComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exampleTimeRangesCB, javax.swing.GroupLayout.PREFERRED_SIZE, 224, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(messagesLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(disableCacheCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(binaryCB)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(serversComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(hapiServerRecentComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(messagesLabel)
                    .addComponent(binaryCB, javax.swing.GroupLayout.DEFAULT_SIZE, 25, Short.MAX_VALUE)
                    .addComponent(disableCacheCheckBox))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel2)
                        .addComponent(timeRangeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton1)
                    .addComponent(exampleTimeRangesCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        TimeRangeTool tt= new TimeRangeTool();
        tt.setSelectedRange(timeRangeComboBox.getText());
        int r= JOptionPane.showConfirmDialog( this, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
        if ( r==JOptionPane.OK_OPTION) {
            timeRangeComboBox.setText(tt.getSelectedRange());
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void serversComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serversComboBoxActionPerformed
        try {
            if ( !initialized ) return;
            final URL url= HapiServer.encodeURL( (String)serversComboBox.getSelectedItem() );
            if ( currentServer==null || !url.toExternalForm().equals(currentServer.toExternalForm()) ) {
                DefaultListModel m= new DefaultListModel() ;
                m.add(0,"Reading list of available datasets...");
                idsList2.setModel( m );
            }
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        resetServer( url );
                    } catch (IOException | JSONException ex) {
                        logger.log(Level.SEVERE, null, ex);
                        DefaultListModel m= new DefaultListModel() ;
                        m.add(0,"Error: unable to connect");
                        idsList2.setModel( m );
                    }
                }
            };
            new Thread( run, "resetServer").start();
        } catch (MalformedURLException ex ) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_serversComboBoxActionPerformed

    private void clearAllBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearAllBActionPerformed
        boolean first= currentServer==null || !currentServer.toString().contains("https://cdaweb.gsfc.nasa.gov/registry/hdp/hapi");
        for ( Component c: parameterCheckboxes ) {
            if ( c instanceof JCheckBox ) {
                if ( first ) {
                    ((JCheckBox)c).setSelected(true);
                    first= false;
                } else {
                    ((JCheckBox)c).setSelected(false);
                }
            }
        }
    }//GEN-LAST:event_clearAllBActionPerformed

    private void setAllBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setAllBActionPerformed
        for ( Component c: parameterCheckboxes ) {
            if ( c instanceof JCheckBox ) {
                ((JCheckBox)c).setSelected(true);
            }
        }
    }//GEN-LAST:event_setAllBActionPerformed

    private void extraInfoButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extraInfoButtonActionPerformed
        final JEditorPane jep= new JEditorPane();
        jep.setContentType("text/html");
        jep.setText( currentExtra );
        jep.setEditable( false );
        jep.setOpaque(false);
        jep.addHyperlinkListener( new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent hle) { // from http://stackoverflow.com/questions/14170041/is-it-possible-to-create-programs-in-java-that-create-text-to-link-in-chrome
                if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                    Desktop desktop = Desktop.getDesktop();
                    try {
                        desktop.browse(hle.getURL().toURI());
                    } catch (URISyntaxException | IOException ex) {
                        logger.log( Level.WARNING, ex.getMessage(), ex );
                    }
                }
            }
        });
        final JScrollPane p= new JScrollPane(jep);
        p.setPreferredSize( new Dimension( 800,400 ) );
        p.setMaximumSize( new Dimension( 800,400 ) );
        SwingUtilities.invokeLater(new Runnable() {  
            @Override
            public void run() {
                jep.setCaretPosition(0);
                p.getVerticalScrollBar().setValue(0);
            }
        });
        JOptionPane.showMessageDialog( this, p, "Extra Info", JOptionPane.INFORMATION_MESSAGE );
    }//GEN-LAST:event_extraInfoButtonActionPerformed

    private void clearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearButtonActionPerformed
        datasetFilterComboBox.setSelectedItem("");
        parameterFilterComboBox.setSelectedItem("");
    }//GEN-LAST:event_clearButtonActionPerformed

    private void datasetFilterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_datasetFilterComboBoxActionPerformed
        Runnable run= new Runnable() {
            @Override
            public void run() {
                resetServerCatalog( currentServer );
            }
        };
        SwingUtilities.invokeLater(run);
    }//GEN-LAST:event_datasetFilterComboBoxActionPerformed

    private void exampleTimeRangesCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_exampleTimeRangesCBItemStateChanged
        String s= (String)exampleTimeRangesCB.getSelectedItem();
        if ( s.startsWith("Example") ) {
            //do nothing
        } else {
            timeRangeComboBox.setSelectedItem(s);
        }
    }//GEN-LAST:event_exampleTimeRangesCBItemStateChanged

    private void cachedFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cachedFileButtonActionPerformed
        
        String[] params= getParameters(true).split(",");
        Map<String,DatumRange> ff;
        String str= (String)timeRangeComboBox.getSelectedItem();
        String format= binaryCB.isSelected() ? "binary" : "csv";
        
        try {
            DatumRange tr;
            if ( str==null ) {
                tr= currentRange;
            } else {
                tr= DatumRangeUtil.parseTimeRange(str);
            }
            if ( tr==null ) {
                JOptionPane.showMessageDialog(this,"id doesn't provide range");
                return;
            }
            ff = HapiUtil.getCacheFiles( this.currentServer, this.currentId, params, tr, format );
            
        } catch ( ParseException ex ) {
            JOptionPane.showMessageDialog( this, "Unable to parse timerange: "+str);
            return;
        }
        
        if ( ff==null ) {
            JOptionPane.showMessageDialog( this, "No cache files found in the interval");
            return;
        }
        File cacheFolder= HapiUtil.cacheFolder(  this.currentServer, "/data/" + this.currentId  );
        HapiCacheManager mm= new HapiCacheManager();
        String[] ss= ff.keySet().toArray( new String[ff.size()] );
        mm.setFiles( cacheFolder, ss );
        if ( JOptionPane.showConfirmDialog(this,mm,"Manage Cached Data",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION ) {
            System.err.println("cacheFolder: "+cacheFolder );
            for ( String s: ff.keySet() ) {
                File f1= new File( cacheFolder, s );
                if ( !f1.delete() ) {
                    logger.log(Level.INFO, "unable to delete {0}", f1);
                }
            }
            //FileUtil.deleteFileTree(cacheFolder); //TODO: off of the event thread
        }
        
    }//GEN-LAST:event_cachedFileButtonActionPerformed

    private void parameterFilterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parameterFilterComboBoxActionPerformed
        resetVariableTimer.tickle("resetFilter");
    }//GEN-LAST:event_parameterFilterComboBoxActionPerformed

    private void hapiServerRecentComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hapiServerRecentComboBoxActionPerformed
        loadKnownServersSoon();
    }//GEN-LAST:event_hapiServerRecentComboBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox binaryCB;
    private javax.swing.JButton cachedFileButton;
    private javax.swing.JButton clearAllB;
    private javax.swing.JButton clearButton;
    private org.autoplot.datasource.RecentComboBox datasetFilterComboBox;
    private javax.swing.JCheckBox disableCacheCheckBox;
    private javax.swing.JComboBox<String> exampleTimeRangesCB;
    private javax.swing.JButton extraInfoButton;
    private org.autoplot.datasource.RecentComboBox hapiServerRecentComboBox;
    private javax.swing.JList<String> idsList2;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JLabel messagesLabel;
    private org.autoplot.datasource.RecentComboBox parameterFilterComboBox;
    private javax.swing.JPanel parametersPanel;
    private javax.swing.JScrollPane parametersScrollPane;
    private javax.swing.JComboBox<String> serversComboBox;
    private javax.swing.JButton setAllB;
    private org.autoplot.datasource.RecentComboBox timeRangeComboBox;
    private javax.swing.JLabel titleLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public boolean reject(String uri) throws Exception {
        return false;
    }

    @Override
    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        URISplit split = URISplit.parse(uri);
        if ( split.file==null || split.file.equals("file:///") ) { // use TSDS's one by default.
            split.file= defaultServer.toString();
        }  
        if ( !split.file.endsWith("/hapi") ) {
            int i= split.file.lastIndexOf("/hapi");
            if ( i>-1 ) {
                split.file= split.file.substring(0,i+5);
            }
        }
        try {
            serversComboBox.setSelectedItem(split.file);
            idsJSON= HapiServer.getCatalog(new URL(split.file));
        } catch ( IOException ex ) {
            messagesLabel.setText("Unable to connect to server");
        }
        return true;
    }
    
    /** make the currentParameters checklist reflect currentParameters spec.
     * 
     * @param parameters comma-delineated list of currentParameters.
     */
    private void setParameters( String parameters ) {
        for ( Component c: parameterCheckboxes ) {
            if ( c instanceof JCheckBox ) {
                ((JCheckBox)c).setSelected(false);
            }
        }
        if ( parameters.length()>0 ) {
            String[] ss= parameters.split(",");
            int iparam=0;
            for ( Component c: parameterCheckboxes ) {
                if ( c instanceof JCheckBox ) {
                    String name= ((JCheckBox)c).getName();
                    ((JCheckBox)c).setSelected(false);
                    for (String s : ss) {
                        if (s.equals(name)) {
                            ((JCheckBox)c).setSelected(true);
                            if ( iparam>0 && firstParameter==null ) {
                                firstParameter= c;
                            }
                        }
                    }
                    iparam++;
                }
            }
        } else {
            for ( Component c: parameterCheckboxes ) {
                if ( c instanceof JCheckBox ) {
                    ((JCheckBox)c).setSelected(true);
                }
            }
        }
        if ( firstParameter!=null ) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    Rectangle r= firstParameter.getBounds();
                    parametersScrollPane.getViewport().setViewPosition( new Point( 0, Math.max( 0, r.y - parametersScrollPane.getHeight()/4 ) ) );
                }
            });
            
        }
    }

    /**
     * 
     * @param enumerate if true show all parameters, instead of ""
     * @return all the parameters
     */
    private String getParameters(boolean enumerate) {
        StringBuilder b= new StringBuilder();
        boolean areAllTrue= true;
        for ( Component c: parameterCheckboxes ) {
            if ( c instanceof JCheckBox ) {
                if ( ((JCheckBox)c).isSelected() ) {
                    b.append(",").append(c.getName());
                } else {
                    areAllTrue= false;
                }
            }
        }
        if ( areAllTrue && !enumerate ) {
            return "";
        } else {
            return b.substring(1); // remove first comma.
        }
    }
    
    @Override
    public void setURI(String uri) {
        URISplit split = URISplit.parse(uri);        
        if ( split.file==null || split.file.equals("file:///") ) { // use TSDS's one by default.
            split.file= defaultServer.toString();
        } else {
            if ( !split.file.endsWith("/hapi") ) {
                int i= split.file.lastIndexOf("/hapi");
                if ( i>-1 ) {
                    split.file= split.file.substring(0,i+5);
                }
            }
            try {
                currentServer= new URL(split.file);
            } catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        try {
            serversComboBox.setSelectedItem(HapiServer.decodeURL( HapiServer.encodeURL(split.file) ) );
        } catch ( MalformedURLException ex ) {
            serversComboBox.setSelectedItem( split.file ); // do what we did before.
        }
        Map<String,String> params= URISplit.parseParams( split.params );
        
        String id= params.get("id");
        if ( id!=null ) {
            try {
                id= URLDecoder.decode(id,"UTF-8");
                idsList2.setSelectedValue( id, true );
            } catch (UnsupportedEncodingException ex) {
                throw new RuntimeException(ex);
            }
            currentId= id;
        }
        String timerange= params.get("timerange");
        if ( timerange!=null ) {
            //timeRangeTextField.setText(timerange);
            timeRangeComboBox.setText(timerange);
        } 
        providedTimeRange= timerange;
        
        String parameters= params.get("parameters");
        if ( parameters!=null ) {
            parameters= HapiServer.decodeURLParameters(parameters);
            this.currentParameters= parameters;
            setParameters(this.currentParameters);
            resetVariableTimer.tickle("initialUpdate");
        } else {
            resetVariableTimer.tickle("initialUpdateNoParams");
        }
        if ( HapiSpec.BINARY.equals(params.get("format") ) ) {
            this.binaryCB.setSelected(true);
        } else {
            this.binaryCB.setSelected(false);
        }
        
        if ( !HapiServer.useCache() ) {
            cachedFileButton.setVisible(false);
            disableCacheCheckBox.setVisible(false);
        }
        
        disableCacheCheckBox.setSelected( "F".equals(params.get("cache")) );
        messagesLabel.setText("Select dataset above");
        
        initialized= true;
        serversComboBox.setSelectedItem( split.file );
            
    }

    @Override
    public void markProblems(List<String> problems) {
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public String getURI() {
        String parameters= getParameters(false);
        String id= idsList2.getSelectedValue();
        if ( id==null ) {
            id= "";
        } else {
            id = HapiServer.encodeURLParameters(id);
            //id= HapiServer.urlEncode(id);
        }
        String uri= "vap+hapi:";
        try {
            // please encode the URLs before making Autoplot URIs, which really should be ASCII.
            uri = uri + HapiServer.encodeURL( serversComboBox.getSelectedItem().toString() ).toString();
        } catch ( MalformedURLException ex ) {
            uri = uri + serversComboBox.getSelectedItem().toString();
        }
        uri = uri + "?id=" + id + "&timerange="+timeRangeComboBox.getText().replaceAll(" ","+");
        if ( binaryCB.isSelected() && binaryCB.isEnabled() ) {
            uri+= "&format=binary";
        }
        if ( disableCacheCheckBox.isSelected() ) {
            uri+= "&cache=F";
        }
        if ( parameters.length()>0 ) {
            return uri + "&parameters="+HapiServer.encodeURLParameters(parameters);
        } else {
            return uri;
        }
    }
    
    private void loadServerCapabilities( URL server ) throws JSONException {
        boolean binaryIsEnabled= false;
        try {
            JSONObject capabilitiesDoc= HapiServer.getCapabilities(server);
            if ( capabilitiesDoc.has(HapiSpec.OUTPUT_FORMATS ) ) { // new 2016-11-21.  Other is deprecated.
                JSONArray outputFormats= capabilitiesDoc.getJSONArray(HapiSpec.OUTPUT_FORMATS );
                for ( int i=0; i<outputFormats.length(); i++ ) {
                    if ( outputFormats.getString(i).equals(HapiSpec.BINARY) ) {
                        binaryIsEnabled= true;
                    }
                }                    
            } else {
                JSONArray capabilities= capabilitiesDoc.getJSONArray("capabilities"); // deprecated.
                for ( int i=0; i<capabilities.length(); i++ ) {
                    JSONObject c= capabilities.getJSONObject(i);
                    if ( c.has(HapiSpec.FORMATS) ) {
                        JSONArray formats= c.getJSONArray(HapiSpec.FORMATS);
                        for ( int j=0; j<formats.length(); j++ ) {
                            if ( formats.getString(j).equals(HapiSpec.BINARY) ) {
                                binaryIsEnabled= true;
                            }
                        }
                    }
                }
            }
        } catch ( IOException ex ) {
            // this is okay, we'll just assume it doesn't support binary.
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        this.supportsBinary= binaryIsEnabled;
    }
    
    /**
     * This will load the ids into the GUI.
     * See https://github.com/hapi-server/data-specification#catalog
     * This uses the datasetFilterComboBox's value to filter by regular expression, if non-empty.
     * This must be called on the event thread.
     * @param server
     * @throws IOException
     * @throws JSONException 
     */    
    private void resetServerCatalog( URL server ) {
        try {
            if ( !EventQueue.isDispatchThread() ) {
                System.err.println("Here Jeremy");
            }
            final String filter= datasetFilterComboBox.getSelectedItem().toString().trim();
            DefaultListModel model= new DefaultListModel();
            int maxCharacters=0;
            for ( JSONObject catalogEntry: new JSONArrayIterator(idsJSON) ) {
                if ( filter.length()>0 ) {
                    Pattern p= Pattern.compile(filter,Pattern.CASE_INSENSITIVE);
                    String id= catalogEntry.getString("id");
                    String title= null;
                    if ( catalogEntry.has(HapiSpec.TITLE) ) {
                        title= catalogEntry.getString(HapiSpec.TITLE);
                    }
                    if ( p.matcher(id).find() || ( title!=null && p.matcher(title).find() ) ) {
                        model.addElement( catalogEntry.getString("id") );
                        maxCharacters= Math.max( catalogEntry.getString("id").length(), maxCharacters );
                    }
                } else {
                    model.addElement( catalogEntry.getString("id") );
                    maxCharacters= Math.max( catalogEntry.getString("id").length(), maxCharacters );
                }
            }
            idsList2.setModel( model );
            int maxLenPixels= maxCharacters*8; // pixels per character
            maxLenPixels= Math.min( maxLenPixels,600 );
            maxLenPixels= Math.max( maxLenPixels,300 );
            jSplitPane1.setDividerLocation(maxLenPixels);

            if ( !String.valueOf(server).equals(String.valueOf(currentServer)) ) { // avoid name resolution.  Thanks Findbugs!
                idsList2.setSelectedIndex(0);
                currentServer= server;
                idsList2.ensureIndexIsVisible(0);
            } else {
                if ( currentId!=null ) {
                    idsList2.setSelectedValue( currentId, true );
                } else {
                    int i= idsList2.getSelectedIndex();
                    idsList2.ensureIndexIsVisible( i==-1 ? 0 : i );
                }
            }
            binaryCB.setEnabled(supportsBinary);
            
        } catch ( JSONException ex ) {
            logger.log(Level.SEVERE, null, ex );
        }

    }
        
    /**
     * get the catalog of the server.  This should not be called from the event
     * thread.
     * @param server
     * @throws IOException
     * @throws JSONException 
     */
    private void resetServer( final URL server ) throws IOException, JSONException {
        idsJSON= HapiServer.getCatalog(server);
        loadServerCapabilities(server);
        Runnable run= new Runnable() {
            public void run() {
                datasetFilterComboBox.setSelectedItem("");
                resetServerCatalog(server);
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    private String getHtmlFor( Object o ) throws JSONException {
        StringBuilder s= new StringBuilder();
        if ( o instanceof JSONArray ) {
            JSONArray joa= (JSONArray)o;
            for ( int i=0; i<joa.length(); i++ ) {
                s.append(getHtmlFor(joa.get(i))).append("<br>");
            }
        } else if ( o instanceof JSONObject ) {
            JSONObject jo= (JSONObject)o;
            s.append("<table>");
            Iterator iter= jo.keys();
            String k;
            for ( ; iter.hasNext(); ) {
                k=iter.next().toString();
                Object v= jo.get(k);
                String sv= (getHtmlFor(v));
                s.append("<tr valign=top><td>").append(k).append("</td><td>").append(sv).append("</td></tr>");
            }
            s.append("</table>");
        } else if ( o instanceof String ) {
            String so= String.valueOf(o);
            if ( so.startsWith("spase:") ) {
                so= "<a href=\"http://spase.info/registry/render?id="+so+"\">"+so+"</a>";
            } else if ( so.startsWith("http://") || so.startsWith("https://") || so.startsWith("ftp://" ) ) {
                so= "<a href=\""+so+"\">"+so+"</a>";
            } else if ( so.startsWith("doi:") || so.startsWith("DOI:") ) {
                so= "<a href=\"https://doi.org/"+so.substring(4)+"\">"+so+"</a>";
            } else if ( so.startsWith("10.") && so.length()>7 ) {
                Pattern p= Pattern.compile("(10[.][0-9]{3,}[^\\s\"/<>]*/[^\\s\"<>]+)");
                if ( p.matcher(so).matches() ) {
                    so= "<a href=\"https://doi.org/"+so+"\">"+so+"</a>";
                }
            }
            s.append(so);
        } else {
            s.append(o.toString());
        }
        return s.toString();
    }
    
    /**
     * [yr,mon,day,hour,min,sec,nanos]
     * @param array
     * @return approximate seconds
     */
    private static Datum cadenceArrayToDatum( int[] array ) {
        double seconds= array[6]/1e9;
        seconds+= array[5];
        seconds+= array[4]*60;
        seconds+= array[3]*3600;
        seconds+= array[2]*86400; //approx, just to get scale
        seconds+= array[1]*86400*30; //approx, just to get scale
        seconds+= array[0]*86400*365; // approx, just to get scale
        return Units.seconds.createDatum(seconds);
    }
    
    /**
     * return the duration in a easily-human-consumable form.
     * @param milliseconds the duration in milliseconds.
     * @return a duration like "2.6 hours"
     */
    public static String getDurationForHumans( long milliseconds ) {
        if ( milliseconds<2*1000 ) {
            return milliseconds+" milliseconds";
        } else if ( milliseconds<2*60000 ) {
            return String.format( Locale.US, "%.1f",milliseconds/1000.)+" seconds";
        } else if ( milliseconds<2*3600000 ) {
            return String.format( Locale.US, "%.1f",milliseconds/60000.)+" minutes";
        } else if ( milliseconds<2*86400000 ) {
            return String.format( Locale.US, "%.1f",milliseconds/3600000.)+" hours";
        } else {
            double ddays= milliseconds/86400000.;
            if ( ddays<48 ) {
                return String.format( Locale.US, "%.1f",ddays)+" days";
            } else if ( ddays<400 ) {
                return String.format( Locale.US, "%.1f",ddays/7)+" weeks";
            } else {
                return String.format( Locale.US, "%.1f",ddays/365)+" years";
            }
        }
    }    
    
    private void resetIdReportError( URL server, String id, Exception ex ) {
            logger.log(Level.SEVERE, null, ex);
            parametersPanel.removeAll();
            parametersPanel.add(new javax.swing.JLabel("Error reported on server:"));
            String s= ex.getMessage();
            parametersPanel.add(new javax.swing.JLabel(s));
            JLabel space= new javax.swing.JLabel(" ");
            //space.setMinimumSize(new Dimension(30,30));
            //space.setPreferredSize(new Dimension(30,30));
            parametersPanel.add(space);
            final URL url= HapiServer.createURL(server, HapiSpec.INFO_URL, Collections.singletonMap(HapiSpec.URL_PARAM_ID, id ) );
            javax.swing.JButton l= new javax.swing.JButton("Load URL in Browser");
            l.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        Desktop.getDesktop().browse( url.toURI() );
                    } catch (URISyntaxException | IOException ex1) {
                        logger.log(Level.SEVERE, null, ex1);
                    }
                }
            });
            parametersPanel.add(l);
            titleLabel.setText("");        
    }
    
    private void resetIdImmediately(String id,JSONObject info) throws JSONException {
        for ( JSONObject item : new JSONArrayIterator(idsJSON) ) {
            if ( item.getString("id").equals(id) ) {
                if ( item.has(HapiSpec.TITLE) ) {
                    String title= item.getString(HapiSpec.TITLE);
                    titleLabel.setText(title);
                    titleLabel.setToolTipText(title);
                    titleLabel.setMinimumSize(new Dimension(100,titleLabel.getFont().getSize()));
                } else {
                    titleLabel.setText(id);
                }
            }
        }
        JSONArray parameters= info.getJSONArray("parameters");
        String parameterFilter= (String)parameterFilterComboBox.getSelectedItem();
        if ( parameterFilter==null ) parameterFilter="";
        Pattern p= parameterFilter.length()>0 ? Pattern.compile(parameterFilter,Pattern.CASE_INSENSITIVE) : null ;

        StringBuilder extra= new StringBuilder();
        extra.append("<html><table>");
        Iterator iter= info.keys();
        String k;
        for ( ; iter.hasNext(); ) {
            k=iter.next().toString();
            //if ( !k.equals("parameters") ) {
                Object v= info.get(k);
                extra.append("<tr valign=top><td>").append(k).append("</td><td>");
                String s= getHtmlFor(v);
                if ( v.toString().length()>MAX_LENGTH_CHARACTERS ) {
                    extra.append("<i>(").append(v.toString().length()).append(" characters)</i>");
                    //extra.append( s ) ; //v.toString() );
                } else {
                    extra.append( s );
                }
                extra.append("</td></tr>");
            //}
        }
        extra.append("</table></html>");
        currentExtra= extra.toString();
        parametersPanel.removeAll();
        parameterCheckboxes.clear();
        String[] sparams= new String[parameters.length()];
        Boolean startRank2= null;
        for ( int i=0; i<parameters.length(); i++ ) {
            JSONObject parameter= parameters.getJSONObject(i);
//                if ( parameter.has("size") ) {
//                    Object o= parameter.get("size");
//                    if ( !( o instanceof JSONArray ) ) {
//                        logger.log(Level.WARNING, "size is not an array of ints: {0}", o);
//                        continue;
//                    }
//                    JSONArray aa= parameter.getJSONArray("size");
//                    logger.log(Level.WARNING, "size is array is not supported in Autoplot.");
//                    continue;
//                }
            sparams[i]= parameter.getString("name");
            JCheckBox cb= new JCheckBox(sparams[i]);

            String label= sparams[i];
            if ( parameter.has("size") ) {
                label= label+parameter.getString("size");
            }
            cb.setName(sparams[i]);
            
            if ( i==0 ) {
                cb.setSelected(true);
            } else if ( startRank2==null ) {
                startRank2= label.contains("[");
                cb.setSelected(true);
            } else {
                boolean otherIsRank2= label.contains("[");
                cb.setSelected( otherIsRank2 ? false : ( !startRank2 ) );
            }

            final int fi= i;
            cb.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) == ActionEvent.SHIFT_MASK ) {
                        if ( lastParamIndex>-1 ) {
                            if ( lastParamIndex<fi ) {
                                for ( int i=lastParamIndex; i<=fi; i++ ) {
                                    ( (JCheckBox)parameterCheckboxes.get(i) ).setSelected(true);
                                } 
                            } else {
                                for ( int i=fi; i<=lastParamIndex; i++ ) {
                                    ( (JCheckBox)parameterCheckboxes.get(i) ).setSelected(true);
                                } 
                            }
                        }
                    }
                    lastParamIndex= fi;
                    String label= ((JCheckBox) parameterCheckboxes.get(fi)).getText();
                    boolean rank2= label.contains("[");
                    for ( int i=1; i<parameterCheckboxes.size(); i++ ) {
                        Component c= parameterCheckboxes.get(i);
                        if ( c instanceof JCheckBox && c!=((JCheckBox)parameterCheckboxes.get(fi)) ) {
                            boolean otherIsRank2= ((JCheckBox)c).getText().contains("[");
                            boolean isAlreadySelected= ((JCheckBox)c).isSelected();
                            ((JCheckBox)c).setSelected( otherIsRank2 ? false : ( isAlreadySelected && !rank2 ) );
                        }
                    }
                }

            });
            String labelDesc;
            if ( parameter.has("description") ) {
                String d= parameter.getString("description");
                //parametersPanel.add( new javax.swing.JLabel( d ) );
                if ( d.length()>80 ) {
                    cb.setToolTipText(d);
                    labelDesc= label+": "+d.substring(0,80)+"...";
                } else {
                    cb.setToolTipText(d);
                    labelDesc= label+": "+d;
                }
            } else {
                labelDesc= label;
            }
            cb.setText( labelDesc );
            parameterCheckboxes.add( cb );
            
            if ( p==null || ( p.matcher(labelDesc).find() ) ) {
                parametersPanel.add( cb );
            } else {
                cb.setSelected(false);
            }
        }
        parametersPanel.setToolTipText("shift-click will select range of parameters");
        parametersPanel.revalidate();
        parametersPanel.repaint();
        if ( currentParameters!=null ) {
            setParameters(currentParameters);
        }
        DatumRange range= getRange(info);
        if ( range==null ) {
            logger.warning("server is missing required startDate and stopDate parameters.");
            messagesLabel.setText( "range is not provided (non-compliant server)" );
        } else {
            DatumRange sampleRange=null;
            if ( info.has("sampleStartDate") && info.has("sampleStopDate") ) {
                try {
                    sampleRange = new DatumRange( Units.us2000.parse(info.getString("sampleStartDate")), Units.us2000.parse(info.getString("sampleStopDate")) );
                } catch (JSONException | ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } 
            if ( sampleRange==null ) {
                Datum cadence= Units.seconds.createDatum(60);  // assume default cadence of 1 minute results in 1 day sample range.
                if ( info.has("cadence") ) {
                    try{
                        int[] icadence= DatumRangeUtil.parseISO8601Duration(info.getString("cadence"));
                        cadence= cadenceArrayToDatum(icadence);
                    } catch ( ParseException ex ) {
                        logger.log(Level.WARNING, "parse error in cadence: {0}", info.getString("cadence"));
                    }
                }    
                if (range.max().ge(myValidTime)) { // Note stopDate is required since 2017-01-17.
                    logger.warning("server is missing required stopDate parameter.");
                    messagesLabel.setText(range.min().toString() + " to ?");
                    sampleRange = new DatumRange(range.min(), range.min().add(1, Units.days));
                } else {
                    messagesLabel.setText(range.toString());
                    if ( cadence.ge(Units.days.createDatum(1)) ) {
                        Datum end = TimeUtil.nextMidnight(range.max());
                        end= end.subtract( 10,Units.days );
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.days ) ) ) {
                            sampleRange = new DatumRange( end, end.add(10,Units.days) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(10,Units.days), end );
                        } 
                    } else if ( cadence.ge(Units.seconds.createDatum(1)) ) {
                        Datum end = TimeUtil.prevMidnight(range.max());
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.hours ) ) ) {
                            sampleRange = new DatumRange( end, end.add(1,Units.days) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(1,Units.days), end );
                        } 
                    } else {
                        Datum end = TimeUtil.prev( TimeUtil.HOUR, range.max() );
                        if ( range.max().subtract(end).ge( Datum.create(1,Units.minutes ) ) ) {
                            sampleRange = new DatumRange( end, end.add(1,Units.hours) );
                        } else {
                            sampleRange = new DatumRange( end.subtract(1,Units.hours), end );
                        } 
                    }
                    if ( !sampleRange.intersects(range) ) {
                        sampleRange= sampleRange.next();
                    }
                }
            } else {
                String s= range.toString();
                if ( info.has("modificationDate") ) {
                    try {
                        Datum tmod= Units.us2000.parse(info.getString("modificationDate"));
                        Datum ago= TimeUtil.now().subtract(tmod);
                        s += "   last modified " + getDurationForHumans((long)ago.doubleValue(Units.milliseconds) ) + " ago.";
                    } catch (ParseException ex) {
                    }

                }
                messagesLabel.setText( s );
            }
            DefaultComboBoxModel m= new DefaultComboBoxModel(new String[] { "Example Time Ranges",sampleRange.toString() } );
            exampleTimeRangesCB.setModel(m);

            if ( providedTimeRange==null ) {
                timeRangeComboBox.setText( sampleRange.toString() );
            }
        }
                    
    }
    
    private void resetId( final URL server, final String id ) {
        
        final JSONObject info;
        try {
            info = HapiServer.getInfo( server, id );
            currentInfo= info;
        } catch (IOException | JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
            currentInfo= null;
            resetIdReportError(server, id, ex);
            return;
        }
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    resetIdImmediately( id, info );
                } catch ( JSONException ex) {
                    resetIdReportError(server, id, ex);
                }
            }
        };
        SwingUtilities.invokeLater(run);
                            
    }
    
    private static final int MAX_LENGTH_CHARACTERS = 100000;
    
    public static void main( String[] args ) {
        JOptionPane.showConfirmDialog( null, new HapiDataSourceEditorPanel() );
    }
    
}
