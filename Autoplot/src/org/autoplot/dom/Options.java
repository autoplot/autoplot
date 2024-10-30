
package org.autoplot.dom;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.autoplot.MouseModuleType;
import org.das2.graph.DasColorBar;

/**
 * Bean for holding Autoplot configuration options.  Note there are a few AutoplotUI prefs here that shouldn't be,
 * like scriptVisible which indicates if the script tab is shown.
 * @author jbf
 */
public final class Options extends DomNode {

    public static final String PROP_COLOR = "color";
    public static final String PROP_FILLCOLOR = "fillColor";
    
    /**
     * try to recycle old axis settings.  If the new range is near the 
     * old range, then just use the old range.
     */
    public static final String VALUE_AUTORANGE_TYPE_RELUCTANT="reluctant";

    public Options() {
        logger.fine("creating new Options node");
    }

    protected boolean scriptVisible = false;
    public static final String PROP_SCRIPTVISIBLE = "scriptVisible";

    public boolean isScriptVisible() {
        return scriptVisible;
    }

    public void setScriptVisible(boolean scriptVisible) {
        boolean oldScriptVisible = this.scriptVisible;
        this.scriptVisible = scriptVisible;
        propertyChangeSupport.firePropertyChange(PROP_SCRIPTVISIBLE, oldScriptVisible, scriptVisible);
    }

    protected boolean logConsoleVisible = false;
    public static final String PROP_LOGCONSOLEVISIBLE = "logConsoleVisible";

    public boolean isLogConsoleVisible() {
        return logConsoleVisible;
    }

    public void setLogConsoleVisible(boolean logConsoleVisible) {
        boolean oldLogConsoleVisible = this.logConsoleVisible;
        this.logConsoleVisible = logConsoleVisible;
        propertyChangeSupport.firePropertyChange(PROP_LOGCONSOLEVISIBLE, oldLogConsoleVisible, logConsoleVisible);
    }

    /**
     * true when the data tab is visible.
     */
    public static final String PROP_DATAVISIBLE = "dataVisible";

    protected boolean dataVisible = true;

    public boolean isDataVisible() {
        return dataVisible;
    }

    public void setDataVisible(boolean dataVisible) {
        boolean oldDataVisible = this.dataVisible;
        this.dataVisible = dataVisible;
        propertyChangeSupport.firePropertyChange(PROP_DATAVISIBLE, oldDataVisible, dataVisible);
    }

    /**
     * true when the layout tab is visible.
     */
    public static final String PROP_LAYOUTVISIBLE = "layoutVisible";

    protected boolean layoutVisible = true;

    public boolean isLayoutVisible() {
        return layoutVisible;
    }

    public void setLayoutVisible(boolean layoutVisible) {
        boolean oldLayoutVisible = this.layoutVisible;
        this.layoutVisible = layoutVisible;
        propertyChangeSupport.firePropertyChange(PROP_LAYOUTVISIBLE, oldLayoutVisible, layoutVisible);
    }


    protected boolean serverEnabled = false;
    public static final String PROP_SERVERENABLED = "serverEnabled";

    public boolean isServerEnabled() {
        return serverEnabled;
    }

    public void setServerEnabled(boolean serverEnabled) {
        boolean oldServerEnabled = this.serverEnabled;
        this.serverEnabled = serverEnabled;
        propertyChangeSupport.firePropertyChange(PROP_SERVERENABLED, oldServerEnabled, serverEnabled);
    }

    protected String canvasFont = "sans-12";
    public static final String PROP_CANVASFONT = "canvasFont";

    public String getCanvasFont() {
        return canvasFont;
    }

    public void setCanvasFont(String canvasFont) {
        String oldCanvasFont = this.canvasFont;
        this.canvasFont = canvasFont;
        propertyChangeSupport.firePropertyChange(PROP_CANVASFONT, oldCanvasFont, canvasFont);
    }

    protected int width = 640;

    public static final String PROP_WIDTH = "width";

    public int getWidth() {
        return width;
    }

    /**
     * set the initial width of the canvases in pixels
     * @param width 
     */
    public void setWidth(int width) {
        int oldWidth = this.width;
        this.width = width;
        propertyChangeSupport.firePropertyChange(PROP_WIDTH, oldWidth, width);
    }

    protected int height = 480;

    public static final String PROP_HEIGHT = "height";

    public int getHeight() {
        return height;
    }

    /**
     * set the initial height of the canvases in pixels
     * @param height 
     */
    public void setHeight(int height) {
        int oldHeight = this.height;
        this.height = height;
        propertyChangeSupport.firePropertyChange(PROP_HEIGHT, oldHeight, height);
    }

    protected Color foreground = Color.black;
    public static final String PROP_FOREGROUND = "foreground";

    public Color getForeground() {
        return foreground;
    }

    public void setForeground(Color foreground) {
        Color oldForeground = this.foreground;
        this.foreground = new Color(foreground.getRGB()); //otherwise can't serialize
        propertyChangeSupport.firePropertyChange(PROP_FOREGROUND, oldForeground, foreground);
    }

    protected Color background = Color.white;
    public static final String PROP_BACKGROUND = "background";

    public Color getBackground() {
        return background;
    }

    public void setBackground(Color background) {
        Color oldBackground = this.background;
        this.background = new Color(background.getRGB()); //otherwise can't serialize
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUND, oldBackground, background);
    }

    /**
     * Holds value of property color.
     */
    protected Color color = Color.BLACK;

    /**
     * Getter for property color.
     * @return Value of property color.
     */
    public Color getColor() {
        return this.color;
    }

    /**
     * Setter for property color.
     * @param color New value of property color.
     */
    public void setColor(Color color) {
        Color oldColor = this.color;
        this.color = new Color(color.getRGB()); //otherwise can't serialize
        propertyChangeSupport.firePropertyChange(PROP_COLOR, oldColor, color);
    }

    /**
     * Holds value of property fillColor.
     */
    protected Color fillColor = Color.DARK_GRAY;

    /**
     * Getter for property fillColor.
     * @return Value of property fillColor.
     */
    public Color getFillColor() {
        return this.fillColor;
    }

    /**
     * Setter for property fillColor.
     * @param fillColor New value of property fillColor.
     */
    public void setFillColor(Color fillColor) {
        Color oldFillColor = this.fillColor;
        this.fillColor = new Color(fillColor.getRGB()); //otherwise can't serialize
        propertyChangeSupport.firePropertyChange(PROP_FILLCOLOR, oldFillColor, fillColor);
    }

    public final static String PROP_COLORTABLE= "colortable";
    
    protected DasColorBar.Type colortable= DasColorBar.Type.COLOR_WEDGE;
    
    public DasColorBar.Type getColortable() {
        return this.colortable;
    }
    
    public void setColortable(DasColorBar.Type colortable) {
        Object oldVal= this.colortable;
        this.colortable = colortable;
        propertyChangeSupport.firePropertyChange( PROP_COLORTABLE, oldVal, this.colortable );
    }    

    protected boolean oppositeAxisVisible = false;

    public static final String PROP_OPPOSITEAXISVISIBLE = "oppositeAxisVisible";

    public boolean isOppositeAxisVisible() {
        return oppositeAxisVisible;
    }

    public void setOppositeAxisVisible(boolean oppositeAxisVisible) {
        boolean oldOppositeAxisVisible = this.oppositeAxisVisible;
        this.oppositeAxisVisible = oppositeAxisVisible;
        propertyChangeSupport.firePropertyChange(PROP_OPPOSITEAXISVISIBLE, oldOppositeAxisVisible, oppositeAxisVisible);
    }
    
    protected String ticklen = "0.66em";
    public static final String PROP_TICKLEN = "ticklen";

    public String getTicklen() {
        return ticklen;
    }

    public void setTicklen(String ticklen) {
        String oldTicklen = this.ticklen;
        this.ticklen = ticklen;
        propertyChangeSupport.firePropertyChange(PROP_TICKLEN, oldTicklen, ticklen);
    }
    
    protected String lineThickness = "1px";

    public static final String PROP_LINE_THICKNESS = "lineThickness";

    public String getLineThickness() {
        return lineThickness;
    }

    /**
     * lineThickness is the thickness of axes and ticks.
     * @param lineThickness 
     */
    public void setLineThickness(String lineThickness) {
        String oldLineThickness = this.lineThickness;
        this.lineThickness = lineThickness;
        propertyChangeSupport.firePropertyChange(PROP_LINE_THICKNESS, oldLineThickness, lineThickness);
    }

    /**
     * for multiline labels, the alignment, where 0 is left, 0.5 is center, and 1.0 is right.
     */
    protected float multiLineTextAlignment = 0.5f;
    public static final String PROP_MULTILINETEXTALIGNMENT = "multiLineTextAlignment";

    public float getMultiLineTextAlignment() {
        return multiLineTextAlignment;
    }

    public void setMultiLineTextAlignment(float multiLineTextAlignment) {
        float oldMultiLineTextAlignment = this.multiLineTextAlignment;
        this.multiLineTextAlignment = multiLineTextAlignment;
        propertyChangeSupport.firePropertyChange(PROP_MULTILINETEXTALIGNMENT, oldMultiLineTextAlignment, multiLineTextAlignment);
    }
    

    protected boolean flipColorbarLabel = false;
    public static final String PROP_FLIPCOLORBARLABEL = "flipColorbarLabel";

    public boolean isFlipColorbarLabel() {
        return flipColorbarLabel;
    }

    public void setFlipColorbarLabel(boolean flipColorbarLabel) {
        boolean oldFlipColorbarLabel = this.flipColorbarLabel;
        this.flipColorbarLabel = flipColorbarLabel;
        propertyChangeSupport.firePropertyChange(PROP_FLIPCOLORBARLABEL, oldFlipColorbarLabel, flipColorbarLabel);
    }


    protected boolean specialEffects = false;
    /**
     * property specialEffects
     * enables/disables things like animated axes, etc.
     */
    public static final String PROP_SPECIALEFFECTS = "specialEffects";

    public boolean isSpecialEffects() {
        return specialEffects;
    }

    public void setSpecialEffects(boolean specialEffects) {
        boolean oldSpecialEffects = this.specialEffects;
        this.specialEffects = specialEffects;
        propertyChangeSupport.firePropertyChange(PROP_SPECIALEFFECTS, oldSpecialEffects, specialEffects);
    }

    /**
     * property drawAntiAlias
     * should plot symbols and lines be anti aliased?
     */
    protected boolean drawAntiAlias = true;
    public static final String PROP_DRAWANTIALIAS = "drawAntiAlias";

    public boolean isDrawAntiAlias() {
        return drawAntiAlias;
    }

    public void setDrawAntiAlias(boolean drawAntiAlias) {
        boolean oldDrawAntiAlias = this.drawAntiAlias;
        this.drawAntiAlias = drawAntiAlias;
        propertyChangeSupport.firePropertyChange(PROP_DRAWANTIALIAS, oldDrawAntiAlias, drawAntiAlias);
    }

    /**
     * property textAntiAlias
     * should text labels be anti aliased?
     */
    protected boolean textAntiAlias = true;
    public static final String PROP_TEXTANTIALIAS = "textAntiAlias";

    public boolean isTextAntiAlias() {
        return textAntiAlias;
    }

    public void setTextAntiAlias(boolean textAntiAlias) {
        boolean oldTextAntiAlias = this.textAntiAlias;
        this.textAntiAlias = textAntiAlias;
        propertyChangeSupport.firePropertyChange(PROP_TEXTANTIALIAS, oldTextAntiAlias, textAntiAlias);
    }

    protected boolean drawGrid = false;
    public static final String PROP_DRAWGRID = "drawGrid";

    public boolean isDrawGrid() {
        return drawGrid;
    }

    public void setDrawGrid(boolean drawGrid) {
        boolean oldDrawGrid = this.drawGrid;
        this.drawGrid = drawGrid;
        propertyChangeSupport.firePropertyChange(PROP_DRAWGRID, oldDrawGrid, drawGrid);
    }

    protected boolean drawMinorGrid = false;
    public static final String PROP_DRAWMINORGRID = "drawMinorGrid";

    public boolean isDrawMinorGrid() {
        return drawMinorGrid;
    }

    public void setDrawMinorGrid(boolean drawMinorGrid) {
        boolean oldDrawMinorGrid = this.drawMinorGrid;
        this.drawMinorGrid = drawMinorGrid;
        propertyChangeSupport.firePropertyChange(PROP_DRAWMINORGRID, oldDrawMinorGrid, drawMinorGrid);
    }

    /**
     * overRendering is a hint to the plots that the data outside the visible axis bounds should be rendered
     * so that operations like pan are more fluid.
     */
    public static final String PROP_OVERRENDERING = "overRendering";
    protected boolean overRendering = false;

    public boolean isOverRendering() {
        return overRendering;
    }

    public void setOverRendering(boolean overRendering) {
        boolean oldValue = this.overRendering;
        this.overRendering = overRendering;
        propertyChangeSupport.firePropertyChange(PROP_OVERRENDERING, oldValue, overRendering);
    }

 
    protected boolean autoranging = true;
    public static final String PROP_AUTORANGING = "autoranging";

    public boolean isAutoranging() {
        return this.autoranging;
    }

    public void setAutoranging(boolean newautoranging) {
        boolean oldautoranging = autoranging;
        this.autoranging = newautoranging;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGING, oldautoranging, newautoranging);
    }

    /**
     * "" or "reluctant"
     */
    private String autorangeType = "";
    
    public static final String PROP_AUTORANGETYPE = "autorangeType";

    public String getAutorangeType() {
        return autorangeType;
    }

    public void setAutorangeType( String autorangeType ) {
        String oldAutorangeType = this.autorangeType;
        this.autorangeType = autorangeType;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGETYPE, oldAutorangeType, autorangeType);
    }
    
    protected boolean autolabelling = true;
    public static final String PROP_AUTOLABELLING = "autolabelling";

    public boolean isAutolabelling() {
        return autolabelling;
    }

    public void setAutolabelling(boolean autolabelling) {
        boolean oldAutolabelling = this.autolabelling;
        this.autolabelling = autolabelling;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLABELLING, oldAutolabelling, autolabelling);
    }

    protected boolean autolayout = true;
    public static final String PROP_AUTOLAYOUT = "autolayout";

    public boolean isAutolayout() {
        return autolayout;
    }

    public void setAutolayout(boolean autolayout) {
        boolean oldAutolayout = this.autolayout;
        this.autolayout = autolayout;
        propertyChangeSupport.firePropertyChange(PROP_AUTOLAYOUT, oldAutolayout, autolayout);
    }


    /**
     * Use Day of Year, rather than Y,M,D for labels.
     */
    protected boolean dayOfYear= false;
    public static final String PROP_DAY_OF_YEAR = "dayOfYear";

    public boolean isDayOfYear() {
        return dayOfYear;
    }

    public void setDayOfYear(boolean dayOfYear) {
        boolean old= this.dayOfYear;

        this.dayOfYear = dayOfYear;
        propertyChangeSupport.firePropertyChange(PROP_DAY_OF_YEAR, old, dayOfYear );
    }

    /**
     * Use time range editor instead of Data Set Selector.
     */
    public static final String PROP_USE_TIME_RANGE_EDITOR="useTimeRangeEditor";

    protected boolean useTimeRangeEditor= false;

    public boolean isUseTimeRangeEditor() {
        return useTimeRangeEditor;
    }

    public void setUseTimeRangeEditor(boolean useTimeRangeEditor) {
        boolean old= this.useTimeRangeEditor;

        this.useTimeRangeEditor = useTimeRangeEditor;
        propertyChangeSupport.firePropertyChange(PROP_USE_TIME_RANGE_EDITOR, old, useTimeRangeEditor );
    }


    /**
     * Use Nearest neighbor rebinning for viewing spectrograms.
     */
    protected boolean nearestNeighbor= false;
    public static final String PROP_NEARESTNEIGHBOR = "nearestNeighbor";

    public boolean isNearestNeighbor() {
        return nearestNeighbor;
    }

    public void setNearestNeighbor(boolean nearestNeighbor) {
        boolean old= this.nearestNeighbor;
        this.nearestNeighbor = nearestNeighbor;
        propertyChangeSupport.firePropertyChange(PROP_NEARESTNEIGHBOR, old, nearestNeighbor );
    }

    /**
     * the preference for mouse module.
     */
    protected MouseModuleType mouseModule = MouseModuleType.boxZoom;
    public static final String PROP_MOUSEMODULE = "mouseModule";

    public MouseModuleType getMouseModule() {
        return mouseModule;
    }

    public void setMouseModule( MouseModuleType mouseModule) {
        MouseModuleType oldMouseModule = this.mouseModule;
        this.mouseModule = mouseModule;
        propertyChangeSupport.firePropertyChange(PROP_MOUSEMODULE, oldMouseModule, mouseModule);
    }

    /**
     * the slice preference.  Should slices be shown from the rebinned pixels or from the original data
     */
    protected boolean sliceRebinnedData = false;
    public static final String PROP_SLICEREBINNEDDATA = "sliceRebinnedData";

    public boolean isSliceRebinnedData() {
        return sliceRebinnedData;
    }

    public void setSliceRebinnedData(boolean sliceRebinnedData) {
        boolean oldSliceRebinnedData = this.sliceRebinnedData;
        this.sliceRebinnedData = sliceRebinnedData;
        propertyChangeSupport.firePropertyChange(PROP_SLICEREBINNEDDATA, oldSliceRebinnedData, sliceRebinnedData);
    }
    
    /**
     * the tag printed on plots to indicate the date when it was created.
     */    
    protected String printingTag = "";
    
    public static final String PROP_PRINTINGTAG = "printingTag";

    public String getPrintingTag() {
        return printingTag;
    }

    public void setPrintingTag(String printingTag) {
        String oldPrintingTag=  this.printingTag;
        this.printingTag = printingTag;
        propertyChangeSupport.firePropertyChange(PROP_PRINTINGTAG, oldPrintingTag, printingTag);
    }
    
    /**
     * the level of messages that are displayed when printing.
     */
    protected Level printingLogLevel = Level.ALL;
    public static final String PROP_PRINTINGLOGLEVEL = "printingLogLevel";

    public Level getPrintingLogLevel() {
        return printingLogLevel;
    }

    public void setPrintingLogLevel(Level printingLogLevel) {
        Level oldPrintingLogLevel = this.printingLogLevel;
        this.printingLogLevel = printingLogLevel;
        propertyChangeSupport.firePropertyChange(PROP_PRINTINGLOGLEVEL, oldPrintingLogLevel, printingLogLevel);
    }
    
    /**
     * the level of messages that are shown on the display.
     */
    protected Level displayLogLevel = Level.ALL;
    public static final String PROP_DISPLAYLOGLEVEL = "displayLogLevel";

    public Level getDisplayLogLevel() {
        return displayLogLevel;
    }

    public void setDisplayLogLevel(Level displayLogLevel) {
        Level oldDisplayLogLevel = this.displayLogLevel;
        this.displayLogLevel = displayLogLevel;
        propertyChangeSupport.firePropertyChange(PROP_DISPLAYLOGLEVEL, oldDisplayLogLevel, displayLogLevel);
    }

    /**
     * the number of seconds the message bubbles are shown.  300 or more seconds 
     * will mean the message bubble is not erased.
     */
    protected int logMessageTimeoutSec = 20;
    public static final String PROP_LOGMESSAGETIMEOUTSEC = "logMessageTimeoutSec";

    public int getLogMessageTimeoutSec() {
        return logMessageTimeoutSec;
    }

    public void setLogMessageTimeoutSec(int logMessageTimeoutSec) {
        int oldLogMessageTimeoutSec = this.logMessageTimeoutSec;
        this.logMessageTimeoutSec = logMessageTimeoutSec;
        propertyChangeSupport.firePropertyChange(PROP_LOGMESSAGETIMEOUTSEC, oldLogMessageTimeoutSec, logMessageTimeoutSec);
    }

    /**
     * scan the data for the next available chunk, instead of stepping to next interval
     */
    protected boolean scanEnabled = true;
    public static final String PROP_SCANENABLED = "scanEnabled";

    public boolean isScanEnabled() {
        return scanEnabled;
    }

    public void setScanEnabled(boolean scanEnabled) {
        boolean oldScanEnabled = this.scanEnabled;
        this.scanEnabled = scanEnabled;
        propertyChangeSupport.firePropertyChange(PROP_SCANENABLED, oldScanEnabled, scanEnabled );
    }


    
    /**
     * synchronizes any property having to do with the appearance of the
     * plot.  This includes user preferences like the axis grid, and also performance
     * switches which may have a minor effect on appearance such as overrendering.
     * This was introduced to support createPngWalk, which should have an appearance
     * as close as possible to the vap.
     * @param n the node
     * @param exclude the properties to exclude.
     */
    public void syncToAll( DomNode n,List<String> exclude ) {
        this.syncTo(n,exclude);
        if ( !( n instanceof Options ) ) throw new IllegalArgumentException("node should be a Options");                
        Options that = (Options) n;
        if ( !exclude.contains(PROP_DRAWGRID) ) this.setDrawGrid( that.isDrawGrid() );
        if ( !exclude.contains(PROP_DRAWMINORGRID) ) this.setDrawMinorGrid( that.isDrawMinorGrid() );
        if ( !exclude.contains(PROP_MULTILINETEXTALIGNMENT) ) this.setMultiLineTextAlignment( that.getMultiLineTextAlignment() );
        if ( !exclude.contains(PROP_OVERRENDERING) ) this.setOverRendering( that.isOverRendering() );
        if ( !exclude.contains(PROP_TEXTANTIALIAS) ) this.setTextAntiAlias( that.isTextAntiAlias() );
        if ( !exclude.contains(PROP_PRINTINGLOGLEVEL) ) this.setPrintingLogLevel( that.getPrintingLogLevel() );
        if ( !exclude.contains(PROP_DISPLAYLOGLEVEL) ) this.setDisplayLogLevel( that.getDisplayLogLevel() );
        if ( !exclude.contains(PROP_DAY_OF_YEAR) ) this.setDayOfYear( that.isDayOfYear() );
    }

    // Note these are weird: I'm not sure if I've just forgotten items or this was intentional.
    // I suspect that it is intentional that a subset of the options are treated this way.  Seems like
    // there was an issue with colors if I didn't do this.  Anyway, we sync useTimeRangeEditor because
    // of the use case where a product is turned over to a person who doesn't want to see URIs.
    
    /**
     * synchronizes to another node, except unlike other nodes where this is
     * thorough by default, this is the minimal set of properties that 
     * will provide a usable display.  These include the colors and the fonts,
     * and the timeRange editor mode, flip colorbar label, the ticklen, and last
     * scanEnabled.  
     * @see https://sourceforge.net/p/autoplot/bugs/2175/
     * @param n the node
     * @param exclude the properties to exclude.
     */
    @Override
    public void syncTo( DomNode n,List<String> exclude ) {
        super.syncTo(n,exclude);
        if ( !( n instanceof Options ) ) throw new IllegalArgumentException("node should be a Options");                        
        Options that = (Options) n;
        if ( !exclude.contains(PROP_BACKGROUND) ) this.setBackground(that.getBackground());
        if ( !exclude.contains(PROP_FOREGROUND) ) this.setForeground(that.getForeground());
        if ( !exclude.contains(PROP_COLOR) )this.setColor(that.getColor());
        if ( !exclude.contains(PROP_FILLCOLOR) )this.setFillColor(that.getFillColor());
        if ( !exclude.contains(PROP_COLORTABLE) )this.setColortable(that.getColortable());
        if ( !exclude.contains(PROP_CANVASFONT) )this.setCanvasFont(that.getCanvasFont());
        if ( !exclude.contains(PROP_USE_TIME_RANGE_EDITOR) ) this.setUseTimeRangeEditor(that.isUseTimeRangeEditor());
        if ( !exclude.contains(PROP_FLIPCOLORBARLABEL) ) this.setFlipColorbarLabel(that.isFlipColorbarLabel());
        if ( !exclude.contains(PROP_TICKLEN) ) this.setTicklen( that.getTicklen() );
        if ( !exclude.contains(PROP_OPPOSITEAXISVISIBLE ) ) this.setOppositeAxisVisible( that.isOppositeAxisVisible() );
        if ( !exclude.contains(PROP_LINE_THICKNESS) ) this.setLineThickness( that.getLineThickness() );
        if ( !exclude.contains(PROP_SCANENABLED) ) this.setScanEnabled( that.isScanEnabled() );
    }

    @Override
    public void syncTo(DomNode n) {
        this.syncTo(n, new ArrayList<String>() );
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        if ( !( node instanceof Options ) ) throw new IllegalArgumentException("node should be a Options");                        
        Options that = (Options) node;

        List<Diff> result = super.diffs(node);

        boolean b;

        b = that.getBackground().equals(this.getBackground());
        if (!b) result.add(new PropertyChangeDiff(PROP_BACKGROUND, that.getBackground(), this.getBackground()));
        b = that.getForeground().equals(this.getForeground());
        if (!b) result.add(new PropertyChangeDiff(PROP_FOREGROUND, that.getForeground(), this.getForeground()));
        b = that.getColor().equals(this.getColor());
        if (!b) result.add(new PropertyChangeDiff(PROP_COLOR, that.getColor(), this.getColor()));
        b = that.getFillColor().equals(this.getFillColor());
        if (!b) result.add(new PropertyChangeDiff(PROP_FILLCOLOR, that.getFillColor(), this.getFillColor()));
        b = that.getColortable().equals(this.getColortable());
        if (!b) result.add(new PropertyChangeDiff(PROP_COLORTABLE, that.getColortable(), this.getColortable()));
        b = that.getCanvasFont().equals(this.getCanvasFont());
        if (!b) result.add(new PropertyChangeDiff(PROP_CANVASFONT, that.getCanvasFont(), this.getCanvasFont()));
        b = that.isUseTimeRangeEditor() == this.isUseTimeRangeEditor();
        if (!b) result.add(new PropertyChangeDiff(PROP_USE_TIME_RANGE_EDITOR, that.isUseTimeRangeEditor(), this.isUseTimeRangeEditor()));
        b = that.isFlipColorbarLabel()==this.isFlipColorbarLabel();
        if (!b) result.add(new PropertyChangeDiff(PROP_FLIPCOLORBARLABEL, that.isFlipColorbarLabel(), this.isFlipColorbarLabel() ));
        b = that.getTicklen().equals(this.getTicklen() );
        if (!b) result.add(new PropertyChangeDiff(PROP_TICKLEN, that.getTicklen(), this.getTicklen()));
        b = that.getLineThickness().equals(this.getLineThickness() );
        if (!b) result.add(new PropertyChangeDiff(PROP_OPPOSITEAXISVISIBLE, that.isOppositeAxisVisible(), this.isOppositeAxisVisible() ));
        b = that.getLineThickness().equals(this.getLineThickness() );
        if (!b) result.add(new PropertyChangeDiff(PROP_LINE_THICKNESS, that.getLineThickness(), this.getLineThickness()));
        b = that.isScanEnabled()== this.isScanEnabled();
        if (!b) result.add(new PropertyChangeDiff(PROP_SCANENABLED, that.isScanEnabled(), this.isScanEnabled() ));
        return result;
    }

    private OptionsPrefsController controller=null;
    
    public OptionsPrefsController getController() {
        return this.controller;
    }
    
    /**
     * set the controller for this node.  This can only be called once.
     * @param controller 
     */
    protected void setController( OptionsPrefsController controller ) {
        if ( this.controller!=null ) {
            throw new IllegalArgumentException("controller has already been set");
        }
        this.controller= controller;
    }
    
    @Override
    public DomNode copy() {
        Options that= new Options();
        that.setBackground( this.getBackground() );
        that.setForeground( this.getForeground() );
        that.setColor( this.getColor() );
        that.setFillColor( this.getFillColor() );
        that.setColortable( this.getColortable() );
        that.setCanvasFont( this.getCanvasFont() );
        that.setUseTimeRangeEditor( this.isUseTimeRangeEditor() );
        that.setFlipColorbarLabel( this.isFlipColorbarLabel() );
        that.setTicklen( this.getTicklen() );
        that.setOppositeAxisVisible( this.isOppositeAxisVisible() );
        that.setLineThickness( this.getLineThickness() );
        that.setScanEnabled( this.isScanEnabled() );
        return that;
    }

}
