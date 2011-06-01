/*
 * PlotStylePanel.java
 *
 * Created on July 27, 2007, 9:41 AM
 */
package org.virbo.autoplot;

import ZoeloeSoft.projects.JFontChooser.JFontChooser;
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
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Canvas;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.PlotElement;

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
        run.run();
        //RequestProcessor.invokeLater(run);
        AutoplotHelpSystem.getHelpSystem().registerHelpID( plotPanel, "stylePanel");

    }

    private synchronized void doOptionsBindings( ) {
        BindingGroup bc = new BindingGroup();
        Binding b;

        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_DRAWGRID ), majorTicksCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getOptions(), BeanProperty.create( Options.PROP_DRAWMINORGRID ), minorGridCheckBox, BeanProperty.create("selected") );
        bc.addBinding(b);
        b = Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, dom.getCanvases(0), BeanProperty.create( Canvas.PROP_FITTED ), resizeRadioButton, BeanProperty.create("selected") );
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

    private synchronized void doElementBindings() {
        //TODO: why null?
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

        element.removePropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener ); // remove it if it's there already
        element.addPropertyChangeListener( PlotElement.PROP_RENDERTYPE, renderTypeListener );

        repaint();
        validate(); // paint the new GUI
        
        AutoplotHelpSystem.getHelpSystem().registerHelpID(this, "stylePanel");

    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
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
        resizeRadioButton = new javax.swing.JRadioButton();
        fixedRadioButton = new javax.swing.JRadioButton();
        widthLabel = new javax.swing.JLabel();
        widthTextField = new javax.swing.JFormattedTextField();
        heightTextField = new javax.swing.JFormattedTextField();
        heightLabel = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(688, 300));

        jSplitPane2.setLastDividerLocation(300);

        stylePanel.setMinimumSize(new java.awt.Dimension(300, 300));
        stylePanel.setLayout(new java.awt.BorderLayout());
        jSplitPane2.setLeftComponent(stylePanel);

        plotPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Canvas"));

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

        buttonGroup1.add(resizeRadioButton);
        resizeRadioButton.setSelected(true);
        resizeRadioButton.setText("Resize to Fit");
        resizeRadioButton.setToolTipText("Allow the canvas to resize with the GUI");
        resizeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resizeRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(fixedRadioButton);
        fixedRadioButton.setText("Fixed Size");
        fixedRadioButton.setToolTipText("Set the canvas to a fixed size");
        fixedRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedRadioButtonActionPerformed(evt);
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

        org.jdesktop.layout.GroupLayout plotPanelLayout = new org.jdesktop.layout.GroupLayout(plotPanel);
        plotPanel.setLayout(plotPanelLayout);
        plotPanelLayout.setHorizontalGroup(
            plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(plotPanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(plotPanelLayout.createSequentialGroup()
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
                                    .add(foreBackColorsList, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 173, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                        .add(55, 55, 55))
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(jLabel4)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(fontLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 225, Short.MAX_VALUE)
                        .add(6, 6, 6)
                        .add(pickFontButton)
                        .addContainerGap())
                    .add(plotPanelLayout.createSequentialGroup()
                        .add(plotPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
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
                                .add(resizeRadioButton)
                                .add(18, 18, 18)
                                .add(fixedRadioButton)))
                        .addContainerGap(68, Short.MAX_VALUE))))
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
                    .add(resizeRadioButton)
                    .add(fixedRadioButton)
                    .add(jLabel7))
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
            .add(jSplitPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 699, Short.MAX_VALUE)
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
}//GEN-LAST:event_foreBackColorsListActionPerformed

    private void foregroundColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_foregroundColorButtonActionPerformed
        Color c = JColorChooser.showDialog(this, "foreground color", foregroundColorButton.getBackground());
        foreBackColorsList.setSelectedIndex(fores.length);
        List<PlotElement> pe= Arrays.asList( dom.getPlotElements() );
        for ( PlotElement p: pe ) {
            if ( p.getStyle().getColor().equals( dom.getCanvases(0).getController().getDasCanvas().getForeground())) {
                p.getStyle().setColor(c);
            }
        }
        foregroundColorButton.setIcon( GraphUtil.colorIcon( c, ICON_SIZE, ICON_SIZE ) );
        dom.getOptions().setForeground(c);
        dom.getOptions().setForeground(c);
        dom.getOptions().setColor(c);
}//GEN-LAST:event_foregroundColorButtonActionPerformed

    private void backgroundColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backgroundColorButtonActionPerformed
        Color c = JColorChooser.showDialog(this, "background color", backgroundColorButton.getBackground());
        foreBackColorsList.setSelectedIndex(fores.length);
        backgroundColorButton.setIcon( GraphUtil.colorIcon( c, ICON_SIZE, ICON_SIZE ) );
        dom.getOptions().setBackground(c);
    }//GEN-LAST:event_backgroundColorButtonActionPerformed

    private void pickFontButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickFontButtonActionPerformed
        JFontChooser chooser = new JFontChooser((JFrame) SwingUtilities.getWindowAncestor(this) );
        String sci= "2 \u00d7 10E7";
        chooser.setExampleText("Electron Differential Energy Flux\n12:00\n2001-01-10\n"+sci+"\n");
        chooser.setFont( Font.decode( dom.getOptions().getCanvasFont() ) );
        if (chooser.showDialog() == JFontChooser.OK_OPTION) {
            dom.getController().getDasCanvas().setBaseFont(chooser.getFont());
            Font f = dom.getController().getDasCanvas().getFont();
            fontLabel.setText( DomUtil.encodeFont(f));
            dom.getOptions().setCanvasFont( DomUtil.encodeFont(f) );
        }
}//GEN-LAST:event_pickFontButtonActionPerformed

    private void fixedRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedRadioButtonActionPerformed
        boolean s= fixedRadioButton.isSelected();
        widthTextField.setEnabled(s);
        heightTextField.setEnabled(s);
        widthLabel.setEnabled(s);
        heightLabel.setEnabled(s);
    }//GEN-LAST:event_fixedRadioButtonActionPerformed

    private void resizeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resizeRadioButtonActionPerformed
        fixedRadioButtonActionPerformed(evt);
    }//GEN-LAST:event_resizeRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backgroundColorButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton fixedRadioButton;
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
    private javax.swing.JRadioButton resizeRadioButton;
    private javax.swing.JPanel stylePanel;
    private javax.swing.JLabel widthLabel;
    private javax.swing.JFormattedTextField widthTextField;
    // End of variables declaration//GEN-END:variables
}
