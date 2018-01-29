
/*
 * LayoutPanel.java
 *
 * Created on Mar 7, 2009, 6:24:23 AM
 */
package org.autoplot;

import org.autoplot.AutoplotUtil;
import org.autoplot.BindToHiddenDialog;
import org.autoplot.ApplicationModel;
import org.autoplot.AddPlotsDialog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
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
import javax.swing.JPanel;
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
import static org.autoplot.GuiSupport.getStylePanel;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.Axis;
import org.autoplot.dom.BindingModel;
import org.autoplot.dom.Column;
import org.autoplot.dom.DataSourceController;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.DomOps;
import org.autoplot.dom.DomUtil;
import org.autoplot.dom.Options;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.PlotElementStyle;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElementController;
import org.autoplot.dom.Row;
import org.autoplot.util.CanvasLayoutPanel;
import org.autoplot.datasource.DataSourceEditorPanel;
import org.autoplot.datasource.DataSourceEditorPanelUtil;

/**
 * LayoutPanel shows all the plots and plot elements on the canvas.  
 * @author jbf
 */
public class LayoutPanel extends javax.swing.JPanel {

    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui.layout");

    private Plot draggingPlot=null;
    private Point dragInitialClick= null;
    private Point dragLocation=null;

    private Application dom;
    private ApplicationModel applicationModel; // used for history.
    
    /** Creates new form LayoutPanel */
    public LayoutPanel() {
        initComponents();
        canvasLayoutPanel1.addPropertyChangeListener(CanvasLayoutPanel.PROP_COMPONENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Plot plot = dom.getController().getPlotFor((Component) canvasLayoutPanel1.getComponent());
                List<Object> p= canvasLayoutPanel1.getSelectedComponents();
                if (plot != null) {
                    dom.getController().setPlot(plot);
                    canvasLayoutPanel1.setSelectedComponents(p);
                }
                tallerButton.setEnabled(plot!=null);
                shorterButton.setEnabled(plot!=null);
                addPlotsButton.setEnabled(plot!=null);
                addPlotsButton.setEnabled(plot!=null);
                int count = getSelectedPlots().size();
                sameHeightButton.setEnabled( count>1 );
                String selectText;
                switch (count) {
                    case 1:
                        if ( plot!=null ) {
                            selectText= plot.getId();
                        } else {
                            selectText= "";
                        }
                        break;
                    default:
                        selectText= String.format( "%d plots selected", count );
                        break;
                }
                selectedPlotLabel.setText(selectText);
            }
        });
        plotElementListComponent.addListSelectionListener(plotElementSelectionListener);

        canvasLayoutPanel1.addPropertyChangeListener(CanvasLayoutPanel.PROP_SELECTEDCOMPONENTS, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                List<Plot> selectedPlots= getSelectedPlots();
                int count = selectedPlots.size();
                sameHeightButton.setEnabled( count>1 );
                String selectText;
                switch (count) {
                    case 1:
                        if ( selectedPlots.get(0)!=null ) {
                            selectText= selectedPlots.get(0).getId();
                        } else {
                            selectText= "";
                        }
                        break;
                    default:
                        selectText= String.format( "%d plots selected", count );
                        break;
                }
                selectedPlotLabel.setText(selectText);
            }
        });
        
        createPopupMenus();

        MouseListener popupTrigger = createPopupTrigger();
        canvasLayoutPanel1.addMouseListener(popupTrigger);
        
        canvasLayoutPanel1.addMouseMotionListener( new MouseMotionListener() {  // TODO: this should probably be moved to CanvasLayoutPanel
            @Override
            public void mouseDragged(MouseEvent e) {
                if ( draggingPlot==null && dragInitialClick==null ) {
                    Object s= canvasLayoutPanel1.getCanvasComponentAt( e.getX(), e.getY() );
                    if ( s instanceof Component ) {
                        draggingPlot= dom.getController().getPlotFor( (Component)s );
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        dom.getController().setStatus("swap "+draggingPlot+ ", drop to swap positions." );
                    } else {
                        dragInitialClick= e.getPoint();
                        dom.getController().setStatus("select plots by drawing a box." );
                    }
                    canvasLayoutPanel1.setRectangleSelect(null);
                } else if ( dragInitialClick!=null ) {
                    dragLocation= e.getPoint();
                    Rectangle rect= new Rectangle( dragInitialClick );
                    rect.add( e.getPoint() );
                    canvasLayoutPanel1.setSelectedComponents( rect );
                    canvasLayoutPanel1.setRectangleSelect(rect);
                    int count = getSelectedPlots().size();
                    sameHeightButton.setEnabled( count>1 );
                } else if ( draggingPlot!=null ) {
                    Object s= canvasLayoutPanel1.getCanvasComponentAt( e.getX(), e.getY() );
                    if ( s instanceof Component ) {
                        Plot targetPlot= dom.getController().getPlotFor( (Component)s );
                        if ( targetPlot!=null && targetPlot!=draggingPlot ) {
                            dom.getController().setStatus("swap "+draggingPlot+ " and " +targetPlot );
                        } else {
                            dom.getController().setStatus("swap "+draggingPlot+ ", drop to swap positions." );
                        }
                    }                    
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                
            }
        });
        
        plotElementListComponent.addMouseListener(popupTrigger);
        dataSourceList.addMouseListener(popupTrigger);
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
                canvasLayoutPanel1.setRectangleSelect(null);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = contextMenus.get(e.getComponent());
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
                if ( draggingPlot!=null ) {
                    Object s= canvasLayoutPanel1.getCanvasComponentAt( e.getX(), e.getY() );
                    if ( s instanceof Component ) {
                        Plot targetPlot= dom.getController().getPlotFor( (Component)s );
                        if ( targetPlot!=null ) {
                            DomOps.swapPosition( draggingPlot, targetPlot );
                            if ( dom.getOptions().isAutolayout() ) DomOps.newCanvasLayout(dom);
                            dom.getController().setStatus("swapped "+draggingPlot+ " and " +targetPlot );
                        }
                    }
                    setCursor(null);
                    draggingPlot= null;
                }
                if ( dragInitialClick!=null ) {
                    Rectangle rect= new Rectangle( dragInitialClick );
                    rect.add( e.getPoint() );
                    canvasLayoutPanel1.setSelectedComponents( rect );
                    dragInitialClick= null;
                }
                canvasLayoutPanel1.setRectangleSelect(null);
                
            }
        };
    }
    
    /**
     * set the applicationModel for access to history.
     * @param applicationModel 
     */
    public void setApplicationModel( ApplicationModel applicationModel ) {
        this.applicationModel= applicationModel;
    }
    
    private Map<Component, JPopupMenu> contextMenus = null;

    private Action removeBindingsAction= new AbstractAction("Remove Bindings") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                Plot domPlot = dom.getController().getPlot();
                List<PlotElement> elements = dom.getController().getPlotElementsFor(domPlot);
                for (PlotElement element : elements) {
                    dom.getController().unbind(element);
                }
                dom.getController().unbind(domPlot);
            }
        };


        private Action deletePlotAction= new AbstractAction("Delete Plot") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                List<Object> os= canvasLayoutPanel1.getSelectedComponents();
                for ( Object o: os ) {
                    if (dom.getPlots().length > 1) {
                        Plot domPlot=null;
                        if ( o instanceof Component ) {
                            domPlot= dom.getController().getPlotFor((Component)o);
                        }
                        if ( domPlot==null ) continue;
                        List<PlotElement> plotElements = dom.getController().getPlotElementsFor(domPlot);
                        for (PlotElement pan : plotElements) {
                            if (dom.getPlotElements().length > 1) {
                                dom.getController().deletePlotElement(pan);
                            } else {
                                dom.getController().setStatus("warning: the last plot element may not be deleted");
                            }
                        }
                        dom.getController().deletePlot(domPlot);
                    } else {
                        dom.getController().setStatus("warning: last plot may not be deleted");
                    }
                }
            }
        };

        private Action addPlotsAction= new AbstractAction("Add Plots...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                AddPlotsDialog dia= new AddPlotsDialog();
                dia.getNumberOfColumnsSpinner().setModel( new SpinnerNumberModel(1,1,6,1) );
                dia.getNumberOfRowsSpinner().setModel( new SpinnerNumberModel(1,1,6,1) );
                if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog(plotElementListComponent, 
                        dia, "Add Plots", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE, 
                        new ImageIcon( AutoplotUtil.getAutoplotIcon() ) ) ) {
                    int nr= (Integer)dia.getNumberOfRowsSpinner().getValue();
                    int nc= (Integer)dia.getNumberOfColumnsSpinner().getValue();
                    if ( nr>6 || nc>6 ) {
                        JOptionPane.showMessageDialog( LayoutPanel.this, "No more than 6 rows or columns can be added at once.");
                    } else {
                        Plot p= dom.getController().getPlot();
                        dom.getController().addPlots( nr,nc, dia.getDirection() );
                        if ( dia.getDirection()==null ) {
                            dom.getController().deletePlot(p);
                        }
                    }
                }
            }
        };




    private synchronized void createPopupMenus() {
        contextMenus = new HashMap<>();

        JMenuItem item;

        contextMenus.put( canvasLayoutPanel1, plotActionsMenu );
        contextMenus.put( dataSourceList, dataSourceActionsMenu );

        JPopupMenu plotElementContextMenu = new JPopupMenu();

        item = new JMenuItem(new AbstractAction("Edit Plot Element Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                List<PlotElement> os= plotElementListComponent.getSelectedValuesList();
                PlotElement p= (PlotElement)plotElementListComponent.getSelectedValue();
                PropertyEditor edit;
                switch (os.size()) {
                    case 0:
                        return;
                    case 1:
                        edit = new PropertyEditor(p);
                        break;
                    default:
                        PlotElement[] peers= new PlotElement[os.size()];
                        os.toArray(peers);
                        edit= PropertyEditor.createPeersEditor( p, peers );
                        break;
                }
                edit.showDialog(LayoutPanel.this);
            }
        });
        item.setToolTipText("edit the plot element or elements");
        plotElementContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Edit Plot Element Style Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                                
                List<PlotElement> os= plotElementListComponent.getSelectedValuesList();
                PlotElement p= (PlotElement)plotElementListComponent.getSelectedValue();
                PropertyEditor edit;
                if ( os.size()==1 ) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    PlotStylePanel.StylePanel editorPanel= getStylePanel( p.getRenderType() );
                    editorPanel.doElementBindings(p);
                    AutoplotUtil.showMessageDialog( LayoutPanel.this, editorPanel, p.getRenderType() + " Style", JOptionPane.OK_OPTION );
                } else if ( os.size()>1 ) {
                    PlotElementStyle[] peers= new PlotElementStyle[os.size()];
                    for ( int i=0; i<os.size(); i++ ) peers[i]= (os.get(i)).getStyle();
                    edit= PropertyEditor.createPeersEditor( p.getStyle(), peers );
                    edit.showDialog(LayoutPanel.this);
                }
            }
        });

        item.setToolTipText("edit the style of plot element or elements");
        plotElementContextMenu.add(item);

        item = new JMenuItem(new AbstractAction("Delete Plot Element") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                List<PlotElement> os= plotElementListComponent.getSelectedValuesList();
                for ( Object o : os ) {
                    PlotElement element = (PlotElement) o;
                    dom.getController().deletePlotElement(element);
                }
                
            }
        });
        plotElementContextMenu.add(item);
        
        item = new JMenuItem(new AbstractAction("Move Plot Element Above Others") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);     
                List<PlotElement> pes= new ArrayList<>();
                List<PlotElement> os= plotElementListComponent.getSelectedValuesList();
                for ( Object o : os ) {
                    PlotElement element = (PlotElement) o;
                    pes.add(element);
                }
                for ( PlotElement pe: pes ) {
                    Plot p= dom.getController().getPlotFor(pe);
                    p.getController().toTop(pe);
                }
                
            }
        });
        plotElementContextMenu.add(item);
        
        item = new JMenuItem(new AbstractAction("Blur Focus") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                dom.getController().setPlotElement(null);
                plotElementListComponent.setSelectedIndices(new int[0]);
            }
        });
        plotElementContextMenu.add(item);

        contextMenus.put(plotElementListComponent, plotElementContextMenu );
        contextMenus.put( bindingListComponent, bindingActionsMenu );

    }
    private transient ListSelectionListener plotElementSelectionListener = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if ( plotElementListComponent.getValueIsAdjusting() ) return;
            if (plotElementListComponent.getSelectedValuesList().size() == 1) {
                if ( ! dom.getController().isValueAdjusting() ) {
                    Object o= plotElementListComponent.getSelectedValue();
                    if ( !(o instanceof PlotElement ) ) {
                        System.err.println("expected plotElements in panelListComponent, returning");
                        //Jemmy had this error...
                        return;
                    }
                    PlotElement pe = (PlotElement)o;
                    Plot plot = dom.getController().getPlotFor(pe);
                    if ( plot!=null ) {
                        dom.getController().setPlot(plot);
                        dom.getController().setPlotElement(pe);
                    } else {
                        logger.fine("plot not found for plotElement");
                    }
                }
            }
        }
    };
    
    private transient PropertyChangeListener plotElementsListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updatePlotElementList();
        }
    };

    private transient PropertyChangeListener bindingsListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updateBindingList();
        }
    };

    private transient PropertyChangeListener dataSourceListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            dataSourceList.repaint();
        }
    };
            
    private transient PropertyChangeListener dataSourcesListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updateDataSourceList();
            DataSourceFilter[] old= (DataSourceFilter[]) evt.getOldValue();
            DataSourceFilter[] nww= (DataSourceFilter[]) evt.getNewValue();
            List<DataSourceFilter> oldList= Arrays.asList(old);
            for ( DataSourceFilter dsf: nww ) {
                if ( !oldList.contains(dsf) ) {
                    dsf.addPropertyChangeListener( DataSourceFilter.PROP_URI, dataSourceListener );
                    dsf.getController().addPropertyChangeListener( DataSourceController.PROP_TSB, dataSourceListener );
                }
            }
            List<DataSourceFilter> newList= Arrays.asList(nww);
            for ( DataSourceFilter dsf: old ) {
                if ( !newList.contains(dsf) ) {
                    dsf.removePropertyChangeListener( DataSourceFilter.PROP_URI, dataSourceListener );
                    dsf.getController().removePropertyChangeListener( DataSourceController.PROP_TSB, dataSourceListener );
                }
            }
        }
    };
    

    transient private PropertyChangeListener plotListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            Plot plot= dom.getController().getPlot();
            if ( plot==null ) {
                return;
            }
            List<PlotElement> p = dom.getController().getPlotElementsFor(plot);
            List<PlotElement> allElements = Arrays.asList(dom.getPlotElements());
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < p.size(); i++) {
                if ( p.get(i).isActive() ) indices.add( allElements.indexOf(p.get(i)) );
            }

            final int[] iindices= new int[indices.size()];
            for ( int i=0; i<indices.size(); i++ ) iindices[i]= indices.get(i);

            Runnable run= new Runnable() {
                @Override
                public void run() {
                    logger.finer("enter plotListener");
                    plotElementListComponent.setSelectedIndices(iindices);
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
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            PlotElement p = dom.getController().getPlotElement();
            List<PlotElement> allElements = Arrays.asList(dom.getPlotElements());

            final int index= allElements.indexOf(p);
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    plotElementListComponent.setSelectedIndex(index);
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }
            
        }
    };

    private void updateSelected() {
        
        int[] iindices= plotElementListComponent.getSelectedIndices();
        PlotElement[] peles=  dom.getPlotElements();

        List<Object> selected= new ArrayList();

        DasPlot dasPlot=null;
        Plot plot= dom.getController().getPlot();
        if ( plot!=null ) {
            dasPlot = plot.getController().getDasPlot();
            selected.add(dasPlot);
            List<DataSourceFilter> dsfs= new ArrayList();
            for ( int i=0; i<iindices.length; i++ ) {
                try {
                    PlotElementController pec= peles[iindices[i]].getController();
                    selected.add( pec.getRenderer() );
                    dsfs.add( (DataSourceFilter)DomUtil.getElementById( dom, pec.getPlotElement().getDataSourceFilterId() ) );
                    if ( dsfs.size()>0 ) {
                        dataSourceList.setSelectedValue( dsfs.get(0), true);        
                    }
                } catch ( IndexOutOfBoundsException ex ) {
                    // this happens because of multiple threads... TODO: fix this sometime...
                    System.err.println("harmless indexOutOfBoundsException needs to be fixed sometime");
                }
            }
        }

        updateDataSourceList();
        canvasLayoutPanel1.setSelectedComponents( selected );
        canvasLayoutPanel1.setComponent(dasPlot);
        
        //deletePlotAction.setEnabled( plotCount>0 );
        //tallerButton.setEnabled( plotCount>0 );
        //shorterButton.setEnabled( plotCount>0 );
        //sameHeightButton.setEnabled( plotCount>1 );
        deletePlotAction.setEnabled( true );
        tallerButton.setEnabled( true );
        shorterButton.setEnabled( true );
        sameHeightButton.setEnabled( true );
    }

    public void setApplication(Application app) {
        this.dom = app;
        updatePlotElementList();
        updateBindingList();
        updateDataSourceList();
        canvasLayoutPanel1.setContainer(app.getController().getDasCanvas());
        canvasLayoutPanel1.addComponentType(DasPlot.class, Color.BLUE);
        app.getController().bind(app.getOptions(), Options.PROP_BACKGROUND, canvasLayoutPanel1, "background");
        app.addPropertyChangeListener(Application.PROP_PLOT_ELEMENTS, plotElementsListener);
        app.addPropertyChangeListener(Application.PROP_BINDINGS, bindingsListener);
        app.addPropertyChangeListener(Application.PROP_DATASOURCEFILTERS, dataSourcesListener );
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT, plotListener);
        app.getController().addPropertyChangeListener(ApplicationController.PROP_PLOT_ELEMENT, plotElementListener);
        for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
            dsf.addPropertyChangeListener( DataSourceFilter.PROP_URI, dataSourceListener );
            dsf.getController().addPropertyChangeListener( DataSourceController.PROP_TSB, dataSourceListener );
        }        
    }

    private ListCellRenderer myListCellRenderer=  new DefaultListCellRenderer() {
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
                            @Override
                            public void propertyChange(PropertyChangeEvent evt) {
                                plotElementListComponent.repaint();
                            }
                        });
                    }
                }
            }
            return label;
        }
    };

    private void updatePlotElementListImmediately() {
        final Object[] foo= dom.getPlotElements();
        final AbstractListModel elementsList = new AbstractListModel() {
            @Override
            public int getSize() {
                return foo.length;
            }
            @Override
            public Object getElementAt(int index) {
                return foo[index];
            }
        };
        plotElementListComponent.setModel(elementsList);
        plotElementListComponent.setCellRenderer( myListCellRenderer );
    }

    private void updatePlotElementList() {
        Runnable run= new Runnable() { 
            @Override
            public void run() {
                updatePlotElementListImmediately();
            } 
        };
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
        final List bindingList= new ArrayList( Arrays.asList(dom.getBindings() ) );
        List rm= getSublist( bindingList, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                return ((BindingModel)o1).getDstProperty().equals("colortable") ? 0 : 1;
            }
        }, null );
        bindingList.removeAll(rm);
        AbstractListModel elementsList = new AbstractListModel() {
            @Override
            public int getSize() {
                return bindingList.size();
            }
            @Override
            public Object getElementAt(int index) {
                return bindingList.get(index);
            }
        };
        bindingListComponent.setModel(elementsList);
        bindingListComponent.repaint();
    }

    private void updateDataSourceList() {
        final List list= new ArrayList( Arrays.asList(dom.getDataSourceFilters() ) );
        AbstractListModel elementsList = new AbstractListModel() {
            @Override
            public int getSize() {
                return list.size();
            }
            @Override
            public Object getElementAt(int index) {
                return list.get(index);
            }
        };
        dataSourceList.setCellRenderer( new DefaultListCellRenderer()  {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l= (JLabel)super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
                DataSourceFilter dsf= (DataSourceFilter)value;
                if ( dsf.getController().getTsb()!=null ) {
                    l.setIcon( new ImageIcon( LayoutPanel.class.getResource("/resources/blue.gif" ) ) );
                    l.setToolTipText( "<html>"+dsf.getUri()+"<br>Data source provides Time Series Browsing");
                } else {
                    l.setIcon( new ImageIcon( LayoutPanel.class.getResource("/org/autoplot/resources/idle-icon.png" ) ) );
                    if ( dsf.getUri().length()==0 ) {
                        l.setToolTipText(null);
                    } else {
                        l.setToolTipText( "<html>"+dsf.getUri() );
                    }
                }
                return l;
            }
        });
        dataSourceList.setModel(elementsList);
        dataSourceList.repaint();
    }    
    
    /**
     * return a list of the selected plots, with the primary selection the first
     * item.
     * @return the selected plots.
     */
    public List<Plot> getSelectedPlots( ) {
        List<Object> os= canvasLayoutPanel1.getSelectedComponents();
        List<Plot> result= new ArrayList();

        for ( Object o: os ) {
            if (dom.getPlots().length > 1) {
                Plot domPlot=null;
                if ( o instanceof Component ) {
                    domPlot= dom.getController().getPlotFor((Component)o);
                }
                if ( domPlot==null ) continue;
                result.add(domPlot);
            }
        }
        Object o= canvasLayoutPanel1.getComponent();
        if ( o instanceof Component ) {
            Plot domPlot= dom.getController().getPlotFor((Component)o);
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
        dataSourceActionsMenu = new javax.swing.JPopupMenu();
        editMenuItem = new javax.swing.JMenuItem();
        jSplitPane1 = new javax.swing.JSplitPane();
        jPanel2 = new javax.swing.JPanel();
        jSplitPane3 = new javax.swing.JSplitPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        plotElementListComponent = new javax.swing.JList();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        jSplitPane2 = new javax.swing.JSplitPane();
        jPanel1 = new javax.swing.JPanel();
        canvasLayoutPanel1 = new org.autoplot.util.CanvasLayoutPanel();
        tallerButton = new javax.swing.JButton();
        shorterButton = new javax.swing.JButton();
        sameHeightButton = new javax.swing.JButton();
        addPlotsButton = new javax.swing.JButton();
        fixLayoutButton = new javax.swing.JButton();
        deletePlotButton = new javax.swing.JButton();
        selectedPlotLabel = new javax.swing.JLabel();
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

        editMenuItem.setText("Edit");
        editMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editMenuItemActionPerformed(evt);
            }
        });
        dataSourceActionsMenu.add(editMenuItem);

        jSplitPane1.setDividerLocation(330);
        jSplitPane1.setResizeWeight(0.5);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Plot Elements [?]"));
        jPanel2.setToolTipText("List of plot elements (renderings of data) on the canvas");

        jSplitPane3.setDividerLocation(370);
        jSplitPane3.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        plotElementListComponent.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        plotElementListComponent.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                plotElementListComponentValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(plotElementListComponent);

        jSplitPane3.setTopComponent(jScrollPane1);

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Sources"));

        dataSourceList.setFont(dataSourceList.getFont().deriveFont(dataSourceList.getFont().getSize()-2f));
        dataSourceList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        dataSourceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                dataSourceListValueChanged(evt);
            }
        });
        jScrollPane4.setViewportView(dataSourceList);

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 343, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jScrollPane4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 63, Short.MAX_VALUE)
        );

        jSplitPane3.setRightComponent(jPanel4);

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane3)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jSplitPane3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 461, Short.MAX_VALUE)
        );

        jSplitPane1.setRightComponent(jPanel2);

        jSplitPane2.setDividerLocation(370);
        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane2.setResizeWeight(0.5);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Plots [?]"));
        jPanel1.setToolTipText("<html>Layout of plots on the canvas<br>Click for help");
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

        addPlotsButton.setAction(addPlotsAction);
        addPlotsButton.setText("Add Plots...");
        addPlotsButton.setToolTipText("Add plots around the focus plot");

        fixLayoutButton.setText("Fix Layout");
        fixLayoutButton.setToolTipText("Remove gaps and overlaps in vertical stack of plots");
        fixLayoutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixLayoutButtonActionPerformed(evt);
            }
        });

        deletePlotButton.setAction(deletePlotAction);
        deletePlotButton.setText("Delete Plots");
        deletePlotButton.setToolTipText("Delete the selected plot(s)");
        deletePlotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deletePlotButtonActionPerformed(evt);
            }
        });

        selectedPlotLabel.setFont(selectedPlotLabel.getFont().deriveFont(selectedPlotLabel.getFont().getSize()-2f));
        selectedPlotLabel.setText(" ");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(tallerButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(shorterButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(sameHeightButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)
                    .add(fixLayoutButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(addPlotsButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(deletePlotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                .add(8, 8, 8))
            .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel1Layout.createSequentialGroup()
                .add(selectedPlotLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(canvasLayoutPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 266, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(selectedPlotLabel)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(tallerButton)
                    .add(addPlotsButton)
                    .add(sameHeightButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(fixLayoutButton)
                    .add(shorterButton)
                    .add(deletePlotButton)))
        );

        jSplitPane2.setTopComponent(jPanel1);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Bindings [?]"));
        jPanel3.setToolTipText("List of connections between DOM properties");

        bindingListComponent.setFont(bindingListComponent.getFont().deriveFont(bindingListComponent.getFont().getSize()-2f));
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
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 329, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 82, Short.MAX_VALUE)
        );

        jSplitPane2.setRightComponent(jPanel3);

        jSplitPane1.setLeftComponent(jSplitPane2);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jSplitPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 718, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void propertiesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_propertiesMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        DasPlot component= (DasPlot)canvasLayoutPanel1.getComponent();
        Plot domPlot = dom.getController().getPlotFor(component);
        if ( domPlot==null ) {
            this.dom.getController().setStatus("warning: nothing selected");
            return;
        }
        List<Object> components= canvasLayoutPanel1.getSelectedComponents();
        List<Plot> plots= new ArrayList();
        for ( int i=0; i<components.size(); i++ ) {
            if ( components.get(i) instanceof Component ) { // might have renderer selected
                plots.add(dom.getController().getPlotFor( (Component) components.get(i) ) );
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
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        List<Plot> plots= getSelectedPlots();
        if ( plots.size()==2 ) {
            DomOps.swapPosition( plots.get(0), plots.get(1) );
            if ( dom.getOptions().isAutolayout() ) DomOps.newCanvasLayout(dom);
            this.dom.getController().setStatus("swapped "+plots.get(0)+ " and " +plots.get(1) );
        } else {
            this.dom.getController().setStatus("warning: select two plots");
        }
    }//GEN-LAST:event_swapMenuItemActionPerformed

    private void addPlotsBelowMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addPlotsBelowMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        // TODO add your handling code here:
    }//GEN-LAST:event_addPlotsBelowMenuItemActionPerformed

    private void addHiddenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addHiddenMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        BindToHiddenDialog dia= new BindToHiddenDialog();

        int op= JOptionPane.showConfirmDialog( this, dia, "Add hidden plot for binding", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE );
        if ( op==JOptionPane.OK_OPTION ) {
            final String lock = "Add hidden plot";

            List<Plot> plots= getSelectedPlots();
            if ( plots.isEmpty() ) return;

            dom.getController().registerPendingChange( this, lock);
            try {
                dom.getController().performingChange( this, lock);

                Column col= DomOps.getOrCreateSelectedColumn(dom, getSelectedPlots(), true );
                Row row= DomOps.getOrCreateSelectedRow(dom, getSelectedPlots(), true );

                Plot p= dom.getController().addPlot(row, col);
                PlotElement pe= dom.getController().addPlotElement( p, null );
                pe.setActive(false);
                p.setVisible(false);
                p.getXaxis().setVisible(false);
                p.getYaxis().setVisible(false);

                Plot[] bottomTopPlots= DomOps.bottomAndTopMostPlot(dom, plots);

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
                        BindingModel check= 
                            dom.getController().findBinding(dom, Application.PROP_TIMERANGE, p1.getXaxis(), Axis.PROP_RANGE );
                        if ( check!=null ) {
                            dom.getController().bind(dom, Application.PROP_TIMERANGE, p.getXaxis(), "range" );
                        } else {
                            dom.getController().bind( p.getXaxis(), "range", p1.getXaxis(), "range" );
                        }
                        p.getXaxis().setLog( log );
                        dom.getController().bind( p.getXaxis(), "log", p1.getXaxis(), "log" );
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
                        dom.getController().bind( p.getYaxis(), "range", p1.getYaxis(), "range" );
                        p.getYaxis().setLog( log );
                        dom.getController().bind( p.getYaxis(), "log", p1.getYaxis(), "log" );
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
                        dom.getController().bind( p.getZaxis(), "range", p1.getZaxis(), "range" );
                        p.getZaxis().setLog( log );
                        dom.getController().bind( p.getZaxis(), "log", p1.getZaxis(), "log" );
                    }
                }
                // bind the colortables
                if ( dia.getCondenseColorBarsCB().isSelected() ) { 
                    for ( Plot p1: getSelectedPlots() ) {
                        dom.getController().bind( p, "colortable", p1, "colortable" );
                    }
                }

                if ( dia.getCondenseXAxisLabelsCB().isSelected() ) {
                    String t= plots.get(0).getTitle();
                    for ( Plot p1: getSelectedPlots() ) {
                        p1.getXaxis().setDrawTickLabels(false);
                        p1.getXaxis().setLabel("");
                        p1.setTitle("");
                        Row r= dom.getCanvases(0).getController().getRowFor(p1);
                        r.setTop( r.getTop().replaceAll( "(.*)\\+([\\d\\.]+)em(.*)","$1+0.5em" ) );
                        r.setBottom( r.getBottom().replaceAll( "(.*)\\-([\\d\\.]+)em","$1-0.5em" ) );
                    }
                    bottomTopPlots[1].setTitle(t);
                    bottomTopPlots[0].getXaxis().setDrawTickLabels(true);
                    row.setTop( row.getTop().replaceAll( "(.*)\\+([\\d\\.]+)em(.*)","$1+0.5em" ) );
                    row.setBottom( row.getBottom().replaceAll( "(.*)\\-([\\d\\.]+)em","$1-0.5em" ) );
                    
                }

            } finally {
                dom.getController().changePerformed( this, lock);
            }


        }
    }//GEN-LAST:event_addHiddenMenuItemActionPerformed

    private void deleteBindingsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBindingsMenuItemActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        Object[] bindings= bindingListComponent.getSelectedValues();
        for ( Object o:bindings ) {
            BindingModel b= (BindingModel)o;
            dom.getController().deleteBinding(b);
        }
    }//GEN-LAST:event_deleteBindingsMenuItemActionPerformed

    private void plotElementListComponentValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_plotElementListComponentValueChanged
        logger.log(Level.FINE, "panelListComponentValueChanged {0}", evt.getValueIsAdjusting());
        if ( !evt.getValueIsAdjusting() ) {
            updateSelected();
        }
    }//GEN-LAST:event_plotElementListComponentValueChanged

    private void fixLayout( java.awt.event.ActionEvent evt) {                                         
        org.das2.util.LoggerManager.logGuiEvent(evt);       
        org.autoplot.dom.DomOps.newCanvasLayout(dom);
    }
    
    private void biggerMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biggerMIActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        List<Row> rows= new ArrayList<>();
        for ( Plot p1: getSelectedPlots() ) {
            if ( p1.isVisible() ) {
                Row row= p1.getController().getRow();
                if ( !rows.contains(row) ) rows.add(row);
            }
        }
        
        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseLayoutStr( r.getTop() );
                double[] d2= DasDevicePosition.parseLayoutStr( r.getBottom() );
                d2[0]= d1[0] + ( d2[0]-d1[0] ) * 1.25;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
            } catch ( ParseException ex ) {
                logger.info("ParseException ignored");
            }
        }

        if ( dom.getOptions().isAutolayout() ) org.autoplot.dom.DomOps.newCanvasLayout(dom);
        
    }//GEN-LAST:event_biggerMIActionPerformed

    private void sameSizeMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sameSizeMIActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        List<Row> rows= new ArrayList<>();
        for ( Plot p1: getSelectedPlots() ) {
            if ( p1.isVisible() ) {
                Row row= p1.getController().getRow();
                if ( !rows.contains(row) ) rows.add(row);
            }
        }

        double size= 0;
        double emMaxTop= 0;
        double emMaxBottom= 0;
        int n= 0;

        // calculate the average size
        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseLayoutStr( r.getTop() );
                double[] d2= DasDevicePosition.parseLayoutStr( r.getBottom() );
                size= size + ( d2[0]-d1[0] );
                emMaxBottom= Math.max( emMaxBottom, d2[1] );
                emMaxTop= Math.max( emMaxTop, d2[1] );
                n= n+1;
            } catch ( ParseException ex ) {
                logger.info("ParseException ignored");
            }
        }

        size= size / n;

        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseLayoutStr( r.getTop() );
                double[] d2= DasDevicePosition.parseLayoutStr( r.getBottom() );
                d2[0]= d1[0] + size;
                d2[1]= emMaxBottom;
                d1[1]= emMaxTop;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
                r.setTop( DasDevicePosition.formatFormatStr(d1) );
            } catch ( ParseException ex ) {}
        }

        if ( dom.getOptions().isAutolayout() ) org.autoplot.dom.DomOps.newCanvasLayout(dom);
        
    }//GEN-LAST:event_sameSizeMIActionPerformed

    private void smallerMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smallerMIActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        List<Row> rows= new ArrayList<>();
        for ( Plot p1: getSelectedPlots() ) {
            if ( p1.isVisible() ) {
                Row row= p1.getController().getRow();
                if ( !rows.contains(row) ) rows.add(row);
            }
        }

        for ( Row r: rows ) {
            try {
                double[] d1= DasDevicePosition.parseLayoutStr( r.getTop() );
                double[] d2= DasDevicePosition.parseLayoutStr( r.getBottom() );
                d2[0]= d1[0] + ( d2[0]-d1[0] ) * 0.80;
                r.setBottom( DasDevicePosition.formatFormatStr(d2) );
            } catch ( ParseException ex ) {
                logger.info("ParseException ignored");
            }
        }

        if ( dom.getOptions().isAutolayout() ) org.autoplot.dom.DomOps.newCanvasLayout(dom);

    }//GEN-LAST:event_smallerMIActionPerformed

    private void deletePlotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deletePlotButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_deletePlotButtonActionPerformed

    private void sameHeightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sameHeightButtonActionPerformed
        sameSizeMIActionPerformed(evt);
    }//GEN-LAST:event_sameHeightButtonActionPerformed

    private void shorterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shorterButtonActionPerformed
        smallerMIActionPerformed(evt);
    }//GEN-LAST:event_shorterButtonActionPerformed

    private void tallerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tallerButtonActionPerformed
        biggerMIActionPerformed(evt);
    }//GEN-LAST:event_tallerButtonActionPerformed

    private void fixLayoutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixLayoutButtonActionPerformed
        fixLayout(evt);
    }//GEN-LAST:event_fixLayoutButtonActionPerformed

    private void dataSourceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_dataSourceListValueChanged
        Object s= dataSourceList.getSelectedValue();
        if ( s instanceof DataSourceFilter ) { // transitional state where strings are in there
            dom.getController().setDataSourceFilter( (DataSourceFilter)s );
            dom.getController().setFocusUri(((DataSourceFilter)s).getUri());
            List<PlotElement> pes= DomUtil.getPlotElementsFor(dom,((DataSourceFilter)s));            
            if ( pes.size()>0 ) {
                if ( !pes.contains( (PlotElement)plotElementListComponent.getSelectedValue() ) ) {
                    dom.getController().setPlotElement(pes.get(0));
                }
            }
        }
        
    }//GEN-LAST:event_dataSourceListValueChanged

    private void editMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editMenuItemActionPerformed
        Object s= dataSourceList.getSelectedValue();
        if ( s instanceof DataSourceFilter ) { // transitional state where strings are in there
            dom.getController().setDataSourceFilter((DataSourceFilter)s);
            dom.getController().setFocusUri(((DataSourceFilter)s).getUri());
            String uri= ((DataSourceFilter)s).getUri();
            if ( uri.startsWith("vap+internal:") ) {
                GuiSupport.editPlotElement( applicationModel, this );
            } else {
                JPanel parent= new JPanel();
                parent.setLayout( new BorderLayout() );
                DataSourceEditorPanel p= DataSourceEditorPanelUtil.getDataSourceEditorPanel( parent, uri );
                if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, parent, "Edit "+((DataSourceFilter)s).getId(), JOptionPane.OK_CANCEL_OPTION ) ) {
                    uri= p.getURI();
                    ((DataSourceFilter)s).setUri(uri);
                }
            }
        }
    }//GEN-LAST:event_editMenuItemActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem addHiddenMenuItem;
    private javax.swing.JMenuItem addPlotsBelowMenuItem;
    private javax.swing.JButton addPlotsButton;
    private javax.swing.JMenuItem biggerMI;
    private javax.swing.JPopupMenu bindingActionsMenu;
    private javax.swing.JList bindingListComponent;
    private org.autoplot.util.CanvasLayoutPanel canvasLayoutPanel1;
    private javax.swing.JPopupMenu dataSourceActionsMenu;
    private javax.swing.JList<String> dataSourceList;
    private javax.swing.JMenuItem deleteBindingsMenuItem;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JButton deletePlotButton;
    private javax.swing.JMenuItem editMenuItem;
    private javax.swing.JButton fixLayoutButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JSplitPane jSplitPane3;
    private javax.swing.JTable jTable1;
    private javax.swing.JPopupMenu plotActionsMenu;
    private javax.swing.JList plotElementListComponent;
    private javax.swing.JMenu plotMenu;
    private javax.swing.JMenu plotsMenu;
    private javax.swing.JMenuItem propertiesMenuItem;
    private javax.swing.JMenuItem removeBindingsMenuItem;
    private javax.swing.JButton sameHeightButton;
    private javax.swing.JMenuItem sameSizeMI;
    private javax.swing.JLabel selectedPlotLabel;
    private javax.swing.JButton shorterButton;
    private javax.swing.JMenu sizeMenu;
    private javax.swing.JMenuItem smallerMI;
    private javax.swing.JMenuItem swapMenuItem;
    private javax.swing.JButton tallerButton;
    // End of variables declaration//GEN-END:variables

}
