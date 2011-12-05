/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.virbo.autoplot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import org.das2.components.DatumEditor;
import org.das2.components.propertyeditor.ColorEditor;
import org.das2.components.propertyeditor.EnumerationEditor;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.autoplot.help.AutoplotHelpSystem;
import org.das2.graph.DasCanvas;
import org.das2.graph.GraphUtil;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Canvas;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.PlotElementStyle;

/**
 *
 * @author  jbf
 */
public class PlotStylePanel extends javax.swing.JPanel {

    private final static int ICON_SIZE=16;
    private final static Color[] fores = new Color[]{Color.BLACK, Color.WHITE, Color.WHITE};
    private final static Color[] backs = new Color[]{Color.WHITE, Color.BLACK, Color.BLUE.darker()};


    ApplicationModel applicationModel;
    
    EnumerationEditor psymEditor;
    EnumerationEditor lineEditor;
    EnumerationEditor edit;
    EnumerationEditor rebin;
    ColorEditor colorEditor;
    ColorEditor fillColorEditor;
    DatumEditor referenceEditor;
    BindingGroup elementBindingContext;
    PlotElement currentElement;

    Application dom;

    interface StylePanel {
        public abstract void doElementBindings(PlotElement element);
    }
    
    /** Creates new form PlotStylePanel */
    public PlotStylePanel(final ApplicationModel applicationModel) {
        this.applicationModel = applicationModel;
        this.dom= applicationModel.getDocumentModel();
        
        this.dom.getController().addPropertyChangeListener( ApplicationController.PROP_PLOT_ELEMENT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                doElementBindings();
            }
        });
        
        initComponents();

        validate();

        Runnable run= new Runnable() {
            public void run() {
                doOptionsBindings();
                doElementBindings();

                String ff= dom.getController().getCanvas().getFont();
                fontLabel.setText(ff);
                //guiFontLabel.setText( parent.getFont().toString());

                DasCanvas c= dom.getController().getDasCanvas();
                int index = 3; // custom
                for (int i = 0; i < fores.length; i++) {
                    if (fores[i].equals(c.getForeground()) && backs[i].equals(c.getBackground())) {
                        index = i;
                    }
                }
                foreBackColorsList.setSelectedIndex(index);
            }
        };
        SwingUtilities.invokeLater(run);
        AutoplotHelpSystem.getHelpSystem().registerHelpID( plotPanel, "stylePanel");

    }

    private synchronized void doOptionsBindings( ) {
        BindingGroup bc = new BindingGroup();
        Binding b;

        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_DRAWGRID ), majorTicksCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_DRAWMINORGRID ), minorGridCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);
        Converter colorIconConverter= new Converter() {
            @Override
            public Object convertForward(Object s) {
                return GraphUtil.colorIcon( ((Color)s), ICON_SIZE, ICON_SIZE );
            }
            @Override
            public Object convertReverse(Object t) {
                return Color.RED; // shouldn't enter here.
            }
        };
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_FOREGROUND ), foregroundColorButton, BeanProperty.create("icon") );
        b.setConverter( colorIconConverter );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_BACKGROUND ), backgroundColorButton, BeanProperty.create("icon") );
        b.setConverter( colorIconConverter );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getCanvases(0), BeanProperty.create( Canvas.PROP_FONT ), fontLabel, BeanProperty.create("text") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getCanvases(0), BeanProperty.create( Canvas.PROP_FITTED ), fittedCB, BeanProperty.create("selected") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getCanvases(0), BeanProperty.create( Canvas.PROP_HEIGHT ), heightTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getCanvases(0), BeanProperty.create( Canvas.PROP_WIDTH ), widthTextField, BeanProperty.create("text_ON_ACTION_OR_FOCUS_LOST") );
        bc.addBinding(b);
        bc.bind();
    }

    private transient PropertyChangeListener renderTypeListener= new PropertyChangeListener() {
        public void propertyChange( PropertyChangeEvent ev ) {
            doElementBindings();
        }
    };

    private transient PropertyChangeListener colorListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            checkColors();
        }
    };

    private synchronized void doElementBindings() {

        if ( currentElement!=null ) {
            currentElement.getStyle().removePropertyChangeListener( PlotElementStyle.PROP_COLOR, colorListener);
            currentElement.removePropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener ); // remove it if it's there already
        }

        PlotElement element= dom.getController().getPlotElement();
        if ( element==null ) return;

        if ( stylePanel.getComponentCount()==1 ) {
            stylePanel.remove( stylePanel.getComponent(0) );
        }
        
        StylePanel editorPanel=null;
        if ( element.getRenderType()==RenderType.spectrogram || element.getRenderType()==RenderType.nnSpectrogram ) {
            editorPanel= new SpectrogramStylePanel(applicationModel);
        } else if ( element.getRenderType()==RenderType.hugeScatter ) {
            editorPanel= new HugeScatterStylePanel(applicationModel);
        } else if ( element.getRenderType()==RenderType.colorScatter ) {
            editorPanel= new ColorScatterStylePanel(applicationModel);
        } else {
            editorPanel= new SeriesStylePanel(applicationModel);
        }

        editorPanel.doElementBindings(element);

        stylePanel.add((JPanel)editorPanel,BorderLayout.CENTER);
        //TODO: verify
        element.addPropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener );
        element.getStyle().addPropertyChangeListener( PlotElementStyle.PROP_COLOR, colorListener );

        currentElement= element;
        
        repaint();
        validate(); // paint the new GUI
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "stylePanel");

    }

    /**
     * return true if the colors are unacceptably close.
     * @param cA
     * @param cB
     * @return
     */
    private static boolean closeColors( Color cA, Color cB ) {
        if ( cA.equals(cB) ) return true;
        float[] colorA = new float[] { cA.getRed(), cA.getGreen(), cA.getBlue() };
        float[] colorB = new float[] { cB.getRed(), cB.getGreen(), cB.getBlue() };
        double dist= Math.sqrt( Math.pow( colorA[0]-colorB[0], 2 )
                + Math.pow( colorA[1]-colorB[1], 2 )
                + Math.pow( colorA[2]-colorB[2], 2 ) );
        return ( dist<5 ) ;
    }

    /**
     * check to see that foreground!=background.  Check for each plot element, foreground!=background
     */
    private void checkColors() {
        Color back= dom.getOptions().getBackground();
        Color fore= dom.getOptions().getForeground();
        Color color= dom.getOptions().getColor();

        if ( closeColors( fore, back ) ) {
            if ( back.getRed()<128 ) {
                fore= Color.WHITE;
            } else {
                fore= Color.BLACK;
            }
            dom.getOptions().setForeground(fore);
        }
        if ( closeColors( color, back ) ) {
            if ( back.getRed()<128 ) {
                color= Color.WHITE;
            } else {
                color= Color.BLACK;
            }
            dom.getOptions().setColor(color);
        }
        List<PlotElement> pe= Arrays.asList( dom.getPlotElements() );
        for ( PlotElement p: pe ) {
            System.err.printf( "%s %s\n" , p.getStyle().getColor() ,dom.getCanvases(0).getController().getDasCanvas().getForeground() );
            if ( closeColors( p.getStyle().getColor(), back ) ) {
                final PlotElement pf= p;
                final Color colorf= color;
                SwingUtilities.invokeLater( new Runnable() { public void run() { pf.getStyle().setColor(colorf); } } );
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane2 = new javax.swing.JSplitPane();
        stylePanel = new javax.swing.JPanel();
        plotPanel = new javax.swing.JPanel();
        majorTicksCheckBox = new javax.swing.JCheckBox();
        jLabel12 = new javax.swing.JLabel();
        minorGridCheckBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        foreBackColorsList = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        foregroundColorButton = new javax.swing.JButton();
        backgroundColorButton = new javax.swing.JButton();
        fontLabel = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        pickFontButton = new javax.swing.JButton();
        widthLabel = new javax.swing.JLabel();
        widthTextField = new javax.swing.JFormattedTextField();
        heightTextField = new javax.swing.JFormattedTextField();
        heightLabel = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        fittedCB = new javax.swing.JCheckBox();

        setPreferredSize(new java.awt.Dimension(688, 300));

        jSplitPane2.setLastDividerLocation(300);

        stylePanel.setMinimumSize(new java.awt.Dimension(300, 300));
        stylePanel.setLayout(new java.awt.BorderLayout());
        jSplitPane2.setLeftComponent(stylePanel);

        plotPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Canvas [?]"));

        majorTicksCheckBox.setText("major ticks");
        majorTicksCheckBox.setToolTipText("Draw grid lines at major ticks ");

        jLabel12.setText("Grid:");

        minorGridCheckBox.setText("minor ticks");
        minorGridCheckBox.setToolTipText("Draw grid lines at minor ticks ");

        jLabel1.setText("Fore/Back Colors:");

        foreBackColorsList.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "black on white", "white on black", "white on blue", "custom" }));
        foreBackColorsList.setToolTipText("Set foreground and background colors");
        foreBackColorsList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                foreBackColorsListActionPerformed(evt);
            }
        });

        jLabel3.setText("Background:");

        jLabel2.setText("Foreground:");

        foregroundColorButton.setToolTipText("Pick foreground color");
        foregroundColorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                foregroundColorButtonActionPerformed(evt);
            }
        });

        backgroundColorButton.setToolTipText("Pick background color");
        backgroundColorButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backgroundColorButtonActionPerformed(evt);
            }
        });

        fontLabel.setText("jLabel5");

        jLabel4.setText("Canvas Font:");

        pickFontButton.setText("Pick");
        pickFontButton.setToolTipText("Pick canvas font");
        pickFontButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pickFontButtonActionPerformed(evt);
            }
        });

        widthLabel.setText("Width:");
        widthLabel.setEnabled(false);

        widthTextField.setText("100");
        widthTextField.setToolTipText("width of fixed size canvas in pixels");
        widthTextField.setEnabled(false);
        widthTextField.setFocusLostBehavior(javax.swing.JFormattedTextField.COMMIT);

        heightTextField.setText("100");
        heightTextField.setToolTipText("height of fixed size canvas in pixels");
        heightTextField.setEnabled(false);
        heightTextField.setFocusLostBehavior(javax.swing.JFormattedTextField.COMMIT);

        heightLabel.setText("Height:");
        heightLabel.setEnabled(false);

        jLabel7.setText("Canvas Size:");

        fittedCB.setText("Adjust to Fit into Application");
        fittedCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fittedCBActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout plotPanelLayout = new org.jdesktop.layout.GroupLayout(plotPanel);
        plotPanel.setLayout(plotPanelLayout);
        plotPanelLayout.setHorizontalGroup(
            plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(plotPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(jLabel12)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(majorTicksCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(minorGridCheckBox))
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(plotPanelLayout.createSequentialGroup()
                                .add(10, 10, 10)
                                .add(jLabel2)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(foregroundColorButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(jLabel1))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(plotPanelLayout.createSequentialGroup()
                                .add(jLabel3)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(backgroundColorButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                            .add(foreBackColorsList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 173, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(jLabel4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(fontLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 191, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(pickFontButton))
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(widthLabel)
                            .add(heightLabel))
                        .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                            .add(plotPanelLayout.createSequentialGroup()
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(heightTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 63, Short.MAX_VALUE))
                            .add(plotPanelLayout.createSequentialGroup()
                                .add(12, 12, 12)
                                .add(widthTextField))))
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(jLabel7)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(fittedCB, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 235, Short.MAX_VALUE)))
                .add(12, 12, 12))
        );

        plotPanelLayout.linkSize(new java.awt.Component[] {backgroundColorButton, foregroundColorButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        plotPanelLayout.setVerticalGroup(
            plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(plotPanelLayout.createSequentialGroup()
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(majorTicksCheckBox)
                    .add(jLabel12)
                    .add(minorGridCheckBox))
                .add(18, 18, 18)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(foreBackColorsList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(jLabel3)
                    .add(backgroundColorButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(foregroundColorButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(fontLabel)
                    .add(pickFontButton))
                .add(18, 18, 18)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel7)
                    .add(fittedCB))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(widthLabel)
                    .add(widthTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(heightLabel)
                    .add(heightTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );

        jSplitPane2.setRightComponent(plotPanel);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jSplitPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void foreBackColorsListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_foreBackColorsListActionPerformed
        int i = foreBackColorsList.getSelectedIndex();

        if (i < fores.length) {
            foregroundColorButton.setIcon( GraphUtil.colorIcon( fores[i], ICON_SIZE, ICON_SIZE ) );
            backgroundColorButton.setIcon( GraphUtil.colorIcon( backs[i], ICON_SIZE, ICON_SIZE ) );
            List<PlotElement> pe= Arrays.asList( dom.getPlotElements() );
            for ( PlotElement p: pe ) {
                if (p.getStyle().getColor().equals( dom.getCanvases(0).getController().getDasCanvas().getForeground())) {
                    p.getStyle().setColor(fores[i]);
                }
            }
            dom.getOptions().setForeground(fores[i]);
            dom.getOptions().setColor(fores[i]);
            dom.getOptions().setBackground(backs[i]);
        }
        checkColors();
}//GEN-LAST:event_foreBackColorsListActionPerformed

    private void foregroundColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_foregroundColorButtonActionPerformed
        Color c = JColorChooser.showDialog(this, "Foreground Color", foregroundColorButton.getBackground());
        if ( c!=null ) {
            foreBackColorsList.setSelectedIndex(fores.length);
            List<PlotElement> pe= Arrays.asList( dom.getPlotElements() );
            for ( PlotElement p: pe ) {
                if ( p.getStyle().getColor().equals( dom.getOptions().getForeground() ) ) {
                    p.getStyle().setColor(c);
                }
            }
            foregroundColorButton.setIcon( GraphUtil.colorIcon( c, ICON_SIZE, ICON_SIZE ) );
            dom.getCanvases(0).getController().getDasCanvas().setForeground(c);
            dom.getOptions().setForeground(c);
            dom.getOptions().setColor(c);
            checkColors();
        }
}//GEN-LAST:event_foregroundColorButtonActionPerformed

    private void backgroundColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backgroundColorButtonActionPerformed
        Color c = JColorChooser.showDialog(this, "Background Color", backgroundColorButton.getBackground());
        if ( c!=null ) {
            foreBackColorsList.setSelectedIndex(fores.length);
            backgroundColorButton.setIcon( GraphUtil.colorIcon( c, ICON_SIZE, ICON_SIZE ) );
            dom.getOptions().setBackground(c);
            checkColors();
        }
    }//GEN-LAST:event_backgroundColorButtonActionPerformed

    private void pickFontButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickFontButtonActionPerformed
        Font f= GuiSupport.pickFont( (JFrame) SwingUtilities.getWindowAncestor(this), applicationModel );
        if ( f!=null ) fontLabel.setText( DomUtil.encodeFont(f));
}//GEN-LAST:event_pickFontButtonActionPerformed

    private void fittedCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fittedCBActionPerformed
        boolean s= ! fittedCB.isSelected();
        widthTextField.setEnabled(s);
        heightTextField.setEnabled(s);
        widthLabel.setEnabled(s);
        heightLabel.setEnabled(s);
    }//GEN-LAST:event_fittedCBActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backgroundColorButton;
    private javax.swing.JCheckBox fittedCB;
    private javax.swing.JLabel fontLabel;
    private javax.swing.JComboBox foreBackColorsList;
    private javax.swing.JButton foregroundColorButton;
    private javax.swing.JLabel heightLabel;
    private javax.swing.JFormattedTextField heightTextField;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JCheckBox majorTicksCheckBox;
    private javax.swing.JCheckBox minorGridCheckBox;
    private javax.swing.JButton pickFontButton;
    private javax.swing.JPanel plotPanel;
    private javax.swing.JPanel stylePanel;
    private javax.swing.JLabel widthLabel;
    private javax.swing.JFormattedTextField widthTextField;
    // End of variables declaration//GEN-END:variables
}
