
package org.autoplot.dom;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.graph.AnchorPosition;
import org.das2.graph.AnchorType;
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
    private String text = "Annotation";

    public static final String PROP_TEXT = "text";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String oldText = this.text;
        this.text = text;
        propertyChangeSupport.firePropertyChange(PROP_TEXT, oldText, text);
    }
    
    private String url = "";

    public static final String PROP_URL = "url";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        String oldUrl = this.url;
        this.url = url;
        propertyChangeSupport.firePropertyChange(PROP_URL, oldUrl, url);
    }

    private double scale = 1.0;

    public static final String PROP_SCALE = "scale";

    public double getScale() {
        return scale;
    }

    /**
     * set the amount to scale the image by, if using URL to point at an image, where 0.5 is half of the
     * original image size.
     * @param scale 
     */
    public void setScale(double scale) {
        double oldScale = this.scale;
        this.scale = scale;
        propertyChangeSupport.firePropertyChange(PROP_SCALE, oldScale, scale);
    }
    
    private String fontSize = "1.4em";

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
    
    private DatumRange xrange= DatumRange.newDatumRange(0,10,Units.dimensionless);

    public static final String PROP_XRANGE = "xrange";

    public DatumRange getXrange() {
        return xrange;
    }

    public void setXrange(DatumRange xrange) {
        DatumRange oldXrange = this.xrange;
        this.xrange = xrange;
        propertyChangeSupport.firePropertyChange(PROP_XRANGE, oldXrange, xrange);
    }

    private DatumRange yrange= DatumRange.newDatumRange(0,10,Units.dimensionless);;

    public static final String PROP_YRANGE = "yrange";

    public DatumRange getYrange() {
        return yrange;
    }

    public void setYrange(DatumRange yrange) {
        DatumRange oldYrange = this.yrange;
        this.yrange = yrange;
        propertyChangeSupport.firePropertyChange(PROP_YRANGE, oldYrange, yrange);
    }

    private Datum pointAtX = Datum.create(0);

    public static final String PROP_POINTATX = "pointAtX";

    public Datum getPointAtX() {
        return pointAtX;
    }

    public void setPointAtX(Datum pointAtX) {
        Datum oldPointAtX = this.pointAtX;
        this.pointAtX = pointAtX;
        propertyChangeSupport.firePropertyChange(PROP_POINTATX, oldPointAtX, pointAtX);
    }

    private Datum pointAtY = Datum.create(0);

    public static final String PROP_POINTATY = "pointAtY";

    public Datum getPointAtY() {
        return pointAtY;
    }

    public void setPointAtY(Datum pointAtY) {
        Datum oldPointAtY = this.pointAtY;
        this.pointAtY = pointAtY;
        propertyChangeSupport.firePropertyChange(PROP_POINTATY, oldPointAtY, pointAtY);
    }
    
    private String pointAtOffset="";

    public static final String PROP_POINTATOFFSET = "pointAtOffset";

    /**
     * return the offset from the thing we point at, if any.  For example, "1em"
     * means back off 1em from the target.
     * @return 
     */
    public String getPointAtOffset() {
        return pointAtOffset;
    }

    public void setPointAtOffset(String pointAtOffset) {
        String oldPointAtOffset = this.pointAtOffset;
        this.pointAtOffset = pointAtOffset;
        propertyChangeSupport.firePropertyChange(PROP_POINTATOFFSET, oldPointAtOffset, pointAtOffset);
    }

    private boolean showArrow = false;

    public static final String PROP_SHOWARROW = "showArrow";

    public boolean isShowArrow() {
        return showArrow;
    }

    public void setShowArrow(boolean showArrow) {
        boolean oldShowArrow = this.showArrow;
        this.showArrow = showArrow;
        propertyChangeSupport.firePropertyChange(PROP_SHOWARROW, oldShowArrow, showArrow);
    }

    private AnchorType anchorType = AnchorType.CANVAS;

    public static final String PROP_ANCHORTYPE = "anchorType";

    public AnchorType getAnchorType() {
        return anchorType;
    }

    public void setAnchorType(AnchorType anchorType) {
        AnchorType oldAnchorType = this.anchorType;
        this.anchorType = anchorType;
        propertyChangeSupport.firePropertyChange(PROP_ANCHORTYPE, oldAnchorType, anchorType);
    }
    
    private boolean splitAnchorType = false;

    public static final String PROP_SPLITANCHORTYPE = "splitAnchorType";

    public boolean isSplitAnchorType() {
        return splitAnchorType;
    }

    public void setSplitAnchorType(boolean splitAnchorType) {
        boolean oldSplitAnchorType = this.splitAnchorType;
        this.splitAnchorType = splitAnchorType;
        propertyChangeSupport.firePropertyChange(PROP_SPLITANCHORTYPE, oldSplitAnchorType, splitAnchorType);
    }

    private AnchorType verticalAnchorType = AnchorType.CANVAS;

    public static final String PROP_VERTICALANCHORTYPE = "verticalAnchorType";

    public AnchorType getVerticalAnchorType() {
        return verticalAnchorType;
    }

    public void setVerticalAnchorType(AnchorType verticalAnchorType) {
        AnchorType oldVerticalAnchorType = this.verticalAnchorType;
        this.verticalAnchorType = verticalAnchorType;
        propertyChangeSupport.firePropertyChange(PROP_VERTICALANCHORTYPE, oldVerticalAnchorType, verticalAnchorType);
    }
    
    private String anchorOffset= "1em,1em";

    public static final String PROP_ANCHOROFFSET = "anchorOffset";

    public String getAnchorOffset() {
        return anchorOffset;
    }

    public void setAnchorOffset(String anchorOffset) {
        String oldAnchorOffset = this.anchorOffset;
        this.anchorOffset = anchorOffset;
        propertyChangeSupport.firePropertyChange(PROP_ANCHOROFFSET, oldAnchorOffset, anchorOffset);
    }


    private String plotId = "";

    public static final String PROP_PLOTID = "plotId";

    public String getPlotId() {
        return plotId;
    }

    public void setPlotId(String plotId) {
        String oldPlotId = this.plotId;
        this.plotId = plotId;
        propertyChangeSupport.firePropertyChange(PROP_PLOTID, oldPlotId, plotId);
    }

    private BorderType anchorBorderType = BorderType.NONE;

    public static final String PROP_ANCHORBORDERTYPE = "anchorBorderType";

    public BorderType getAnchorBorderType() {
        return anchorBorderType;
    }

    public void setAnchorBorderType(BorderType anchorBorderType) {
        BorderType oldAnchorBorderType = this.anchorBorderType;
        this.anchorBorderType = anchorBorderType;
        propertyChangeSupport.firePropertyChange(PROP_ANCHORBORDERTYPE, oldAnchorBorderType, anchorBorderType);
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
        if ( !( n instanceof Annotation ) ) throw new IllegalArgumentException("node should be an Annotation");                                        
        Annotation that = (Annotation) n;
        if ( !exclude.contains( PROP_TEXT ) ) this.setText(that.getText());
        if ( !exclude.contains( PROP_URL ) ) this.setUrl(that.getUrl());
        if ( !exclude.contains( PROP_FONTSIZE ) ) this.setFontSize(that.getFontSize());
        if ( !exclude.contains( PROP_SCALE ) ) this.setScale(that.getScale() );
        if ( !exclude.contains( PROP_BORDERTYPE ) ) this.setBorderType(that.getBorderType() );
        if ( !exclude.contains( PROP_ANCHORPOSITION ) ) this.setAnchorPosition(that.getAnchorPosition() );
        if ( !exclude.contains( PROP_ANCHOROFFSET ) ) this.setAnchorOffset(that.getAnchorOffset() );
        if ( !exclude.contains( PROP_ANCHORTYPE ) ) this.setAnchorType(that.getAnchorType() );
        if ( !exclude.contains( PROP_SPLITANCHORTYPE ) ) this.setSplitAnchorType( that.isSplitAnchorType() );
        if ( !exclude.contains( PROP_VERTICALANCHORTYPE ) ) this.setVerticalAnchorType( that.getVerticalAnchorType() );
        if ( !exclude.contains( PROP_ANCHORBORDERTYPE ) ) this.setAnchorBorderType(that.getAnchorBorderType() );
        if ( !exclude.contains( PROP_XRANGE ) ) this.setXrange( that.getXrange() );
        if ( !exclude.contains( PROP_YRANGE ) ) this.setYrange( that.getYrange() );
        if ( !exclude.contains( PROP_POINTATX ) ) this.setPointAtX( that.getPointAtX() );
        if ( !exclude.contains( PROP_POINTATY ) ) this.setPointAtY( that.getPointAtY() );
        if ( !exclude.contains( PROP_POINTATOFFSET ) ) this.setPointAtOffset( that.getPointAtOffset() );
        if ( !exclude.contains( PROP_SHOWARROW ) ) this.setShowArrow( that.isShowArrow() );
        if ( !exclude.contains( PROP_OVERRIDECOLORS ) ) this.setOverrideColors(that.isOverrideColors() );
        if ( !exclude.contains( PROP_TEXTCOLOR ) ) this.setTextColor(that.getTextColor() );
        if ( !exclude.contains( PROP_FOREGROUND ) ) this.setForeground(that.getForeground() );
        if ( !exclude.contains( PROP_BACKGROUND ) ) this.setBackground(that.getBackground() );
        if ( !exclude.contains( PROP_PLOTID ) ) this.setPlotId(that.getPlotId());
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
        if ( !( node instanceof Annotation ) ) throw new IllegalArgumentException("node should be an Annotation");                                        
        
        Annotation that = (Annotation) node;
        List<Diff> result = new ArrayList();
        boolean b;

        b=  that.text.equals(this.text) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_TEXT, that.text, this.text ) );
        b=  that.url.equals(this.url) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_URL, that.url, this.url ) );
        b=  that.scale==this.scale;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_SCALE, that.scale, this.scale ) );
        b=  that.fontSize.equals(this.fontSize) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_FONTSIZE, that.fontSize, this.fontSize ) );
        b=  that.borderType.equals(this.borderType) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_BORDERTYPE, that.borderType, this.borderType ) );
        b=  that.anchorPosition.equals(this.anchorPosition) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ANCHORPOSITION, that.anchorPosition, this.anchorPosition ) );
        b=  that.anchorOffset.equals(this.anchorOffset) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ANCHOROFFSET, that.anchorOffset, this.anchorOffset ) );
        b=  that.anchorType.equals(this.anchorType) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ANCHORTYPE, that.anchorType, this.anchorType ) );
        b=  that.splitAnchorType==this.splitAnchorType;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_SPLITANCHORTYPE, that.splitAnchorType, this.splitAnchorType ) );
        b=  that.verticalAnchorType.equals( this.verticalAnchorType );
        if ( !b ) result.add(new PropertyChangeDiff( PROP_VERTICALANCHORTYPE, that.verticalAnchorType, this.verticalAnchorType ) );
        b=  that.anchorBorderType.equals(this.anchorBorderType) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ANCHORBORDERTYPE, that.anchorBorderType, this.anchorBorderType ) );
        b=  that.xrange.equals(this.xrange) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_XRANGE, that.xrange, this.xrange ) );
        b=  that.yrange.equals(this.yrange) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_YRANGE, that.yrange, this.yrange ) );
        b=  that.pointAtX.equals(this.pointAtX) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_POINTATX, that.pointAtX, this.pointAtX ) );
        b=  that.pointAtY.equals(this.pointAtY) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_POINTATY, that.pointAtY, this.pointAtY ) );
        b=  that.pointAtOffset.equals(this.pointAtOffset) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_POINTATOFFSET, that.pointAtOffset, this.pointAtOffset ) );
        b=  that.showArrow==this.showArrow;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_SHOWARROW, that.showArrow, this.showArrow ) );
        b=  that.textColor.equals(this.textColor) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_TEXTCOLOR, that.textColor, this.textColor ) );
        b=  that.foreground.equals(this.foreground) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_FOREGROUND, that.foreground, this.foreground ) );
        b=  that.background.equals(this.background) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_BACKGROUND, that.background, this.background ) );
        b=  that.plotId.equals(this.plotId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_PLOTID, that.plotId, this.plotId ) );
        b=  that.rowId.equals(this.rowId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_ROWID, that.rowId, this.rowId ) );
        b=  that.columnId.equals(this.columnId) ;
        if ( !b ) result.add(new PropertyChangeDiff( PROP_COLUMNID, that.columnId, this.columnId) );

        return result;
    }
}
