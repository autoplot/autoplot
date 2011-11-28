/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * DataPanel.java
 *
 * Created on Aug 28, 2009, 9:06:25 AM
 */

package org.virbo.autoplot;

import java.awt.event.ActionEvent;
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
import javax.swing.DefaultComboBoxModel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.autoplot.help.AutoplotHelpSystem;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.DataSourceController;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.util.TickleTimer;
import org.virbo.datasource.DataSetSelector;

/**
 * PlotElement for controlling how data is handled.
 * @author jbf
 */
public class DataPanel extends javax.swing.JPanel {

    Application dom;
    ApplicationController applicationController;
    DataSourceFilter dsf; // current focus
    BindingGroup dataSourceFilterBindingGroup;
    PlotElement element;// current focus
    DataSetSelector dataSetSelector;

    PropertyChangeListener compListener; // listen to component property changes
    
    private final static Logger logger = Logger.getLogger("virbo.autoplot");

    public DataPanel( Application dom ) {
        initComponents();

        //dataSetSelector= new DataSetSelector();
        //dataAddressPanel.add( dataSetSelector, BorderLayout.NORTH );
        
        this.dom = dom;
        this.applicationController= this.dom.getController();
        this.applicationController.addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doElementBindings();
            }
        });
        this.applicationController.addPropertyChangeListener(ApplicationController.PROP_DATASOURCEFILTER, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doDataSourceFilterBindings();
            }
        });
        
        if ( sliceIndexListener==null ) {
            sliceIndexListener= new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int pos = (Integer) sliceIndexSpinner.getValue();
                    pos -= e.getWheelRotation();
                    if (pos < 0) pos = 0;
                    //int maxpos = dsf.getController().getMaxSliceIndex(dsf.getSliceDimension());
                    int maxpos = (Integer)((SpinnerNumberModel)(sliceIndexSpinner.getModel())).getMaximum();
                    if ( maxpos==0 ) return;
                    if (pos >= maxpos) pos = maxpos;
                    sliceIndexSpinner.setValue(pos);
                }
            };
            sliceIndexSpinner.addMouseWheelListener(sliceIndexListener);
        }

        if ( sliceIndexListener2==null ) {
            sliceIndexListener2= new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    doIncrUp(-1 * e.getWheelRotation());
                }
            };
            componentTextField.addMouseWheelListener(sliceIndexListener2);
        }

        componentTextField.getInputMap().put( KeyStroke.getKeyStroke(KeyEvent.VK_UP,0), "INCREMENT_UP" );
        componentTextField.getInputMap().put( KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0), "INCREMENT_DOWN" );
        ActionMap am= componentTextField.getActionMap();
        am.put( "INCREMENT_UP", new AbstractAction("incr_up") {
            public void actionPerformed(ActionEvent e) {
                incrUpCount++;
                if ( System.currentTimeMillis()-lastIncrUp > 300 ) {
                    doIncrUp(incrUpCount);
                    incrUpCount=0;
                    lastIncrUp=  System.currentTimeMillis();
                } else {
                    tt.tickle("incr");
                }
            }
        } );
        
        am.put( "INCREMENT_DOWN", new AbstractAction("incr_down") {
            public void actionPerformed(ActionEvent e) {
                incrUpCount--;
                if ( System.currentTimeMillis()-lastIncrUp > 300 ) {
                    doIncrUp(incrUpCount);
                    incrUpCount=0;
                    lastIncrUp=  System.currentTimeMillis();
                } else {
                    tt.tickle("incr");
                }
            }
        } );

        compListener= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                componentChanged();
            }
        };
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel1, "dataPanel_1");
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this.jPanel2, "dataPanel_2");
    }

    protected void setExpertMode( boolean expert ) {
        componentTextField.setVisible(expert);
        operationsLabel.setVisible(expert);
    }

    private void doIncrUp( int amount ) {
            String s= componentTextField.getText();
            int cp= componentTextField.getCaretPosition();
            String match= ".*\\|slice\\d\\(\\d*";
            String match2= ".*\\|slices\\((\\d+,)*\\d*";
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
            componentTextField.setText(s);
            applicationController.getPlotElement().setComponent( componentTextField.getText() );
            componentTextField.setCaretPosition(cp);
    }

    private long lastIncrUp=0;
    private int incrUpCount=0; // number to send to incrUp

    TickleTimer tt= new TickleTimer( 100, new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( incrUpCount!=0 ) {
                doIncrUp(incrUpCount);
                incrUpCount= 0;
                lastIncrUp=  System.currentTimeMillis();
            }
        }
    } );


    public void doBindings() {
        doElementBindings();
        doDataSourceFilterBindings();
        componentChanged(); // force update
    }
    
    private String doAdjust( String s, int cp, int add ) {
        int i0= cp;
        while ( i0>=0 && !Character.isDigit(s.charAt(i0) ) ) i0--;
        while ( i0>=0 && Character.isDigit(s.charAt(i0) ) ) i0--;
        i0++;
        int i1= cp;
        while ( i1<s.length() && Character.isDigit(s.charAt(i1) ) ) i1++;
        int ch= Integer.parseInt( s.substring(i0,i1) );
        ch= ch+add;
        if ( ch<0 ) ch=0;
        return s.substring(0,i0) + ch + s.substring(i1);
    }

    private void updateComponent() {
        if ( adjusting ) return;
        String sprocess="";
        if ( doSliceCheckBox.isSelected() ) {
            sprocess+="|slice"+sliceTypeComboBox.getSelectedIndex() + "(" + sliceIndexSpinner.getValue() + ")";
        }
        if ( transposeCheckBox.isSelected() ) {
            sprocess+="|transpose";
        }
        if ( element!=null ) {
            element.setComponentAutomatically(sprocess);
        } else {
            new Exception("who entered this code").printStackTrace();
        }
    }

    private void componentChanged() {
        if ( adjusting ) return;
        if ( element==null ) {
            return;
        }
        String scomp= element.getComponent();
        Pattern slicePattern= Pattern.compile("\\|slice(\\d+)\\((\\d+)\\)(\\|transpose)?");
        Matcher m= slicePattern.matcher(scomp);
        if ( m.matches() ) {
            setAdjusting(true);
            doSliceCheckBox.setSelected(true);
            sliceTypeComboBox.setSelectedIndex(Integer.parseInt(m.group(1)));

            if ( dsf!=null ) {
                int max = dsf.getController().getMaxSliceIndex(sliceTypeComboBox.getSelectedIndex());
                if (max > 0) max--;
                sliceIndexSpinner.setModel(new SpinnerNumberModel(0, 0, max, 1));
                sliceIndexSpinner.setValue(Integer.parseInt(m.group(2)));
            }
            transposeCheckBox.setSelected( m.group(3)!=null );
            setAdjusting(false);
        }
        doSliceCheckBox.setEnabled(m.matches());
        sliceIndexSpinner.setEnabled(m.matches());
        sliceIndexLabel.setEnabled(m.matches());
        sliceTypeComboBox.setEnabled(m.matches());
        transposeCheckBox.setEnabled(m.matches());

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
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DataSourceController.PROP_DEPNAMES)) {
                    updateSliceTypeComboBox( applicationController.getDataSourceFilter(), false );
                } 
            }
        };

    transient MouseWheelListener sliceIndexListener=null;
    transient MouseWheelListener sliceIndexListener2=null;

    private void updateSliceTypeComboBox( DataSourceFilter dsf, boolean immediately ) {

        final String[] depNames1 = new String[4];

        final String[] depNames = (String[]) dsf.getController().getDepnames().toArray( new String[ dsf.getController().getDepnames().size()] ); //TODO: what if panelId changes...
        for (int i = 0; i < depNames.length; i++) {
            depNames1[i] = depNames[i] + " (" + dsf.getController().getMaxSliceIndex(i) + " bins)";
        }
        if ( immediately ) {
            sliceTypeComboBox.setModel(new DefaultComboBoxModel(depNames1));
            if ( element!=null && !componentTextField.getText().equals( element.getComponent() ) ) {
                componentTextField.setText( element.getComponent() );
                componentChanged();
            }
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    sliceTypeComboBox.setModel(new DefaultComboBoxModel(depNames1));
                    if ( element!=null && !componentTextField.getText().equals( element.getComponent() ) ) {
                        componentTextField.setText( element.getComponent() );
                        componentChanged();
                    }
                }
            });
        }
    }

    private synchronized void doElementBindings() {
        BindingGroup bc = new BindingGroup();
        if (elementBindingGroup != null) elementBindingGroup.unbind();
        if ( element!=null ) element.removePropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );

        PlotElement p = applicationController.getPlotElement();
        element= p;

        componentTextField.setText(p.getComponent());

        componentChanged();
        
        element.addPropertyChangeListener( PlotElement.PROP_COMPONENT, compListener );
        bc.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, element, BeanProperty.create("component"), this.componentTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST")) );

        elementBindingGroup = bc;
        bc.bind();

    }

    private synchronized void doDataSourceFilterBindings() {

        if (dataSourceFilterBindingGroup != null) dataSourceFilterBindingGroup.unbind();

        if ( dsf!=null ) {
            dsf.getController().removePropertyChangeListener(dsfListener);
        }
        
        final DataSourceFilter newDsf = applicationController.getDataSourceFilter();

        if (newDsf == null) {
            dataSourceFilterBindingGroup = null;
            return;
        }

        updateSliceTypeComboBox(newDsf,true);

        BindingGroup bc = new BindingGroup();
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("fill"), this.fillValueComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("validRange"), this.validRangeComboBox, BeanProperty.create("selectedItem")));
        bc.addBinding(Bindings.createAutoBinding( UpdateStrategy.READ_WRITE,newDsf, BeanProperty.create("uri"), this.uriTextField, BeanProperty.create("text")));
        try {
            bc.bind();
        } catch ( RuntimeException e ) {
            throw e;
        }
        dataSourceFilterBindingGroup = bc;

        if ( newDsf!=null ) {
            int sliceDimension= sliceTypeComboBox.getSelectedIndex();
            int max= newDsf.getController().getMaxSliceIndex( sliceDimension );
            if ( max>0 ) sliceIndexSpinner.setModel( new SpinnerNumberModel( 0, 0, max-1, 1) );
        }

        dsfListener= new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(DataSourceController.PROP_DEPNAMES)) {
                    updateSliceTypeComboBox( newDsf, false );
                }
            }
        };

        dsf= newDsf;
        newDsf.getController().addPropertyChangeListener(dsfListener);

    }

    private JMenuItem createMenuItem( final String insert, String doc ) {
        JMenuItem result= new JMenuItem( new AbstractAction( insert ) {
            public void actionPerformed(ActionEvent e) {
                String v= componentTextField.getText();
                int i= componentTextField.getCaretPosition();
                componentTextField.setText( v.substring(0,i) + insert + v.substring(i) );
            }
        });
        result.setToolTipText(doc);
        return result;
    }

    private JPopupMenu processMenu;
    void initProcessMenu() {
        processMenu= new JPopupMenu();
        processMenu.add( createMenuItem( "|histogram()", "perform an \"auto\" histogram of the data that automatically sets bins. " ) );
        processMenu.add( createMenuItem( "|logHistogram()", "perform the auto histogram in the log space." ) );
        processMenu.add( createMenuItem( "|log10()", "take the base-10 log of the data." ) );
        processMenu.add( createMenuItem( "|exp10()", "plot pow(10,ds)" ) );
        processMenu.add( createMenuItem( "|slice0(0)", "slice the data on the zeroth dimension (often time) at the given index." ) );
        processMenu.add( createMenuItem( "|slice1(0)", "slice the data on the first dimension at the given index." ) );
        processMenu.add( createMenuItem( "|slices(':',2,3))", "slice the data on the first and second dimensions, leaving the zeroth alone." ) );
        processMenu.add( createMenuItem( "|collapse0()", "average over the zeroth dimension to reduce the dimensionality." ) );
        processMenu.add( createMenuItem( "|collapse1()", "average over the first dimension to reduce the dimensionality." ) );
        processMenu.add( createMenuItem( "|transpose()", "transpose the rank 2 dataset." ) );
        processMenu.add( createMenuItem( "|fftPower(128)", "plot power spectrum by breaking waveform data in windows of length size (experimental, not for publication)." ) );
        processMenu.add( createMenuItem( "|smooth(5)", "boxcar average over the rank 1 data" ) );
        processMenu.add( createMenuItem( "|diff()", "finite differences between adjacent elements in the rank 1 data." ) );
        processMenu.add( createMenuItem( "|accum()", "running sum of the rank 1 data. (opposite of diff)." ) );
        processMenu.add( createMenuItem( "|grid()", "grid the rank2 buckshot but gridded data into a rank 2 table." ) );
        processMenu.add( createMenuItem( "|flatten()", "flatten a rank 2 dataset. The result is a n,3 dataset of [x,y,z]. (opposite of grid)" ) );
        processMenu.add( createMenuItem( "|negate()", "flip the sign on the data." ) );
        processMenu.add( createMenuItem( "|cos()", "cos of the data in radians. (No units check)" ) );
        processMenu.add( createMenuItem( "|sin()", "sin of the data in radians. (No units check)" ) );
        processMenu.add( createMenuItem( "|toDegrees()", "convert the data to degrees. (No units check)" ) );
        processMenu.add( createMenuItem( "|toRadians()", "convert the data to radians. (No units check) " ) );
        processMenu.add( createMenuItem( "|magnitude()", "calculate the magnitude of the vectors " ) );
        processMenu.add( createMenuItem( "|unbundle('Bx')", "unbundle a component " ) );
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

        jPanel2 = new javax.swing.JPanel();
        sliceTypeComboBox = new javax.swing.JComboBox();
        sliceIndexSpinner = new javax.swing.JSpinner();
        sliceIndexLabel = new javax.swing.JLabel();
        transposeCheckBox = new javax.swing.JCheckBox();
        operationsLabel = new javax.swing.JLabel();
        componentTextField = new javax.swing.JTextField();
        doSliceCheckBox = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        validRangeLabel = new javax.swing.JLabel();
        validRangeComboBox = new javax.swing.JComboBox();
        fillValueLabel = new javax.swing.JLabel();
        fillValueComboBox = new javax.swing.JComboBox();
        jPanel3 = new javax.swing.JPanel();
        uriTextField = new javax.swing.JTextField();

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Post Processing [?]\n"));
        jPanel2.setToolTipText("When the operations field starts with the pipe (|) character, it is a list of filters that are to be applied to the data.  Otherwise it is the component of the dataset to plot, such as \"Bx\".  GUI controls for slicing are provided as well, and these simply control the string.");

        sliceTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "first", "second", "last" }));
        sliceTypeComboBox.setSelectedIndex(2);
        sliceTypeComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sliceTypeComboBoxItemStateChanged(evt);
            }
        });
        sliceTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sliceTypeComboBoxActionPerformed(evt);
            }
        });

        sliceIndexSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliceIndexSpinnerStateChanged(evt);
            }
        });

        sliceIndexLabel.setText("Slice Index:");

        transposeCheckBox.setText("Transpose");
        transposeCheckBox.setToolTipText("Transpose the rank 2 dataset after slicing.\n"); // NOI18N
        transposeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transposeCheckBoxActionPerformed(evt);
            }
        });

        operationsLabel.setText("Operations:");
        operationsLabel.setToolTipText("Process string that specifies component to plot, or how a data set's dimensionality should be reduced before display.");

        componentTextField.setText(" ");
        componentTextField.setToolTipText("Process string that specifies component to plot, or how a data set's dimensionality should be reduced before display.");
        componentTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                componentTextFieldMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                componentTextFieldMouseReleased(evt);
            }
        });
        componentTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                componentTextFieldActionPerformed(evt);
            }
        });
        componentTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                componentTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                componentTextFieldFocusLost(evt);
            }
        });

        doSliceCheckBox.setText("Slice Dimension");
        doSliceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doSliceCheckBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(operationsLabel)
                        .add(18, 18, 18)
                        .add(componentTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(doSliceCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(sliceTypeComboBox, 0, 260, Short.MAX_VALUE)
                        .add(18, 18, 18))
                    .add(jPanel2Layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(sliceIndexLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(transposeCheckBox))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(operationsLabel)
                    .add(componentTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(doSliceCheckBox)
                    .add(sliceTypeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(sliceIndexLabel)
                    .add(sliceIndexSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(transposeCheckBox)
                .addContainerGap(86, Short.MAX_VALUE))
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Source [?]"));
        jPanel1.setToolTipText("Additional processes applied to the data after loading.  For example, data source typically identifies fill, but in case this is done improperly, the value can be set here.");

        validRangeLabel.setText("Valid Range:");
        validRangeLabel.setToolTipText("Measurements within this range are considered valid.  This field may be changed to exclude outliers or data that has not automatically been detected as fill.\n");

        validRangeComboBox.setEditable(true);
        validRangeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "", "-1e30 to 1e30", "-1 to 101", "0 to 1e38" }));
        validRangeComboBox.setToolTipText("Measurements within this range are considered valid.  This field may be changed to exclude outliers or data that has not automatically been detected as fill. ");
        validRangeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                validRangeComboBoxActionPerformed(evt);
            }
        });

        fillValueLabel.setText("Fill Value:");
        fillValueLabel.setToolTipText("This value is used to identify invalid data that should not be plotted.");

        fillValueComboBox.setEditable(true);
        fillValueComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "", "-1e31", "0.0", "-1" }));
        fillValueComboBox.setToolTipText("This value is used to identify invalid data that should not be plotted.");
        fillValueComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fillValueComboBoxActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(validRangeLabel)
                    .add(fillValueLabel))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(validRangeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(177, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(validRangeLabel)
                    .add(validRangeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(fillValueLabel)
                    .add(fillValueComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(23, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Address (URI)"));
        jPanel3.setToolTipText("The Data Address, or URI, identifies the data that is loaded.  Plug-in data sources, resolved from this address, are used to load in the data for the given URI.  (This is not editable, but will be soon.)");

        uriTextField.setEditable(false);
        uriTextField.setFont(uriTextField.getFont().deriveFont(uriTextField.getFont().getSize()-2f));
        uriTextField.setText("This will be the current focus URI");
        uriTextField.setToolTipText("After the data is loaded, the plot element can apply additional operations before displaying the data.  ");

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .add(uriTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(uriTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void sliceTypeComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sliceTypeComboBoxItemStateChanged
        updateComponent();
}//GEN-LAST:event_sliceTypeComboBoxItemStateChanged

    private synchronized void sliceTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sliceTypeComboBoxActionPerformed
        if ( adjusting ) return; // TODO: probably get rid of this entirely.
        logger.log( Level.FINE, "set slice dimension {0}", sliceTypeComboBox.getSelectedIndex() );
        //DataSourceFilter dsf = applicationController.getDataSourceFilter();
        //dsf.setSliceDimension(sliceTypeComboBox.getSelectedIndex());
        int max = dsf.getController().getMaxSliceIndex(sliceTypeComboBox.getSelectedIndex());
        if (max > 0) max--; // make inclusive, was exclusive.
        this.sliceIndexSpinner.setModel(new SpinnerNumberModel(0, 0, max, 1));

        updateComponent();
}//GEN-LAST:event_sliceTypeComboBoxActionPerformed

    private void sliceIndexSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sliceIndexSpinnerStateChanged
        updateComponent();
}//GEN-LAST:event_sliceIndexSpinnerStateChanged

    private void transposeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transposeCheckBoxActionPerformed
        updateComponent();
}//GEN-LAST:event_transposeCheckBoxActionPerformed

    private void componentTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_componentTextFieldActionPerformed
        applicationController.getPlotElement().setComponentAutomatically( componentTextField.getText() );
        setAdjusting(false);
        componentChanged();
        setAdjusting(true);
}//GEN-LAST:event_componentTextFieldActionPerformed

    private void doSliceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doSliceCheckBoxActionPerformed
        updateComponent();
}//GEN-LAST:event_doSliceCheckBoxActionPerformed

    private void validRangeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_validRangeComboBoxActionPerformed
        String s = (String) validRangeComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        applicationController.getDataSourceFilter().setValidRange(s);
}//GEN-LAST:event_validRangeComboBoxActionPerformed

    private void fillValueComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fillValueComboBoxActionPerformed
        String s = (String) fillValueComboBox.getSelectedItem();
        if (s.equals("(none)")) s = "";
        applicationController.getDataSourceFilter().setFill(s);
}//GEN-LAST:event_fillValueComboBoxActionPerformed

    private void componentTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_componentTextFieldFocusGained
        setAdjusting(true);
    }//GEN-LAST:event_componentTextFieldFocusGained

    private void componentTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_componentTextFieldFocusLost
        setAdjusting(false);
        componentChanged();
    }//GEN-LAST:event_componentTextFieldFocusLost

    private void componentTextFieldMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_componentTextFieldMousePressed
        if ( evt.isPopupTrigger() ) {
            showProcessMenu(evt);
        }
    }//GEN-LAST:event_componentTextFieldMousePressed

    private void componentTextFieldMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_componentTextFieldMouseReleased
        if ( evt.isPopupTrigger() ) {
            showProcessMenu(evt);
        }
    }//GEN-LAST:event_componentTextFieldMouseReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField componentTextField;
    private javax.swing.JCheckBox doSliceCheckBox;
    private javax.swing.JComboBox fillValueComboBox;
    private javax.swing.JLabel fillValueLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JLabel operationsLabel;
    private javax.swing.JLabel sliceIndexLabel;
    private javax.swing.JSpinner sliceIndexSpinner;
    private javax.swing.JComboBox sliceTypeComboBox;
    private javax.swing.JCheckBox transposeCheckBox;
    private javax.swing.JTextField uriTextField;
    private javax.swing.JComboBox validRangeComboBox;
    private javax.swing.JLabel validRangeLabel;
    // End of variables declaration//GEN-END:variables

}
