
package org.autoplot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.das2.datum.LoggerManager;
import org.jdesktop.beansbinding.BindingGroup;
import org.autoplot.util.TickleTimer;
import org.das2.qds.QDataSet;
import org.das2.qds.examples.Schemes;
import org.autoplot.datasource.InputVerifier;
import org.das2.qds.filters.FiltersChainPanel;

/**
 * Make a special component for managing filters.  This extracts the functionality
 * from the DataPanel.
 * @author jbf
 */
public class OperationsPanel extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("autoplot.gui");
    
    JTextField operatorsTextField;
    
    /**
     * Creates new form OperationsPanel
     */
    public OperationsPanel() {
        initComponents();
        
        operatorsComboBox.setPreferenceNode("operations");
        
        filtersChainPanel.setAddSubtractButtons(false);
        
        operatorsTextField= (JTextField)operatorsComboBox.getEditor().getEditorComponent();
        filtersChainPanel.addPropertyChangeListener( FiltersChainPanel.PROP_FILTER, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String filter= filtersChainPanel.getFilter();
                setFilter(filter);
                operatorsComboBox.addToRecent( filter, false );
            }
        });
        operatorsTextField.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( filtersChainPanel.validateFilter( operatorsTextField.getText() ) ) {
                    filtersChainPanel.setFilter( operatorsTextField.getText() ); // allow filtersChainPanel to do verification
                }
            }
        });
        operatorsTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if ( filtersChainPanel.validateFilter( operatorsTextField.getText() ) ) {
                    filtersChainPanel.setFilter( operatorsTextField.getText() ); // allow filtersChainPanel to do verification
                }
            }
        });
        
        setUpOperationsListeners();
        setUpIncr();
        
    }
    
    transient MouseWheelListener sliceIndexListener=null;

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
    
    private void setUpIncr() {
        if ( sliceIndexListener==null ) {
            sliceIndexListener= new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    doIncrUpOrDown(-1 * e.getWheelRotation());
                }
            };
            operatorsTextField.addMouseWheelListener(sliceIndexListener);
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
        
//        compListener= new PropertyChangeListener() {
//            @Override
//            public void propertyChange(PropertyChangeEvent evt) {
//                componentChanged();
//            }
//        };
        
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
     * increment the field at the caret position in the slice or slices function.
     * @param amount positive or negative number of steps.
     */
    private void doIncrUpOrDown( int amount ) {
        String s= operatorsTextField.getText();
        final String olds= s;
        final int cp= operatorsTextField.getCaretPosition();
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
        final String news= s;

        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    if ( !olds.equals(news) ) {
                        if ( filtersChainPanel.validateFilter(news) ) {
                            filtersChainPanel.setFilter(news);
                            if ( dataSet!=null ) {
                                filtersChainPanel.resetInput(dataSet);
                                filtersChainPanel.setFilter(news);
                            }
                            operatorsTextField.setText(news);
                            operatorsTextField.setCaretPosition(cp);
                        } else {
                            String s= filtersChainPanel.getFilter();
                            operatorsTextField.setText(s);
                            operatorsTextField.setCaretPosition(cp);
                        }
                    }
                    componentChanged();
                    setFilter(filtersChainPanel.getFilter());
                } catch ( ArrayIndexOutOfBoundsException ex ) {
                    logger.log( Level.WARNING, ex.getMessage(), ex );
                    operatorsTextField.setText(olds);
                    operatorsTextField.setCaretPosition(cp);
                }

            }
        };
        SwingUtilities.invokeLater(run);
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
                    if ( dataSet!=null ) {
                        filtersChainPanel.resetInput(dataSet);
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
                filtersChainPanel.resetInput(ds);  
                filtersChainPanel.setFilter(filter);
            }
        };
        SwingUtilities.invokeLater(run);        
    }
    
    private void componentChanged() {
        if ( adjusting ) return;
        resetFiltersChain();
        this.operationsHistoryTimer.tickle();
    }
    
    private void setUpOperationsListeners( ) {
        // org.virbo.datasource.GuiUtil.addResizeListenerToAll(this);  // On 2015-01-16 it was doing strange things.
        
        ((JTextField)operatorsComboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener( new DocumentListener() {
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                if ( !adjusting ) {
                    componentChanged();
                } else {
                    logger.fine("Unexpected update that cannot be performed because we are adjusting.");
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
        
        operatorsComboBox.getEditor().getEditorComponent().addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                org.das2.util.LoggerManager.logGuiEvent(evt);
                setAdjusting(false);
                componentChanged();
            }
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                org.das2.util.LoggerManager.logGuiEvent(evt);
                setAdjusting(true);
            }
        });
        
        operatorsComboBox.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                setAdjusting(false);
                componentChanged();
                setAdjusting(operatorsComboBox.getEditor().getEditorComponent().hasFocus());
            }
        });
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        filtersChainPanel = new org.das2.qds.filters.FiltersChainPanel();
        operatorsComboBox = new org.autoplot.datasource.RecentComboBox();
        editComponentButton = new javax.swing.JButton();
        operationsLabel = new javax.swing.JLabel();
        dataSetLabel = new javax.swing.JLabel();

        filtersChainPanel.setName("filtersChainPanel"); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), filtersChainPanel, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        filtersChainPanel.setLayout(new javax.swing.BoxLayout(filtersChainPanel, javax.swing.BoxLayout.LINE_AXIS));

        operatorsComboBox.setName("operatorsComboBox"); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), operatorsComboBox, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        operatorsComboBox.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                operatorsComboBoxFocusLost(evt);
            }
        });
        operatorsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                operatorsComboBoxActionPerformed(evt);
            }
        });

        editComponentButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/resources/pipeMag2.png"))); // NOI18N
        editComponentButton.setToolTipText("Open filters editor");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), editComponentButton, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        editComponentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editComponentButtonActionPerformed(evt);
            }
        });

        operationsLabel.setText("Operations:");
        operationsLabel.setToolTipText("Process string that specifies component to plot, or how a data set's dimensionality should be reduced before display.");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), operationsLabel, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        dataSetLabel.setFont(dataSetLabel.getFont().deriveFont(dataSetLabel.getFont().getSize()-4f));
        dataSetLabel.setText("(dataset will go here)");
        dataSetLabel.setName("inputDataSetLabel"); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), dataSetLabel, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(operationsLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(operatorsComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(dataSetLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(editComponentButton))
            .addComponent(filtersChainPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(dataSetLabel)
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(editComponentButton)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(operationsLabel)
                        .addComponent(operatorsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filtersChainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 247, Short.MAX_VALUE))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void operatorsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_operatorsComboBoxActionPerformed
        logger.log(Level.FINE, "operatorsComboBox: {0}", operatorsComboBox.getSelectedItem());
        doEnter();
    }//GEN-LAST:event_operatorsComboBoxActionPerformed

    /**
     * action and focus lost
     */
    private void doEnter() {
        String news= operatorsTextField.getText();
        int cp= operatorsTextField.getCaretPosition();
        if ( filtersChainPanel.validateFilter(news) ) {
            filtersChainPanel.setFilter(news);
            if ( dataSet!=null ) {
                filtersChainPanel.resetInput(dataSet);
                filtersChainPanel.setFilter(news);
            }
            operatorsTextField.setText(news);
            operatorsTextField.setCaretPosition(cp);
        } else {
            String s= filtersChainPanel.getFilter();
            operatorsTextField.setText(s);
            operatorsTextField.setCaretPosition(cp);
        }        
    }
    
    private void editComponentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editComponentButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);

        JPanel panel= new JPanel(new BorderLayout());

        panel.add( new JLabel("<html><em>Add filters to apply to the data before plotting.<br> "), BorderLayout.NORTH );

        FiltersChainPanel p= new FiltersChainPanel();

        Dimension d= new Dimension(480,320);
        panel.setPreferredSize( d );
        panel.setMinimumSize( d );

        panel.add( p, BorderLayout.CENTER );
        p.setFilter(operatorsTextField.getText());
        QDataSet inputDs= this.dataSet;
        if ( inputDs!=null ) {
            p.setInput(inputDs);
            p.setFilter(operatorsTextField.getText());
        }
        int ret= AutoplotUtil.showConfirmDialog( this, panel, "Edit Filters", JOptionPane.OK_CANCEL_OPTION  );
        if ( ret==JOptionPane.OK_OPTION ) {
            String newFilter= p.getFilter();
            operatorsTextField.setText( newFilter );
            setFilter(newFilter);
            operatorsComboBox.setSelectedItem( newFilter );
            //componentChanged();
            operatorsComboBox.addToRecent( newFilter );
        }
    }//GEN-LAST:event_editComponentButtonActionPerformed

    private void operatorsComboBoxFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_operatorsComboBoxFocusLost
        doEnter();
    }//GEN-LAST:event_operatorsComboBoxFocusLost

    private String filter = "";

    public static final String PROP_FILTER = "filter";

    public String getFilter() {
        return filter;
    }

    public void setFilter(final String filter) {
        final String oldFilter = this.filter;
        this.filter = filter;
        Runnable run= new Runnable() {
            @Override
            public void run() {
                QDataSet oldDataSet= filtersChainPanel.getInput();
                String oldFilter= filtersChainPanel.getFilter();
                if ( oldFilter.equals(filter) && oldDataSet!=null && oldDataSet.equals(dataSet) ) {
                    //return;
                    if ( !filter.equals(operatorsTextField.getText()) ) {
                        try {
                            int carot= operatorsTextField.getCaretPosition();
                            operatorsTextField.setText(filter);
                            operatorsTextField.setCaretPosition(Math.min(filter.length(),carot));
                        } catch ( IllegalStateException ex ) {
                            logger.fine("looks like someone else is editing the operators text field already, returning.");
                        }
                    }
                    return;
                }
                filtersChainPanel.setFilter(filter);
                filtersChainPanel.resetInput(dataSet);
                if ( !oldFilter.equals(filter) || !filter.equals(operatorsTextField.getText()) ) {           
                    try {
                        int carot= operatorsTextField.getCaretPosition();
                        operatorsTextField.setText(filter);
                        operatorsTextField.setCaretPosition(Math.min(filter.length(),carot));
                    } catch ( IllegalStateException ex ) {
                        logger.fine("looks like someone else is editing the operators text field already, returning.");
                    }
                }
            }
        };
        if ( !oldFilter.equals(filter) ) {        
            firePropertyChange(PROP_FILTER, oldFilter, filter);
        }
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
    }
    
    private QDataSet dataSet = null;

    public static final String PROP_DATASET = "dataSet";

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet( QDataSet dataSet ) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet;
        this.dataSetLabel.setText( dataSet==null ? "None" : String.valueOf( dataSet ) );
        if ( dataSet==null ) {
            this.dataSetLabel.setToolTipText("The data set has not been set");
        } else {
            this.dataSetLabel.setToolTipText(null);
        }
        this.filtersChainPanel.setInput(dataSet);
        firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }

    private Boolean adjusting = false;

    public static final String PROP_ADJUSTING = "adjusting";

    public Boolean isAdjusting() {
        return adjusting;
    }

    public void setAdjusting(Boolean adjusting) {
        Boolean oldAdjusting = this.adjusting;
        this.adjusting = adjusting;
        firePropertyChange(PROP_ADJUSTING, oldAdjusting, adjusting);
    }

    /**
     * when testing GUI components, the GUI should be created on the 
     * event thread to mimic the behavior of Autoplot.
     * @throws Exception 
     */
    private static void mainEvt() throws Exception {
        OperationsPanel p= new OperationsPanel();
        QDataSet ds= Schemes.simpleSpectrogramTimeSeries();
        try {
            org.autoplot.jythonsupport.Util.getDataSet("http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-09");
        } catch ( Exception ex ) {
            throw ex;
        }
        p.setDataSet( ds );
        p.addPropertyChangeListener( OperationsPanel.PROP_FILTER, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println("New Value: "+evt.getNewValue());
            }
        });
        
        JPanel testPanel= new JPanel();
        testPanel.setLayout( new BorderLayout() );
        testPanel.add( p, BorderLayout.CENTER );
        JTextField tf= new JTextField("");
        testPanel.add( tf, BorderLayout.SOUTH );
        BindingGroup bg= new BindingGroup();
        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(
            org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, 
            tf, org.jdesktop.beansbinding.BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST"), 
            p, org.jdesktop.beansbinding.BeanProperty.create("filter") );
        bg.addBinding(binding);
        bg.bind();
        
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( null, testPanel, "Test Panel", JOptionPane.OK_CANCEL_OPTION ) ) {
            System.err.println( p.getFilter() );
        }

    }
    
    public static void main( String[] args ) throws Exception {
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    mainEvt();
                } catch (Exception ex) {
                    Logger.getLogger(OperationsPanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        SwingUtilities.invokeAndWait(run);
                
    }

    /**
     * useful for testing, and this may be removed.
     * @return the filterChainPanel.
     */
    public FiltersChainPanel getFiltersChainPanel() {
        return this.filtersChainPanel;
    }
    
    /**
     * disable the text field when expert is false.
     * @param expert 
     */
    public void setExpertMode( boolean expert ) {
        this.operatorsTextField.setEditable(expert);
        this.editComponentButton.setEnabled(expert);
        this.filtersChainPanel.setExpertMode(expert);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel dataSetLabel;
    private javax.swing.JButton editComponentButton;
    private org.das2.qds.filters.FiltersChainPanel filtersChainPanel;
    private javax.swing.JLabel operationsLabel;
    private org.autoplot.datasource.RecentComboBox operatorsComboBox;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
