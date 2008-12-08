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
        if (source == panel) {
            childName = "panel";
        } else if (Application.this.panels.contains(source)) {
            childName = "panels[" + panels.indexOf(source) + "]";
        } else if (source == options) {
            childName = "options";
        } else if (source == plot) {
            childName = "plot";
        } else if (plots.contains(source)) {
            childName = "plots[" + plots.indexOf(source) + "]";
        } else if (source == canvas) {
            childName = "canvas";
        } else {
            throw new IllegalArgumentException("child not found: "+source);
        }
        return new PropertyChangeEvent(this, childName + "." + ev.getPropertyName(), ev.getOldValue(), ev.getNewValue());
    }
    protected Panel panel;
    public static final String PROP_PANEL = "panel";

    public Panel getPanel() {
        return panel;
    }

    public void setPanel(Panel panel) {
        Panel oldPanel = this.panel;
        this.panel = panel;
        propertyChangeSupport.firePropertyChange(PROP_PANEL, oldPanel, panel);
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
        this.panels.set(index, panel);
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
    protected Canvas canvas = new Canvas();
    public static final String PROP_CANVAS = "canvas";

    public Canvas getCanvas() {
        return canvas;
    }

    public void setCanvas(Canvas canvas) {
        Canvas oldCanvas = this.canvas;
        this.canvas = canvas;
        propertyChangeSupport.firePropertyChange(PROP_CANVAS, oldCanvas, canvas);
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
    //TODO: fire
    }
    protected Plot plot;
    public static final String PROP_PLOT = "plot";

    public Plot getPlot() {
        return plot;
    }

    public void setPlot(Plot plot) {
        Plot oldPlot = this.plot;
        this.plot = plot;
        propertyChangeSupport.firePropertyChange(PROP_PLOT, oldPlot, plot);
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
        result.canvas = (Canvas) this.getCanvas().copy();
        
        Panel[] panelsCopy= this.getPanels();
        for ( int i=0; i<panelsCopy.length; i++ ) {
            panelsCopy[i]= (Panel) panelsCopy[i].copy();
        }
        result.setPanels( panelsCopy );
        
        Plot[] plotsCopy= this.getPlots();
        for ( int i=0; i<panelsCopy.length; i++ ) {
            plotsCopy[i]= (Plot) plotsCopy[i].copy();
        }
        result.setPlots( plotsCopy );
        
        if ( panel!=null ) {
            int i = this.panels.indexOf(panel);
            result.panel = result.getPanels(i);
        } else {
            result.panel= null;
        }
        if ( plot!=null ) {
            int i = this.plots.indexOf(this.plot);
            result.plot = result.getPlots(i);
        } else {
            result.plot = null;
        }
        return result;
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList<DomNode>();
        result.addAll(plots);
        result.addAll(panels);
        result.add(canvas);
        result.add(options);
        
        return result;
    }

    private void syncToPlotsAndPanels(Plot[] plots, Panel[] panels) {
        while (this.plots.size() < plots.length) {
            controller.addPlot();
        }
        while (this.plots.size() > plots.length) {
            Plot plott= this.plots.get(this.plots.size() - 1);
            List<Panel> panelss= controller.getPanelsFor(plott);
            for ( Panel panell:panelss ) {
                panell.setPlotId(""); // make it an orphan
            }
            controller.deletePlot(plot);
        }
        for (int i = 0; i < plots.length; i++) {
            this.plots.get(i).syncTo(plots[i]);
        }

        while (this.panels.size() < panels.length) {
            int i = this.panels.size();
            String idd = panels[i].getPlotId();
            Plot p = null;
            for (int j = 0; j < plots.length; j++) {
                if (plots[j].getId().equals(idd)) p = plots[j];
            }
            controller.addPanel(p);
        }
        while (this.panels.size() > panels.length) {
            controller.deletePanel( this.panels.get(this.panels.size() - 1));
        }

        for (int i = 0; i < panels.length; i++) {
            this.panels.get(i).syncTo(panels[i]);
        }


    }

    private void syncConnectors( Connector[] connectors ) {
        List<Connector> addConnectors= new ArrayList<Connector>();
        List<Connector> deleteConnectors= new ArrayList<Connector>();
        
        List<Connector> thisConnectors= Arrays.asList(this.connectors);
        List<Connector> thatConnectors= Arrays.asList(connectors);
        
        for ( Connector c: thatConnectors ) {
            if ( !thisConnectors.contains(c) ) addConnectors.add(c);
        }
        
        for ( Connector c: this.connectors ) {
            if ( thatConnectors.contains(c) ) deleteConnectors.add(c);
        }
        
        for ( Connector c:addConnectors ) {
            Plot plotA= (Plot)DomUtil.getElementById(this, c.plotA );
            Plot plotB= (Plot)DomUtil.getElementById(this, c.plotB) ;
            controller.addConnector( plotA, plotB );
        }
        
        for ( Connector c:deleteConnectors ) {
            controller.deleteConnector( c );
        }

    }
    

    private void syncBindings( BindingModel[] bindings ) {
        
        List<BindingModel> addBindings= new ArrayList<BindingModel>();
        List<BindingModel> deleteBindings= new ArrayList<BindingModel>();
        
        List<BindingModel> thisBindings= Arrays.asList(this.bindings);
        List<BindingModel> thatBindings= Arrays.asList(bindings);
        
        for ( BindingModel c: thatBindings ) {
            if ( !thisBindings.contains(c) ) addBindings.add(c);
        }
        
        for ( BindingModel c: this.bindings ) {
            if ( thatBindings.contains(c) ) deleteBindings.add(c);
        }
        
        for ( BindingModel c:addBindings ) {
            DomNode src= DomUtil.getElementById(this,c.srcId);
            DomNode dst= DomUtil.getElementById(this,c.dstId);
            controller.bind( src, c.srcProperty, dst, c.dstProperty  );
        }
        
        for ( BindingModel c:deleteBindings ) {
            DomNode src= DomUtil.getElementById(this,c.srcId);
            DomNode dst= DomUtil.getElementById(this,c.dstId);
            controller.deleteBinding( controller.findBinding( src, c.srcProperty, dst, c.dstProperty  ) );
        }

    }
    
    public void syncTo(DomNode n) {
        Application that = (Application) n;
        this.getCanvas().syncTo(that.getCanvas());
        this.getOptions().syncTo(that.getOptions());

        syncToPlotsAndPanels(that.getPlots(), that.getPanels());

        syncBindings(that.getBindings());
        syncConnectors(that.getConnectors());
        
        int i = that.panels.indexOf(that.panel);
        this.setPanel( this.getPanels(i) );
        i = that.plots.indexOf(that.plot);
        this.setPlot( this.getPlots(i) );
        
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
        
        boolean b;

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
        
        result.addAll( DomUtil.childDiffs( "canvas",  this.getCanvas().diffs(that.getCanvas()) ));
        
        result.addAll( DomUtil.childDiffs( "options", this.getOptions().diffs(  that.getOptions()) ));
        
        return result;
    }
}
