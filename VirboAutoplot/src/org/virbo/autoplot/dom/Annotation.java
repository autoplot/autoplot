/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.das2.graph.AnchorPosition;
import org.das2.graph.BorderType;

/**
 * Annotations for annotating the canvas.
 * @author jbf
 */
public class Annotation extends DomNode {
    
    AnnotationController controller;

    public Annotation() {
        super();
    }
    
    /**
     * the granny text to display.
     */
    private String text = "";

    public static final String PROP_TEXT = "text";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String oldText = this.text;
        this.text = text;
        propertyChangeSupport.firePropertyChange(PROP_TEXT, oldText, text);
    }

    private String fontSize = "1em";

    public static final String PROP_FONTSIZE = "fontSize";

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        String oldFontSize = this.fontSize;
        this.fontSize = fontSize;
        propertyChangeSupport.firePropertyChange(PROP_FONTSIZE, oldFontSize, fontSize);
    }

    private BorderType borderType = BorderType.NONE;

    public static final String PROP_BORDERTYPE = "borderType";

    public BorderType getBorderType() {
        return borderType;
    }

    public void setBorderType(BorderType borderType) {
        BorderType oldBorderType = this.borderType;
        this.borderType = borderType;
        propertyChangeSupport.firePropertyChange(PROP_BORDERTYPE, oldBorderType, borderType);
    }

    private AnchorPosition anchorPosition = AnchorPosition.NE;

    public static final String PROP_ANCHORPOSITION = "anchorPosition";

    public AnchorPosition getAnchorPosition() {
        return anchorPosition;
    }

    public void setAnchorPosition(AnchorPosition anchorPosition) {
        AnchorPosition oldAnchorPosition = this.anchorPosition;
        this.anchorPosition = anchorPosition;
        propertyChangeSupport.firePropertyChange(PROP_ANCHORPOSITION, oldAnchorPosition, anchorPosition);
    }

    
    private String rowId="";
    public static final String PROP_ROWID = "rowId";

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        String oldRowId = this.rowId;
        this.rowId = rowId;
        propertyChangeSupport.firePropertyChange(PROP_ROWID, oldRowId, rowId);
    }
    
    private String columnId="";
    public static final String PROP_COLUMNID = "columnId";

    public String getColumnId() {
        return columnId;
    }

    public void setColumnId(String columnId) {
        String oldColumnId = this.columnId;
        this.columnId = columnId;
        propertyChangeSupport.firePropertyChange(PROP_COLUMNID, oldColumnId, columnId);
    }

    public AnnotationController getController() {
        return controller;
    }

    @Override
    public void syncTo(DomNode n) {
        super.syncTo(n);
        syncTo(n,new ArrayList<String>() );
    }

    @Override
    public void syncTo(DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        Annotation that = (Annotation) n;
        if ( !exclude.contains( PROP_TEXT ) ) this.setText(that.getText());
        if ( !exclude.contains( PROP_FONTSIZE ) ) this.setText(that.getFontSize());
        if ( !exclude.contains( PROP_BORDERTYPE ) ) this.setBorderType(that.getBorderType() );
        if ( !exclude.contains( PROP_ANCHORPOSITION ) ) this.setAnchorPosition(that.getAnchorPosition() );
        if ( !exclude.contains( PROP_ROWID ) ) this.setRowId(that.getRowId());
        if ( !exclude.contains( PROP_COLUMNID ) ) this.setColumnId(that.getColumnId());
    }

    @Override
    public DomNode copy() {
        Annotation result= (Annotation) super.copy();
        result.controller= null;
        return result;
    }



    @Override
    public List<Diff> diffs(DomNode node) {
        Annotation that = (Annotation) node;
        List<Diff> result = new ArrayList();
        boolean b;

        b=  that.text.equals(this.text) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_TEXT, that.text, this.text ) );
        b=  that.fontSize.equals(this.fontSize) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_FONTSIZE, that.fontSize, this.fontSize ) );
        b=  that.borderType.equals(this.borderType) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_BORDERTYPE, that.borderType, this.borderType ) );
        b=  that.anchorPosition.equals(this.anchorPosition) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ANCHORPOSITION, that.anchorPosition, this.anchorPosition ) );
        b=  that.rowId.equals(this.rowId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ROWID, that.rowId, this.rowId ) );
        b=  that.columnId.equals(this.columnId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_COLUMNID, that.columnId, this.columnId) );

        return result;
    }
}
