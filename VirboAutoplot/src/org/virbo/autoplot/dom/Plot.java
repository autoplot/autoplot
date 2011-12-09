/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.graph.DasColorBar;
import org.das2.graph.LegendPosition;

/**
 *
 * @author jbf
 */
public class Plot extends DomNode {

    public Plot() {
    }
    protected Axis xaxis = new Axis();
    public static final String PROP_XAXIS = "xaxis";

    public Axis getXaxis() {
        return xaxis;
    }

    public void setXaxis(Axis xaxis) {
        Axis oldXaxis = this.xaxis;
        this.xaxis = xaxis;
        propertyChangeSupport.firePropertyChange(PROP_XAXIS, oldXaxis, xaxis);
    }
    protected Axis yaxis = new Axis();
    public static final String PROP_YAXIS = "yaxis";

    public Axis getYaxis() {
        return yaxis;
    }

    public void setYaxis(Axis yaxis) {
        Axis oldYaxis = this.yaxis;
        this.yaxis = yaxis;
        propertyChangeSupport.firePropertyChange(PROP_YAXIS, oldYaxis, yaxis);
    }
    protected Axis zaxis = new Axis();
    public static final String PROP_ZAXIS = "zaxis";

    public Axis getZaxis() {
        return zaxis;
    }

    public void setZaxis(Axis zaxis) {
        Axis oldZaxis = this.zaxis;
        this.zaxis = zaxis;
        propertyChangeSupport.firePropertyChange(PROP_ZAXIS, oldZaxis, zaxis);
    }
    protected String title = "";
    /**
     * title for the plot. 
     */
    public static final String PROP_TITLE = "title";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title;
        propertyChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
    }

    protected LegendPosition legendPosition = LegendPosition.NE;
    public static final String PROP_LEGENDPOSITION = "legendPosition";

    public LegendPosition getLegendPosition() {
        return legendPosition;
    }

    public void setLegendPosition(LegendPosition legendPosition) {
        LegendPosition oldLegendPosition = this.legendPosition;
        this.legendPosition = legendPosition;
        propertyChangeSupport.firePropertyChange(PROP_LEGENDPOSITION, oldLegendPosition, legendPosition);
    }

    /**
     * indicates that the label was set by a machine, not a human, and can be
     * updated automatically.
     */
    protected boolean autoLabel = false;
    public static final String PROP_AUTOLABEL = "autoLabel";

    public boolean isAutoLabel() {
        return autoLabel;
    }

    public void setAutoLabel(boolean autolabel) {
        boolean oldAutolabel = this.autoLabel;
        this.autoLabel = autolabel;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABEL, oldAutolabel, autolabel);
    }

    /**
     * false indicates that the plot and its data will not
     * be drawn.
     */
    public static final String PROP_VISIBLE = "visible";
    protected boolean visible = true;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean oldVisible = this.visible;
        this.visible = visible;
        propertyChangeSupport.firePropertyChange(PROP_VISIBLE, oldVisible, visible);
    }

    /**
     * indicates the application is allowed to automatically create bindings to
     * the plot, typically when it is first created.
     */
    public static final String PROP_AUTOBINDING = "autoBinding";
    protected boolean autoBinding = false;

    public boolean isAutoBinding() {
        return autoBinding;
    }

    public void setAutoBinding(boolean autoBinding) {
        boolean oldAutoBinding = this.autoBinding;
        this.autoBinding = autoBinding;
        propertyChangeSupport.firePropertyChange(PROP_AUTOBINDING, oldAutoBinding, autoBinding);
    }

    protected boolean isotropic = false;
    public static final String PROP_ISOTROPIC = "isotropic";

    public boolean isIsotropic() {
        return isotropic;
    }

    public void setIsotropic(boolean isotropic) {
        boolean oldIsotropic = this.isotropic;
        this.isotropic = isotropic;
        propertyChangeSupport.firePropertyChange(PROP_ISOTROPIC, oldIsotropic, isotropic);
    }
    
    public final static String PROP_COLORTABLE= "colortable";

    private DasColorBar.Type colortable= DasColorBar.Type.COLOR_WEDGE;

    public DasColorBar.Type getColortable() {
        return this.colortable;
    }

    public void setColortable(DasColorBar.Type colortable) {
        Object oldVal= this.colortable;
        this.colortable = colortable;
        propertyChangeSupport.firePropertyChange( PROP_COLORTABLE, oldVal, this.colortable );
    }

    protected String rowId="";
    public static final String PROP_ROWID = "rowId";

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        String oldRowId = this.rowId;
        this.rowId = rowId;
        propertyChangeSupport.firePropertyChange(PROP_ROWID, oldRowId, rowId);
    }
    protected String columnId="";
    public static final String PROP_COLUMNID = "columnId";

    public String getColumnId() {
        return columnId;
    }

    public void setColumnId(String columnId) {
        String oldColumnId = this.columnId;
        this.columnId = columnId;
        propertyChangeSupport.firePropertyChange(PROP_COLUMNID, oldColumnId, columnId);
    }

    private DatumRange context= Axis.DEFAULT_RANGE;
    public static final String PROP_CONTEXT= "context";

    public DatumRange getContext() {
        return context;
    }

    public void setContext(DatumRange context) {
        DatumRange old= this.context;
        this.context = context;
        propertyChangeSupport.firePropertyChange(PROP_CONTEXT, old, context );
    }

    /**
     * The address of a dataset where additional labels for the x axis ticks can be
     * found.  This should be the address of a bundle dataset.
     */
    private String ticksURI= "";
    public static final String PROP_TICKS_URI= "ticksURI";

    public String getTicksURI() {
        return ticksURI;
    }

    public void setTicksURI( String ticksURI ) {
        String old= this.ticksURI;
        this.ticksURI = ticksURI;
        propertyChangeSupport.firePropertyChange(PROP_TICKS_URI, old, ticksURI );
    }

    protected PlotController controller;

    public PlotController getController() {
        return controller;
    }

    @Override
    public DomNode copy() {
        Plot result = (Plot) super.copy();
        result.controller = null;
        result.xaxis = (Axis) result.xaxis.copy();
        result.yaxis = (Axis) result.yaxis.copy();
        result.zaxis = (Axis) result.zaxis.copy();
        return result;
    }

    @Override
    public void syncTo(DomNode n) {
        syncTo( n, new ArrayList<String>() );
    }

    @Override
    public void syncTo(DomNode n, List<String> exclude) {
        super.syncTo(n,exclude);
        Plot that = (Plot) n;
        if (!exclude.contains(PROP_TITLE)) this.setTitle(that.getTitle());
        if (!exclude.contains(PROP_ISOTROPIC)) this.setIsotropic(that.isIsotropic());
        if (!exclude.contains(PROP_COLORTABLE ) ) this.setColortable( that.colortable );
        if (!exclude.contains(PROP_ROWID)) this.setRowId(that.getRowId());
        if (!exclude.contains(PROP_COLUMNID)) this.setColumnId(that.getColumnId());
        if (!exclude.contains(PROP_AUTOLABEL)) this.setAutoLabel(that.isAutoLabel());
        if (!exclude.contains(PROP_XAXIS)) this.xaxis.syncTo(that.getXaxis(),exclude); // possibly exclude id's.
        if (!exclude.contains(PROP_YAXIS)) this.yaxis.syncTo(that.getYaxis(),exclude);
        if (!exclude.contains(PROP_ZAXIS)) this.zaxis.syncTo(that.getZaxis(),exclude);
        if (!exclude.contains(PROP_VISIBLE)) this.setVisible(that.isVisible());
        if (!exclude.contains(PROP_CONTEXT)) this.setContext(that.getContext());
        if (!exclude.contains(PROP_TICKS_URI)) this.setTicksURI(that.getTicksURI());
        if (!exclude.contains(PROP_LEGENDPOSITION)) this.setLegendPosition(that.getLegendPosition());
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList<DomNode>();
        result.add(xaxis);
        result.add(yaxis);
        result.add(zaxis);

        return result;
    }

    @Override
    public List<Diff> diffs(DomNode node) {

        Plot that = (Plot) node;
        List<Diff> result = super.diffs(node);

        boolean b;

        b = that.title.equals(this.title);
        if (!b) result.add(new PropertyChangeDiff(PROP_TITLE, that.title, this.title));
        b = that.isotropic == this.isotropic;
        if (!b) result.add(new PropertyChangeDiff(PROP_ISOTROPIC, that.isotropic, this.isotropic));
        b=  that.colortable.equals(this.colortable) ;
        if ( !b ) result.add( new PropertyChangeDiff( PROP_COLORTABLE, that.colortable , this.colortable ) );
        b = that.autoLabel == this.autoLabel;
        if (!b) result.add(new PropertyChangeDiff(PROP_AUTOLABEL, that.autoLabel, this.autoLabel));
        b = that.autoBinding == this.autoBinding;
        if (!b) result.add(new PropertyChangeDiff(PROP_AUTOBINDING, that.autoBinding, this.autoBinding));
        b = that.rowId.equals(this.rowId);
        if (!b) result.add(new PropertyChangeDiff(PROP_ROWID, that.rowId, this.rowId));
        b = that.columnId.equals(this.columnId);
        if (!b) result.add(new PropertyChangeDiff(PROP_COLUMNID, that.columnId, this.columnId));
        b = that.visible == this.visible;
        if (!b) result.add(new PropertyChangeDiff(PROP_VISIBLE, that.visible, this.visible));
        if ( that.context==this.context ) {
            b= true;
        } else {
            b = that.context!=null && that.context.equals(this.context);
        }
        if (!b) result.add(new PropertyChangeDiff(PROP_CONTEXT, that.context, this.context));
        b= that.ticksURI.equals(this.ticksURI);
        if (!b) result.add(new PropertyChangeDiff(PROP_TICKS_URI, that.ticksURI, this.ticksURI));
        b= that.legendPosition.equals(this.legendPosition);
        if (!b) result.add(new PropertyChangeDiff(PROP_LEGENDPOSITION, that.legendPosition, this.legendPosition ));
        result.addAll(DomUtil.childDiffs( PROP_XAXIS, this.getXaxis().diffs(that.getXaxis())));
        result.addAll(DomUtil.childDiffs( PROP_YAXIS, this.getYaxis().diffs(that.getYaxis())));
        result.addAll(DomUtil.childDiffs( PROP_ZAXIS, this.getZaxis().diffs(that.getZaxis())));
        return result;
    }
}
