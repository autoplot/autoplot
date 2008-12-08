/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;

/**
 *
 * @author jbf
 */
public class CanvasController {
    DasCanvas dasCanvas;
    private Application application;
    private Canvas canvas;
    private PropertyChangeListener plotsListener;
    
    public CanvasController( Application dom, Canvas canvas ) {
        this.application= dom;
        this.canvas= canvas;
        canvas.setController(this);
        plotsListener= definePlotsListener();
        dom.addPropertyChangeListener( Application.PROP_PLOTS, plotsListener );
    }
    
    protected void setDasCanvas( DasCanvas canvas ) {
        assert ( dasCanvas!=null );
        this.dasCanvas= canvas;
        
        ApplicationController ac= application.getController();
        
        ac.bind(this.canvas, Canvas.PROP_SIZE, dasCanvas, "size"); //TODO: check this
        ac.bind(this.canvas, Canvas.PROP_FITTED, dasCanvas, "fitted");
        
    }

    public DasCanvas getDasCanvas() {
        return dasCanvas;
    }

    private PropertyChangeListener definePlotsListener() {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Plot[] plots= application.getPlots();
                int n= plots.length;
                for ( int i=0; i<n; i++ ) {
                    DasPlot dasPlot= plots[i].getController().getDasPlot();
                    DasRow row= dasPlot.getRow();
                    row.setMaximum( (i+1.)/n );
                    row.setMinimum( (i+0.)/n );
                    row.setEmMinimum(4);
                }
                Timer timer= new Timer(100,new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        dasCanvas.repaint();
                    }
                });
                timer.setRepeats(false);
                timer.restart();
                
            }
        };
    }
    
    protected void bindTo(final DasRow outerRow, final DasColumn outerColumn) {
        outerRow.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( !outerRow.isValueIsAdjusting() ) canvas.setRow( DasRow.formatLayoutStr(outerRow,true) + ","+  DasRow.formatLayoutStr(outerRow,false) );
            }
        });
        
        outerColumn.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( !outerColumn.isValueIsAdjusting() ) canvas.setColumn( DasRow.formatLayoutStr(outerColumn,true) + ","+  DasRow.formatLayoutStr(outerColumn,false) );
            }
        });
        
        canvas.addPropertyChangeListener( Canvas.PROP_ROW, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    DasRow.parseLayoutStr(outerRow, canvas.getRow());
                } catch (ParseException ex) {
                    Logger.getLogger(CanvasController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        canvas.addPropertyChangeListener( Canvas.PROP_COLUMN, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    DasColumn.parseLayoutStr(outerColumn, canvas.getColumn());
                } catch (ParseException ex) {
                    Logger.getLogger(CanvasController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }
}
