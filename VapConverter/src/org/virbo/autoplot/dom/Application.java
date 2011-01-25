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
import org.das2.datum.Units;

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

    
    public static final String PROP_PANELS = "panels";
    protected List<Panel> panels = new LinkedList<Panel>();

    public Panel[] getPanels() {
        return panels.toArray(new Panel[panels.size()]);
    }

    public void setPanels(Panel[] panels) {
        Panel[] oldPanels = this.panels.toArray(new Panel[this.panels.size()]);
        this.panels = Arrays.asList(panels);
        propertyChangeSupport.firePropertyChange(PROP_PANELS, oldPanels, panels);
    }

    public Panel getPanels(int index) {
        return this.panels.get(index);
    }

    public void setPanels(int index, Panel newPanels) {
        Panel oldPanels = this.panels.get(index);
        this.panels.set(index, newPanels);
        propertyChangeSupport.fireIndexedPropertyChange(PROP_PANELS, index, oldPanels, newPanels);
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
    
    /*** end properties *****************************/
    protected Options options = new Options();

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    protected DatumRange timeRange = new DatumRange( 0, 100, Units.dimensionless );
    /**
     * all time axes should hang off of this.
     */
    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(DatumRange timeRange) {
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
    

}
