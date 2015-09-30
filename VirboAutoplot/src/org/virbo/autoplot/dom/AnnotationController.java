
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasDevicePosition;
import org.jdesktop.beansbinding.Converter;

/**
 * implements the annotation
 * @author jbf
 */
public class AnnotationController extends DomNodeController {
    private final Annotation annotation;
    private final Application dom;
    private final DasAnnotation dasAnnotation;
        
    public AnnotationController( Application dom, Annotation annotation, DasAnnotation dasAnnotation ) {
        super( annotation );
        this.dom = dom;
        this.annotation = annotation;
        bindTo(dasAnnotation);
        this.dasAnnotation= dasAnnotation;
        annotation.controller = this;
    }    
        
    void syncTo( DomNode n, List<String> exclude ) {
        Annotation that = (Annotation) n;
        if ( !exclude.contains( Annotation.PROP_TEXT ) ) annotation.setText(that.getText());
        if ( !exclude.contains( Annotation.PROP_ROWID ) ) annotation.setRowId(that.getRowId());
        if ( !exclude.contains( Annotation.PROP_COLUMNID ) ) annotation.setColumnId(that.getColumnId());
    }
    
    /**
     * converts forward from relative font spec to point size.
     * @return 
     */
    private Converter fontConverter() {
        return new Converter() {
            @Override
            public Object convertForward(Object s) {
                try {
                    double[] dd= DasDevicePosition.parseLayoutStr((String)s);
                    if ( dd[1]==1 && dd[2]==0 ) {
                        return 0.f;
                    } else {
                        double parentSize= dom.getCanvases(0).controller.dasCanvas.getFont().getSize2D();
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
                    double parentSize= dom.getCanvases(0).controller.dasCanvas.getFont().getSize2D();
                    double relativeSize= size / parentSize;
                    return String.format( "%.2fem", relativeSize );
                }
            }  
        };
    }
    
    private void bindTo( final DasAnnotation p ) {
        ApplicationController ac = dom.controller;
        p.setFontSize( new Float(0.));
        ac.bind( annotation, "text", p, "text");
        ac.bind( annotation, "fontSize", p, "fontSize", fontConverter() );
        ac.bind( annotation, "borderType", p, "borderType" );
        ac.bind( annotation, "anchorPosition", p, "anchorPosition" );
        annotation.addPropertyChangeListener( Annotation.PROP_ROWID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Row r= (Row) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    p.setRow(r.controller.getDasRow());
                }
            }
        });
        annotation.addPropertyChangeListener( Annotation.PROP_ROWID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Row r= (Row) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    p.setRow(r.controller.getDasRow());
                }
            }
        });
        annotation.addPropertyChangeListener( Annotation.PROP_COLUMNID, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Column r= (Column) DomUtil.getElementById( dom.getCanvases(0), (String)evt.getNewValue() );
                if ( r!=null ) {
                    p.setColumn(r.controller.getDasColumn());
                }
            }
        });
        
    }

    protected void removeBindings() {
        ApplicationController ac = dom.controller;
        ac.unbind( annotation );
    }
}
