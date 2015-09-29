
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.das2.graph.DasAnnotation;

/**
 * implements the annotation
 * @author jbf
 */
public class AnnotationController extends DomNodeController {
    private final Annotation annotation;
    private final Application dom;
        
    public AnnotationController( Application dom, Annotation annotation, DasAnnotation dasAnnotation ) {
        super( annotation );
        this.dom = dom;
        this.annotation = annotation;
        annotation.controller = this;
        bindTo(dasAnnotation);
    }    
        
    void syncTo( DomNode n, List<String> exclude ) {
        Annotation that = (Annotation) n;
        if ( !exclude.contains( Annotation.PROP_TEXT ) ) annotation.setText(that.getText());
        if ( !exclude.contains( Annotation.PROP_ROWID ) ) annotation.setRowId(that.getRowId());
        if ( !exclude.contains( Annotation.PROP_COLUMNID ) ) annotation.setColumnId(that.getColumnId());
    }
    
    private void bindTo( final DasAnnotation p ) {
        ApplicationController ac = dom.controller;
        ac.bind( annotation, "text", p, "text");
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
