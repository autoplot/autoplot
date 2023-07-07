
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
    

    private void bindTo( final DasAnnotation p ) {
        ApplicationController ac = dom.controller;
        p.setFontSize( 0.f );
        
        String plotId= annotation.getPlotId();
        if ( plotId!=null && plotId.length()>0 ) {
            LabelConverter lc= new LabelConverter( dom, (Plot)DomUtil.getElementById( dom, plotId  ), null, null, annotation );
            ac.bind( annotation, Annotation.PROP_TEXT, p, "text", lc );
        } else {
            ac.bind( annotation, Annotation.PROP_TEXT, p, "text");
        }
        ac.bind( annotation, Annotation.PROP_URL, p, "url" );
        ac.bind( annotation, "fontSize", p, "fontSize", getFontConverter(p) );
        ac.bind( annotation, "scale", p, "scale" );
        ac.bind( annotation, "borderType", p, "borderType" );
        ac.bind( annotation, "anchorPosition", p, "anchorPosition" );
        ac.bind( annotation, Annotation.PROP_ANCHORTYPE, p, DasAnnotation.PROP_ANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_SPLITANCHORTYPE, p, DasAnnotation.PROP_SPLITANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_VERTICALANCHORTYPE, p, DasAnnotation.PROP_VERTICALANCHORTYPE );
        ac.bind( annotation, Annotation.PROP_ANCHOROFFSET, p, DasAnnotation.PROP_ANCHOROFFSET );
        ac.bind( annotation, "anchorBorderType", p, "anchorBorderType");
        ac.bind( annotation, "xrange", p, "xrange" );
        ac.bind( annotation, "yrange", p, "yrange" );
        ac.bind( annotation, "pointAtX", p, "pointAtX" );
        ac.bind( annotation, "pointAtY", p, "pointAtY" );
        ac.bind( annotation, Annotation.PROP_POINTATOFFSET, p, DasAnnotation.PROP_POINTATOFFSET );
        ac.bind( annotation, "showArrow", p, "showArrow" );
        ac.bind( annotation, "overrideColors", p, "overrideColors" );
        ac.bind( annotation, "textColor", p, "textColor" );
        ac.bind( annotation, "foreground", p, "foreground" );
        ac.bind( annotation, "background", p, "background" );
        ac.bind( annotation, "glow", p, "glow" );
        
        annotation.addPropertyChangeListener( Annotation.PROP_ROWID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                logger.finer("propertyChange "+Annotation.PROP_ROWID);
                String id= (String)evt.getNewValue();
                Row r=null;
                if ( id.length()>0 ) r= (Row) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    p.setRow(r.controller.getDasRow());
                } else {
                    p.setRow(allRow);
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
                    p.setColumn(r.controller.getDasColumn());
                } else {
                    p.setColumn(allColumn);
                }
            }
        });

        annotation.addPropertyChangeListener( Annotation.PROP_PLOTID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                if ( ((String)evt.getNewValue()).length()==0 ) {
                    p.setPlot(null);
                } else {
                    Plot pl= (Plot) DomUtil.getElementById( dom, (String)evt.getNewValue() );                
                    if ( pl!=null ) {
                        DasPlot dasPlot= pl.getController().getDasPlot();
                        p.setPlot( dasPlot );
                    } else {
                        p.setPlot( null );
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
