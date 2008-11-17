/*
 * MetaDataPanel.java
 *
 * Created on July 27, 2007, 11:54 AM
 */
package org.virbo.autoplot;

import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.datum.Units;
import org.das2.util.CombinedTreeModel;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsutil.PropertiesTreeModel;
import org.virbo.metatree.NameValueTreeModel;

/**
 *
 * @author  jbf
 */
public class MetaDataPanel extends javax.swing.JPanel {

    ApplicationModel applicationModel;
    CombinedTreeModel tree;
    
    PropertiesTreeModel dsTree;
    private QDataSet dsTreeDs;
    
    /** Creates new form MetaDataPanel */
    public MetaDataPanel(ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        initComponents();
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            metaDataTree.setModel(null);
        } } );
        applicationModel.addPropertyChangeListener(this.appModelListener);
        update();
    }

    public void update() {
        tree = new CombinedTreeModel("metadata");
        try {
            DataSource dsrc = applicationModel.dataSource();
            if (dsrc != null) {
                ProgressMonitor mon = new NullProgressMonitor();
                Map<String,Object> meta= dsrc.getMetaData(mon);
                MetadataModel model= dsrc.getMetadataModel();
                if ( model==null ) {
                    model= MetadataModel.createNullModel();
                }
                String root= "Metadata ("+ model.getLabel()+")";
                        
                final TreeModel dsrcMeta = NameValueTreeModel.create( root, meta );
                if (dsrcMeta != null) {
                    SwingUtilities.invokeLater( new Runnable() { public void run() {
                        tree.mountTree( dsrcMeta, 10 );
                        metaDataTree.setModel(tree);
                    } } );
                }
            } else {
                SwingUtilities.invokeLater( new Runnable() { public void run() {
                    metaDataTree.setModel(tree);
                } } );
            }
        } catch (Exception e) {
            applicationModel.application.getExceptionHandler().handle(e);
        }
    }
    PropertyChangeListener appModelListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_FILL)) {
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

    private void updateStatistics() {
        statisticsDirty = true;
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                if (statisticsDirty) {
                    updateStatisticsImmediately();
                    updateDataSetPropertiesView();
                }
            }
        });
    }

    private synchronized void updateDataSetPropertiesView() {
        if ( applicationModel.dataset==null ) {
            return;
        }
        if ( applicationModel.dataset!= this.dsTreeDs ) {
            dsTree= new PropertiesTreeModel("dataset= ",applicationModel.dataset);
            this.dsTreeDs= applicationModel.dataset;
        }
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                tree.mountTree( dsTree, 30 );
            }
        });
    }

    private synchronized void updateStatisticsImmediately() {

        QDataSet ds = applicationModel.fillDataset;
        if (ds == null) {
            // TODO: do something to indicate state
            return;
        }

        AutoplotUtil.MomentDescriptor moments = AutoplotUtil.moment(ds);

        final LinkedHashMap map = new LinkedHashMap();

        map.put("# invalid", String.valueOf(moments.invalidCount) + " of " + String.valueOf(moments.validCount + moments.invalidCount));
        String s;
        if (moments.validCount > 0) {
            s = format(moments.moment[0]);
        } else {
            s = "";
        }
        map.put("Mean", s);

        if (moments.validCount > 1) {
            s = format(moments.moment[1]);
        } else {
            s = "";
        }
        map.put("Std Dev", s);

        QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);
        
        Double cadence;
        Units xunits;
        
        if ( dep0==null ) {
            xunits= Units.dimensionless;
            cadence= 1.;
        } else {
            cadence = (Double) dep0.property(QDataSet.CADENCE);
            xunits = (Units) dep0.property(QDataSet.UNITS);
        }
        if (xunits == null) {
            xunits = Units.dimensionless;
        }

        if ( cadence!=null ) {
            Datum d = DatumUtil.asOrderOneUnits(xunits.getOffsetUnits().createDatum(cadence));
            Units u = d.getUnits();
            map.put("Cadence", format(d.doubleValue(u)) + " " + u);
        } else {
            map.put("Cadence", "null" );
        }

        SwingUtilities.invokeLater( new Runnable() {
            public void run( ) {
                tree.mountTree( NameValueTreeModel.create("Statistics", map), 20 );
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
