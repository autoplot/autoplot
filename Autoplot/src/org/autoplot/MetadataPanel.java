
package org.autoplot;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import org.das2.datum.DatumUtil;
import org.das2.util.CombinedTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.util.LoggerManager;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.DataSourceController;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementController;
import org.das2.qds.DataSetUtil;
import org.das2.qds.JoinDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.RankZeroDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.MetadataModel;
import org.das2.qds.util.AutoHistogram;
import org.das2.qds.util.PropertiesTreeModel;
import org.autoplot.metatree.NameValueTreeModel;
import org.das2.datum.Datum;
import org.das2.datum.Units;

/**
 * MetadataPanel shows statistics and metadata for the dataset, such as units and fill values.
 * @author  jbf
 */
public class MetadataPanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    Application dom;
    CombinedTreeModel tree;
    TreeModel dsTree;
    private QDataSet dsTreeDs;
    TreeModel componentDataSetTree=null;
    DataSourceFilter bindToDataSourceFilter = null;  //TODO: these should be weak references or such.
    PlotElement bindToPlotElement =null;
    private NameValueTreeModel statsTree;

    Thread updateComponentDataSetThread= null;
    Thread updateStatisticsThread= null;
    
    private static final Logger logger= LoggerManager.getLogger("gui.metadata");
    
    public MetadataPanel(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom = applicationModel.getDocumentModel();
        initComponents();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                metaDataTree.setModel(null);
            }
        });

        dom.getController().addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                bindToDataSourceFilter( dom.getController().getDataSourceFilter() );
            }
        });

        dom.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                bindToPlotElement( dom.getController().getPlotElement() );
            }
        });

        DataSourceFilter dsf= dom.getController().getDataSourceFilter();
        if ( dsf!=null ) bindToDataSourceFilter(dsf);

        //applicationModel.addPropertyChangeListener(this.appModelListener);
        updateProperties();
        updateStatistics();
        updateComponentDataSet();
        bindToPlotElement(dom.getController().getPlotElement());

        MouseListener popupTrigger = createPopupTrigger();
        metaDataTree.addMouseListener( popupTrigger );
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "metadataPanel");
        
        Timer t= new Timer( 1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( MetadataPanel.this.isShowing() ) {
                    if ( updateComponentDataSetThread!=null ) {
                        logger.fine("updating component");
                        updateComponentDataSetThread.start();
                        updateComponentDataSetThread= null; // let it run until there's another update.
                    }
                    if ( updateStatisticsThread!=null ) {
                        logger.fine("updating statistics");
                        updateStatisticsThread.start();
                        updateStatisticsThread= null; // let it run until there's another update.
                    }
                }
            }
        } );
        t.setRepeats(true);
        t.start();
    }

    private MouseListener createPopupTrigger() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = jPopupMenu1;
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = jPopupMenu1;
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };
    }
    
    private void bindToDataSourceFilter( DataSourceFilter dsf ) {
        if (bindToDataSourceFilter != null) {
            DataSourceController dsc = bindToDataSourceFilter.getController();
            dsc.removePropertyChangeListener(propertiesListener);
            dsc.removePropertyChangeListener(fillListener);
        }
        if ( dsf!=null ) {
            dsf.getController().addPropertyChangeListener(DataSourceController.PROP_RAWPROPERTIES, propertiesListener);
            dsf.getController().addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillListener);
        }
        bindToDataSourceFilter= dsf; // BUGFIX
        updateProperties();
        updateStatistics();
    }

    private void bindToPlotElement( PlotElement pe ) {
        if (bindToPlotElement != null) {
            PlotElementController pec = bindToPlotElement.getController();
            pec.removePropertyChangeListener(componentListener);
        }
        if ( pe!=null ) {
            pe.getController().addPropertyChangeListener(DataSourceController.PROP_DATASET, componentListener );
        }
        bindToPlotElement= pe;
        updateComponentDataSet();

    }

    private void updateProperties() {

        try {
            DataSourceFilter dsf = dom.getController().getDataSourceFilter();
            DataSourceController dsfc= null;
            DataSource dataSource = null;
            if (dsf != null) {
                dsfc= dsf.getController();
                dataSource = dsfc.getDataSource();
            }
            if ( dsfc==null ) {
                String label = "(data source controller is null)";
                tree = new CombinedTreeModel(label);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        metaDataTree.setModel(tree);
                    }
                });

            } else if (dataSource != null) {
                tree = new CombinedTreeModel("" + dataSource.getURI());
                Map<String, Object> meta = dsfc.getRawProperties(); //TODO: consider that dataSource.getMetadata() might be better.  This might result in extra read for some sources.
                MetadataModel model = dataSource.getMetadataModel();
                String root = "Metadata";
                if (model != null) {
                    if (!model.getLabel().equals("")) {
                        root = root + "(" + model.getLabel() + ")";
                    }
                }

                final TreeModel dsrcMeta = NameValueTreeModel.create(root, meta);
                if (dsrcMeta != null) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            tree.mountTree(dsrcMeta, 10);
                            metaDataTree.setModel(tree);
                        }
                    });
                }

            } else {
                String label = "(no data source)";
                if ( dsfc.getDataSet() != null) {  
                    label = "dataset";
                }
                tree = new CombinedTreeModel(label);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        metaDataTree.setModel(tree);
                    }
                });
                
            }
        } catch (Exception e) {
            tree = new CombinedTreeModel("Exception: " + e);
            applicationModel.getExceptionHandler().handle(e);
        }
    }
    transient PropertyChangeListener propertiesListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(DataSourceController.PROP_RAWPROPERTIES)) {
                updateProperties();
            }
        }
    };
    /**
     * update when the fill dataset changes.
     */
    transient PropertyChangeListener fillListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(DataSourceController.PROP_FILLDATASET)) {
                //System.err.println("fillChanged: "+evt+" "+evt.getPropertyName()+" "+evt.getOldValue()+" "+evt.getNewValue());
                updateStatistics();
            }
        }
    };

    /**
     * update when the fill dataset changes.
     */
    transient PropertyChangeListener componentListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(PlotElementController.PROP_DATASET )) {
                updateComponentDataSet();
            }
        }
    };

    private void updateComponentDataSet() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                updateComponentDataSetPropertiesView();
            }
        };
        updateComponentDataSetThread= new Thread(run,"updateComponentDataSet");
    }

//    private String format(double d) {
//        if (Math.abs(Math.log(d) / Math.log(10)) < 3) {
//            DecimalFormat df1 = new DecimalFormat("0.00");
//            return df1.format(d);
//        } else {
//            DecimalFormat df = new DecimalFormat("0.00E0");
//            return df.format(d);
//        }
//    }
    boolean statisticsDirty;

    /**
     * create a string showing a histogram.  
     * @see org.das2.qds.DataSetUtil.toSparkline
     */
//    private String histStr(QDataSet ds) {
//        QDataSet hist = Ops.histogram(ds, 20);
//        QDataSet dep0 = (QDataSet) hist.property(QDataSet.DEPEND_0);
//        Datum res = DataSetUtil.asDatum((RankZeroDataSet) dep0.property(QDataSet.CADENCE));
//        Units u = (Units) dep0.property(QDataSet.UNITS);
//        if (u == null) {
//            u = Units.dimensionless;
//        }
//
//        String scale;
//        if (metaDataTree.getFont().canDisplay((char) 2581)) {
//            scale = "\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588";
//        } else {
//            scale = " .:!#";
//        }
//        int scaleMax = scale.length();
//
//        Integer max = (Integer) hist.property("max");
//
//        StringBuffer s = new StringBuffer();
//
//        s.append("" + Datum.create(dep0.value(0), u, res.doubleValue(u.getOffsetUnits())) + " ");
//        for (int i = 0; i < hist.length(); i++) {
//            int norm = (int) hist.value(i) * scaleMax / max;
//            if (norm == scaleMax) {
//                norm = scaleMax - 1; // make last bin inclusive
//            }            //s.append( (char)(2581+norm) );
//            s.append(scale.charAt(norm));
//        }
//        s.append(" " + Datum.create(dep0.value(dep0.length() - 1), u, res.doubleValue(u.getOffsetUnits())));
//        if ("log".equals(dep0.property(QDataSet.SCALE_TYPE))) {
//            s.append(" log");
//        }
//        return s.toString();
//    }

    /**
     * this will update the dataset and "processed dataset" nodes.
     */
    private void updateStatistics() {
        statisticsDirty = true;
        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (statisticsDirty) {
                    updateStatisticsImmediately();
                    updateDataSetPropertiesView();
                    updateComponentDataSetPropertiesView();
                }
            }
        };
        updateStatisticsThread= new Thread( run, "updateStats" );
    }

    private synchronized void updateDataSetPropertiesView() {
        assert EventQueue.isDispatchThread() == false;
        final TreeModel unmount;
        DataSourceFilter dsf = dom.getController().getDataSourceFilter();
        QDataSet ds= dsf.getController().getDataSet();
        if ( ds == null) {
            unmount = dsTree;
            dsTree= NameValueTreeModel.create("Dataset", java.util.Collections.singletonMap("dataset", "(no dataset)") );
            this.dsTreeDs = null;
        } else {
            if ( ds != this.dsTreeDs) {
                unmount = dsTree;
                dsTree = new PropertiesTreeModel("Dataset= ", ds, 20);
                this.dsTreeDs = ds;
            } else {
                unmount = null;
            }
        }
        updateComponentDataSetPropertiesView();
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (unmount != null) {
                    tree.unmountTree(unmount);
                }
                tree.mountTree(dsTree, 30);
            }
        });
    }

    /**
     * update the "Processed Dataset" node
     */
    private synchronized void updateComponentDataSetPropertiesView() {
        assert EventQueue.isDispatchThread() == false;
        final TreeModel unmount;
        PlotElement pe= dom.getController().getPlotElement();
        if ( pe==null ) {
            unmount = componentDataSetTree;
            componentDataSetTree= NameValueTreeModel.create("Processed Dataset", java.util.Collections.singletonMap("dataset", "(no dataset)") );
        } else {
            QDataSet ds= pe.getController().getDataSet();
            if ( ds == null) {
                unmount = componentDataSetTree;
                componentDataSetTree= NameValueTreeModel.create("Processed Dataset", java.util.Collections.singletonMap("dataset", "(no dataset)") );
            } else {
                unmount = componentDataSetTree;
                if ( ds!=this.dsTreeDs ) {
                    componentDataSetTree = new PropertiesTreeModel("Processed Dataset= ", ds, 20);
                } else {
                    componentDataSetTree= NameValueTreeModel.create("Processed Dataset contains no additional processing", java.util.Collections.singletonMap("dataset", "(no additional processing)") );
                }
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (unmount != null) {
                    tree.unmountTree(unmount);
                }
                tree.mountTree(componentDataSetTree, 40);
            }
        });
    }
    
    private QDataSet getCadenceJoin( QDataSet ds ) {
        JoinDataSet cadence= new JoinDataSet(1);
        for ( int i=0; i<ds.length(); i++ ) {
            QDataSet dep0= (QDataSet) ds.slice(0).property(QDataSet.DEPEND_0);
            if ( dep0==null ) {
                cadence.join( DataSetUtil.asDataSet(1) );
            } else {
                if ( dep0.property(QDataSet.CADENCE)!=null ) {
                    cadence.join( (QDataSet) dep0.property(QDataSet.CADENCE) );
                }
            }
        }
        return cadence;
    }

    @SuppressWarnings("unchecked")
    private synchronized void updateStatisticsImmediately() {
        assert EventQueue.isDispatchThread() == false;
        final TreeModel unmount= statsTree;
        
        DataSourceFilter dsf = dom.getController().getDataSourceFilter();
        final LinkedHashMap map = new LinkedHashMap();

        QDataSet ds = dsf.getController().getDataSet();
        if (ds == null) {
            map.put("dataset", "(no dataset)");

        } else {

            //int nelements= DataSetUtil.totalLength( ds );
            RankZeroDataSet moments ;
            long validCount;
            long invalidCount;
            String s;

            QDataSet hist = dsf.getController().getHistogram();
            map.put("Histogram", hist);

            if (hist != null) {
                moments = AutoHistogram.moments(hist);

                validCount = (Long) moments.property("validCount");
                invalidCount= (Long) moments.property("invalidCount");
                
                map.put("# invalid", "" + invalidCount + " of " + String.valueOf(validCount + invalidCount));

                if (validCount > 0) {
                    s = String.valueOf(moments);
                } else {
                    s = "";
                }
                map.put("Mean", s);

                if (validCount > 1 && moments.property("stddev")!=null ) {
                    s = String.valueOf(DatumUtil.asOrderOneUnits(DataSetUtil.asDatum((QDataSet) moments.property("stddev"))));
                } else {
                    s = "";
                }
                map.put("Std Dev", s);

                QDataSet range= AutoHistogram.simpleRange( hist );
                map.put("min", range.slice(0) );
                map.put("max", range.slice(1) );
            }

            QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);

            QDataSet cadence;

            if (dep0 == null) {
                if ( SemanticOps.isJoin(ds) ) {
                    cadence= getCadenceJoin( ds );
                } else {
                    cadence = DataSetUtil.asDataSet(1);
                }
            } else {
                cadence = (QDataSet) dep0.property(QDataSet.CADENCE);
            }

            if (cadence != null) {
                Datum d = DatumUtil.asOrderOneUnits(DataSetUtil.asDatum(cadence));
                Units u = d.getUnits();
                map.put("Cadence", d );
                if ( u.isConvertibleTo(Units.seconds) ) {
                    try {
                        map.put("Frequency (1/Cadence)",DatumUtil.asOrderOneUnits(Datum.create(1).divide(d)));
                    } catch ( IllegalArgumentException ex ) {
                        logger.log(Level.FINE,"inverse datum cannot be calculated.",ex);
                    }
                }
            } else {
                map.put("Cadence", "null");
            }

        //s= histStr( ds );
        //map.put( "Histogram", s );

        }

        statsTree= NameValueTreeModel.create("Statistics", map);
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (unmount != null) {
                    tree.unmountTree(unmount);
                }
                tree.mountTree(statsTree, 20);
            }
        });

        statisticsDirty = false;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        copyValueMenuItem = new javax.swing.JMenuItem();
        jScrollPane1 = new javax.swing.JScrollPane();
        metaDataTree = new javax.swing.JTree();

        copyMenuItem.setText("copy");
        copyMenuItem.setToolTipText("Copy item to system clip board");
        copyMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(copyMenuItem);

        copyValueMenuItem.setText("copy value");
        copyValueMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyValueMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(copyValueMenuItem);

        jScrollPane1.setViewportView(metaDataTree);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 285, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 254, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void copyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyMenuItemActionPerformed
        LoggerManager.logGuiEvent(evt);
        TreePath tp= metaDataTree.getSelectionPath();
        StringSelection stringSelection = new StringSelection( tp.getLastPathComponent().toString() );
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {
            @Override
            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        });
    }//GEN-LAST:event_copyMenuItemActionPerformed

    private void copyValueMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyValueMenuItemActionPerformed
        LoggerManager.logGuiEvent(evt);
        TreePath tp= metaDataTree.getSelectionPath();
        String s= tp.getLastPathComponent().toString();
        int i= s.indexOf('=');
        if ( i>-1 ) {
            s= s.substring(i+1);
        }
        StringSelection stringSelection = new StringSelection( s );
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, new ClipboardOwner() {
            @Override
            public void lostOwnership(Clipboard clipboard, Transferable contents) {
            }
        });
    }//GEN-LAST:event_copyValueMenuItemActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem copyValueMenuItem;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree metaDataTree;
    // End of variables declaration//GEN-END:variables
}
