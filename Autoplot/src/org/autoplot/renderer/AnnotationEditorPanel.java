
package org.autoplot.renderer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Canvas;
import org.autoplot.dom.Column;
import org.autoplot.dom.Row;
import org.autoplot.jythonsupport.ui.JLinkyLabel;
import org.das2.components.DatumRangeEditor;
import org.das2.components.GrannyTextEditor;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.EnumerationEditor;
import org.das2.datum.Datum;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
import org.das2.graph.BorderType;
import org.das2.graph.GraphUtil;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;

/**
 * Friendly editor for Annotation objects.
 * @author jbf
 */
public class AnnotationEditorPanel extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("autoplot.gui");
    
    ColorEditor backgroundEditor;
    ColorEditor foregroundEditor;
    ColorEditor textColorEditor;
    EnumerationEditor anchorPositionEditor;
    
    BindingGroup bindings;
    DatumRangeEditor xrangeEditor, yrangeEditor;
    AnchorType anchorType;
    Annotation ann;
            
    /**
     * Creates new form AnnotationEditorPanel
     */
    public AnnotationEditorPanel() {
        initComponents();
        anchorType= AnchorType.CANVAS;
        anchorPositionEditor= new EnumerationEditor( AnchorPosition.N );
        backgroundEditor= new ColorEditor(Color.WHITE);
        foregroundEditor= new ColorEditor(Color.BLACK);
        textColorEditor= new ColorEditor(Color.BLACK);
        borderTypeEnumerationEditor= new EnumerationEditor(BorderType.RECTANGLE);
        anchorBorderTypeEnumerationEditor= new EnumerationEditor(BorderType.RECTANGLE);
        anchorPositionPanel.add(anchorPositionEditor.getCustomEditor());
        backgroundColorPanel.add(backgroundEditor.getSmallEditor());
        foregroundColorPanel.add(foregroundEditor.getSmallEditor());
        borderTypePanel.add(borderTypeEnumerationEditor.getCustomEditor());
        anchorBorderTypePanel.add(anchorBorderTypeEnumerationEditor.getCustomEditor());
        textColorPanel.add(textColorEditor.getSmallEditor());
        xrangeEditor= new DatumRangeEditor();
        yrangeEditor= new DatumRangeEditor();
        xrangePanel.add( xrangeEditor.getCustomEditor() );
        yrangePanel.add( yrangeEditor.getCustomEditor() );
        
        JLinkyLabel ll= new JLinkyLabel( null,
            "<html>This <a href='https://github.com/autoplot/documentation/blob/master/docs/annotations.md'>web page</a> "
                        + "shows how the annotations are controlled.");
        
        linkyLabelPanel.add( ll, BorderLayout.CENTER );
        this.validate();
    }

    public AnchorType getAnchorType() {
        return anchorType;
    }

    public void setAnchorType(AnchorType anchorType) {
        AnchorType oldValue= this.anchorType;
        this.anchorType = anchorType;
        
        if ( anchorType==AnchorType.DATA ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( dataControlPanel, BorderLayout.CENTER );
            this.dataAnchorTypeButton.setSelected( true );
        } else if ( anchorType==AnchorType.PLOT ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( plotControlPanel, BorderLayout.CENTER );
            logger.warning("strange plot anchor type is not supported.");
        } else if ( anchorType==AnchorType.CANVAS ) {
            anchorToPanel.removeAll();
            anchorToPanel.add( canvasControlPanel, BorderLayout.CENTER );
            this.canvasAnchorTypeButton.setSelected( true );
        } else {
            return;
        }
        this.validate();
        this.repaint();
        firePropertyChange( "anchorType", oldValue, anchorType );
    }
    
    
    private Converter getDatumToStringConverter() {
        return new Converter() {
            Units u= null;
            @Override
            public Object convertForward(Object s) {
                if ( u==null ) {
                    u= ((Datum)s).getUnits();
                }
                return s.toString();
            }
            
            @Override
            public Object convertReverse(Object t) {
                try {
                    return u.parse((String)t);
                } catch (ParseException ex) {
                    return u.createDatum(0);
                }
            }
        };
    }
    
    private void addBinding( BindingGroup bc, Object ann, String srcprop, Object dest, String destprop ) {
        bc.addBinding( Bindings.createAutoBinding( AutoBinding.UpdateStrategy.READ_WRITE, ann, BeanProperty.create( srcprop ), dest, BeanProperty.create(destprop)));
    }
    
    private void addBinding( BindingGroup bc, Object ann, String srcprop, Object dest, String destprop, Converter c ) {
        Binding b= Bindings.createAutoBinding( AutoBinding.UpdateStrategy.READ_WRITE, ann, BeanProperty.create( srcprop ), 
            dest, BeanProperty.create(destprop) );
        b.setConverter( c );
        bc.addBinding( b );
    }
    
    /**
     * bind to this annotation
     * @param ann 
     */
    public void doBindings( final Annotation ann ) {
        if ( bindings!=null ) throw new IllegalArgumentException("already bound");

        this.ann= ann;
        
        plotIdComboBox.setModel( new DefaultComboBoxModel<>( new String[] { ann.getPlotId() } ) );
        
        ArrayList<String> rows= new ArrayList<>();
        rows.add("");
        Canvas c= null;
        if ( ann.getController()!=null ) {
            c= ann.getController().getCanvas();
            rows.add(c.getMarginRow().getId());
            for ( Row r: c.getRows() ) {
                rows.add( r.getId() );
            }
        }
        rowIdComboBox.setModel( new DefaultComboBoxModel<>( rows.toArray( new String[rows.size()] ) ) );
        rowIdComboBox.setSelectedItem( ann.getRowId() );
        
        ArrayList<String> columns= new ArrayList<>();
        columns.add("");
        if ( ann.getController()!=null ) {
            c= ann.getController().getCanvas();
            columns.add(c.getMarginColumn().getId());
            for ( Column c1: c.getColumns() ) {
                columns.add( c1.getId() );
            }
        }
        columnIdComboBox.setModel( new DefaultComboBoxModel<>( columns.toArray( new String[columns.size()] ) ) );
        columnIdComboBox.setSelectedItem( ann.getColumnId() );
                
        BindingGroup bc = new BindingGroup();

        addBinding( bc, ann, Annotation.PROP_TEXT, textField, "text_ON_ACTION_OR_FOCUS_LOST" );
        addBinding( bc, ann, Annotation.PROP_URL, urlTextField, "text_ON_ACTION_OR_FOCUS_LOST" );
        addBinding( bc, ann, Annotation.PROP_SCALE, scaleCB, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_ANCHORPOSITION, anchorPositionEditor, "value" );        
        addBinding( bc, ann, Annotation.PROP_ANCHORTYPE, this, "anchorType" );
        addBinding( bc, ann, Annotation.PROP_OVERRIDECOLORS, customColorsCheckBox, "selected" );
        addBinding( bc, ann, Annotation.PROP_BACKGROUND, backgroundEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_FOREGROUND, foregroundEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_TEXTCOLOR, textColorEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_XRANGE, xrangeEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_YRANGE, yrangeEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_PLOTID, plotIdComboBox, "selectedItem");
        addBinding( bc, ann, Annotation.PROP_ROWID, rowIdComboBox, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_COLUMNID, columnIdComboBox, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_ANCHOROFFSET, anchorOffsetTF, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_SCALE, scaleCB, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_SHOWARROW, pointAtCheckBox, "selected" );
        addBinding( bc, ann, Annotation.PROP_POINTATX, pointAtXTF, "text_ON_ACTION_OR_FOCUS_LOST", getDatumToStringConverter() );
        addBinding( bc, ann, Annotation.PROP_POINTATY, pointAtYTF, "text_ON_ACTION_OR_FOCUS_LOST", getDatumToStringConverter() );
        addBinding( bc, ann, Annotation.PROP_POINTATOFFSET, pointAtOffsetCB, "selectedItem" );
        addBinding( bc, ann, Annotation.PROP_BORDERTYPE, borderTypeEnumerationEditor, "value" );
        addBinding( bc, ann, Annotation.PROP_ANCHORBORDERTYPE, anchorBorderTypeEnumerationEditor, "value" );
        bc.bind();
        
        bindings= bc;
        
        useUrl.setSelected( ann.getUrl().trim().length()>0 );

        useUrl.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetTextUrlPanel( useUrl.isSelected() );
            }
        });
        
        resetTextUrlPanel( useUrl.isSelected() );
        
    }
    
    private void resetTextUrlPanel( boolean useUrl ) {
        textUrlPanel.removeAll();
        if ( useUrl ) {
            textUrlPanel.add( urlPanel );
        } else {
            textUrlPanel.add( this.textFieldPanel );
        }
        textUrlPanel.validate();
        textUrlPanel.repaint();
    }
    
    /**
     * remove all the bindings and references to objects.
     */
    public void releaseBindings() {
        if ( bindings!=null ) {
            bindings.unbind();
            bindings= null;
        }
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

        buttonGroup1 = new javax.swing.ButtonGroup();
        dataControlPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        xrangePanel = new javax.swing.JPanel();
        yrangePanel = new javax.swing.JPanel();
        plotControlPanel = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        plotIdComboBox = new javax.swing.JComboBox<>();
        canvasControlPanel = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        rowIdComboBox = new javax.swing.JComboBox<>();
        jLabel11 = new javax.swing.JLabel();
        columnIdComboBox = new javax.swing.JComboBox<>();
        urlPanel = new javax.swing.JPanel();
        urlTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        scaleCB = new javax.swing.JComboBox<>();
        typeButtonGroup = new javax.swing.ButtonGroup();
        borderTypeEnumerationEditor = new org.das2.components.propertyeditor.EnumerationEditor();
        textFieldPanel = new javax.swing.JPanel();
        textField = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        verticalButtonGroup = new javax.swing.ButtonGroup();
        anchorBorderTypeEnumerationEditor = new org.das2.components.propertyeditor.EnumerationEditor();
        jLabel2 = new javax.swing.JLabel();
        anchorPositionPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        canvasAnchorTypeButton = new javax.swing.JRadioButton();
        jCheckBox1 = new javax.swing.JCheckBox();
        ydataAnchor = new javax.swing.JRadioButton();
        yplotAnchor = new javax.swing.JRadioButton();
        ycanvasAnchor = new javax.swing.JRadioButton();
        dataAnchorTypeButton = new javax.swing.JRadioButton();
        customColorsCheckBox = new javax.swing.JCheckBox();
        anchorToPanel = new javax.swing.JPanel();
        customColorsPanel = new javax.swing.JPanel();
        textColorPanel = new javax.swing.JPanel();
        backgroundColorPanel = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        foregroundColorPanel = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        anchorOffsetTF = new javax.swing.JComboBox<>();
        pointAtCheckBox = new javax.swing.JCheckBox();
        annotationTextButton = new javax.swing.JRadioButton();
        useUrl = new javax.swing.JRadioButton();
        textUrlPanel = new javax.swing.JPanel();
        pointAtPanel = new javax.swing.JPanel();
        pointAtXTF = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        pointAtYTF = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        pointAtOffsetCB = new javax.swing.JComboBox<>();
        jLabel16 = new javax.swing.JLabel();
        borderTypePanel = new javax.swing.JPanel();
        linkyLabelPanel = new javax.swing.JPanel();
        plotAnchorTypeButton = new javax.swing.JRadioButton();
        jLabel18 = new javax.swing.JLabel();
        anchorBorderTypePanel = new javax.swing.JPanel();

        dataControlPanel.setAlignmentX(0.0F);
        dataControlPanel.setAlignmentY(0.0F);

        jLabel6.setText("x:");

        jLabel7.setText("Two ranges define a box in data space.");

        jLabel8.setText("y:");

        xrangePanel.setLayout(new java.awt.BorderLayout());

        yrangePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout dataControlPanelLayout = new javax.swing.GroupLayout(dataControlPanel);
        dataControlPanel.setLayout(dataControlPanelLayout);
        dataControlPanelLayout.setHorizontalGroup(
            dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataControlPanelLayout.createSequentialGroup()
                .addComponent(jLabel6)
                .addGap(3, 3, 3)
                .addComponent(xrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(yrangePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(jLabel7)
        );
        dataControlPanelLayout.setVerticalGroup(
            dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dataControlPanelLayout.createSequentialGroup()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(dataControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(yrangePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(xrangePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        dataControlPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel6, jLabel8, xrangePanel, yrangePanel});

        plotControlPanel.setAlignmentX(0.0F);
        plotControlPanel.setAlignmentY(0.0F);

        jLabel9.setText("Plot containing annotation:");

        plotIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        plotIdComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotIdComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout plotControlPanelLayout = new javax.swing.GroupLayout(plotControlPanel);
        plotControlPanel.setLayout(plotControlPanelLayout);
        plotControlPanelLayout.setHorizontalGroup(
            plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(plotControlPanelLayout.createSequentialGroup()
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(plotIdComboBox, 0, 140, Short.MAX_VALUE))
        );
        plotControlPanelLayout.setVerticalGroup(
            plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(plotControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel9)
                .addComponent(plotIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        canvasControlPanel.setAlignmentX(0.0F);
        canvasControlPanel.setAlignmentY(0.0F);

        jLabel10.setText("Row:");

        rowIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel11.setText("Column:");

        columnIdComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout canvasControlPanelLayout = new javax.swing.GroupLayout(canvasControlPanel);
        canvasControlPanel.setLayout(canvasControlPanelLayout);
        canvasControlPanelLayout.setHorizontalGroup(
            canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(canvasControlPanelLayout.createSequentialGroup()
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rowIdComboBox, 0, 84, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(columnIdComboBox, 0, 84, Short.MAX_VALUE)
                .addGap(18, 18, 18))
        );
        canvasControlPanelLayout.setVerticalGroup(
            canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(canvasControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel10)
                .addComponent(rowIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel11)
                .addComponent(columnIdComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        urlTextField.setText("jTextField1");

        jLabel1.setText("Scale:");

        scaleCB.setEditable(true);
        scaleCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1.10", "1.00", ".80", ".60", ".50", ".20", ".10", " " }));
        scaleCB.setToolTipText("Scale the image by this amount");

        javax.swing.GroupLayout urlPanelLayout = new javax.swing.GroupLayout(urlPanel);
        urlPanel.setLayout(urlPanelLayout);
        urlPanelLayout.setHorizontalGroup(
            urlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(urlPanelLayout.createSequentialGroup()
                .addComponent(urlTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 286, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scaleCB, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        urlPanelLayout.setVerticalGroup(
            urlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(urlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(urlTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel1)
                .addComponent(scaleCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        textField.setText("jTextField1");

        jButton1.setText("...");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout textFieldPanelLayout = new javax.swing.GroupLayout(textFieldPanel);
        textFieldPanel.setLayout(textFieldPanelLayout);
        textFieldPanelLayout.setHorizontalGroup(
            textFieldPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textFieldPanelLayout.createSequentialGroup()
                .addComponent(textField, javax.swing.GroupLayout.DEFAULT_SIZE, 504, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        textFieldPanelLayout.setVerticalGroup(
            textFieldPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(textFieldPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jButton1)
                .addComponent(textField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jLabel2.setText("Position:");

        anchorPositionPanel.setLayout(new java.awt.BorderLayout());

        jLabel3.setText("Anchor To:");

        buttonGroup1.add(canvasAnchorTypeButton);
        canvasAnchorTypeButton.setSelected(true);
        canvasAnchorTypeButton.setText("Canvas");
        canvasAnchorTypeButton.setToolTipText("Annotation is anchored to a row and column on the canvas, or to the canvas itsself.");
        canvasAnchorTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                canvasAnchorTypeButtonActionPerformed(evt);
            }
        });

        jCheckBox1.setText("vertical anchor:");
        jCheckBox1.setToolTipText("Anchor vertical to this instead");

        verticalButtonGroup.add(ydataAnchor);
        ydataAnchor.setText("Data");

        verticalButtonGroup.add(yplotAnchor);
        yplotAnchor.setText("Plot");

        verticalButtonGroup.add(ycanvasAnchor);
        ycanvasAnchor.setText("Canvas");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(canvasAnchorTypeButton)
                .addGap(18, 18, 18)
                .addComponent(jCheckBox1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ydataAnchor)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(yplotAnchor)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ycanvasAnchor)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(canvasAnchorTypeButton)
                    .addComponent(jCheckBox1)
                    .addComponent(ydataAnchor)
                    .addComponent(yplotAnchor)
                    .addComponent(ycanvasAnchor)))
        );

        buttonGroup1.add(dataAnchorTypeButton);
        dataAnchorTypeButton.setText("Data");
        dataAnchorTypeButton.setToolTipText("Annotation is anchored to the data itself, using xrange and yrange properties");
        dataAnchorTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataAnchorTypeButtonActionPerformed(evt);
            }
        });

        customColorsCheckBox.setText("Custom Colors");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, customColorsPanel, org.jdesktop.beansbinding.ELProperty.create("${visible}"), customColorsCheckBox, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        anchorToPanel.setAlignmentX(0.0F);
        anchorToPanel.setLayout(new java.awt.BorderLayout());

        textColorPanel.setLayout(new java.awt.BorderLayout());

        backgroundColorPanel.setLayout(new java.awt.BorderLayout());

        jLabel5.setText("Background:");

        jLabel4.setText("Text Color:");

        jLabel12.setText("Foreground:");

        foregroundColorPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout customColorsPanelLayout = new javax.swing.GroupLayout(customColorsPanel);
        customColorsPanel.setLayout(customColorsPanelLayout);
        customColorsPanelLayout.setHorizontalGroup(
            customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(backgroundColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE)
                    .addComponent(textColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(foregroundColorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        customColorsPanelLayout.setVerticalGroup(
            customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(customColorsPanelLayout.createSequentialGroup()
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel4)
                    .addComponent(textColorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(foregroundColorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(customColorsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(backgroundColorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        customColorsPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {backgroundColorPanel, foregroundColorPanel, textColorPanel});

        jLabel13.setText("Anchor Offset:");
        jLabel13.setToolTipText("<html>The offset from the anchor position, in ems or pixels. <br>The offset direction depends on the anchor position. <br>For example, if the anchor is \"N\" then offsets move towards <br>the south and east.");

        anchorOffsetTF.setEditable(true);
        anchorOffsetTF.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0em,0em", "1em,1em", "10px,10px", " " }));

        pointAtCheckBox.setText("Point At:");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, pointAtPanel, org.jdesktop.beansbinding.ELProperty.create("${visible}"), pointAtCheckBox, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        typeButtonGroup.add(annotationTextButton);
        annotationTextButton.setSelected(true);
        annotationTextButton.setText("Annotation Text:");
        annotationTextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                annotationTextButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(useUrl);
        useUrl.setText("Image URL:");
        useUrl.setToolTipText("This is no longer supported, see image in granny strings");
        useUrl.setEnabled(false);
        useUrl.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useUrlActionPerformed(evt);
            }
        });

        textUrlPanel.setLayout(new java.awt.BorderLayout());

        pointAtXTF.setText("0.0");

        jLabel15.setText("y:");

        jLabel14.setText("x:");

        pointAtYTF.setText("0.0");

        jLabel17.setText("point at offset:");
        jLabel17.setToolTipText("Amount to back off from the target");

        pointAtOffsetCB.setEditable(true);
        pointAtOffsetCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0em", "0.5em", "1em", "" }));

        javax.swing.GroupLayout pointAtPanelLayout = new javax.swing.GroupLayout(pointAtPanel);
        pointAtPanel.setLayout(pointAtPanelLayout);
        pointAtPanelLayout.setHorizontalGroup(
            pointAtPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pointAtPanelLayout.createSequentialGroup()
                .addGroup(pointAtPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pointAtPanelLayout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pointAtXTF, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pointAtYTF, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(pointAtPanelLayout.createSequentialGroup()
                        .addComponent(jLabel17)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pointAtOffsetCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pointAtPanelLayout.setVerticalGroup(
            pointAtPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pointAtPanelLayout.createSequentialGroup()
                .addGroup(pointAtPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(pointAtXTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15)
                    .addComponent(pointAtYTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pointAtPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(pointAtOffsetCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel16.setText("Border Type:");

        borderTypePanel.setLayout(new java.awt.BorderLayout());

        linkyLabelPanel.setLayout(new java.awt.BorderLayout());

        buttonGroup1.add(plotAnchorTypeButton);
        plotAnchorTypeButton.setText("Plot");
        plotAnchorTypeButton.setToolTipText("Annotation is anchored to the plot, can can refer to context and the data.  ");
        plotAnchorTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotAnchorTypeButtonActionPerformed(evt);
            }
        });

        jLabel18.setText("Anchor Border Type:");

        anchorBorderTypePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(anchorToPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dataAnchorTypeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(plotAnchorTypeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(textUrlPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(linkyLabelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(anchorPositionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                    .addGap(25, 25, 25)
                                    .addComponent(customColorsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(customColorsCheckBox))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(anchorOffsetTF, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel16)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(borderTypePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(annotationTextButton)
                                .addGap(18, 18, 18)
                                .addComponent(useUrl)))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(22, 22, 22)
                                .addComponent(pointAtPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel18)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(anchorBorderTypePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(pointAtCheckBox))
                                .addGap(0, 6, Short.MAX_VALUE))))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(annotationTextButton)
                    .addComponent(useUrl))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(textUrlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(2, 2, 2)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(dataAnchorTypeButton)
                        .addComponent(plotAnchorTypeButton))
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(anchorToPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE)
                    .addComponent(anchorPositionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jLabel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(borderTypePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(anchorBorderTypePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(anchorOffsetTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pointAtCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(customColorsCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(customColorsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pointAtPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 49, Short.MAX_VALUE)
                .addComponent(linkyLabelPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void dataAnchorTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataAnchorTypeButtonActionPerformed
        if ( dataAnchorTypeButton.isSelected() ) {
            setAnchorType(AnchorType.DATA);
        }
    }//GEN-LAST:event_dataAnchorTypeButtonActionPerformed

    private void canvasAnchorTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_canvasAnchorTypeButtonActionPerformed
        if ( canvasAnchorTypeButton.isSelected() ) {
            setAnchorType(AnchorType.CANVAS);
        }
    }//GEN-LAST:event_canvasAnchorTypeButtonActionPerformed

    private void annotationTextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_annotationTextButtonActionPerformed
        resetTextUrlPanel( useUrl.isSelected() );
    }//GEN-LAST:event_annotationTextButtonActionPerformed

    private void useUrlActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useUrlActionPerformed
        resetTextUrlPanel( useUrl.isSelected() );
    }//GEN-LAST:event_useUrlActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        GrannyTextEditor textedit= AutoplotUI.newGrannyTextEditorWithMacros();
        textedit.setValue( this.textField.getText() );
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( this, textedit, "Edit Text", JOptionPane.OK_CANCEL_OPTION ) ) {
            this.textField.setText( textedit.getValue() );
            ann.setText(textedit.getValue());
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void plotAnchorTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotAnchorTypeButtonActionPerformed
        if ( plotAnchorTypeButton.isSelected() ) {
            setAnchorType(AnchorType.PLOT);
        }
    }//GEN-LAST:event_plotAnchorTypeButtonActionPerformed

    private void plotIdComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotIdComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_plotIdComboBoxActionPerformed

    public static void main( String[] args ) {
        AnnotationEditorPanel p= new AnnotationEditorPanel();
        Annotation a= new Annotation();
        p.doBindings(a);
        AutoplotUtil.showMessageDialog( null, p, "tester", JOptionPane.OK_OPTION );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.das2.components.propertyeditor.EnumerationEditor anchorBorderTypeEnumerationEditor;
    private javax.swing.JPanel anchorBorderTypePanel;
    private javax.swing.JComboBox<String> anchorOffsetTF;
    private javax.swing.JPanel anchorPositionPanel;
    private javax.swing.JPanel anchorToPanel;
    private javax.swing.JRadioButton annotationTextButton;
    private javax.swing.JPanel backgroundColorPanel;
    private org.das2.components.propertyeditor.EnumerationEditor borderTypeEnumerationEditor;
    private javax.swing.JPanel borderTypePanel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton canvasAnchorTypeButton;
    private javax.swing.JPanel canvasControlPanel;
    private javax.swing.JComboBox<String> columnIdComboBox;
    private javax.swing.JCheckBox customColorsCheckBox;
    private javax.swing.JPanel customColorsPanel;
    private javax.swing.JRadioButton dataAnchorTypeButton;
    private javax.swing.JPanel dataControlPanel;
    private javax.swing.JPanel foregroundColorPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel linkyLabelPanel;
    private javax.swing.JRadioButton plotAnchorTypeButton;
    private javax.swing.JPanel plotControlPanel;
    private javax.swing.JComboBox<String> plotIdComboBox;
    private javax.swing.JCheckBox pointAtCheckBox;
    private javax.swing.JComboBox<String> pointAtOffsetCB;
    private javax.swing.JPanel pointAtPanel;
    private javax.swing.JTextField pointAtXTF;
    private javax.swing.JTextField pointAtYTF;
    private javax.swing.JComboBox<String> rowIdComboBox;
    private javax.swing.JComboBox<String> scaleCB;
    private javax.swing.JPanel textColorPanel;
    private javax.swing.JTextField textField;
    private javax.swing.JPanel textFieldPanel;
    private javax.swing.JPanel textUrlPanel;
    private javax.swing.ButtonGroup typeButtonGroup;
    private javax.swing.JPanel urlPanel;
    private javax.swing.JTextField urlTextField;
    private javax.swing.JRadioButton useUrl;
    private javax.swing.ButtonGroup verticalButtonGroup;
    private javax.swing.JPanel xrangePanel;
    private javax.swing.JRadioButton ycanvasAnchor;
    private javax.swing.JRadioButton ydataAnchor;
    private javax.swing.JRadioButton yplotAnchor;
    private javax.swing.JPanel yrangePanel;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
