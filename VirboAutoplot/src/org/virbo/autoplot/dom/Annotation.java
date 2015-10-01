/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.awt.Color;
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

    private boolean overrideColors = false;

    public static final String PROP_OVERRIDECOLORS = "overrideColors";

    public boolean isOverrideColors() {
        return overrideColors;
    }

    public void setOverrideColors(boolean overrideColors) {
        boolean oldOverrideColors = this.overrideColors;
        this.overrideColors = overrideColors;
        propertyChangeSupport.firePropertyChange(PROP_OVERRIDECOLORS, oldOverrideColors, overrideColors);
    }
    
    private Color textColor = new Color(0, 0, 0);

    public static final String PROP_TEXTCOLOR = "textColor";

    public Color getTextColor() {
        return textColor;
    }

    /**
     * the color of the text, or if transparent then the border
     * color should be used.
     *
     * @param textColor 
     */
    public void setTextColor(Color textColor) {
        Color oldTextColor = this.textColor;
        this.textColor = textColor;
        propertyChangeSupport.firePropertyChange(PROP_TEXTCOLOR, oldTextColor, textColor);
    }
        
    private Color foreground = new Color(0, 0, 0);

    public static final String PROP_FOREGROUND = "foreground";

    public Color getForeground() {
        return foreground;
    }

    public void setForeground(Color foreground) {
        Color oldForeground = this.foreground;
        this.foreground = foreground;
        propertyChangeSupport.firePropertyChange(PROP_FOREGROUND, oldForeground, foreground);
    }

    private Color background = new Color(255,255,255);

    public static final String PROP_BACKGROUND = "background";

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color background) {
        Color oldBackground = this.background;
        this.background = background;
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUND, oldBackground, background);
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
        if ( !exclude.contains( PROP_FONTSIZE ) ) this.setFontSize(that.getFontSize());
        if ( !exclude.contains( PROP_BORDERTYPE ) ) this.setBorderType(that.getBorderType() );
        if ( !exclude.contains( PROP_ANCHORPOSITION ) ) this.setAnchorPosition(that.getAnchorPosition() );
        if ( !exclude.contains( PROP_OVERRIDECOLORS ) ) this.setOverrideColors(that.isOverrideColors() );
        if ( !exclude.contains( PROP_TEXTCOLOR ) ) this.setTextColor(that.getTextColor() );
        if ( !exclude.contains( PROP_FOREGROUND ) ) this.setForeground(that.getForeground() );
        if ( !exclude.contains( PROP_BACKGROUND ) ) this.setBackground(that.getBackground() );
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
        b=  that.textColor.equals(this.textColor) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_TEXTCOLOR, that.textColor, this.textColor ) );
        b=  that.foreground.equals(this.foreground) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_FOREGROUND, that.foreground, this.foreground ) );
        b=  that.background.equals(this.background) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_BACKGROUND, that.background, this.background ) );
        b=  that.rowId.equals(this.rowId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ROWID, that.rowId, this.rowId ) );
        b=  that.columnId.equals(this.columnId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_COLUMNID, that.columnId, this.columnId) );

        return result;
    }
}
