/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.state;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar.Type;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.autoplot.dom.PlotElementStyle;
import org.autoplot.dom.Plot;

/**
 *
 * @author jbf
 */
public class PersistenceTests {

    public static void main(String[] args) throws IOException {

        PersistenceTests my= new PersistenceTests();
        //my.setEnum( Type.COLOR_WEDGE );
        //my.getPlot().setTitle( "My Title" );
        //my.getPlot().getXaxis().setLabel("My Axis");
        //my.getPlot().getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("2008-001") ); // MonthDatumRange doesn't work

        my.getStyle().setReference( Units.hertz.createDatum( 60 ) );
        my.getStyle().setPlotSymbol( DefaultPlotSymbol.STAR );

        //TODO: this doesn't really work, since the scheme ("animals") isn't preserved.
        my.setDatum( EnumerationUnits.create("animals").createDatum("squirrel" ) );

        File f = new File("enum.xml");
        StatePersistence.saveState(f, my, "");
        //StatePersistence.saveState(f, my.getEnum());

    }
    
    protected Type enumm = Type.GRAYSCALE;
    public static final String PROP_ENUM = "enum";

    public Type getEnum() {
        return enumm;
    }

    public void setEnum(Type enumm) {
        Type oldEnum = this.enumm;
        this.enumm = enumm;
        propertyChangeSupport.firePropertyChange(PROP_ENUM, oldEnum, enumm);
    }

    protected Plot plot = new Plot();
    public static final String PROP_PLOT = "plot";


    public Plot getPlot() {
        return plot;
    }


    public void setPlot(Plot plot) {
        Plot oldPlot = this.plot;
        this.plot = plot;
        propertyChangeSupport.firePropertyChange(PROP_PLOT, oldPlot, plot);
    }

    protected PlotElementStyle style = new PlotElementStyle();
    public static final String PROP_STYLE = "style";

    public PlotElementStyle getStyle() {
        return style;
    }

    public void setStyle(PlotElementStyle style) {
        PlotElementStyle oldStyle = this.style;
        this.style = style;
        propertyChangeSupport.firePropertyChange(PROP_STYLE, oldStyle, style);
    }

    protected Datum datum = null;
    public static final String PROP_DATUM = "datum";

    public Datum getDatum() {
        return datum;
    }

    public void setDatum(Datum datum) {
        Datum oldDatum = this.datum;
        this.datum = datum;
        propertyChangeSupport.firePropertyChange(PROP_DATUM, oldDatum, datum);
    }


    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
