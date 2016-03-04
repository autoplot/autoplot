
/*
 * DataPanel.java
 *
 * Created on Aug 28, 2009, 9:06:25 AM
 */

package org.virbo.autoplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.BindingSupport;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.PlotElementController;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.InputVerifier;
import org.virbo.filters.FiltersChainPanel;

/**
 * GUI tab for controlling how data is handled, provides feedback for what is
 * being plotted, allows fill value to be specified, and provides the filters
 * chain of operations to apply to the data before plotting.
 * @author jbf
 */
public class DataPanel extends javax.swing.JPanel {

    Application dom;
    AutoplotUI app;
    ApplicationController applicationController;
    DataSourceFilter dsf; // current focus
    BindingGroup dataSourceFilterBindingGroup;
    
    /**
     * To suppress extraneous GUI events when binding to combo boxes,
     * this is true while we are binding.
     */
    boolean bindingTransitionalState= false; 
    
    PlotElement element;// current focus

    final JTextField operatorsTextField;
    
    private final transient PropertyChangeListener compListener; // listen to component property changes
    
    private final static Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.gui");

    public DataPanel( AutoplotUI app) {
        initComponents();
        filtersChainPanel.setName("postProcessingFiltersChainPanel");
        operationsPanel1.setName("operationsPanel");
        this.app= app;
        
        setUpOperationsListeners();
        
        operatorsTextField= ((JTextField)operatorsComboBox.getEditor().getEditorComponent());
        operatorsComboBox.setSelectedItem("");
        operatorsTextField.setText("");
        filtersChainPanel.setFilter("");
        
        //dataSetSelector= new DataSetSelector();
        //dataAddressPanel.add( dataSetSelector, BorderLayout.NORTH );
        
        this.dom = app.getDocumentModel();
        this.applicationController= this.dom.getController();
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                doPlotElementBindings();
            }
        });
        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                doDataSourceFilterBindings();
            }
        });
        

        if ( sliceIndexListener2==null ) {
            sliceIndexListener2= new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    doIncrUpOrDown(-1 * e.getWheelRotation());
                }
            };
            operatorsTextField.addMouseWheelListener(sliceIndexListener2);
        }

        operatorsTextField.getInputMap().put( KeyStroke.getKeyStroke(KeyEvent.VK_UP,0), "INCREMENT_UP" );
        operatorsTextField.getInputMap().put( KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0), "INCREMENT_DOWN" );
        ActionMap am= operatorsTextField.getActionMap();
        am.put( "INCREMENT_UP", new AbstractAction("incr_up") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                incrUpCount++;
                if ( System.currentTimeMillis()-lastIncrUp > 300 ) {
                    doIncrUpOrDown(incrUpCount);
                    incrUpCount=0;
                    lastIncrUp=  System.currentTimeMillis();
                } else {
                    tt.tickle("incr");
                }
            }
        } );
        
        am.put( "INCREMENT_DOWN", new AbstractAction("incr_down") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                incrUpCount--;
                if ( System.currentTimeMillis()-lastIncrUp > 300 ) {
                    doIncrUpOrDown(incrUpCount);
                    incrUpCount=0;
                    lastIncrUp=  System.currentTimeMillis();
                } else {
                    tt.tickle("incr");
                }
            }
        } );
        
        compListener= new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                componentChanged();
            }
        };
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel1, "dataPanel_1");
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel2, "dataPanel_2");

        operatorsTextField.setText(" ");

        operatorsTextField.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                org.das2.util.LoggerManager.logGuiEvent(evt);                
                componentTextFieldActionPerformed(evt);
            }
        });
        operatorsTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                componentTextFieldFocusGained(evt);
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                componentTextFieldFocusLost(evt);
            }
        });
        
        doBindings();

    }

    private void setUpOperationsListeners( ) {
        // org.virbo.datasource.GuiUtil.addResizeListenerToAll(this);  // On 2015-01-16 it was doing strange things.
        
        ((JTextField)operatorsComboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener( new DocumentListener() {
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                if ( !adjusting ) {
                    componentChanged();
                } else {
                    logger.info("Unexpected update that cannot be performed because we are adjusting.");
                }
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        
        operatorsComboBox.setVerifier( new InputVerifier() {
            @Override
            public boolean verify(String value) {
                return ( value.trim().length()>0 && value.length()<70 ); // long operations mess up the droplist.
            }
        });
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                operatorsComboBox.setPreferenceNode("operations");
            }
        };
        new Thread(run).start();
        
        operatorsComboBox.getEditor().getEditorComponent().addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                LoggerManager.logGuiEvent(evt);
                setAdjusting(false);
                componentChanged();
            }
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                LoggerManager.logGuiEvent(evt);
                setAdjusting(true);
            }
        });
        
        operatorsComboBox.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                if ( applicationController!=null ) {
                    applicationController.getPlotElement().setComponentAutomatically( (String)operatorsComboBox.getSelectedItem() );
                }
                setAdjusting(false);
                componentChanged();
                setAdjusting(operatorsComboBox.getEditor().getEditorComponent().hasFocus());
            }
        });
    }

    private void componentTextFieldActionPerformed(java.awt.event.ActionEvent evt) {
        applicationController.getPlotElement().setComponentAutomatically(operatorsTextField.getText() );
        setAdjusting(false);
        componentChanged();
        setAdjusting(operatorsComboBox.getEditor().getEditorComponent().hasFocus());
    }
    
    private void componentTextFieldFocusGained(java.awt.event.FocusEvent evt) {
        LoggerManager.logGuiEvent(evt);
        setAdjusting(true);
    }

    private void componentTextFieldFocusLost(java.awt.event.FocusEvent evt) {
        LoggerManager.logGuiEvent(evt);
        setAdjusting(false);
        componentChanged();
    }

    protected void setExpertMode( boolean expert ) {
        operatorsTextField.setVisible(expert);
        operationsLabel.setVisible(expert);
    }

    /**
     * increment the field at the caret position in the slice or slices function.
     * @param amount positive or negative number of steps.
     */
    private void doIncrUpOrDown( int amount ) {
            String s= operatorsTextField.getText();
            String olds= s;
            int cp= operatorsTextField.getCaretPosition();
            String match= ".*\\|slice\\d\\(\\d*";
            String match2= ".*\\|slices\\(([\\:\\'\\d]+,)*\\d*";
            if ( cp<s.length() ) {
                Matcher m= Pattern.compile(match).matcher( s.substring(0,cp));
                if ( m.matches() ) {
                    s= doAdjust( s, cp, amount );
                } else {
                    Matcher m2= Pattern.compile(match2).matcher( s.substring(0,cp) );
                    if ( m2.matches() ) {
                        s= doAdjust( s, cp, amount );
                    }
                }
            } else {
                return;
            }
            try {
                if ( !olds.equals(s) ) {
                    filtersChainPanel.setFilter(s);
                    if ( dsf!=null ) {
                        filtersChainPanel.setInput(null);
                        filtersChainPanel.setInput(dsf.getController().getFillDataSet());
                        filtersChainPanel.setFilter(s);
                    }
                }
                operatorsTextField.setText(s);
                applicationController.getPlotElement().setComponent(operatorsTextField.getText() );
                operatorsTextField.setCaretPosition(cp);
                componentChanged();
            } catch ( ArrayIndexOutOfBoundsException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
                operatorsTextField.setText(olds);
                applicationController.getPlotElement().setComponent(operatorsTextField.getText() );
                operatorsTextField.setCaretPosition(cp);
            }
    }

    private long lastIncrUp=0;
    private int incrUpCount=0; // number to send to incrUp

    TickleTimer tt= new TickleTimer( 100, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ( incrUpCount!=0 ) {
                doIncrUpOrDown(incrUpCount);
                incrUpCount= 0;
                lastIncrUp=  System.currentTimeMillis();
            }
        }
    } );
    
    TickleTimer operationsHistoryTimer= new TickleTimer( 500, new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            //operatorsComboBox.addToRecent(filtersChainPanel.getFilter());
        }
    } );

    /**
     * encourage making local copies for thread safety.
     * @return the current plotElement.
     */
    private synchronized PlotElement getElement() {
        return element;
    }
        
    private synchronized DataSourceFilter getDsf() {
        return dsf;
    }

    /**
     * bind to the data source and plot element.
     */
    protected final void doBindings() {
        logger.fine("doBindings");
        doPlotElementBindings();
        doDataSourceFilterBindings();
        componentChanged(); // force update
    }
    
    /**
     * increment or decrement the string containing an integer at the 
     * caret position.
     * @param s the string, e.g. "|slice0(20)"
     * @param cp the caret position, e.g. 10 
     * @param add the amount to add, e.g. 2
     * @return the new string, e.g. "|slice0(22)"
     */
    private static String doAdjust( String s, int cp, int add ) {
        int i0= cp;
        while ( i0>=0 && !Character.isDigit(s.charAt(i0) ) ) i0--;
        while ( i0>=0 && Character.isDigit(s.charAt(i0) ) ) i0--;
        i0++;
        int i1= cp;
        while ( i1<s.length() && Character.isDigit(s.charAt(i1) ) ) i1++;
        try {
            int ch= Integer.parseInt( s.substring(i0,i1) );
            ch= ch+add;
            if ( ch<0 ) ch=0;
            return s.substring(0,i0) + ch + s.substring(i1);
        } catch ( NumberFormatException e ) { //e.g. slices(':')
            return s; 
        }
    }

    /**
     * set up the filters chain.  This must be called from the event 
     * thread, and while the filter chain is not bound
     */
    private void resetFiltersChain() {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                String fcpf= filtersChainPanel.getFilter();
                final String newf= operatorsTextField.getText();
                if ( !fcpf.equals(newf) ) {
                    filtersChainPanel.setFilter(newf);
                    if ( dsf!=null ) {
                        filtersChainPanel.setInput(null);
                        filtersChainPanel.setInput(dsf.getController().getFillDataSet());
                        filtersChainPanel.setFilter(newf);
                    } else {
                        filtersChainPanel.setInput(null);
                    }
                }
            };
        };
        if ( EventQueue.isDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }        
    }
    
    private void resetFiltersChainDataSet( final QDataSet ds ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                String filter= filtersChainPanel.getFilter();
                filtersChainPanel.setFilter("");
                filtersChainPanel.setInput(null);
                filtersChainPanel.setInput(ds);  
                filtersChainPanel.setFilter(filter);
            }
        };
        SwingUtilities.invokeLater(run);        
    }
    
    private void componentChanged() {
        if ( adjusting ) return;
        PlotElement lelement= getElement();
        if ( lelement==null ) {
            return;
        }
        resetFiltersChain();
        this.operationsHistoryTimer.tickle();
    }

    protected boolean adjusting = false;
    /**
     * true indicates the component is in transition.
     */
    public static final String PROP_ADJUSTING = "adjusting";

    public boolean isAdjusting() {
        return adjusting;
    }

    public void setAdjusting(boolean adjusting) {
        boolean oldAdjusting = this.adjusting;
        this.adjusting = adjusting;
        firePropertyChange(PROP_ADJUSTING, oldAdjusting, adjusting);
    }

    private BindingGroup elementBindingGroup;

    transient PropertyChangeListener dsfListener= new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        DataPanel.this.operatorsTextField.setText("");
                        DataPanel.this.filtersChainPanel.setFilter("");
                        DataPanel.this.filtersChainPanel.setInput(null);
                    }
                };
                if ( SwingUtilities.isEventDispatchThread() ) {
                    run.run();
                } else {
                    SwingUtilities.invokeLater(run);
                }
            }
        };

    transient MouseWheelListener sliceIndexListener=null;
    transient MouseWheelListener sliceIndexListener2=null;
    
    /**
     * show the context after the slicing and operations for the user's reference.
     * TODO: make sure this is released so stray datasets can be garbage collected.
     */
    private final transient PropertyChangeListener contextListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updateProcessDataSetLabel();
        }
    };

    private final transient PropertyChangeListener fillDataSetListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            final QDataSet ds= (QDataSet)evt.getNewValue();
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    filtersChainPanel.setInput(ds);
                }
            };
            if ( SwingUtilities.isEventDispatchThread() ) {
                run.run();
            } else {
                SwingUtilities.invokeLater(run);
            }
            
        }
    };
    
    private void updateProcessDataSetLabel() {
        PlotElement lelement= getElement();
        DataSourceFilter dsf1= dom.getController().getDataSourceFilterFor(lelement);
        QDataSet orig= dsf1==null ? null : dsf1.getController().getFillDataSet();
        QDataSet proc= lelement.getController().getDataSet();
        if ( orig==proc || proc==null ) {
            processDataSetLabel.setText( "" );
        } else {
            String lbl= String.valueOf( proc );
            QDataSet ds= lelement.getController().getDataSet();
            String s=  DataSetUtil.contextAsString( ds ).trim();
            processDataSetLabel.setText( "<html>These operations result in the dataset<br>"+lbl + ( s.length()==0 ? "" : ( "<br>@ "+ s ) ) );
        }
    }

    private synchronized void doPlotElementBindings() {
        BindingGroup bc = new BindingGroup();
        if (elementBindingGroup != null) elementBindingGroup.unbind();
        setAdjusting( true ); // suppress events
        if ( element!=null ) {
            element.removePropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );
            element.getController().removePropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        }

        PlotElement p = applicationController.getPlotElement();
        element= p;

        operatorsTextField.setText(p.getComponent());

        Runnable run= new Runnable() {
            @Override
            public void run() {
                filtersChainPanel.setFilter(element.getComponent()); // because adjusting==true.
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
        
        element.addPropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );
        bc.addBinding(Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, element, BeanProperty.create("component"), this.operatorsTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")) );
        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, element.getController(), BeanProperty.create("sliceAutoranges"), this.sliceAutorangesCB, BeanProperty.create("selected") ) );

        elementBindingGroup = bc;
        bc.bind();
        setAdjusting( false );
        
        p.getController().addPropertyChangeListener( PlotElementController.PROP_DATASET, contextListener );
        updateProcessDataSetLabel();

    }

    private synchronized void doDataSourceFilterBindings() {

        if (dataSourceFilterBindingGroup != null) dataSourceFilterBindingGroup.unbind();

        if ( dsf!=null ) {
            dsf.getController().removePropertyChangeListener(DataSourceController.PROP_DATASOURCE,dsfListener);
            dsf.getController().removePropertyChangeListener(DataSourceController.PROP_FILLDATASET, fillDataSetListener );
        }
        
        final DataSourceFilter newDsf = applicationController.getDataSourceFilter();

        if (newDsf == null) {
            dataSourceFilterBindingGroup = null;
            dataSetLabel.setText( "(no dataset)" );
            return;
        }

        final QDataSet ds= newDsf.getController().getFillDataSet();
        dataSetLabel.setText( ds==null ? "(no dataset)" : ds.toString() );
        
        newDsf.getController().addPropertyChangeListener( DataSourceController.PROP_FILLDATASET, fillDataSetListener );
        newDsf.getController().addPropertyChangeListener( DataSourceController.PROP_DATASOURCE, dsfListener );
        
        resetFiltersChainDataSet(ds);

        bindingTransitionalState= true;
        BindingGroup bc = new BindingGroup();
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("filters"), this.operationsPanel1, BeanProperty.create("filter")) );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("uri"), this.dataSetSelector, BeanProperty.create("value")));
        Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("controller.dataSet"),this.dataSetLabel, BeanProperty.create("text"));
        b.setConverter( BindingSupport.toStringConverter );
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, newDsf, BeanProperty.create("controller.dataSet"), this.operationsPanel1, BeanProperty.create("dataSet")));
        bc.addBinding(b);

        try {
            bc.bind();
        } catch ( RuntimeException e ) {
            throw e;
        }
        dataSourceFilterBindingGroup = bc;
        bindingTransitionalState= false;
                
        dsfListener= new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                
            }
        };

        dsf= newDsf;
        newDsf.getController().addPropertyChangeListener(dsfListener);

    }

    private JMenuItem createMenuItem( final String insert, String doc ) {
        JMenuItem result= new JMenuItem( new AbstractAction( insert ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                                
                String v= operatorsTextField.getText();
                int i= operatorsTextField.getCaretPosition();
                operatorsTextField.setText( v.substring(0,i) + insert + v.substring(i) );
            }
        });
        result.setToolTipText(doc);
        return result;
    }

    private JPopupMenu processMenu;
    void initProcessMenu() {
        processMenu= new JPopupMenu();
        processMenu.add( createMenuItem( "|accum()", "running sum of the rank 1 data. (opposite of diff)." ) );
        processMenu.add( createMenuItem( "|add(1)", "add a scalar " ) );
        processMenu.add( createMenuItem( "|butterworth(2,500,550,True)", "Butterworth notch filter" ) );
        processMenu.add( createMenuItem( "|collapse0()", "average over the zeroth dimension to reduce the dimensionality." ) );
        processMenu.add( createMenuItem( "|collapse1()", "average over the first dimension to reduce the dimensionality." ) );
        processMenu.add( createMenuItem( "|cos()", "cos of the data in radians. (No units check)" ) );
        processMenu.add( createMenuItem( "|dbAboveBackgroundDim1(10)", "show data as decibels above the 10% level" ) );
        processMenu.add( createMenuItem( "|diff()", "finite differences between adjacent elements in the rank 1 data." ) );
        processMenu.add( createMenuItem( "|divide(2)", "divide by a scalar " ) );
        processMenu.add( createMenuItem( "|exp10()", "plot pow(10,ds)" ) );
        processMenu.add( createMenuItem( "|fftPower(128)", "plot power spectrum by breaking waveform data in windows of length size (experimental, not for publication)." ) );
        processMenu.add( createMenuItem( "|flatten()", "flatten a rank 2 dataset. The result is a n,3 dataset of [x,y,z]. (opposite of grid)" ) );
        processMenu.add( createMenuItem( "|grid()", "grid the rank2 buckshot but gridded data into a rank 2 table." ) );
        processMenu.add( createMenuItem( "|hanning(128)", "run a hanning window before taking fft." ) );
        processMenu.add( createMenuItem( "|histogram()", "perform an \"auto\" histogram of the data that automatically sets bins. " ) );
        processMenu.add( createMenuItem( "|logHistogram()", "perform the auto histogram in the log space." ) );
        processMenu.add( createMenuItem( "|log10()", "take the base-10 log of the data." ) );
        processMenu.add( createMenuItem( "|magnitude()", "calculate the magnitude of the vectors " ) );
        processMenu.add( createMenuItem( "|multiply(2)", "multiply by a scalar " ) );
        processMenu.add( createMenuItem( "|negate()", "flip the sign on the data." ) );
        processMenu.add( createMenuItem( "|pow(2.)", "square the data" ) );
        processMenu.add( createMenuItem( "|setUnits('nT')", "reset the units to the new units" ) );
        processMenu.add( createMenuItem( "|sin()", "sin of the data in radians. (No units check)" ) );
        processMenu.add( createMenuItem( "|slice0(0)", "slice the data on the zeroth dimension (often time) at the given index." ) );
        processMenu.add( createMenuItem( "|slice1(0)", "slice the data on the first dimension at the given index." ) );
        processMenu.add( createMenuItem( "|slices(':',2,3))", "slice the data on the first and second dimensions, leaving the zeroth alone." ) );
        processMenu.add( createMenuItem( "|sqrt()", "square root of the data" ) );
        processMenu.add( createMenuItem( "|smooth(5)", "boxcar average over the rank 1 data" ) );
        processMenu.add( createMenuItem( "|toDegrees()", "convert the data to degrees. (No units check)" ) );
        processMenu.add( createMenuItem( "|toRadians()", "convert the data to radians. (No units check) " ) );
        processMenu.add( createMenuItem( "|transpose()", "transpose the rank 2 dataset." ) );
        processMenu.add( createMenuItem( "|unbundle('Bx')", "unbundle a component " ) );
        processMenu.add( createMenuItem( "|valid()", "replace data with 1 where valid, 0 where invalid" ) );
    }
    void showProcessMenu( MouseEvent ev) {
        initProcessMenu();
        processMenu.show(ev.getComponent(), ev.getX(), ev.getY());
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jPanel2 = new javax.swing.JPanel();
        operationsLabel = new javax.swing.JLabel();
        sliceAutorangesCB = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        dataSetLabel = new javax.swing.JLabel();
        editComponentPanel = new javax.swing.JButton();
        operatorsComboBox = new org.virbo.datasource.RecentComboBox();
        processDataSetLabel = new javax.swing.JLabel();
        filtersChainPanel = new org.virbo.filters.FiltersChainPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        dataSetSelector = new org.virbo.datasource.DataSetSelector();
        operationsPanel1 = new org.virbo.autoplot.OperationsPanel();

        setName("dataPanel"); // NOI18N

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Post Processing [?]\n"));
        jPanel2.setName("dataPostProcessingPanel"); // NOI18N

        operationsLabel.setText("Operations:");
        operationsLabel.setToolTipText("Process string that specifies component to plot, or how a data set's dimensionality should be reduced before display.");

        sliceAutorangesCB.setText("Autorange after operations");
        sliceAutorangesCB.setToolTipText("Changing the slice index will re-autorange the data");
        sliceAutorangesCB.setName("sliceAutorangesCB"); // NOI18N

        jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getSize()-4f));
        jLabel1.setText("Apply additional operations to the dataset before plotting");
        jLabel1.setName("DataPostProcessingInstructionsLabel"); // NOI18N

        dataSetLabel.setFont(dataSetLabel.getFont().deriveFont(dataSetLabel.getFont().getSize()-4f));
        dataSetLabel.setText("(dataset will go here)");
        dataSetLabel.setName("inputDataSetLabel"); // NOI18N

        editComponentPanel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/resources/pipeMag2.png"))); // NOI18N
        editComponentPanel.setToolTipText("Open filters editor");
        editComponentPanel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editComponentPanelActionPerformed(evt);
            }
        });

        operatorsComboBox.setName("operatorsComboBox"); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, filtersChainPanel, org.jdesktop.beansbinding.ELProperty.create("${filter}"), operatorsComboBox, org.jdesktop.beansbinding.BeanProperty.create("selectedItem"));
        bindingGroup.addBinding(binding);

        operatorsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                operatorsComboBoxActionPerformed(evt);
            }
        });

        processDataSetLabel.setFont(processDataSetLabel.getFont().deriveFont(processDataSetLabel.getFont().getSize()-4f));
        processDataSetLabel.setText("(filtered dataset will go here)");

        filtersChainPanel.setName("filtersChainPanel"); // NOI18N
        filtersChainPanel.setLayout(new javax.swing.BoxLayout(filtersChainPanel, javax.swing.BoxLayout.LINE_AXIS));

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jPanel2Layout.createSequentialGroup()
                                .add(operationsLabel)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(operatorsComboBox, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(dataSetLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(editComponentPanel)
                        .addContainerGap())
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(sliceAutorangesCB, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(235, 235, 235))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createSequentialGroup()
                        .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(org.jdesktop.layout.GroupLayout.LEADING, processDataSetLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(filtersChainPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 562, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSetLabel)
                .add(5, 5, 5)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(editComponentPanel)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(operationsLabel)
                        .add(operatorsComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(filtersChainPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(sliceAutorangesCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(processDataSetLabel)
                .add(16, 16, 16))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Source [?]"));
        jPanel1.setName("dataSourcePanel"); // NOI18N

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getSize()-4f));
        jLabel2.setText("Apply these operations to the data after loading.  Fill and valid range can be specified with putProperty filter.");

        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataSetSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(operationsPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(operationsPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 145, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1.getAccessibleContext().setAccessibleName("Data Source and Initial Processing [?]");

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents
    
    private void editComponentPanelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editComponentPanelActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);  

        JPanel panel= new JPanel(new BorderLayout());
        
        panel.add( new JLabel("<html><em>Add filters to apply to the data before plotting.<br> "), BorderLayout.NORTH );
        
        FiltersChainPanel p= new FiltersChainPanel();
        
        Dimension d= new Dimension(480,320);
        panel.setPreferredSize( d );
        panel.setMinimumSize( d );
        
        panel.add( p, BorderLayout.CENTER );
        p.setFilter(operatorsTextField.getText());
        if ( this.dsf!=null ) {
            QDataSet inputDs= this.dsf.getController().getFillDataSet();
            p.setInput(inputDs); 
            p.setFilter(operatorsTextField.getText());
        }
        int ret= AutoplotUtil.showConfirmDialog( this, panel, "Edit Filters", JOptionPane.OK_CANCEL_OPTION  );
        if ( ret==JOptionPane.OK_OPTION ) {
            String newFilter= p.getFilter();
            operatorsTextField.setText( newFilter );
            applicationController.getPlotElement().setComponentAutomatically( newFilter );
            operatorsComboBox.setSelectedItem( newFilter );
            //recentComboBox.actionPerformed(evt); // kludge to get it to log the new filter
            componentChanged();
            operatorsComboBox.addToRecent( newFilter );
        }
    }//GEN-LAST:event_editComponentPanelActionPerformed

    private void operatorsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_operatorsComboBoxActionPerformed
        logger.log(Level.FINE, "operatorsComboBox: {0}", operatorsComboBox.getSelectedItem());
    }//GEN-LAST:event_operatorsComboBoxActionPerformed

    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        String uri= dataSetSelector.getValue();
        int modifiers= evt.getModifiers();
        app.doPlotGoButton( uri, modifiers );
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    /**
     * for testing, provide access.
     * @return the FiltersChainPanel
     */
    public FiltersChainPanel getFiltersChainPanel() {
        return this.filtersChainPanel;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel dataSetLabel;
    private org.virbo.datasource.DataSetSelector dataSetSelector;
    private javax.swing.JButton editComponentPanel;
    private org.virbo.filters.FiltersChainPanel filtersChainPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel operationsLabel;
    private org.virbo.autoplot.OperationsPanel operationsPanel1;
    private org.virbo.datasource.RecentComboBox operatorsComboBox;
    private javax.swing.JLabel processDataSetLabel;
    private javax.swing.JCheckBox sliceAutorangesCB;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

}
