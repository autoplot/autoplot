/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;

/**
 *
 * @author jbf
 */
public class Application extends DomNode {

    public Application() {
    }
    
    protected List<DataSourceFilter> dataSourceFilters= Arrays.asList( new DataSourceFilter[0] );
    public static final String PROP_DATASOURCEFILTERS = "dataSourceFilters";

    public DataSourceFilter[] getDataSourceFilters() {
        return (DataSourceFilter[]) dataSourceFilters.toArray( new DataSourceFilter[dataSourceFilters.size()] );
    }

    public void setDataSourceFilters(DataSourceFilter[] dataSourceFilters) {
        DataSourceFilter[] oldDataSourceFilters = (DataSourceFilter[]) this.dataSourceFilters.toArray( new DataSourceFilter[this.dataSourceFilters.size()] );
        this.dataSourceFilters = Arrays.asList(dataSourceFilters);
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTERS, oldDataSourceFilters, dataSourceFilters);
    }

    public DataSourceFilter getDataSourceFilters(int index) {
        return this.dataSourceFilters.get(index);
    }

    public void setDataSourceFilters(int index, DataSourceFilter newDataSourceFilter) {
        DataSourceFilter oldDataSourceFilters = this.dataSourceFilters.get(index);
        this.dataSourceFilters.set(index, newDataSourceFilter );
        propertyChangeSupport.fireIndexedPropertyChange(PROP_DATASOURCEFILTERS, index, oldDataSourceFilters, newDataSourceFilter);
    }

    
    public static final String PROP_PLOT_ELEMENTS = "plotElements";
    protected List<PlotElement> plotElements = new LinkedList<PlotElement>();

    public PlotElement[] getPlotElements() {
        return plotElements.toArray(new PlotElement[plotElements.size()]);
    }

    public void setPlotElements(PlotElement[] pele) {
        PlotElement[] old = this.plotElements.toArray(new PlotElement[this.plotElements.size()]);
        this.plotElements = Arrays.asList(pele);
        propertyChangeSupport.firePropertyChange(PROP_PLOT_ELEMENTS, old, pele);
    }

    public PlotElement getPlotElements(int index) {
        return this.plotElements.get(index);
    }

    public void setPlotElements(int index, PlotElement pele) {
        PlotElement old = this.plotElements.get(index);
        this.plotElements.set(index, pele);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOT_ELEMENTS, index, old, pele);
    }
    public static final String PROP_PLOTS = "plots";
    protected List<Plot> plots = new LinkedList<Plot>();

    public Plot[] getPlots() {
        return plots.toArray(new Plot[plots.size()]);
    }

    public void setPlots(Plot[] plots) {
        Plot[] oldPlots = this.plots.toArray(new Plot[this.plots.size()]);
        this.plots = Arrays.asList(plots);
        propertyChangeSupport.firePropertyChange(PROP_PLOTS, oldPlots, plots);
    }

    public Plot getPlots(int index) {
        return this.plots.get(index);
    }

    public void setPlots(int index, Plot newPlots) {
        Plot oldPlots = this.plots.get(index);
        this.plots.set(index, newPlots);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOTS, index, oldPlots, newPlots);
    }
    
    public static final String PROP_CANVASES = "canvases";
    protected List<Canvas> canvases = new LinkedList<Canvas>();

    public Canvas[] getCanvases() {
        return canvases.toArray(new Canvas[canvases.size()]);
    }

    public void setCanvases(Canvas[] canvases) {
        Canvas[] old = this.canvases.toArray(new Canvas[this.canvases.size()]);
        this.canvases = Arrays.asList(canvases);
        propertyChangeSupport.firePropertyChange(PROP_CANVASES, old, canvases);
    }

    public Canvas getCanvases(int index) {
        return this.canvases.get(index);
    }

    public void setCanvases(int index, Canvas newCanvas ) {
        Canvas old = this.canvases.get(index);
        this.canvases.set(index, newCanvas );
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOTS, index, old, newCanvas );
    }
    
    ApplicationController controller;

    public ApplicationController getController() {
        return controller;
    }
    /*** end properties *****************************/
    protected Options options = new Options();

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    protected DatumRange timeRange = DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
    /**
     * all time axes should hang off of this.
     */
    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(DatumRange timeRange) {
        if ( timeRange==null ) {
            throw new IllegalArgumentException("timeRange set to null");
        }
        DatumRange oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
        propertyChangeSupport.firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);
    }


    protected List<BindingModel> bindings= Collections.emptyList();
    public static final String PROP_BINDINGS = "bindings";

    public BindingModel[] getBindings() {
        BindingModel[] result= bindings.toArray(new BindingModel[bindings.size()]);
        return result;
    }

    public void setBindings(BindingModel[] bindings) {
        BindingModel[] oldBindings = getBindings();
        this.bindings = Arrays.asList(bindings);
        propertyChangeSupport.firePropertyChange(PROP_BINDINGS, oldBindings, bindings);
    }
    
    public BindingModel getBindings(int index) {
        return this.bindings.get(index);
    }

    public void setBindings(int index, BindingModel newBinding) {
        BindingModel oldBinding = this.bindings.get(index);
        this.bindings.set(index, newBinding);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_BINDINGS, index, oldBinding, newBinding);
    }

    protected List<Connector> connectors= Collections.EMPTY_LIST;
    public static final String PROP_CONNECTORS = "connectors";

    public Connector[] getConnectors() {
        Connector[] result= connectors.toArray(new Connector[connectors.size()]);
        return result;
    }

    public void setConnectors(Connector[] connectors) {
        Connector[] oldConnectors = getConnectors();
        this.connectors = Arrays.asList(connectors);
        propertyChangeSupport.firePropertyChange(PROP_CONNECTORS, oldConnectors, connectors);
    }
    
    public Connector getConnectors(int index) {
        return this.connectors.get(index);
    }

    public void setConnectors(int index, Connector newConnector) {
        Connector oldConnector = this.connectors.get(index);
        this.connectors.set(index, newConnector);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_CONNECTORS, index, oldConnector, newConnector);
    }
    
    
    /*****  end properties *********************/

    @Override
    public DomNode copy() {
        Application result = (Application) super.copy();
        result.controller= null;
        
        result.options = (Options) this.getOptions().copy();
        
        DataSourceFilter[] DataSourceFiltersCopy= this.getDataSourceFilters();
        for ( int i=0; i<DataSourceFiltersCopy.length; i++ ) {
            DataSourceFiltersCopy[i]= (DataSourceFilter) DataSourceFiltersCopy[i].copy();
        }
        result.setDataSourceFilters( DataSourceFiltersCopy );

        Plot[] plotsCopy= this.getPlots();
        for ( int i=0; i<plotsCopy.length; i++ ) {
            plotsCopy[i]= (Plot) plotsCopy[i].copy();
        }
        result.setPlots( plotsCopy );

        PlotElement[] peleCopy= this.getPlotElements();
        for ( int i=0; i<peleCopy.length; i++ ) {
            peleCopy[i]= (PlotElement) peleCopy[i].copy();
        }
        result.setPlotElements( peleCopy );

        Connector[] connectorsCopy= this.getConnectors();
        System.arraycopy(connectorsCopy, 0, connectorsCopy, 0, connectorsCopy.length);
        result.setConnectors( connectorsCopy );
        
        Canvas[] canvasesCopy= this.getCanvases();
        for ( int i=0; i<canvasesCopy.length; i++ ) {
            canvasesCopy[i]= (Canvas) canvasesCopy[i].copy();
        }
        result.setCanvases( canvasesCopy );

        return result;
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList<DomNode>();
        result.addAll(plots);
        result.addAll(plotElements);
        result.addAll(dataSourceFilters);
        result.addAll(canvases);
        result.add(options);
        
        return result;
    }
    
    @Override
    public void syncTo(DomNode n) {
        syncTo(n,new ArrayList<String>() );
    }

    @Override
    public void syncTo(DomNode n,List<String> exclude) {
        super.syncTo(n,exclude);
        if ( this.controller!=null ) { //TODO: what if there's no controller, shouldn't we sync that?
            this.controller.syncTo( (Application)n, exclude );
        }
    }

    private void addArrayDiffs( String property, Object[] thata, Object[] thisa, List<Diff> result ) {
        try {
        if ( thata.length > thisa.length ) {
            for ( int i=thata.length-1; i>=thisa.length; i-- ) {
                result.add( new ArrayNodeDiff( property, ArrayNodeDiff.Action.Delete, thata[i], i ) );
            }
         }

        if ( thata.length < thisa.length ) {
            for ( int i=thisa.length-1; i<thisa.length; i++ ) {
                result.add( new ArrayNodeDiff( property, ArrayNodeDiff.Action.Insert, thisa[i], i ) );
            }        
        }
        } catch ( ArrayIndexOutOfBoundsException ex ) {
            throw ex;
        }
    }

    /**
     * List the differences between the two nodes.
     * These should always be from this to that.
     * TODO: somehow this ends up working, although PlotElement and Style don't follow this rule.
     * @param node
     * @return
     */
    @Override
    public List<Diff> diffs(DomNode node) {

        Application that = (Application) node;
        
        List<Diff> result = new ArrayList<Diff>();

        addArrayDiffs( "dataSourceFilters", this.getDataSourceFilters(), that.getDataSourceFilters(), result );

        addArrayDiffs( "plotElements", this.getPlotElements(), that.getPlotElements(), result );

        addArrayDiffs( "plots", this.getPlots(), that.getPlots(), result );

        addArrayDiffs( "canvases", this.getCanvases(), that.getCanvases(), result );

        addArrayDiffs( "bindings", this.getBindings(), that.getBindings(), result );

        addArrayDiffs( "connectors", this.getConnectors(), that.getConnectors(), result );

        for ( int i=0; i<Math.min(this.dataSourceFilters.size(),that.dataSourceFilters.size()); i++ ) {
            DataSourceFilter thisDataSourceFilter= this.dataSourceFilters.get(i);
            DataSourceFilter thatDataSourceFilter= that.dataSourceFilters.get(i);
            result.addAll( DomUtil.childDiffs( "dataSourceFilters["+i+"]", thatDataSourceFilter.diffs( thisDataSourceFilter ) ) );
        }
        
        for ( int i=0; i<Math.min(this.canvases.size(),that.canvases.size()); i++ ) {
            Canvas thisCanvas= this.canvases.get(i);
            Canvas thatCanvas= that.canvases.get(i);
            result.addAll( DomUtil.childDiffs( "Canvases["+i+"]", thatCanvas.diffs( thisCanvas ) ) );
        }

        for ( int i=0; i<Math.min(this.plots.size(),that.plots.size()); i++ ) {
            Plot thisPlot= this.plots.get(i);
            Plot thatPlot= that.plots.get(i);
            result.addAll( DomUtil.childDiffs( "plots["+i+"]", thatPlot.diffs( thisPlot ) ) );
        }

        for ( int i=0; i<Math.min(this.plotElements.size(),that.plotElements.size()); i++ ) {
            result.addAll( DomUtil.childDiffs( "plotElements["+i+"]", that.getPlotElements(i).diffs( this.plotElements.get(i) ) ) );
        }
        
        result.addAll( DomUtil.childDiffs( "options", this.getOptions().diffs(  that.getOptions()) ));

        if ( !that.timeRange.equals( this.timeRange ) ) {
            result.add( new PropertyChangeDiff( "timeRange", this.timeRange, that.timeRange ) );  //TODO: why is this backwards but it works?
        }

        return result;
    }

}
