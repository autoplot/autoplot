/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;

/**
 *
 * @author jbf
 */
public class Application extends DomNode {

    PropertyChangeListener childListener = new PropertyChangeListener() {
        public String toString() {
           return ""+Application.this;
        }
        public void propertyChange(PropertyChangeEvent evt) {
            Application.this.propertyChangeSupport.firePropertyChange(promoteChild(evt));
        }
    };

    public Application() {
        options.addPropertyChangeListener(childListener);
    }

    private PropertyChangeEvent promoteChild(PropertyChangeEvent ev) {
        String childName;
        final Object source = ev.getSource();
        if (Application.this.panels.contains(source)) {
            childName = "panels[" + panels.indexOf(source) + "]";
        } else if (source == options) {
            childName = "options";
        } else if (plots.contains(source)) { //TODO: change plots to array from list.
            childName = "plots[" + plots.indexOf(source) + "]";
        } else if ( dataSourceFilters.contains(source)) {
            childName = "dataSourceFilters[" + dataSourceFilters.indexOf(source) + "]";
        } else if ( canvases.contains(source) ) {
            childName = "canvases["+canvases.indexOf(source)+"]";
        } else {
            throw new IllegalArgumentException("child not found: "+source);
        }
        return new PropertyChangeEvent(this, childName + "." + ev.getPropertyName(), ev.getOldValue(), ev.getNewValue());
    }
    
    protected List<DataSourceFilter> dataSourceFilters= Arrays.asList( new DataSourceFilter[0] );
    public static final String PROP_DATASOURCEFILTERS = "dataSourceFilters";

    public DataSourceFilter[] getDataSourceFilters() {
        return (DataSourceFilter[]) dataSourceFilters.toArray();
    }

    public void setDataSourceFilters(DataSourceFilter[] dataSourceFilters) {
        DataSourceFilter[] oldDataSourceFilters = (DataSourceFilter[]) this.dataSourceFilters.toArray();
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

    protected DatumRange timeRange = DatumRangeUtil.parseTimeRangeValid("2008-11-26");
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


    protected BindingModel[] bindings= new BindingModel[0];
    public static final String PROP_BINDINGS = "bindings";

    public BindingModel[] getBindings() {
        return bindings;
    }

    public void setBindings(BindingModel[] bindings) {
        BindingModel[] oldBindings = this.bindings;
        this.bindings = bindings;
        propertyChangeSupport.firePropertyChange(PROP_BINDINGS, oldBindings, bindings);
    }
    
    public void setBindings( List<BindingModel> bindings ) {
        setBindings( bindings.toArray(new BindingModel[bindings.size()] ) );
    }

    public BindingModel getBindings(int index) {
        return this.bindings[index];
    }

    public void setBindings(int index, BindingModel newBindings) {
        BindingModel oldBindings = this.bindings[index];
        this.bindings[index] = newBindings;
        propertyChangeSupport.fireIndexedPropertyChange(PROP_BINDINGS, index, oldBindings, newBindings);
    }

    protected Connector[] connectors= new Connector[0];
    public static final String PROP_CONNECTORS = "connectors";

    public Connector[] getConnectors() {
        return connectors;
    }

    public void setConnectors(Connector[] connectors) {
        Connector[] oldConnectors = this.connectors;
        this.connectors = connectors;
        propertyChangeSupport.firePropertyChange(PROP_CONNECTORS, oldConnectors, connectors);
    }

    /**
     * convenient list setter.
     * @param connectors
     */
    protected void setConnectors( List<Connector> connectors ) {
        setConnectors( connectors.toArray(new Connector[connectors.size()] ) );
    }
    
    public Connector getConnectors(int index) {
        return this.connectors[index];
    }

    public void setConnectors(int index, Connector newConnectors) {
        Connector oldConnectors = this.connectors[index];
        this.connectors[index] = newConnectors;
        propertyChangeSupport.fireIndexedPropertyChange(PROP_CONNECTORS, index, oldConnectors, newConnectors);
    }
    
    
    /*****  end properties *********************/


    public boolean equals(Object o) {
        return super.equals(o); // use me to check for failed copy.  A node should never be compared to itsself.
    }

    public DomNode copy() {
        Application result = (Application) super.copy();
        result.controller= null;
        
        result.options = (Options) this.getOptions().copy();
        
        Panel[] panelsCopy= this.getPanels();
        for ( int i=0; i<panelsCopy.length; i++ ) {
            panelsCopy[i]= (Panel) panelsCopy[i].copy();
        }
        result.setPanels( panelsCopy );
        
        Plot[] plotsCopy= this.getPlots();
        for ( int i=0; i<plotsCopy.length; i++ ) {
            plotsCopy[i]= (Plot) plotsCopy[i].copy();
        }
        result.setPlots( plotsCopy );

        Connector[] connectorsCopy= this.getConnectors();
        for ( int i=0; i<connectorsCopy.length; i++ ) {
            connectorsCopy[i]=  connectorsCopy[i]; // connectors are really immutable.
        }
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
        result.addAll(panels);
        result.addAll(dataSourceFilters);
        result.addAll(canvases);
        result.add(options);
        
        return result;
    }
    
    public void syncTo(DomNode n) {
        super.syncTo(n);
        if ( this.controller!=null ) {
            this.controller.syncTo( (Application)n );
        }
        
                
    }

    private void addArrayDiffs( String property, Object[] thata, Object[] thisa, List<Diff> result ) {
        try {
        if ( thata.length > thisa.length ) {
            for ( int i=thata.length-1; i>=thisa.length; i-- ) {
                result.add( new DeleteNodeDiff( property, thata[i], i ) );
            }
         }

        if ( thata.length < thisa.length ) {
            for ( int i=thisa.length-1; i<thisa.length; i++ ) {
                result.add( new InsertNodeDiff( property, thisa[i], i ) );
            }        
        }
        } catch ( ArrayIndexOutOfBoundsException ex ) {
            throw ex;
        }
    }
            
    public List<Diff> diffs(DomNode node) {

        Application that = (Application) node;
        
        List<Diff> result = new ArrayList<Diff>();
        
        addArrayDiffs( "panels", that.getPanels(), this.getPanels(), result );

        addArrayDiffs( "plots", that.getPlots(), this.getPlots(), result );

        addArrayDiffs( "bindings", that.getBindings(), this.getBindings(), result );

        addArrayDiffs( "connectors", that.getConnectors(), this.getConnectors(), result );
        
        for ( int i=0; i<Math.min(this.plots.size(),that.plots.size()); i++ ) {
            Plot thisPlot= this.plots.get(i);
            Plot thatPlot= that.plots.get(i);
            result.addAll( DomUtil.childDiffs( "plots["+i+"]", thatPlot.diffs( thisPlot ) ) );
        }

        for ( int i=0; i<Math.min(this.panels.size(),that.panels.size()); i++ ) {
            result.addAll( DomUtil.childDiffs( "panels["+i+"]", that.getPanels(i).diffs( this.panels.get(i) ) ) );
        }
        
        result.addAll( DomUtil.childDiffs( "options", this.getOptions().diffs(  that.getOptions()) ));
        
        return result;
    }

}
