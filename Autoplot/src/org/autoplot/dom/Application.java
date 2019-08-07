
package org.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.autoplot.datasource.DataSourceUtil;

/**
 * Represents a state of the application as a whole, with its one canvas and
 * multiple plots, axes, and bindings.
 * @author jbf
 */
public class Application extends DomNode {

    public Application() {
    }

    /**
     * default time range indicates when the range is not being used.  This should never been seen by the user.
     */
    public static final DatumRange DEFAULT_TIME_RANGE= DataSourceUtil.DEFAULT_TIME_RANGE;
    
    
    protected CopyOnWriteArrayList<DataSourceFilter> dataSourceFilters= new CopyOnWriteArrayList( new DataSourceFilter[0] );
    public static final String PROP_DATASOURCEFILTERS = "dataSourceFilters";

    public DataSourceFilter[] getDataSourceFilters() {
        return (DataSourceFilter[]) dataSourceFilters.toArray( new DataSourceFilter[dataSourceFilters.size()] );
    }

    public void setDataSourceFilters(DataSourceFilter[] dataSourceFilters) {
        DataSourceFilter[] oldDataSourceFilters = (DataSourceFilter[]) this.dataSourceFilters.toArray( new DataSourceFilter[this.dataSourceFilters.size()] );
        this.dataSourceFilters =  new CopyOnWriteArrayList( dataSourceFilters );
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCEFILTERS, oldDataSourceFilters, dataSourceFilters);
    }

    public DataSourceFilter getDataSourceFilters(int index) {
        return this.dataSourceFilters.get(index);
    }

    public void setDataSourceFilters(int index, DataSourceFilter newDataSourceFilter) {
        DataSourceFilter oldDataSourceFilters = this.dataSourceFilters.set(index, newDataSourceFilter );
        propertyChangeSupport.fireIndexedPropertyChange(PROP_DATASOURCEFILTERS, index, oldDataSourceFilters, newDataSourceFilter);
    }

    
    public static final String PROP_PLOT_ELEMENTS = "plotElements";
    protected CopyOnWriteArrayList<PlotElement> plotElements = new CopyOnWriteArrayList();

    public PlotElement[] getPlotElements() {
        return plotElements.toArray(new PlotElement[plotElements.size()]);
    }

    public void setPlotElements(PlotElement[] pele) {
        PlotElement[] old = (PlotElement[]) this.plotElements.toArray( new PlotElement[this.plotElements.size()] );
        this.plotElements = new CopyOnWriteArrayList( pele );
        propertyChangeSupport.firePropertyChange(PROP_PLOT_ELEMENTS, old, pele);
    }

    public PlotElement getPlotElements(int index) {
        return this.plotElements.get(index);
    }

    public void setPlotElements(int index, PlotElement pele) {
        PlotElement old = this.plotElements.set(index,pele);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOT_ELEMENTS, index, old, pele);
    }
    
    
    public static final String PROP_PLOTS = "plots";
    protected CopyOnWriteArrayList<Plot> plots = new CopyOnWriteArrayList();

    public Plot[] getPlots() {
        return plots.toArray(new Plot[plots.size()]);
    }

    public void setPlots(Plot[] plots) {
        Plot[] oldPlots = this.plots.toArray(new Plot[this.plots.size()]);
        this.plots = new CopyOnWriteArrayList(plots);
        propertyChangeSupport.firePropertyChange(PROP_PLOTS, oldPlots, plots);
    }

    public Plot getPlots(int index) {
        return this.plots.get(index);
    }

    public void setPlots(int index, Plot newPlots) {
        Plot oldPlots = this.plots.set(index, newPlots);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOTS, index, oldPlots, newPlots);
    }
    
    
    public static final String PROP_CANVASES = "canvases";
    protected CopyOnWriteArrayList<Canvas> canvases = new CopyOnWriteArrayList();

    public Canvas[] getCanvases() {
        return canvases.toArray(new Canvas[canvases.size()]);
    }

    public void setCanvases(Canvas[] canvases) {
        Canvas[] old = this.canvases.toArray(new Canvas[this.canvases.size()]);
        this.canvases = new CopyOnWriteArrayList(canvases);
        propertyChangeSupport.firePropertyChange(PROP_CANVASES, old, canvases);
    }

    public Canvas getCanvases(int index) {
        return this.canvases.get(index);
    }

    public void setCanvases(int index, Canvas newCanvas ) {
        Canvas old = this.canvases.set(index, newCanvas );
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PLOTS, index, old, newCanvas );
    }
    
    
    public static final String PROP_ANNOTATIONS = "annotations";
    protected CopyOnWriteArrayList<Annotation> annotations= new CopyOnWriteArrayList();

    public Annotation[] getAnnotations() {
        return annotations.toArray(new Annotation[annotations.size()]);
    }

    public void setAnnotations(Annotation[] annotations) {
        Annotation[] oldAnnotations = this.annotations.toArray(new Annotation[this.annotations.size()]);
        this.annotations = new CopyOnWriteArrayList(annotations);
        propertyChangeSupport.firePropertyChange(PROP_ANNOTATIONS, oldAnnotations, annotations);
    }

    public Annotation getAnnotations(int index) {
        return this.annotations.get(index);
    }

    public void setAnnotations(int index, Annotation annotation) {
        Annotation old = this.annotations.set(index, annotation );
        propertyChangeSupport.fireIndexedPropertyChange(PROP_ANNOTATIONS, index, old, annotation );
    }

    
    ApplicationController controller;

    public ApplicationController getController() {
        return controller;
    }
    
    protected Options options = new Options();

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }
    
    protected DatumRange timeRange = DEFAULT_TIME_RANGE;
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
        if ( timeRange.width().value()==0 ) {
            throw new IllegalArgumentException("timeRange.width().value()==0");
        }
        DatumRange oldTimeRange = this.timeRange;
        this.timeRange = timeRange;
//        if ( timeRange.width().value()>0 && timeRange.getUnits()==oldTimeRange.getUnits() && !timeRange.equals(oldTimeRange) ) {
//            int dmin= (int)( DatumRangeUtil.normalize(timeRange,oldTimeRange.min())*10000 + 0.5 );
//            int dmax= (int)( DatumRangeUtil.normalize(timeRange,oldTimeRange.max())*10000 + 0.5 );
//            if ( dmin==0 && dmax==10000 ) {
//                logger.severe("strange ringing where events are tiny changes");
//            }
//        }
        propertyChangeSupport.firePropertyChange(PROP_TIMERANGE, oldTimeRange, timeRange);        
    }

    /**
     * URI pointing to events list, or empty String.
     */
    private String eventsListUri = "";
    public static final String PROP_EVENTSLISTURI = "eventsListUri";

    public String getEventsListUri() {
        return eventsListUri;
    }

    public void setEventsListUri(String eventsListUri) {
        String oldEventsListUri = this.eventsListUri;
        this.eventsListUri = eventsListUri;
        propertyChangeSupport.firePropertyChange(PROP_EVENTSLISTURI, oldEventsListUri, eventsListUri);
    }

    
    public static final String PROP_BINDINGS = "bindings";
    protected CopyOnWriteArrayList<BindingModel> bindings= new CopyOnWriteArrayList();

    public BindingModel[] getBindings() {
        BindingModel[] result= bindings.toArray(new BindingModel[bindings.size()]);
        return result;
    }

    public void setBindings(BindingModel[] bindings) {
        BindingModel[] oldBindings = getBindings();
        this.bindings = new CopyOnWriteArrayList(bindings);
        try {
            propertyChangeSupport.firePropertyChange(PROP_BINDINGS, oldBindings, bindings);
        } catch ( NullPointerException ex ) {
            try {
                logger.fine("strange case where script creates NullPointerException");
                Thread.sleep(100);
            } catch (InterruptedException ex1) {
                Logger.getLogger(Application.class.getName()).log(Level.SEVERE, null, ex1);
            }
            propertyChangeSupport.firePropertyChange(PROP_BINDINGS, oldBindings, bindings); // thread safety, presumably.  Kludge around this...
            logger.log(Level.WARNING, null, ex );
        }
    }
    
    public BindingModel getBindings(int index) {
        return this.bindings.get(index);
    }

    public void setBindings(int index, BindingModel newBinding) {
        BindingModel oldBinding = this.bindings.set(index, newBinding);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_BINDINGS, index, oldBinding, newBinding);
    }

    
    public static final String PROP_CONNECTORS = "connectors";
    protected CopyOnWriteArrayList<Connector> connectors= new CopyOnWriteArrayList();

    public Connector[] getConnectors() {
        Connector[] result= connectors.toArray(new Connector[connectors.size()]);
        return result;
    }

    public void setConnectors(Connector[] connectors) {
        Connector[] oldConnectors = getConnectors();
        this.connectors = new CopyOnWriteArrayList(connectors);
        propertyChangeSupport.firePropertyChange(PROP_CONNECTORS, oldConnectors, connectors);
    }
    
    public Connector getConnectors(int index) {
        return this.connectors.get(index);
    }

    public void setConnectors(int index, Connector newConnector) {
        Connector oldConnector = this.connectors.set(index, newConnector);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_CONNECTORS, index, oldConnector, newConnector);
    }
    
    
    /*****  end properties *********************/

    /**
     * return a copy of this application state.
     * @return a copy of this application state.
     */
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
        for ( int i=0; i<connectorsCopy.length; i++ ) {
            connectorsCopy[i]= (Connector)connectorsCopy[i].copy(); // this was way to hard to uncover...
        }
        result.setConnectors( connectorsCopy );

        Annotation[] annotationsCopy= this.getAnnotations(); // note this is a copy!
        for ( int i=0; i<annotationsCopy.length; i++ ) {
            annotationsCopy[i]= (Annotation)annotationsCopy[i].copy(); 
        }
        result.setAnnotations( annotationsCopy );
        
        Canvas[] canvasesCopy= this.getCanvases();
        for ( int i=0; i<canvasesCopy.length; i++ ) {
            canvasesCopy[i]= (Canvas) canvasesCopy[i].copy();
        }
        result.setCanvases( canvasesCopy );

        return result;
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList();
        result.addAll(plots);
        result.addAll(plotElements);
        result.addAll(dataSourceFilters);
        result.addAll(canvases);
        result.addAll(connectors);
        result.addAll(annotations);
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
        if ( !( n instanceof Application ) ) throw new IllegalArgumentException("node should be an Application");                                
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

        if ( !( node instanceof Application ) ) throw new IllegalArgumentException("node should be an Application");                                
        Application that = (Application) node;
        
        List<Diff> result = new ArrayList<>();

        addArrayDiffs( "dataSourceFilters", this.getDataSourceFilters(), that.getDataSourceFilters(), result );

        addArrayDiffs( "plotElements", this.getPlotElements(), that.getPlotElements(), result );

        addArrayDiffs( "plots", this.getPlots(), that.getPlots(), result );

        addArrayDiffs( "canvases", this.getCanvases(), that.getCanvases(), result );

        addArrayDiffs( "bindings", this.getBindings(), that.getBindings(), result );

        addArrayDiffs( "connectors", this.getConnectors(), that.getConnectors(), result );

        addArrayDiffs( "annotations", this.getAnnotations(), that.getAnnotations(), result );

        for ( int i=0; i<Math.min(this.dataSourceFilters.size(),that.dataSourceFilters.size()); i++ ) {
            DataSourceFilter thisDataSourceFilter= this.dataSourceFilters.get(i);
            DataSourceFilter thatDataSourceFilter= that.dataSourceFilters.get(i);
            result.addAll( DomUtil.childDiffs( "dataSourceFilters["+i+"]", thatDataSourceFilter.diffs( thisDataSourceFilter ) ) );
        }
        
        for ( int i=0; i<Math.min(this.canvases.size(),that.canvases.size()); i++ ) {
            Canvas thisCanvas= this.canvases.get(i);
            Canvas thatCanvas= that.canvases.get(i);
            result.addAll( DomUtil.childDiffs( "canvases["+i+"]", thatCanvas.diffs( thisCanvas ) ) );
        }

        for ( int i=0; i<Math.min(this.plots.size(),that.plots.size()); i++ ) {
            Plot thisPlot= this.plots.get(i);
            Plot thatPlot= that.plots.get(i);
            result.addAll( DomUtil.childDiffs( "plots["+i+"]", thatPlot.diffs( thisPlot ) ) );
        }

        for ( int i=0; i<Math.min(this.connectors.size(),that.connectors.size()); i++ ) {
            Connector thisConnector= this.connectors.get(i);
            Connector thatConnector= that.connectors.get(i);
            result.addAll( DomUtil.childDiffs( "connectors["+i+"]", thatConnector.diffs( thisConnector ) ) );
        }

        for ( int i=0; i<Math.min(this.annotations.size(),that.annotations.size()); i++ ) {
            Annotation thisAnnotation= this.annotations.get(i);
            Annotation thatAnnotation= that.annotations.get(i);
            result.addAll( DomUtil.childDiffs( "annotations["+i+"]", thatAnnotation.diffs( thisAnnotation ) ) );
        }
        
        for ( int i=0; i<Math.min(this.plotElements.size(),that.plotElements.size()); i++ ) {
            result.addAll( DomUtil.childDiffs( "plotElements["+i+"]", that.getPlotElements(i).diffs( this.plotElements.get(i) ) ) );
        }
        
        result.addAll( DomUtil.childDiffs( "options", this.getOptions().diffs(  that.getOptions()) ));

        if ( !that.timeRange.equals( this.timeRange ) ) {
            result.add( new PropertyChangeDiff( "timeRange", this.timeRange, that.timeRange ) );  //TODO: why is this backwards but it works?
        }

        if ( !that.eventsListUri.equals( this.eventsListUri ) ) {
            result.add( new PropertyChangeDiff( "eventsListUri", this.eventsListUri, that.eventsListUri ) );  //TODO: why is this backwards but it works?
        }

        return result;
    }

    /**
     * return the DomNode referenced by id.  This was introduced because
     * it is often difficult to identify the index of a plot in the plots array
     * but its id is known, and this avoids the import of DomUtil.
     * @param id an id, such as "plot_2"
     * @return the node
     * @throws IllegalArgumentException if the id is not found.
     * @see DomUtil#getElementById(org.autoplot.dom.DomNode, java.lang.String) 
     */
    public DomNode getElementById( String id ) {
        DomNode result= DomUtil.getElementById( this, id );
        if ( result==null ) {
            throw new IllegalArgumentException("unable to find node \""+id+"\"");
        } else {
            return result;
        }
    }
}
