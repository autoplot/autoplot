/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.beans.binding.BindingContext;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasPlot;

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
            throw new IllegalArgumentException("child not found");
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
    /**
     * Holds value of property showContextOverview.
     */
    private boolean showContextOverview;

    public boolean isShowContextOverview() {
        return this.showContextOverview;
    }

    public void setShowContextOverview(boolean showContextOverview) {
        //boolean oldShowContextOverview = this.showContextOverview;
        this.showContextOverview = showContextOverview;
    //propertyChangeSupport.firePropertyChange("showContextOverview", new Boolean(oldShowContextOverview), new Boolean(showContextOverview));
    }
    private boolean autoOverview = true;
    public static final String PROP_AUTOOVERVIEW = "autoOverview";

    public boolean isAutoOverview() {
        return this.autoOverview;
    }

    public void setAutoOverview(boolean newautoOverview) {
        //boolean oldautoOverview = autoOverview;
        this.autoOverview = newautoOverview;
    //propertyChangeSupport.firePropertyChange(PROP_AUTOOVERVIEW, oldautoOverview, newautoOverview);
    }
    private boolean autoranging = true;
    public static final String PROP_AUTORANGING = "autoranging";

    public boolean isAutoranging() {
        return this.autoranging;
    }

    public void setAutoranging(boolean newautoranging) {
        boolean oldautoranging = autoranging;
        this.autoranging = newautoranging;
        propertyChangeSupport.firePropertyChange(PROP_AUTORANGING, oldautoranging, newautoranging);
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

    public BindingModel getBindings(int index) {
        return this.bindings[index];
    }

    public void setBindings(int index, BindingModel newBindings) {
        BindingModel oldBindings = this.bindings[index];
        this.bindings[index] = newBindings;
        propertyChangeSupport.fireIndexedPropertyChange(PROP_BINDINGS, index, oldBindings, newBindings);
    }

    /*****  end properties *********************/
    BindingContext canvasBindingContext = null;

    public void bindTo(DasCanvas canvas) {
        if (canvasBindingContext != null) canvasBindingContext.unbind();
        BindingContext bc = new BindingContext();
        bc.addBinding(this, "${options.background}", canvas, "background");
        bc.addBinding(this, "${options.foreground}", canvas, "foreground");
        bc.addBinding(this, "${options.canvasFont}", canvas, "font");
        bc.bind();
        canvasBindingContext = bc;
    }
    BindingContext plotBindingContext = null;

    public void bindTo(DasPlot plot) {
        if (plotBindingContext != null) plotBindingContext.unbind();
        BindingContext bc = new BindingContext();
        bc.addBinding(this, "${plot.title}", plot, "title");
        bc.addBinding(this, "${plot.xaxis.label}", plot, "XAxis.label");
        bc.addBinding(this, "${plot.yaxis.label}", plot, "YAxis.label");
        bc.addBinding(this, "${plot.zaxis.label}", plot, "ZAxis.label");
        bc.addBinding(this, "${options.drawGrid}", plot, "drawGrid");
        bc.addBinding(this, "${options.drawMinorGrid}", plot, "drawMinorGrid");
        bc.bind();
    }

    public boolean equals(Object o) {
        return super.equals(o); // use me to check for failed copy.  A node should never be compared to itsself.
    }

    public DomNode copy() {
        Application result = (Application) super.copy();
        result.controller= null;
        result.options = (Options) this.getOptions().copy();
        result.setPanels(Arrays.copyOf(this.getPanels(), this.getPanels().length));
        result.setPlots(Arrays.copyOf(this.getPlots(), this.getPlots().length));
        int i = this.panels.indexOf(panel);
        result.panel = result.getPanels(i);
        i = this.plots.indexOf(this.plot);
        result.plot = result.getPlots(i);
        assert (result.plot == result.getPlots(i));
        result.canvas = (Canvas) this.getCanvas().copy();
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
            controller.deletePlot(this.plots.get(this.plots.size() - 1), true);
        }
        for (int i = 0; i < plots.length; i++) {
            this.plots.get(i).syncTo(plots[i]);
        }

        while (this.panels.size() < panels.length) {
            int i = this.panels.size();
            String id = panels[i].getPlotId();
            Plot p = null;
            for (int j = 0; j < plots.length; j++) {
                if (plots[j].getId().equals(id)) p = plots[j];
            }
            controller.addPanel(p);
        }
        while (this.panels.size() > panels.length) {
            controller.deletePanel(this.panels.get(this.panels.size() - 1));
        }

        for (int i = 0; i < panels.length; i++) {
            this.panels.get(i).syncTo(panels[i]);
        }


    }

    public void syncTo(DomNode n) {
        Application that = (Application) n;
        this.setAutoOverview(that.isAutoOverview());
        this.setAutolabelling(that.isAutolabelling());
        this.setAutolayout(that.isAutolayout());
        this.setAutoranging(that.isAutoranging());
        this.getCanvas().syncTo(that.getCanvas());
        this.getOptions().syncTo(that.getOptions());
        syncToPlotsAndPanels(that.getPlots(), that.getPanels());

        int i = that.panels.indexOf(that.panel);
        this.setPanel( this.getPanels(i) );
        i = that.plots.indexOf(that.plot);
        this.setPlot( this.getPlots(i) );
        
    }

    public Map<String, String> diffs(DomNode node) {

        Application that = (Application) node;
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();

        boolean b;

        //b= that.options.canvasFont.equals( this.options.canvasFont );
        //if ( !b ) result.put(", font " + that.options.canvasFont+ " to " +( this.options.canvasFont ));

        Map<String, String> diffs1 = this.getPanel().diffs(that.getPanel());
        for (String k : diffs1.keySet()) {
            result.put("panel." + k, diffs1.get(k));
        }

        if ( that.getPanels().length > this.getPanels().length ) {
            result.put( "panels", "inserted "+(that.getPanels().length - this.getPanels().length) );
        }

        if ( that.getPanels().length < this.getPanels().length ) {
            result.put( "panels", "removed "+ ( -1* (that.getPanels().length - this.getPanels().length) ) );
        }
        
        diffs1 = this.getPlot().diffs(that.getPlot());
        for (String k : diffs1.keySet()) {
            result.put("plot." + k, diffs1.get(k));
        }

        if ( that.getPlots().length > this.getPlots().length ) {
            result.put( "panels", "inserted "+(that.getPanels().length - this.getPanels().length) );
        }

        if ( that.getPlots().length < this.getPlots().length ) {
            result.put( "plots", "removed "+ ( -1* (that.getPlots().length - this.getPlots().length) ) );
        }
        
        diffs1 = this.getCanvas().diffs(that.getCanvas());
        for (String k : diffs1.keySet()) {
            result.put("canvas." + k, diffs1.get(k));
        }

        Map<String, String> diffs2 = this.getOptions().diffs(that.getOptions());
        for (String k : diffs2.keySet()) {
            result.put("options." + k, diffs2.get(k));
        }

        return result;
    }
}
