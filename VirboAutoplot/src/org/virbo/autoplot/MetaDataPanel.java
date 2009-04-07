/*
 * MetaDataPanel.java
 *
 * Created on July 27, 2007, 11:54 AM
 */
package org.virbo.autoplot;

import java.awt.EventQueue;
import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.datum.Units;
import org.das2.util.CombinedTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import org.das2.system.RequestProcessor;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.AutoHistogram;
import org.virbo.dsutil.PropertiesTreeModel;
import org.virbo.metatree.NameValueTreeModel;

/**
 *
 * @author  jbf
 */
public class MetaDataPanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    Application dom;
    CombinedTreeModel tree;
    TreeModel dsTree;
    DataSourceFilter bindToDataSourceFilter = null;  //TODO: these should be weak references or such.
    private QDataSet dsTreeDs;

    /** Creates new form MetaDataPanel */
    public MetaDataPanel(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom = applicationModel.getDocumentModel();
        initComponents();

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                metaDataTree.setModel(null);
            }
        });

        dom.getController().addPropertyChangeListener(ApplicationController.PROP_PANEL, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                bindToPanel();
            }
        });

        dom.getController().addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                bindToDataSourceFilter();
            }
        });

        bindToPanel();
        bindToDataSourceFilter();

        //applicationModel.addPropertyChangeListener(this.appModelListener);
        updateProperties();
        updateStatistics();
    }

    private void bindToDataSourceFilter() {
        if (bindToDataSourceFilter != null) {
            DataSourceController dsc = bindToDataSourceFilter.getController();
            dsc.removePropertyChangeListener(propertiesListener);
            dsc.removePropertyChangeListener(fillListener);
        }
        dom.getController().getDataSourceFilter().getController().addPropertyChangeListener(DataSourceController.PROP_RAWPROPERTIES, propertiesListener);
        dom.getController().getDataSourceFilter().getController().addPropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillListener);
        updateProperties();
        updateStatistics();
    }

    private void bindToPanel() {
    }

    public void updateProperties() {

        try {
            DataSourceFilter dsf = dom.getController().getDataSourceFilter();

            DataSource dsrc = null;
            if (dsf != null) {
                dsrc = dsf.getController()._getDataSource();
            }
            if (dsrc != null) {
                tree = new CombinedTreeModel("" + dsrc.getURL());
                Map<String, Object> meta = dsf.getController().getRawProperties();  // findbugs NP_GUARANTEED_DEREF okay
                MetadataModel model = dsrc.getMetadataModel();
                String root = "Metadata";
                if (model != null) {
                    if (!model.getLabel().equals("")) {
                        root = root + "(" + model.getLabel() + ")";
                    }
                }

                final TreeModel dsrcMeta = NameValueTreeModel.create(root, meta);
                if (dsrcMeta != null) {
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            tree.mountTree(dsrcMeta, 10);
                            metaDataTree.setModel(tree);
                        }
                    });
                }
            } else {
                String label = "(no data source)";
                if (dsf.getController().getDataSet() != null) {  // findbugs indicates NP_GUARANTEED_DEREF, but I don't see it. JBF
                    label = "dataset";
                }
                tree = new CombinedTreeModel(label);
                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        metaDataTree.setModel(tree);
                    }
                });
            }
        } catch (Exception e) {
            tree = new CombinedTreeModel("Exception: " + e);
            applicationModel.application.getExceptionHandler().handle(e);
        }
    }
    transient PropertyChangeListener propertiesListener = new PropertyChangeListener() {

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

        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(DataSourceController.PROP_FILLDATASET)) {
                //System.err.println("fillChanged: "+evt+" "+evt.getPropertyName()+" "+evt.getOldValue()+" "+evt.getNewValue());
                updateStatistics();
            }
        }
    };

    private String format(double d) {
        if (Math.abs(Math.log(d) / Math.log(10)) < 3) {
            DecimalFormat df1 = new DecimalFormat("0.00");
            return df1.format(d);
        } else {
            DecimalFormat df = new DecimalFormat("0.00E0");
            return df.format(d);
        }
    }
    boolean statisticsDirty;

    private String histStr(QDataSet ds) {
        QDataSet hist = Ops.histogram(ds, 20);
        QDataSet dep0 = (QDataSet) hist.property(QDataSet.DEPEND_0);
        Datum res = DataSetUtil.asDatum((RankZeroDataSet) dep0.property(QDataSet.CADENCE));
        Units u = (Units) dep0.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        String scale;
        if (metaDataTree.getFont().canDisplay((char) 2581)) {
            scale = "\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588";
        } else {
            scale = " .:!#";
        }
        int scaleMax = scale.length();

        Integer max = (Integer) hist.property("max");

        StringBuffer s = new StringBuffer();

        s.append("" + Datum.create(dep0.value(0), u, res.doubleValue(u.getOffsetUnits())) + " ");
        for (int i = 0; i < hist.length(); i++) {
            int norm = (int) hist.value(i) * scaleMax / max;
            if (norm == scaleMax) {
                norm = scaleMax - 1; // make last bin inclusive
            }            //s.append( (char)(2581+norm) );
            s.append(scale.charAt(norm));
        }
        s.append(" " + Datum.create(dep0.value(dep0.length() - 1), u, res.doubleValue(u.getOffsetUnits())));
        if ("log".equals(dep0.property(QDataSet.SCALE_TYPE))) {
            s.append(" log");
        }
        return s.toString();
    }

    private void updateStatistics() {
        statisticsDirty = true;
        Runnable run = new Runnable() {

            public void run() {
                if (statisticsDirty) {
                    updateStatisticsImmediately();
                    updateDataSetPropertiesView();
                }
            }
        };
        RequestProcessor.invokeLater(run);
    }

    private synchronized void updateDataSetPropertiesView() {
        assert EventQueue.isDispatchThread() == false;
        final TreeModel unmount;
        DataSourceFilter dsf = dom.getController().getDataSourceFilter();
        if (dsf.getController().getDataSet() == null) {
            //dsTree= NameValueTreeModel.create("dataset", Collections.singletonMap("dataset=", "no dataset") );
            //(PropertiesTreeModel( "no dataset", null );
            return;
        } else {
            if (dsf.getController().getDataSet() != this.dsTreeDs) {
                unmount = dsTree;
                dsTree = new PropertiesTreeModel("Dataset= ", dsf.getController().getDataSet(), 20);
                this.dsTreeDs = dsf.getController().getDataSet();
            } else {
                unmount = null;
            }
        }
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                if (unmount != null) {
                    tree.unmountTree(unmount);
                }
                tree.mountTree(dsTree, 30);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private synchronized void updateStatisticsImmediately() {
        assert EventQueue.isDispatchThread() == false;

        DataSourceFilter dsf = dom.getController().getDataSourceFilter();
        final LinkedHashMap map = new LinkedHashMap();

        QDataSet ds = dsf.getController().getDataSet();
        if (ds == null) {
            map.put("dataset", "(no dataset)");

        } else {

            RankZeroDataSet moments = DataSetOps.moment(ds);

            long validCount = (Integer) moments.property("validCount");
            long invalidCount = (Integer) moments.property("invalidCount");
            map.put("# invalid", "" + invalidCount + " of " + String.valueOf(validCount + invalidCount));
            String s;
            if (validCount > 0) {
                s = String.valueOf(moments);
            } else {
                s = "";
            }
            map.put("Mean", s);

            if (validCount > 1) {
                s = String.valueOf(DatumUtil.asOrderOneUnits(DataSetUtil.asDatum((RankZeroDataSet) moments.property("stddev"))));
            } else {
                s = "";
            }
            map.put("Std Dev", s);

            QDataSet hist = dsf.getController().getHistogram();
            map.put("Histogram", hist);

            if (hist != null) {
                moments = AutoHistogram.moments(hist);
                validCount = (Long) moments.property("validCount");

                if (validCount > 0) {
                    s = String.valueOf(moments);
                } else {
                    s = "";
                }
                map.put("AH_Mean", s);

                if (validCount > 1) {
                    s = String.valueOf(DatumUtil.asOrderOneUnits(DataSetUtil.asDatum((RankZeroDataSet) moments.property("stddev"))));
                } else {
                    s = "";
                }
                map.put("AH_Std Dev", s);
            }

            QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);

            RankZeroDataSet cadence;

            if (dep0 == null) {
                cadence = DataSetUtil.asDataSet(1);
            } else {
                cadence = (RankZeroDataSet) dep0.property(QDataSet.CADENCE);
            }

            if (cadence != null) {
                Datum d = DatumUtil.asOrderOneUnits(DataSetUtil.asDatum(cadence));
                Units u = d.getUnits();
                map.put("Cadence", format(d.doubleValue(u)) + " " + u);
            } else {
                map.put("Cadence", "null");
            }

        //s= histStr( ds );
        //map.put( "Histogram", s );

        }

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                tree.mountTree(NameValueTreeModel.create("Statistics", map), 20);
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

        jScrollPane1 = new javax.swing.JScrollPane();
        metaDataTree = new javax.swing.JTree();

        jScrollPane1.setViewportView(metaDataTree);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 404, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 323, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree metaDataTree;
    // End of variables declaration//GEN-END:variables
}
