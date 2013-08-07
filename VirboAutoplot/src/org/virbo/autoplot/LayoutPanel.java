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
import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.Renderer;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.BindingModel;
import org.virbo.autoplot.dom.Column;
import org.virbo.autoplot.dom.DomOps;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.PlotElementStyle;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotElementController;
import org.virbo.autoplot.dom.Row;
import org.virbo.autoplot.util.CanvasLayoutPanel;

/**
 * LayoutPanel shows all the plots and plot elements on the canvas.  
 * @author jbf
 */
public class LayoutPanel extends javax.swing.JPanel {

    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.layout");

    /** Creates new form LayoutPanel */
    public LayoutPanel() {
        initComponents();
        canvasLayoutPanel1.addPropertyChangeListener(CanvasLayoutPanel.PROP_COMPONENT, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                Plot plot = app.getController().getPlotFor((Component) canvasLayoutPanel1.getComponent());
                List<Object> p= canvasLayoutPanel1.getSelectedComponents();
                if (plot != null) {
                    app.getController().setPlot(plot);
                    canvasLayoutPanel1.setSelectedComponents(p);
                }
            }
        });
        panelListComponent.addListSelectionListener(plotElementSelectionListener);

        createPopupMenus();

        MouseListener popupTrigger = createPopupTrigger();
        canvasLayoutPanel1.addMouseListener(popupTrigger);
        panelListComponent.addMouseListener(popupTrigger);
        bindingListComponent.addMouseListener(popupTrigger);

        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "layoutPanel");
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
                List<PlotElement> elements = app.getController().getPlotElementsFor(domPlot);
                for (PlotElement element : elements) {
                    app.getController().unbind(element);
                }
                app.getController().unbind(domPlot);
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
                        List<PlotElement> plotElements = app.getController().getPlotElementsFor(domPlot);
                        for (PlotElement pan : plotElements) {
                            if (app.getPlotElements().length > 1) {
                                app.getController().deletePlotElement(pan);
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
                dia.getNumberOfColumnsSpinner().setModel( new SpinnerNumberModel(1,1,5,1) );
                dia.getNumberOfRowsSpinner().setModel( new SpinnerNumberModel(1,1,5,1) );
                if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog(panelListComponent, 
                        dia, "Add Plots", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE, 
                        new ImageIcon( AutoplotUtil.getAutoplotIcon() ) ) ) {
                    int nr= (Integer)dia.getNumberOfRowsSpinner().getValue();
                    int nc= (Integer)dia.getNumberOfColumnsSpinner().getValue();
                    if ( nr>5 || nc>5 ) {
                        JOptionPane.showMessageDialog( LayoutPanel.this, "No more than 5 rows or columns can be added at once.");
                    } else {
                        Plot p= app.getController().getPlot();
                        app.getController().addPlots( nr,nc, dia.getDirection() );
                        if ( dia.getDirection()==null ) {
                            app.getController().deletePlot(p);
                        }
                    }
                }
            }
        };




    private synchronized void createPopupMenus() {
        contextMenus = new HashMap<Component, JPopupMenu>();

        JMenuItem item;

        contextMenus.put( canvasLayoutPanel1, plotActionsMenu );

        JPopupMenu panelContextMenu = new JPopupMenu();

        item = new JMenuItem(new AbstractAction("Edit Plot Element Properties") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                PlotElement p= (PlotElement)panelListComponent.getSelectedValue();
                PropertyEditor edit;
                if ( os.length==0 ) {
                    return;
                } else if ( os.length==1 ) {
                    edit = new PropertyEditor(p);
                } else {
                    PlotElement[] peers= new PlotElement[os.length];
                    for ( int i=0; i<os.length; i++ ) peers[i]= (PlotElement)os[i];
                    edit= PropertyEditor.createPeersEditor( p, peers );
                }
                edit.showDialog(LayoutPanel.this);
            }
        });
        item.setToolTipText("edit the plot element or elements");
        panelContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Edit Plot Element Style Properties") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                PlotElement p= (PlotElement)panelListComponent.getSelectedValue();
                PropertyEditor edit;
                if ( os.length==0 ) {
                    return;
                } else if ( os.length==1 ) {
                    edit = new PropertyEditor(p.getStyle());
                } else {
                    PlotElementStyle[] peers= new PlotElementStyle[os.length];
                    for ( int i=0; i<os.length; i++ ) peers[i]= ((PlotElement)os[i]).getStyle();
                    edit= PropertyEditor.createPeersEditor( p.getStyle(), peers );
                }
                edit.showDialog(LayoutPanel.this);
            }
        });

        item.setToolTipText("edit the style of plot element or elements");
        panelContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Plot Element") {

            public void actionPerformed(ActionEvent e) {
                Object[] os= panelListComponent.getSelectedValues();
                for ( Object o : os ) {
                    PlotElement element = (PlotElement) o;
                    app.getController().deletePlotElement(element);
                }
                
            }
        });
        panelContextMenu.add(item);

        contextMenus.put(panelListComponent, panelContextMenu);
        contextMenus.put( bindingListComponent, bindingActionsMenu );

    }
    transient ListSelectionListener plotElementSelectionListener = new ListSelectionListener() {

        public void valueChanged(ListSelectionEvent e) {
            if ( panelListComponent.getValueIsAdjusting() ) return;
            if (panelListComponent.getSelectedValues().length == 1) {
                if ( ! app.getController().isValueAdjusting() ) {
                    Object o= panelListComponent.getSelectedValue();
                    if ( !(o instanceof PlotElement ) ) {
                        System.err.println("expected plotElements in panelListComponent, returning");
                        //Jemmy had this error...
                        return;
                    }
                    PlotElement p = (PlotElement)o;
                    Plot plot = app.getController().getPlotFor(p);
                    app.getController().setPlot(plot);
                    app.getController().setPlotElement(p);
                }
            }
        }
    };
    Application app;
    
    transient PropertyChangeListener plotElementsListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            updatePlotElementList();
        }
    };

    transient PropertyChangeListener bindingsListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            updateBindingList();
        }
    };


    transient private PropertyChangeListener plotListener = new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            Plot plot= app.getController().getPlot();
            if ( plot==null ) {
                return;
            }
            List<PlotElement> p = app.getController().getPlotElementsFor(plot);
            List<PlotElement> allElements = Arrays.asList(app.getPlotElements());
            List<Integer> indices = new ArrayList<Integer>();
            for (int i = 0; i < p.size(); i++) {
                if ( p.get(i).isActive() ) indices.add( allElements.indexOf(p.get(i)) );
            }

            final int[] iindices= new int[indices.size()];
            for ( int i=0; i<indices.size(); i++ ) iindices[i]= indices.get(i);

            Runnable run= new Runnable() {
                public void run() {
                    logger.finer("enter plotListener");
                    panelListComponent.setSelectedIndices(iindices);
                    updateSelected();
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }
            
        }
    };
    transient private PropertyChangeListener plotElementListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            PlotElement p = app.getController().getPlotElement();
            List<PlotElement> allElements = Arrays.asList(app.getPlotElements());

            final int index= allElements.indexOf(p);
            Runnable run= new Runnable() {
                public void run() {
                    panelListComponent.setSelectedIndex(index);
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }
            
        }
    };

    void updateSelected() {
        
        int[] iindices= panelListComponent.getSelectedIndices();
        PlotElement[] peles=  app.getPlotElements();

        List<Object> selected= new ArrayList();

        DasPlot dasPlot=null;
        Plot plot= app.getController().getPlot();
        if ( plot!=null ) {
            dasPlot = plot.getController().getDasPlot();
            selected.add(dasPlot);

            for ( int i=0; i<iindices.length; i++ ) {
                try {
                    PlotElementController pec= peles[iindices[i]].getController();
                    selected.add( pec.getRenderer() );
                } catch ( IndexOutOfBoundsException ex ) {
                    // this happens because of multiple threads... TODO: fix this sometime...
                    System.err.println("harmless indexOutOfBoundsException needs to be fixed sometime");
                }
            }
        }

        canvasLayoutPanel1.setSelectedComponents( selected );
        canvasLayoutPanel1.setComponent(dasPlot);
    }

    public void setApplication(Application app) {
        this.app = app;
        updatePlotElementList();
        updateBindingList();
        canvasLayoutPanel1.setContainer(app.getController().getDasCanvas());
        canvasLayoutPanel1.addComponentType(DasPlot.class, Color.BLUE);
        app.getController().bind(app.getOptions(), Options.PROP_BACKGROUND, canvasLayoutPanel1, "background");
        app.addPropertyChangeListener(Application.PROP_PLOT_ELEMENTS, plotElementsListener);
        app.addPropertyChangeListener(Application.PROP_BINDINGS, bindingsListener);
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT, plotListener);
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT_ELEMENT, plotElementListener);
    }

    ListCellRenderer myListCellRenderer=  new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            final javax.swing.JLabel label= (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            final PlotElement val= (PlotElement)value;
            if ( val!=null ) {
                final PlotElementController cont= val.getController();
                if ( cont!=null ) {
                    final Renderer rend= val.getController().getRenderer();
                    if ( rend!=null ) {
                        javax.swing.Icon icon= rend.getListIcon();
                        label.setIcon(icon);
                        rend.addPropertyChangeListener( new PropertyChangeListener() {
                            public void propertyChange(PropertyChangeEvent evt) {
                                panelListComponent.repaint();
                            }
                        });
                    }
                }
            }
            return label;
        }
    };

    private void updatePlotElementListImmediately() {
        final Object[] foo= app.getPlotElements();
        final AbstractListModel elementsList = new AbstractListModel() {
            public int getSize() {
                return foo.length;
            }
            public Object getElementAt(int index) {
                return foo[index];
            }
        };
        panelListComponent.setModel(elementsList);
        panelListComponent.setCellRenderer( myListCellRenderer );
    }

    private void updatePlotElementList() {
        Runnable run= new Runnable() { public void run() {
            updatePlotElementListImmediately();
        } };
        SwingUtilities.invokeLater(run);
    }

    /**
     * return the elements of the list where the comparator indicates equal to the object.
     * @param list
     * @param c
     * @param equalTo
     * @return
     */
    private static List getSublist( List list, Comparator c, Object equalTo ) {
        ArrayList result= new ArrayList(list.size());
        for ( Object o: list ) {
            if ( c.compare( o, equalTo )==0 ) {
                result.add(o);
            }
        }
        return result;
    }

    private void updateBindingList() {
        final List bindingList= new ArrayList( Arrays.asList( app.getBindings() ) );
        List rm= getSublist( bindingList, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((BindingModel)o1).getDstProperty().equals("colortable") ? 0 : 1;
            }
        }, null );
        bindingList.removeAll(rm);
        AbstractListModel elementsList = new AbstractListModel() {
            public int getSize() {
                return bindingList.size();
            }
            public Object getElementAt(int index) {
                return bindingList.get(index);
            }
        };
        bindingListComponent.setModel(elementsList);
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
        propertiesMenuItem = new javax.swing.JMenuItem();
        deleteMenuItem = new javax.swing.JMenuItem(deletePlotAction);
        addPlotsBelowMenuItem = new javax.swing.JMenuItem(addPlotsAction);
        removeBindingsMenuItem = new javax.swing.JMenuItem(removeBindingsAction);
        plotsMenu = new javax.swing.JMenu();
        sizeMenu = new javax.swing.JMenu();
        biggerMI = new javax.swing.JMenuItem();
        smallerMI = new javax.swing.JMenuItem();
        sameSizeMI = new javax.swing.JMenuItem();
        swapMenuItem = new javax.swing.JMenuItem();
        addHiddenMenuItem = new javax.swing.JMenuItem();
        bindingActionsMenu = new javax.swing.JPopupMenu();
        deleteBindingsMenuItem = new javax.swing.JMenuItem();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jSplitPane1 = new javax.swing.JSplitPane();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        panelListComponent = new javax.swing.JList();
        jSplitPane2 = new javax.swing.JSplitPane();
        jPanel1 = new javax.swing.JPanel();
        canvasLayoutPanel1 = new org.virbo.autoplot.util.CanvasLayoutPanel();
        tallerButton = new javax.swing.JButton();
        shorterButton = new javax.swing.JButton();
        sameHeightButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        bindingListComponent = new javax.swing.JList();

        plotMenu.setText("Plot");

        propertiesMenuItem.setText("Properties...");
        propertiesMenuItem.setToolTipText("edit plot properties");
        propertiesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                propertiesMenuItemActionPerformed(evt);
            }
        });
        plotMenu.add(propertiesMenuItem);

        deleteMenuItem.setText("Delete");
        plotMenu.add(deleteMenuItem);

        addPlotsBelowMenuItem.setText("Add Plots...");
        addPlotsBelowMenuItem.setToolTipText("Add a grid of plots below or above the selected plot");
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

        plotsMenu.setText("Canvas");

        sizeMenu.setText("Plot Size");
        sizeMenu.setToolTipText("Adjust the selected plots' size");

        biggerMI.setText("Taller");
        biggerMI.setToolTipText("Make the selected plots 25% taller relative to others");
        biggerMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                biggerMIActionPerformed(evt);
            }
        });
        sizeMenu.add(biggerMI);

        smallerMI.setText("Shorter");
        smallerMI.setToolTipText("Make the selected plots 25% shorter relative to others");
        smallerMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smallerMIActionPerformed(evt);
            }
        });
        sizeMenu.add(smallerMI);

        sameSizeMI.setText("Same Height");
        sameSizeMI.setToolTipText("Make the selected plots have the same height");
        sameSizeMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sameSizeMIActionPerformed(evt);
            }
        });
        sizeMenu.add(sameSizeMI);

        plotsMenu.add(sizeMenu);

        swapMenuItem.setText("Swap Position");
        swapMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swapMenuItemActionPerformed(evt);
            }
        });
        plotsMenu.add(swapMenuItem);

        addHiddenMenuItem.setText("Add Hidden Plot...");
        addHiddenMenuItem.setToolTipText("Add hidden plot for this plot/plots to bind plots together.\n");
        addHiddenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addHiddenMenuItemActionPerformed(evt);
            }
        });
        plotsMenu.add(addHiddenMenuItem);

        plotActionsMenu.add(plotsMenu);

        bindingActionsMenu.setToolTipText("Binding actions");

        deleteBindingsMenuItem.setText("Delete Selected Bindings");
        deleteBindingsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteBindingsMenuItemActionPerformed(evt);
            }
        });
        bindingActionsMenu.add(deleteBindingsMenuItem);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane3.setViewportView(jTable1);

        jSplitPane1.setDividerLocation(300);
        jSplitPane1.setResizeWeight(0.5);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Plot Elements [?]"));
        jPanel2.setToolTipText("List of plot elements (renderings of data) on the canvas");

        panelListComponent.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        panelListComponent.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                panelListComponentValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(panelListComponent);

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 398, Short.MAX_VALUE)
        );

        jSplitPane1.setRightComponent(jPanel2);

        jSplitPane2.setDividerLocation(200);
        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane2.setResizeWeight(0.5);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Plots [?]"));
        jPanel1.setToolTipText("Layout of plots on the canvas");
        jPanel1.setMinimumSize(new java.awt.Dimension(230, 230));

        canvasLayoutPanel1.setText("canvasLayoutPanel1");

        tallerButton.setText("Taller");
        tallerButton.setToolTipText("Make the selected plots taller");
        tallerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tallerButtonActionPerformed(evt);
            }
        });

        shorterButton.setText("Shorter");
        shorterButton.setToolTipText("Make the selected plots shorter");
        shorterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shorterButtonActionPerformed(evt);
            }
        });

        sameHeightButton.setText("Same Height");
        sameHeightButton.setToolTipText("Make the selected plots the same height");
        sameHeightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sameHeightButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel1Layout.createSequentialGroup()
                .add(tallerButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(shorterButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(sameHeightButton)
                .add(0, 39, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {shorterButton, tallerButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 142, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(tallerButton)
                    .add(shorterButton)
                    .add(sameHeightButton)))
        );

        jSplitPane2.setTopComponent(jPanel1);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Bindings [?]"));
        jPanel3.setToolTipText("List of connections between DOM properties");

        bindingListComponent.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane2.setViewportView(bindingListComponent);

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE)
        );

        jSplitPane2.setRightComponent(jPanel3);

        jSplitPane1.setLeftComponent(jSplitPane2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void propertiesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_propertiesMenuItemActionPerformed
        DasPlot component= (DasPlot)canvasLayoutPanel1.getComponent();
        Plot domPlot = app.getController().getPlotFor(component);
        if ( domPlot==null ) {
            this.app.getController().setStatus("warning: nothing selected");
            return;
        }
        List<Object> components= canvasLayoutPanel1.getSelectedComponents();
        List<Plot> plots= new ArrayList();
        for ( int i=0; i<components.size(); i++ ) {
            if ( components.get(i) instanceof Component ) { // might have renderer selected
                plots.add( app.getController().getPlotFor( (Component) components.get(i) ) );
            }
        }
        if ( plots.size()>1 ) {
            PropertyEditor edit = PropertyEditor.createPeersEditor(domPlot,plots.toArray());
            edit.showDialog(LayoutPanel.this);
        } else {
            PropertyEditor edit = new PropertyEditor(domPlot);
            edit.showDialog(LayoutPanel.this);
        }
}//GEN-LAST:event_propertiesMenuItemActionPerformed

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

    private void addHiddenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addHiddenMenuItemActionPerformed
        BindToHiddenDialog dia= new BindToHiddenDialog();

        int op= JOptionPane.showConfirmDialog( this, dia, "Add hidden plot for binding", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
        if ( op==JOptionPane.OK_OPTION ) {
            final String lock = "Add hidden plot";

            List<Plot> plots= getSelectedPlots();
            if ( plots.size()==0 ) return;

            app.getController().registerPendingChange( this, lock);
            app.getController().performingChange( this, lock);

            Column col= DomOps.getOrCreateSelectedColumn( app, getSelectedPlots(), true );
            Row row= DomOps.getOrCreateSelectedRow( app, getSelectedPlots(), true );

            Plot p= app.getController().addPlot(row, col);
            p.setVisible(false);
            p.getXaxis().setVisible(false);
            p.getYaxis().setVisible(false);

            Plot[] bottomTopPlots= DomOps.bottomAndTopMostPlot(app, plots);

            if ( dia.getCondenseColorBarsCB().isSelected() ) {
                p.getZaxis().setVisible(true);
                for ( Plot p1: plots ) {
                    p1.getZaxis().setVisible(false);
                }
                p.getZaxis().setVisible(true);
            } else {
                p.getZaxis().setVisible(false);
            }
            if ( dia.getxAxisCB().isSelected() ) { // bind the xaxes
                DatumRange range= getSelectedPlots().get(0).getXaxis().getRange();
                boolean log= getSelectedPlots().get(0).getXaxis().isLog();
                for ( Plot p1: getSelectedPlots() ) {
                    range= DatumRangeUtil.union( range, p1.getXaxis().getRange() );
                    log= log && p1.getXaxis().isLog();
                }
                for ( Plot p1: getSelectedPlots() ) {
                    p.getXaxis().setRange( range );
                    if ( !log ) p1.getXaxis().setLog(log);
                    app.getController().bind( p.getXaxis(), "range", p1.getXaxis(), "range" );
                    p.getXaxis().setLog( log );
                    app.getController().bind( p.getXaxis(), "log", p1.getXaxis(), "log" );
                }
            }
            if ( dia.getyAxisCB().isSelected() ) { // bind the xaxes
                DatumRange range= getSelectedPlots().get(0).getYaxis().getRange();
                boolean log= getSelectedPlots().get(0).getYaxis().isLog();
                for ( Plot p1: getSelectedPlots() ) {
                    range= DatumRangeUtil.union( range, p1.getYaxis().getRange() );
                    log= log && p1.getYaxis().isLog();
                }
                for ( Plot p1: getSelectedPlots() ) {
                    p.getYaxis().setRange( range );
                    if ( !log ) p1.getYaxis().setLog(log);
                    app.getController().bind( p.getYaxis(), "range", p1.getYaxis(), "range" );
                    p.getYaxis().setLog( log );
                    app.getController().bind( p.getYaxis(), "log", p1.getYaxis(), "log" );
                }
            }
            if ( dia.getzAxisCB().isSelected() ) { // bind the xaxes
                DatumRange range= getSelectedPlots().get(0).getZaxis().getRange();
                boolean log= getSelectedPlots().get(0).getZaxis().isLog();
                for ( Plot p1: getSelectedPlots() ) {
                    range= DatumRangeUtil.union( range, p1.getZaxis().getRange() );
                    log= log && p1.getZaxis().isLog();
                }
                for ( Plot p1: getSelectedPlots() ) {
                    p.getZaxis().setRange( range );
                    if ( !log ) p1.getZaxis().setLog(log);
                    app.getController().bind( p.getZaxis(), "range", p1.getZaxis(), "range" );
                    p.getZaxis().setLog( log );
                    app.getController().bind( p.getZaxis(), "log", p1.getZaxis(), "log" );
                }
            }
            // bind the colortables
            if ( dia.getCondenseColorBarsCB().isSelected() ) { 
                for ( Plot p1: getSelectedPlots() ) {
                    app.getController().bind( p, "colortable", p1, "colortable" );
                }
            }

            if ( dia.getCondenseXAxisLabelsCB().isSelected() ) {
                String t= plots.get(0).getTitle();
                for ( Plot p1: getSelectedPlots() ) {
                    p1.getXaxis().setDrawTickLabels(false);
                    p1.getXaxis().setLabel("");
                    p1.setTitle("");
                    Row r= app.getCanvases(0).getController().getRowFor(p1);
                    r.setTop( r.getTop().replaceAll( "(.*)\\+([\\d\\.]+)em(.*)","$1+0.5em" ) );
                    r.setBottom( r.getBottom().replaceAll( "(.*)\\-([\\d\\.]+)em","$1-0.5em" ) );
                }
                bottomTopPlots[1].setTitle(t);
                bottomTopPlots[0].getXaxis().setDrawTickLabels(true);
            }

            app.getController().changePerformed( this, lock);


        }
    }//GEN-LAST:event_addHiddenMenuItemActionPerformed

    private void deleteBindingsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBindingsMenuItemActionPerformed
        Object[] bindings= bindingListComponent.getSelectedValues();
        for ( Object o:bindings ) {
            BindingModel b= (BindingModel)o;
            app.getController().deleteBinding(b);
        }
    }//GEN-LAST:event_deleteBindingsMenuItemActionPerformed

    private void panelListComponentValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_panelListComponentValueChanged
        logger.fine("panelListComponentValueChanged "+evt.getValueIsAdjusting());
        if ( !evt.getValueIsAdjusting() ) {
            updateSelected();
        }
    }//GEN-LAST:event_panelListComponentValueChanged

    private void biggerMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biggerMIActionPerformed
        List<Row> rows= new ArrayList<Row>();
        for ( Plot p1: getSelectedPlots() ) {
            Row row= p1.getController().getRow();
            if ( !rows.contains(row) ) rows.add(row);
        }
        
        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseFormatStr( r.getTop() );
                double[] d2= DasDevicePosition.parseFormatStr( r.getBottom() );
                d2[0]= d1[0] + ( d2[0]-d1[0] ) * 1.25;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
            } catch ( ParseException ex ) {}
        }

        org.virbo.autoplot.dom.DomOps.newCanvasLayout(app);
        
    }//GEN-LAST:event_biggerMIActionPerformed

    private void sameSizeMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sameSizeMIActionPerformed
        List<Row> rows= new ArrayList<Row>();
        for ( Plot p1: getSelectedPlots() ) {
            Row row= p1.getController().getRow();
            if ( !rows.contains(row) ) rows.add(row);
        }

        double size= 0;
        double emMaxTop= 0;
        double emMaxBottom= 0;
        int n= 0;

        // calculate the average size
        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseFormatStr( r.getTop() );
                double[] d2= DasDevicePosition.parseFormatStr( r.getBottom() );
                size= size + ( d2[0]-d1[0] );
                emMaxBottom= Math.max( emMaxBottom, d2[1] );
                emMaxTop= Math.max( emMaxTop, d2[1] );
                n= n+1;
            } catch ( ParseException ex ) {}
        }

        size= size / n;

        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseFormatStr( r.getTop() );
                double[] d2= DasDevicePosition.parseFormatStr( r.getBottom() );
                d2[0]= d1[0] + size;
                d2[1]= emMaxBottom;
                d1[1]= emMaxTop;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
                r.setTop( DasDevicePosition.formatFormatStr(d1) );
            } catch ( ParseException ex ) {}
        }

        org.virbo.autoplot.dom.DomOps.newCanvasLayout(app);
        
    }//GEN-LAST:event_sameSizeMIActionPerformed

    private void smallerMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallerMIActionPerformed
        List<Row> rows= new ArrayList<Row>();
        for ( Plot p1: getSelectedPlots() ) {
            Row row= p1.getController().getRow();
            if ( !rows.contains(row) ) rows.add(row);
        }

        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseFormatStr( r.getTop() );
                double[] d2= DasDevicePosition.parseFormatStr( r.getBottom() );
                d2[0]= d1[0] + ( d2[0]-d1[0] ) * 0.80;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
            } catch ( ParseException ex ) {}
        }

        org.virbo.autoplot.dom.DomOps.newCanvasLayout(app);

    }//GEN-LAST:event_smallerMIActionPerformed

    private void tallerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tallerButtonActionPerformed
        biggerMIActionPerformed(evt);
    }//GEN-LAST:event_tallerButtonActionPerformed

    private void shorterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shorterButtonActionPerformed
        smallerMIActionPerformed(evt);
    }//GEN-LAST:event_shorterButtonActionPerformed

    private void sameHeightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sameHeightButtonActionPerformed
        sameSizeMIActionPerformed(evt);
    }//GEN-LAST:event_sameHeightButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem addHiddenMenuItem;
    private javax.swing.JMenuItem addPlotsBelowMenuItem;
    private javax.swing.JMenuItem biggerMI;
    private javax.swing.JPopupMenu bindingActionsMenu;
    private javax.swing.JList bindingListComponent;
    private org.virbo.autoplot.util.CanvasLayoutPanel canvasLayoutPanel1;
    private javax.swing.JMenuItem deleteBindingsMenuItem;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JList panelListComponent;
    private javax.swing.JPopupMenu plotActionsMenu;
    private javax.swing.JMenu plotMenu;
    private javax.swing.JMenu plotsMenu;
    private javax.swing.JMenuItem propertiesMenuItem;
    private javax.swing.JMenuItem removeBindingsMenuItem;
    private javax.swing.JButton sameHeightButton;
    private javax.swing.JMenuItem sameSizeMI;
    private javax.swing.JButton shorterButton;
    private javax.swing.JMenu sizeMenu;
    private javax.swing.JMenuItem smallerMI;
    private javax.swing.JMenuItem swapMenuItem;
    private javax.swing.JButton tallerButton;
    // End of variables declaration//GEN-END:variables
}
