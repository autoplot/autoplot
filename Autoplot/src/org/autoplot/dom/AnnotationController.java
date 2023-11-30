
package org.autoplot.dom;

import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.Locale;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;

/**
 * implements the annotation
 * @author jbf
 */
public class AnnotationController extends DomNodeController {
    private final Annotation annotation;
    private final Application dom;
    private final DasAnnotation dasAnnotation;
    private DasRow allRow;
    private DasColumn allColumn;    
    
    public AnnotationController( Application dom, Annotation annotation, DasAnnotation dasAnnotation ) {
        super( annotation );
        this.dom = dom;
        this.annotation = annotation;
        bindTo(dasAnnotation);
        this.dasAnnotation= dasAnnotation;
        allRow= new DasRow( dasAnnotation.getCanvas(), 0., 1. );
        allColumn= new DasColumn( dasAnnotation.getCanvas(), 0., 1. );
        annotation.controller = this;
    }    
    
    /**
     * return the canvas where this annotation lives.
     * @return 
     */
    public Canvas getCanvas() {
        return dom.getCanvases(0);
    }
    
    /**
     * converts forward from relative font spec to point size, used by
     * the annotation and axis nodes.
     * @param dcc the canvas component.
     * @return the converter that converts between strings like "1em" and the font.
     */
    public Converter getFontConverter( final DasCanvasComponent dcc ) {
        return new Converter() {
            @Override
            public Object convertForward(Object s) {
                try {
                    double[] dd= DasDevicePosition.parseLayoutStr((String)s);
                    if ( dd[1]==1 && dd[2]==0 ) {
                        return 0.f;
                    } else {
                        Font f= dcc.getFont();
                        if ( f==null ) {
                            f= Font.decode( dom.getOptions().getCanvasFont() );
                        }
                        double parentSize= f.getSize2D();
                        double newSize= dd[1]*parentSize + dd[2];
                        return (float)newSize;
                    }
                } catch (ParseException ex) {
                    ex.printStackTrace();
                    return 0.f;
                }
            }

            @Override
            public Object convertReverse(Object t) {
                float size= (float)t;
                if ( size==0 ) {
                    return "1em";
                } else {
                    Font f= dcc.getFont();
                    if ( f==null ) {
                        f= Font.decode( dom.getOptions().getCanvasFont() );
                    }
                    double parentSize= f.getSize2D();
                    double relativeSize= size / parentSize;
                    return String.format( Locale.US, "%.2fem", relativeSize );
                }
            }  
        };
    }
    
    PropertyChangeListener contextPropertyChangeListener;
        
    private void bindTo( final DasAnnotation dasAnnotation ) {
        ApplicationController ac = dom.controller;
        dasAnnotation.setFontSize( 0.f );
        
        String plotId= annotation.getPlotId();
        if ( plotId!=null && plotId.length()>0 ) {
            LabelConverter lc= new LabelConverter( dom, (Plot)DomUtil.getElementById( dom, plotId  ), null, null, annotation );
            ac.bind( annotation, Annotation.PROP_TEXT, dasAnnotation, DasAnnotation.PROP_TEXT, lc );
        } else {
            ac.bind( annotation, Annotation.PROP_TEXT, dasAnnotation, DasAnnotation.PROP_TEXT );
        }
        ac.bind( annotation, Annotation.PROP_URL, dasAnnotation, "url" );
        ac.bind( annotation, "fontSize", dasAnnotation, "fontSize", getFontConverter(dasAnnotation) );
        ac.bind( annotation, "scale", dasAnnotation, "scale" );
        ac.bind( annotation, "borderType", dasAnnotation, "borderType" );
        ac.bind( annotation, "anchorPosition", dasAnnotation, "anchorPosition" );
        ac.bind( annotation, Annotation.PROP_ANCHORTYPE, dasAnnotation, DasAnnotation.PROP_ANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_SPLITANCHORTYPE, dasAnnotation, DasAnnotation.PROP_SPLITANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_VERTICALANCHORTYPE, dasAnnotation, DasAnnotation.PROP_VERTICALANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_ANCHOROFFSET, dasAnnotation, DasAnnotation.PROP_ANCHOROFFSET );
        ac.bind( annotation, "anchorBorderType", dasAnnotation, "anchorBorderType");
        ac.bind( annotation, "xrange", dasAnnotation, "xrange" );
        ac.bind( annotation, "yrange", dasAnnotation, "yrange" );
        ac.bind( annotation, "pointAtX", dasAnnotation, "pointAtX" );
        ac.bind( annotation, "pointAtY", dasAnnotation, "pointAtY" );
        ac.bind( annotation, Annotation.PROP_POINTATOFFSET, dasAnnotation, DasAnnotation.PROP_POINTATOFFSET );
        ac.bind( annotation, "showArrow", dasAnnotation, "showArrow" );
        ac.bind( annotation, "overrideColors", dasAnnotation, "overrideColors" );
        ac.bind( annotation, "textColor", dasAnnotation, "textColor" );
        ac.bind( annotation, "foreground", dasAnnotation, "foreground" );
        ac.bind( annotation, "background", dasAnnotation, "background" );
        ac.bind( annotation, "glow", dasAnnotation, "glow" );
        
        annotation.addPropertyChangeListener( Annotation.PROP_ROWID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.finer("propertyChange "+Annotation.PROP_ROWID);
                String id= (String)evt.getNewValue();
                Row r=null;
                if ( id.length()>0 ) r= (Row) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    dasAnnotation.setRow(r.controller.getDasRow());
                } else {
                    dasAnnotation.setRow(allRow);
                }
            }
        });
        annotation.addPropertyChangeListener( Annotation.PROP_COLUMNID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);                
                String id= (String)evt.getNewValue();
                Column r= null;
                if ( id.length()>0 ) r = (Column) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    dasAnnotation.setColumn(r.controller.getDasColumn());
                } else {
                    dasAnnotation.setColumn(allColumn);
                }
            }
        });

        annotation.addPropertyChangeListener( Annotation.PROP_PLOTID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                ac.unbind( annotation, Annotation.PROP_TEXT, dasAnnotation, "text" );

                if ( ((String)evt.getNewValue()).length()==0 ) {
                    dasAnnotation.setPlot(null);
                } else {
                    Plot oldPlot= (Plot) DomUtil.getElementById( dom, (String)evt.getOldValue() ); 
                    if ( oldPlot!=null && contextPropertyChangeListener!=null ) {
                        oldPlot.removePropertyChangeListener( PlotController.PROP_ACTIVEDATASET, contextPropertyChangeListener );
                    }
                    Plot plot= (Plot) DomUtil.getElementById( dom, (String)evt.getNewValue() );                
                    if ( plot!=null && plot.getId().length()>0 ) {
                        LabelConverter lc= new LabelConverter( dom, plot, null, null, annotation );
                        contextPropertyChangeListener = (PropertyChangeEvent evt1) -> {
                            dasAnnotation.setText( (String)lc.convertForward(annotation.getText()) );
                        };
                        plot.getController().addPropertyChangeListener( PlotController.PROP_ACTIVEDATASET, contextPropertyChangeListener );
                        ac.bind( annotation, Annotation.PROP_TEXT, dasAnnotation, DasAnnotation.PROP_TEXT, lc );                        
                    } else {
                        ac.bind( annotation, Annotation.PROP_TEXT, dasAnnotation, "text");
                    }
                    if ( plot!=null ) {
                        DasPlot dasPlot= plot.getController().getDasPlot();
                        dasAnnotation.setPlot( dasPlot );
                    } else {
                        dasAnnotation.setPlot( null );
                    }
                }
            }
        });
        
        dom.getCanvases(0).addPropertyChangeListener( Canvas.PROP_FONT, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);
                annotation.propertyChangeSupport.firePropertyChange( Annotation.PROP_FONTSIZE, null, annotation.getFontSize() );
                dasAnnotation.resize();
            }
            
        });
        
        
    }

    protected void removeBindings() {
        ApplicationController ac = dom.controller;
        ac.unbind( annotation );
    }

    /**
     * provide direct access to the annotation
     * @return the implementing das2 annotation
     */
    public DasAnnotation getDasAnnotation() {
        return dasAnnotation;
    }
    
}
