/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * LayoutPanel.java
 *
 * Created on Mar 7, 2009, 6:24:23 AM
 */
package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.graph.DasPlot;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.CanvasController;
import org.virbo.autoplot.dom.Column;
import org.virbo.autoplot.dom.DomOps;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.PanelStyle;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.Row;
import org.virbo.autoplot.util.CanvasLayoutPanel;

/**
 *
 * @author jbf
 */
public class LayoutPanel extends javax.swing.JPanel {

    /** Creates new form LayoutPanel */
    public LayoutPanel() {
        initComponents();
        updateList();
        canvasLayoutPanel1.addPropertyChangeListener(CanvasLayoutPanel.PROP_COMPONENT, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Plot plot = app.getController().getPlotFor((Component) canvasLayoutPanel1.getComponent());
                if (plot != null) {
                    app.getController().setPlot(plot);
                }
            }
        });
        panelListComponent.addListSelectionListener(panelSelectionListener);

        createPopupMenus();

        MouseListener popupTrigger = createPopupTrigger();
        canvasLayoutPanel1.addMouseListener(popupTrigger);
        panelListComponent.addMouseListener(popupTrigger);
    }

    private MouseListener createPopupTrigger() {
        return new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = contextMenus.get(e.getComponent());
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = contextMenus.get(e.getComponent());
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };
    }
    ;
    Map<Component, JPopupMenu> contextMenus = null;

    Action removeBindingsAction= new AbstractAction("Remove Bindings") {

            public void actionPerformed(ActionEvent e) {
                Plot domPlot = app.getController().getPlot();
                List<Panel> panels = app.getController().getPanelsFor(domPlot);
                for (Panel pan : panels) {
                    app.getController().unbind(pan);
                }
                app.getController().unbind(domPlot);
            }
        };

        Action editPlotPropertiesAction= new AbstractAction("Edit Plot Properties") {

            public void actionPerformed(ActionEvent e) {
                DasPlot component= (DasPlot)canvasLayoutPanel1.getComponent();
                Plot domPlot = app.getController().getPlotFor(component);
                List<Object> components= canvasLayoutPanel1.getSelectedComponents();
                Plot[] plots= new Plot[components.size()];
                for ( int i=0; i<components.size(); i++ ) plots[i]= app.getController().getPlotFor( (Component) components.get(i) );
                if ( components.size()>1 ) {
                    PropertyEditor edit = PropertyEditor.createPeersEditor(domPlot,plots);
                    edit.showDialog(LayoutPanel.this);
                } else {
                    PropertyEditor edit = new PropertyEditor(domPlot);
                    edit.showDialog(LayoutPanel.this);
                }
            }
        };

        Action deletePlotAction= new AbstractAction("Delete Plot") {

            public void actionPerformed(ActionEvent e) {
                List<Object> os= canvasLayoutPanel1.getSelectedComponents();
                for ( Object o: os ) {
                    if (app.getPlots().length > 1) {
                        Plot domPlot=null;
                        if ( o instanceof Component ) {
                            domPlot= app.getController().getPlotFor((Component)o);
                        }
                        if ( domPlot==null ) continue;
                        List<Panel> panels = app.getController().getPanelsFor(domPlot);
                        for (Panel pan : panels) {
                            if (app.getPanels().length > 1) {
                                app.getController().deletePanel(pan);
                            } else {
                                app.getController().setStatus("warning: the last panel may not be deleted");
                            }
                        }
                        app.getController().deletePlot(domPlot);
                    } else {
                        app.getController().setStatus("warning: last plot may not be deleted");
                    }
                }
            }
        };

        Action addPlotsAction= new AbstractAction("Add Plots...") {

            public void actionPerformed(ActionEvent e) {
                AddPlotsDialog dia= new AddPlotsDialog();
                dia.getNumberOfColumnsTextField().setValue(1);
                dia.getNumberOfRowsTextField().setValue(1);
                if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog(panelListComponent, dia, "Add Plots", JOptionPane.OK_CANCEL_OPTION ) ) {
                     int nr= (Integer)dia.getNumberOfRowsTextField().getValue();
                     int nc= (Integer)dia.getNumberOfColumnsTextField().getValue();
                     app.getController().addPlots( nr,nc );
                }
            }
        };




    private synchronized void createPopupMenus() {
        contextMenus = new HashMap<Component, JPopupMenu>();

        JMenuItem item;

        contextMenus.put( canvasLayoutPanel1, plotActionsMenu );

        JPopupMenu panelContextMenu = new JPopupMenu();

        item = new JMenuItem(new AbstractAction("Edit Panel Properties") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                Panel p= (Panel)panelListComponent.getSelectedValue();
                PropertyEditor edit;
                if ( os.length==0 ) {
                    return;
                } else if ( os.length==1 ) {
                    edit = new PropertyEditor(p);
                } else {
                    Panel[] peers= new Panel[os.length];
                    for ( int i=0; i<os.length; i++ ) peers[i]= (Panel)os[i];
                    edit= PropertyEditor.createPeersEditor( p, peers );
                }
                edit.showDialog(LayoutPanel.this);
            }
        });
        item.setToolTipText("edit the panel or panels");
        panelContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Edit Style Properties") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                Panel p= (Panel)panelListComponent.getSelectedValue();
                PropertyEditor edit;
                if ( os.length==0 ) {
                    return;
                } else if ( os.length==1 ) {
                    edit = new PropertyEditor(p.getStyle());
                } else {
                    PanelStyle[] peers= new PanelStyle[os.length];
                    for ( int i=0; i<os.length; i++ ) peers[i]= ((Panel)os[i]).getStyle();
                    edit= PropertyEditor.createPeersEditor( p.getStyle(), peers );
                }
                edit.showDialog(LayoutPanel.this);
            }
        });

        item.setToolTipText("edit the style of panel or panels");
        panelContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Panel") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                for ( Object o : os ) {
                    Panel panel = (Panel) o;
                    app.getController().deletePanel(panel);
                }
                
            }
        });
        panelContextMenu.add(item);

        contextMenus.put(panelListComponent, panelContextMenu);
    }
    transient ListSelectionListener panelSelectionListener = new ListSelectionListener() {

        public void valueChanged(ListSelectionEvent e) {
            if ( panelListComponent.getValueIsAdjusting() ) return;
            if (panelListComponent.getSelectedValues().length == 1) {
                if ( ! app.getController().isValueAdjusting() ) {
                    Panel p = (Panel) panelListComponent.getSelectedValue();
                    Plot plot = app.getController().getPlotFor(p);
                    app.getController().setPlot(plot);
                    app.getController().setPanel(p);
                }
            }
        }
    };
    Application app;
    AbstractListModel panelList;
    transient PropertyChangeListener panelsListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            updateList();
        }
    };
    transient private PropertyChangeListener plotListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            Plot plot= app.getController().getPlot();
            if ( plot==null ) {
                return;
            }
            List<Panel> p = app.getController().getPanelsFor(plot);
            List<Panel> allPanels = Arrays.asList(app.getPanels());
            List<Integer> indices = new ArrayList<Integer>();
            for (int i = 0; i < p.size(); i++) {
                if ( p.get(i).isActive() ) indices.add( allPanels.indexOf(p.get(i)) );
            }
            int[] iindices= new int[indices.size()];
            for ( int i=0; i<indices.size(); i++ ) iindices[i]= indices.get(i);
            panelListComponent.setSelectedIndices(iindices);
            DasPlot dasPlot = app.getController().getPlot().getController().getDasPlot();
            canvasLayoutPanel1.setComponent(dasPlot);
        }
    };
    transient private PropertyChangeListener panelListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            Panel p = app.getController().getPanel();
            List<Panel> allPanels = Arrays.asList(app.getPanels());
            panelListComponent.setSelectedIndex(allPanels.indexOf(p));
        }
    };

    public void setApplication(Application app) {
        this.app = app;
        canvasLayoutPanel1.setContainer(app.getController().getDasCanvas());
        canvasLayoutPanel1.addComponentType(DasPlot.class, Color.BLUE);
        app.getController().bind(app.getOptions(), Options.PROP_BACKGROUND, canvasLayoutPanel1, "background");
        app.addPropertyChangeListener(Application.PROP_PANELS, panelsListener);
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT, plotListener);
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PANEL, panelListener);
    }

    private void updateList() {
        panelList = new AbstractListModel() {
            public int getSize() {
                return app.getPanels().length;
            }

            public Object getElementAt(int index) {
                return app.getPanels(index);
            }
        };
        panelListComponent.setModel(panelList);
    }

    /**
     * return a list of the selected plots, with the primary selection the first
     * item.
     * @return
     */
    private List<Plot> getSelectedPlots( ) {
        List<Object> os= canvasLayoutPanel1.getSelectedComponents();
        List<Plot> result= new ArrayList();

        for ( Object o: os ) {
            if (app.getPlots().length > 1) {
                Plot domPlot=null;
                if ( o instanceof Component ) {
                    domPlot= app.getController().getPlotFor((Component)o);
                }
                if ( domPlot==null ) continue;
                result.add(domPlot);
            }
        }
        Object o= canvasLayoutPanel1.getComponent();
        if ( o instanceof Component ) {
            Plot domPlot= app.getController().getPlotFor((Component)o);
            if ( domPlot!=null ) {
                result.remove(domPlot);
                result.add(0, domPlot);
            }
        }
        return result;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        plotActionsMenu = new javax.swing.JPopupMenu();
        plotMenu = new javax.swing.JMenu();
        propertiesMenuItem = new javax.swing.JMenuItem(editPlotPropertiesAction);
        deleteMenuItem = new javax.swing.JMenuItem(deletePlotAction);
        addPlotsBelowMenuItem = new javax.swing.JMenuItem(addPlotsAction);
        removeBindingsMenuItem = new javax.swing.JMenuItem(removeBindingsAction);
        plotsMenu = new javax.swing.JMenu();
        swapMenuItem = new javax.swing.JMenuItem();
        jScrollPane1 = new javax.swing.JScrollPane();
        panelListComponent = new javax.swing.JList();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        canvasLayoutPanel1 = new org.virbo.autoplot.util.CanvasLayoutPanel();

        plotMenu.setText("Plot");

        propertiesMenuItem.setText("Properties...");
        propertiesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                propertiesMenuItemActionPerformed(evt);
            }
        });
        plotMenu.add(propertiesMenuItem);

        deleteMenuItem.setText("Delete");
        deleteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteMenuItemActionPerformed(evt);
            }
        });
        plotMenu.add(deleteMenuItem);

        addPlotsBelowMenuItem.setText("Add Plots Below...");
        addPlotsBelowMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addPlotsBelowMenuItemActionPerformed(evt);
            }
        });
        plotMenu.add(addPlotsBelowMenuItem);

        removeBindingsMenuItem.setText("Remove Bindings");
        removeBindingsMenuItem.setToolTipText("Remove bindings to other parts of the application");
        plotMenu.add(removeBindingsMenuItem);

        plotActionsMenu.add(plotMenu);

        plotsMenu.setText("Plots");

        swapMenuItem.setText("Swap Position");
        swapMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swapMenuItemActionPerformed(evt);
            }
        });
        plotsMenu.add(swapMenuItem);

        plotActionsMenu.add(plotsMenu);

        panelListComponent.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(panelListComponent);

        jLabel1.setText("Panels:");

        jLabel2.setText("Plots:");

        canvasLayoutPanel1.setText("canvasLayoutPanel1");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jLabel2)
                    .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 189, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel1)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(jLabel2))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 277, Short.MAX_VALUE)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 277, Short.MAX_VALUE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void propertiesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_propertiesMenuItemActionPerformed
        DasPlot component= (DasPlot)canvasLayoutPanel1.getComponent();
                Plot domPlot = app.getController().getPlotFor(component);
                List<Object> components= canvasLayoutPanel1.getSelectedComponents();
                Plot[] plots= new Plot[components.size()];
                for ( int i=0; i<components.size(); i++ ) plots[i]= app.getController().getPlotFor( (Component) components.get(i) );
                if ( components.size()>1 ) {
                    PropertyEditor edit = PropertyEditor.createPeersEditor(domPlot,plots);
                    edit.showDialog(LayoutPanel.this);
                } else {
                    PropertyEditor edit = new PropertyEditor(domPlot);
                    edit.showDialog(LayoutPanel.this);
                }
}//GEN-LAST:event_propertiesMenuItemActionPerformed

    private void deleteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMenuItemActionPerformed
                        List<Object> os= canvasLayoutPanel1.getSelectedComponents();
                for ( Object o: os ) {
                    if (app.getPlots().length > 1) {
                        Plot domPlot=null;
                        if ( o instanceof Component ) {
                            domPlot= app.getController().getPlotFor((Component)o);
                        }
                        if ( domPlot==null ) continue;
                        List<Panel> panels = app.getController().getPanelsFor(domPlot);
                        for (Panel pan : panels) {
                            if (app.getPanels().length > 1) {
                                app.getController().deletePanel(pan);
                            } else {
                                app.getController().setStatus("warning: the last panel may not be deleted");
                            }
                        }
                        app.getController().deletePlot(domPlot);
                    } else {
                        app.getController().setStatus("warning: last plot may not be deleted");
                    }
                }

    }//GEN-LAST:event_deleteMenuItemActionPerformed

    private void swapMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swapMenuItemActionPerformed
        List<Plot> plots= getSelectedPlots();
        if ( plots.size()==2 ) {
            DomOps.swapPosition( plots.get(0), plots.get(1) );
            this.app.getController().setStatus("swapped "+plots.get(0)+ " and " +plots.get(1) );
        } else {
            this.app.getController().setStatus("warning: select two plots");
        }
    }//GEN-LAST:event_swapMenuItemActionPerformed

    private void addPlotsBelowMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addPlotsBelowMenuItemActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_addPlotsBelowMenuItemActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem addPlotsBelowMenuItem;
    private org.virbo.autoplot.util.CanvasLayoutPanel canvasLayoutPanel1;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList panelListComponent;
    private javax.swing.JPopupMenu plotActionsMenu;
    private javax.swing.JMenu plotMenu;
    private javax.swing.JMenu plotsMenu;
    private javax.swing.JMenuItem propertiesMenuItem;
    private javax.swing.JMenuItem removeBindingsMenuItem;
    private javax.swing.JMenuItem swapMenuItem;
    // End of variables declaration//GEN-END:variables
}
