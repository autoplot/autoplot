/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.autoplot;

import org.autoplot.ApplicationModel;
import org.autoplot.APSplash;
import java.awt.event.FocusEvent;
import org.das2.components.DatumRangeEditor;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasPlot;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.dom.Application;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.Axis;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Plot;
import org.autoplot.dom.TimeSeriesBrowseController;
import org.autoplot.datasource.TimeRangeEditor;
import org.autoplot.dom.DataSourceController;
import org.das2.graph.GraphUtil;
import org.das2.components.GrannyTextEditor;

/**
 * Panel for controlling the axes of the current focus plot.
 * @author  jbf
 */
public class AxisPanel extends javax.swing.JPanel {

    private final Application dom;
    private final ApplicationController applicationController;
    private DatumRangeEditor xredit;
    private DatumRangeEditor yredit;
    private DatumRangeEditor zredit;

    private Plot currentPlot; // the plot we are currently controlling, should be consistent with plotBindingGroup.
    
    private BindingGroup plotBindingGroup;
    private boolean plotBindingGroupIsBound= false;
    
    private BindingGroup plotElementBindingGroup;    
    private boolean plotElementBindingGroupIsBound= false;
    
    private String timeRangeBindingType= "none";
    
    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui");

    /** 
     * Creates new form PlotStylePanel
     * @param applicationModel
     */
    public AxisPanel(final ApplicationModel applicationModel) {
        APSplash.checkTime("in axispanel 10");
        this.dom = applicationModel.dom;
        this.applicationController= this.dom.getController();
        dom.addPropertyChangeListener( Application.PROP_BINDINGS, timeRangeContextControllerEnabler );
        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_PLOT, (final PropertyChangeEvent evt) -> {
            Runnable run= () -> {
                Plot oldPlot= (Plot)evt.getOldValue();
                if ( oldPlot!=null ) {
                    oldPlot.getXaxis().removePropertyChangeListener( timeRangeAxisControllerEnabler );
                }
                doPlotBindings();
            };
            SwingUtilities.invokeLater(run);
        });

        APSplash.checkTime("in axispanel 15");
        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_PLOT_ELEMENT, (PropertyChangeEvent evt) -> {
            Runnable run= () -> {
                doPlotElementBindings();
            };
            SwingUtilities.invokeLater(run);
        });
            // there's a strange delay here on a mac.  We work around this be delaying construction on gui.
        APSplash.checkTime("in axispanel 17");
        initComponents();

        APSplash.checkTime("in axispanel 20");
        DasPlot plot = applicationController.getPlot().getController().getDasPlot();
        DasColorBar colorbar = applicationController.getPlot().getController().getDasColorBar();

        xredit = new DatumRangeEditor();
        xredit.setValue(plot.getXAxis().getDatumRange());
        xredit.addFocusListener( createDatumRangeEditorListener(xredit) );
        xredit.setToolTipText("X axis range");
        xredit.setAllowZeroWidth(false);
        xAxisRangePanel.add(xredit, BorderLayout.CENTER);

        yredit = new DatumRangeEditor();
        yredit.setValue(plot.getYAxis().getDatumRange());
        yredit.addFocusListener( createDatumRangeEditorListener(yredit) );
        yredit.setToolTipText("Y axis range");
        yredit.setAllowZeroWidth(false);
        yAxisRangePanel.add(yredit, BorderLayout.CENTER);

        zredit = new DatumRangeEditor();
        zredit.setValue(colorbar.getDatumRange());
        zredit.addFocusListener( createDatumRangeEditorListener(zredit) );
        zredit.setToolTipText("Z axis range");
        zredit.setAllowZeroWidth(false);
        zAxisRangePanel.add(zredit, BorderLayout.CENTER);
        APSplash.checkTime("in axispanel 30");

        xredit.addPropertyChangeListener( (PropertyChangeEvent ev) -> {
            DatumRange dr= (DatumRange)xredit.getValue();
            Runnable run= () -> {
                xLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
                if ( !xLog.isEnabled() ) xLog.setSelected(false);
            };
            SwingUtilities.invokeLater(run);
        });
        
        yredit.addPropertyChangeListener( (PropertyChangeEvent ev) -> {
            DatumRange dr= (DatumRange)yredit.getValue();
            Runnable run= () -> {
                yLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
                if ( !yLog.isEnabled() ) yLog.setSelected(false);
            };
            SwingUtilities.invokeLater(run);
        });
        
        zredit.addPropertyChangeListener( (PropertyChangeEvent ev) -> {
            DatumRange dr= (DatumRange)zredit.getValue();
            Runnable run= () -> {
                zLog.setEnabled( UnitsUtil.isRatioMeasurement(dr.getUnits() ) );
                if ( !zLog.isEnabled() ) zLog.setSelected(false);
            };
            SwingUtilities.invokeLater(run);
        });

        timeRangeEditor1.setNoOneListeningRange( Application.DEFAULT_TIME_RANGE );
        timeRangeEditor1.setRange( Application.DEFAULT_TIME_RANGE );
        
        doPlotBindings();
        doPlotElementBindings();
                
        timeRangeEditor1.addPropertyChangeListener( TimeRangeEditor.PROP_RANGE, timeRangeEditorListener);

        APSplash.checkTime("in axispanel 40");

        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "axesPanel");
        APSplash.checkTime("in axispanel 50");

    }

    /**
     * to avoid use of synchronized blocks, methods must be called from the
     * event thread.  This verifies that the thread is the event thread.
     * @param caller 
     */
    private static void assertEventThread( String caller ) {
        if ( !SwingUtilities.isEventDispatchThread() ) {
            throw new IllegalArgumentException( caller + " must be called from the event thread.");
        }
    }
    
    @Override
    public void paint(Graphics g) {
        if ( !plotBindingGroupIsBound ) {
            plotBindingGroup.bind();
            plotBindingGroupIsBound= true;
        }
        if ( !plotElementBindingGroupIsBound ) {
            plotElementBindingGroup.bind();
            plotElementBindingGroupIsBound= true;
        }
        super.paint(g); //To change body of generated methods, choose Tools | Templates.
    }

    private FocusListener createDatumRangeEditorListener( final DatumRangeEditor edit ) {
        return new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }
            @Override
            public void focusLost(FocusEvent e) {
                edit.setValue( edit.getValue() );
            }
        };
    }

    private void doPlotBindings() {
        
        assertEventThread("doPlotBindings");
        
        BindingGroup bc = new BindingGroup();
        
        Plot p = applicationController.getPlot();
        
        if (plotBindingGroup != null) plotBindingGroup.unbind(); // consider synchronized block, or require that this always be called from the event thread, or check that the plot has changed.
        
        //http://www.infoq.com/news/2007/09/beans-binding
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.label"), xTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.range"), xredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.log"), xLog, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("xaxis.drawTickLabels"), showXAxisLabelsCB, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.label"), yTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.range"), yredit,BeanProperty.create( "value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("yaxis.log"), yLog, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.label"), zTitleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.range"), zredit, BeanProperty.create("value")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.log"), zLog, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("zaxis.visible"), cbVisibleCB, BeanProperty.create("selected")));

        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("title"), titleTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("displayTitle"), titleCB, BeanProperty.create("selected")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("isotropic"), this.isotropicCheckBox, BeanProperty.create("selected")));

        plotBindingGroup = bc;
        plotBindingGroupIsBound= false;

        doCheckTimeRangeControllerEnable();
        p.getXaxis().addPropertyChangeListener( Axis.PROP_RANGE, timeRangeAxisControllerEnabler );

        repaint();

    }
    
    private void runOnEventThread( Runnable run ) {
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private Runnable timeAxisChangeRunnable= new Runnable() {
        @Override
        public void run() {
            Plot p= applicationController.getPlot();
            if ( p!=currentPlot || timeRangeBindingType.equals("xaxis") != UnitsUtil.isTimeLocation( p.getXaxis().getRange().getUnits() ) ) {
                doCheckTimeRangeControllerEnable();
            }
        }
    };
            
    private PropertyChangeListener timeRangeAxisControllerEnabler= (PropertyChangeEvent evt ) -> {
        runOnEventThread( timeAxisChangeRunnable );
    };
            
    /**
     * this listens for binding changes to the plot context property.
     */
    private final PropertyChangeListener timeRangeContextControllerEnabler= (PropertyChangeEvent evt) -> {
        runOnEventThread( () -> { doCheckTimeRangeControllerEnable(); } );
    };

    
    private boolean isSomeoneListening( Plot p ) {
        List<PlotElement> pes= dom.getController().getPlotElementsFor(p);
        for ( PlotElement pe: pes ) {
            DataSourceFilter dsf= pe.getController().getDataSourceFilter();
            DataSourceController dsfc= dsf.getController();
            if ( dsfc==null ) return false;
            TimeSeriesBrowseController tsbc= dsfc.getTimeSeriesBrowseController();
            if ( tsbc!=null ) {
                if ( p.getId().equals(tsbc.getPlotId()) ) {
                    return true;
                }
            }
        }
        return false;
    }
    
    PropertyChangeListener pcl1= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ( timeRangeBindingType.equals("xaxis") ) {
                DatumRange dr= currentPlot.getXaxis().getRange();
                if ( dr.getUnits().isConvertibleTo(timeRangeEditor1.getRange().getUnits() ) ) {
                    timeRangeEditor1.setRange( dr );
                }
            } else if ( timeRangeBindingType.startsWith("context")) {
                DatumRange dr= currentPlot.getContext();
                if ( dr.getUnits().isConvertibleTo(timeRangeEditor1.getRange().getUnits() ) ) {
                    timeRangeEditor1.setRange( dr );
                }
            }
        }
    };
            
    PropertyChangeListener timeRangeEditorListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            DatumRange dr=  timeRangeEditor1.getRange();
            if ( timeRangeBindingType.equals("xaxis") ) {
                if ( dr.getUnits().isConvertibleTo( currentPlot.getXaxis().getRange().getUnits() ) ) {
                    currentPlot.getXaxis().setRange( dr );
                }
            } else if ( timeRangeBindingType.startsWith("context")) {
                if ( dr.getUnits().isConvertibleTo( currentPlot.getContext().getUnits() ) ) {
                    currentPlot.setContext( dr );
                }
            }
        }
    };
    
    /**
     * do the somewhat expensive check to see if the timerange controller needs
     * to be bound.
     */
    private void doCheckTimeRangeControllerEnable( ) {
      
        assertEventThread("doCheckTimeRangeControllerEnable");
        String type;
        
        Plot p= applicationController.getPlot();
        if ( p==null ) return; // transitional state observed in JNLP release
        if ( UnitsUtil.isTimeLocation( p.getXaxis().getRange().getUnits() ) ) {
            type= "xaxis";    
        } else if ( dom.getController().findBindings( p, "context").size()>0 && dom.getController().isTimeSeriesBrowse(p) ) {
            type= "context_"+p.getId();
        } else if ( isSomeoneListening(p) ) {
            type= "context_"+p.getId();
        } else {
            type= "none";
        }

        logger.log(Level.FINE, "timeRangeBindingType {0}", type);
        
        if ( p!=currentPlot && currentPlot!=null ) {
            currentPlot.removePropertyChangeListener(pcl1);
            currentPlot.getXaxis().removePropertyChangeListener(pcl1);
        }
        if ( (!type.equals(timeRangeBindingType)) || ( p!=currentPlot ) ) {
            if ( type.equals("xaxis") ) {
                    this.timeRangeEditor1.setEnabled(true);
                    this.timeRangeEditor1.setToolTipText("controlling "+p.getId()+" xaxis");
                    timeRangeBindingType= type;
                    this.timeRangeEditor1.setRange( p.getXaxis().getRange() );
            } else if ( type.startsWith("context") ) {
                    this.timeRangeEditor1.setEnabled(true);
                    this.timeRangeEditor1.setToolTipText("controlling "+p.getId()+".context");
                    timeRangeBindingType= type;
                    this.timeRangeEditor1.setRange( p.getContext() );
            } else {
                    this.timeRangeEditor1.setEnabled(false);
                    this.timeRangeEditor1.setToolTipText("plot context control has no effect.");
                    this.timeRangeEditor1.setRange( this.timeRangeEditor1.getNoOneListeningRange() );
                    timeRangeBindingType= type;
            }
            currentPlot= p;
            currentPlot.addPropertyChangeListener(pcl1);
            currentPlot.getXaxis().addPropertyChangeListener(pcl1);
            timeRangeEditor1.setToolTipText(timeRangeBindingType); // temporary for debugging
        }
    }
    
    private void doPlotElementBindings() {
        
        assertEventThread("doPlotElementBindings");
        BindingGroup bc = new BindingGroup();
        
        if (plotElementBindingGroup != null) plotElementBindingGroup.unbind();

        PlotElement p = applicationController.getPlotElement();
        
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,p, BeanProperty.create("legendLabel"), legendTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, p, BeanProperty.create("displayLegend"), legendEnableCheckbox, BeanProperty.create("selected")));
        
        plotElementBindingGroup = bc;
        plotElementBindingGroupIsBound= false;
        
        repaint();

    }



    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        xAxisPanel = new javax.swing.JPanel();
        xLog = new javax.swing.JCheckBox();
        xAxisRangePanel = new javax.swing.JPanel();
        xTitleTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        showXAxisLabelsCB = new javax.swing.JCheckBox();
        xaxisGTEButton = new javax.swing.JButton();
        zAxisPanel = new javax.swing.JPanel();
        zLog = new javax.swing.JCheckBox();
        zAxisRangePanel = new javax.swing.JPanel();
        zTitleTextField = new javax.swing.JTextField();
        cbVisibleCB = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        colorbarGTEButton = new javax.swing.JButton();
        yAxisPanel = new javax.swing.JPanel();
        yAxisRangePanel = new javax.swing.JPanel();
        yLog = new javax.swing.JCheckBox();
        isotropicCheckBox = new javax.swing.JCheckBox();
        yTitleTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        yaxisGTEButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        titleTextField = new javax.swing.JTextField();
        legendEnableCheckbox = new javax.swing.JCheckBox();
        legendTextField = new javax.swing.JTextField();
        titleCB = new javax.swing.JCheckBox();
        timeRangeEditor1 = new org.autoplot.datasource.TimeRangeEditor();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        xAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("X Axis [?]"));
        xAxisPanel.setToolTipText("click title for help");

        xLog.setText("Log");
        xLog.setToolTipText("X axis logarithmic scale");
        xLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        xLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                xLogActionPerformed(evt);
            }
        });

        xAxisRangePanel.setLayout(new java.awt.BorderLayout());

        xTitleTextField.setText("jTextField1");
        xTitleTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
        });

        jLabel1.setText("Label:");
        jLabel1.setToolTipText("X axis title");

        showXAxisLabelsCB.setText("Show Labels");
        showXAxisLabelsCB.setToolTipText("Hide the axis labels of the axis, to make a stack of plots more efficient.  Run Tools->\"Fix Layout\" to pack the plots.");
        showXAxisLabelsCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showXAxisLabelsCBActionPerformed(evt);
            }
        });

        xaxisGTEButton.setText("...");
        xaxisGTEButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                xaxisGTEButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout xAxisPanelLayout = new org.jdesktop.layout.GroupLayout(xAxisPanel);
        xAxisPanel.setLayout(xAxisPanelLayout);
        xAxisPanelLayout.setHorizontalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(xAxisPanelLayout.createSequentialGroup()
                        .add(xLog)
                        .add(18, 18, 18)
                        .add(showXAxisLabelsCB)
                        .add(0, 9, Short.MAX_VALUE))
                    .add(xAxisPanelLayout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(xTitleTextField)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(xaxisGTEButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        xAxisPanelLayout.setVerticalGroup(
            xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(xAxisPanelLayout.createSequentialGroup()
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(xTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel1)
                    .add(xaxisGTEButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(xAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(xLog)
                    .add(showXAxisLabelsCB))
                .addContainerGap(12, Short.MAX_VALUE))
        );

        zAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Colorbar [?]"));
        zAxisPanel.setToolTipText("click title for help");

        zLog.setText("Log");
        zLog.setToolTipText("colorbar logarithmic scale");
        zLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        zLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zLogActionPerformed(evt);
            }
        });

        zAxisRangePanel.setLayout(new java.awt.BorderLayout());

        zTitleTextField.setText("jTextField1");
        zTitleTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
        });

        cbVisibleCB.setText("Visible");
        cbVisibleCB.setToolTipText("hide/show colorbar");
        cbVisibleCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbVisibleCBActionPerformed(evt);
            }
        });

        jLabel3.setText("Label:");
        jLabel3.setToolTipText("Colorbar title");

        colorbarGTEButton.setText("...");
        colorbarGTEButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                colorbarGTEButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout zAxisPanelLayout = new org.jdesktop.layout.GroupLayout(zAxisPanel);
        zAxisPanel.setLayout(zAxisPanelLayout);
        zAxisPanelLayout.setHorizontalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(zAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zAxisPanelLayout.createSequentialGroup()
                        .add(zLog)
                        .add(18, 18, 18)
                        .add(cbVisibleCB))
                    .add(zAxisPanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(zTitleTextField)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(colorbarGTEButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        zAxisPanelLayout.setVerticalGroup(
            zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(zAxisPanelLayout.createSequentialGroup()
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(zTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel3)
                    .add(colorbarGTEButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(zLog)
                    .add(cbVisibleCB))
                .addContainerGap(22, Short.MAX_VALUE))
        );

        yAxisPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Y Axis [?]"));
        yAxisPanel.setToolTipText("click title for help");

        yAxisRangePanel.setLayout(new java.awt.BorderLayout());

        yLog.setText("Log");
        yLog.setToolTipText("Y axis logarithmic scale");
        yLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        yLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                yLogActionPerformed(evt);
            }
        });

        isotropicCheckBox.setText("Isotropic");
        isotropicCheckBox.setToolTipText("When Y axis units are convertable to X axis units, automatically set Y axis range to ensure pixel:data ratio is the same.");
        isotropicCheckBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        isotropicCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isotropicCheckBoxActionPerformed(evt);
            }
        });

        yTitleTextField.setText("jTextField1");
        yTitleTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                axisMouseAction(evt);
            }
        });

        jLabel2.setText("Label:");
        jLabel2.setToolTipText("Y axis title");

        yaxisGTEButton.setText("...");
        yaxisGTEButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                yaxisGTEButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout yAxisPanelLayout = new org.jdesktop.layout.GroupLayout(yAxisPanel);
        yAxisPanel.setLayout(yAxisPanelLayout);
        yAxisPanelLayout.setHorizontalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(yLog)
                    .add(isotropicCheckBox)
                    .add(yAxisPanelLayout.createSequentialGroup()
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yTitleTextField)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(yaxisGTEButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        yAxisPanelLayout.setVerticalGroup(
            yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(yAxisPanelLayout.createSequentialGroup()
                .add(yAxisPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(yTitleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2)
                    .add(yaxisGTEButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yAxisRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yLog)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(isotropicCheckBox)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Plot [?]"));
        jPanel1.setToolTipText("click title for help");

        titleTextField.setText("title will go here");
        titleTextField.setMaximumSize(new java.awt.Dimension(700, 2147483647));
        titleTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                titleMouseAction(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                titleMouseAction(evt);
            }
        });

        legendEnableCheckbox.setText("Legend Label:");
        legendEnableCheckbox.setToolTipText("When selected, the label is added to the legend of the plot containing the focus plot element.\n\n");
        legendEnableCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                legendEnableCheckboxActionPerformed(evt);
            }
        });

        legendTextField.setText("label will go here");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, legendEnableCheckbox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), legendTextField, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        legendTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                legendTextFieldMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                legendTextFieldMouseReleased(evt);
            }
        });

        titleCB.setText("Title:");
        titleCB.setToolTipText("Title for the focus plot.  When deselected, the plot title is hidden.\n");
        titleCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                titleCBActionPerformed(evt);
            }
        });

        jButton1.setText("...");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setText("...");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(timeRangeEditor1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 395, Short.MAX_VALUE)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(titleCB)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(titleTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(legendEnableCheckbox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(legendTextField)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(jButton2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                            .add(jButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(titleTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(titleCB)
                    .add(jButton1))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(legendEnableCheckbox)
                    .add(legendTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButton2))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(timeRangeEditor1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(20, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(xAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(yAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(zAxisPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel1, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(xAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(yAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(zAxisPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void legendTextFieldMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_legendTextFieldMousePressed
        if ( evt.isPopupTrigger() ) {
            showLabelMenu(evt);
        }
    }//GEN-LAST:event_legendTextFieldMousePressed

    private void legendTextFieldMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_legendTextFieldMouseReleased
        if ( evt.isPopupTrigger() ) {
            showLabelMenu(evt);
        }
    }//GEN-LAST:event_legendTextFieldMouseReleased

    private void axisMouseAction(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_axisMouseAction
        if ( evt.isPopupTrigger() ) {
            showAxisMenu(evt);
        }
    }//GEN-LAST:event_axisMouseAction

    private void titleMouseAction(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_titleMouseAction
        if ( evt.isPopupTrigger() ) {
            showTitleMenu(evt);
        }
    }//GEN-LAST:event_titleMouseAction

    private void xLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_xLogActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_xLogActionPerformed

    private void showXAxisLabelsCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showXAxisLabelsCBActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);        
    }//GEN-LAST:event_showXAxisLabelsCBActionPerformed

    private void yLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_yLogActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_yLogActionPerformed

    private void isotropicCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isotropicCheckBoxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_isotropicCheckBoxActionPerformed

    private void titleCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_titleCBActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_titleCBActionPerformed

    private void legendEnableCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_legendEnableCheckboxActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_legendEnableCheckboxActionPerformed

    private void zLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zLogActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_zLogActionPerformed

    private void cbVisibleCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbVisibleCBActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
    }//GEN-LAST:event_cbVisibleCBActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        addEditGrannyText(titleTextField);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        addEditGrannyText(legendTextField);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void xaxisGTEButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_xaxisGTEButtonActionPerformed
        addEditGrannyText(xTitleTextField);
    }//GEN-LAST:event_xaxisGTEButtonActionPerformed

    private void yaxisGTEButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_yaxisGTEButtonActionPerformed
        addEditGrannyText(yTitleTextField);
    }//GEN-LAST:event_yaxisGTEButtonActionPerformed

    private void colorbarGTEButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_colorbarGTEButtonActionPerformed
        addEditGrannyText(zTitleTextField);
    }//GEN-LAST:event_colorbarGTEButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox cbVisibleCB;
    private javax.swing.JButton colorbarGTEButton;
    private javax.swing.JCheckBox isotropicCheckBox;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox legendEnableCheckbox;
    private javax.swing.JTextField legendTextField;
    private javax.swing.JCheckBox showXAxisLabelsCB;
    private org.autoplot.datasource.TimeRangeEditor timeRangeEditor1;
    private javax.swing.JCheckBox titleCB;
    private javax.swing.JTextField titleTextField;
    private javax.swing.JPanel xAxisPanel;
    private javax.swing.JPanel xAxisRangePanel;
    private javax.swing.JCheckBox xLog;
    private javax.swing.JTextField xTitleTextField;
    private javax.swing.JButton xaxisGTEButton;
    private javax.swing.JPanel yAxisPanel;
    private javax.swing.JPanel yAxisRangePanel;
    private javax.swing.JCheckBox yLog;
    private javax.swing.JTextField yTitleTextField;
    private javax.swing.JButton yaxisGTEButton;
    private javax.swing.JPanel zAxisPanel;
    private javax.swing.JPanel zAxisRangePanel;
    private javax.swing.JCheckBox zLog;
    private javax.swing.JTextField zTitleTextField;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private void addEditGrannyText( JTextField textField ) {        
        GrannyTextEditor gte= AutoplotUI.newGrannyTextEditorWithMacros();
        gte.setValue( textField.getText() );
        if ( JOptionPane.OK_OPTION==
                JOptionPane.showConfirmDialog( this, gte, "Granny Text Editor", JOptionPane.OK_CANCEL_OPTION ) ) {
            textField.setText(gte.getValue());
            for ( java.awt.event.ActionListener al: textField.getActionListeners() ) {
                try {
                    al.actionPerformed( null );
                } catch ( NullPointerException ex ) {
                    logger.info("getting NullPointerException where this once worked in AxisPanel");
                }
            }
        }
    }
                
    private JMenuItem createMenuItem( final JTextField componentTextField, final String insert, String doc ) {
        JMenuItem result= new JMenuItem( new AbstractAction( insert ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                String v= componentTextField.getText();
                int i= componentTextField.getCaretPosition();
                componentTextField.setText( v.substring(0,i) + insert + v.substring(i) );
            }
        });
        result.setToolTipText(doc);
        return result;
    }

    private JMenuItem createMenuItem( final JTextField componentTextField, final String insert, final String label, String doc ) {
        JMenuItem result= new JMenuItem( new AbstractAction( label ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                String v= componentTextField.getText();
                int i= componentTextField.getCaretPosition();
                componentTextField.setText( v.substring(0,i) + insert + v.substring(i) );
            }
        });
        result.setToolTipText(doc);
        return result;
    }

    private JPopupMenu initLabelMenu( JTextField tf ) {
        JPopupMenu processMenu;
        processMenu= new JPopupMenu();
        processMenu.add( createMenuItem( tf, "%{COMPONENT}", "Component property from the plot element" ) );
        processMenu.add( createMenuItem( tf, "%{CONTEXT}", "Context from the dataset, such as slice location or component" ) );
        processMenu.add( createMenuItem( tf, "%{PLOT_CONTEXT}", "plot context control" ) );
        processMenu.add( createMenuItem( tf, "%{TIMERANGE}", "Special timerange property from the plot element controller" ) );
        processMenu.add( createMenuItem( tf, "%{PROPERTIES.CONTEXT_0}", "get a property from the plotted dataset" ) );
        processMenu.add( createMenuItem( tf, "%{USER_PROPERTIES.CUSTOMPROP}", "get a property from the USER_PROPERTIES of the plotted dataset" ) );
        processMenu.add( createMenuItem( tf, "!c", "Line Break", "Insert new line escape character" ) );
        return processMenu;
    }

    private void showLabelMenu( MouseEvent ev) {
        JPopupMenu processMenu= initLabelMenu( (JTextField)ev.getSource() );
        processMenu.show(ev.getComponent(), ev.getX(), ev.getY());
    }

    private JPopupMenu initTitleMenu( JTextField tf ) {
        JPopupMenu processMenu;
        processMenu= new JPopupMenu();
        processMenu.add( createMenuItem( tf, "%{CONTEXT}", "Context from the dataset, such as slice location or component" ) );
        processMenu.add( createMenuItem( tf, "%{TIMERANGE}", "Special timerange property from the plot element controller" ) );
        processMenu.add( createMenuItem( tf, "%{PLOT_CONTEXT}", "plot context control" ) );        
        processMenu.add( createMenuItem( tf, "!c", "Line Break", "Insert new line escape character" ) );
        processMenu.add( createMenuItem( tf, "&epsilon;", "Insert named character reference for epsilon symbol") );
        processMenu.add( createMenuItem( tf, "&#0229;", "Insert unicode symbol") );        
        return processMenu;
    }

    private void showTitleMenu( MouseEvent ev) {
        JPopupMenu processMenu= initTitleMenu( (JTextField)ev.getSource() );
        processMenu.show(ev.getComponent(), ev.getX(), ev.getY());
    }

    private JPopupMenu initAxisMenu( JTextField tf ) {
        JPopupMenu processMenu;
        processMenu= new JPopupMenu();
        processMenu.add( createMenuItem( tf, "%{UNITS}", "Units of the axis" ) );
        processMenu.add( createMenuItem( tf, "%{RANGE}", "Range of the axis" ) );
        processMenu.add( createMenuItem( tf, "%{SCAN_RANGE}", "Range of the axis scan buttons" ) );
        processMenu.add( createMenuItem( tf, "!c", "Line Break", "Insert new line escape character" ) );
        return processMenu;
    }

    private void showAxisMenu( MouseEvent ev) {
        JPopupMenu processMenu= initAxisMenu( (JTextField)ev.getSource() );
        processMenu.show(ev.getComponent(), ev.getX(), ev.getY());
    }    
}
