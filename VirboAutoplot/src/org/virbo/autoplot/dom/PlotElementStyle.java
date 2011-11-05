/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.PlotSymbol;
import org.das2.graph.PsymConnector;
import org.das2.graph.SpectrogramRenderer.RebinnerEnum;

/**
 *
 * @author jbf
 */
public class PlotElementStyle extends DomNode {

    public PlotElementStyle() {
        
    }
    
    private double symbolSize= 1.0;
    public final static String PROP_FILL_TO_REFERENCE= "fillToReference";
    public final static String PROP_SYMBOL_SIZE= "symbolSize";
    
    
    public double getSymbolSize() {
        return this.symbolSize;
    }
    
    public void setSymbolSize(double symbolSize) {
        Object oldVal= this.symbolSize;
        this.symbolSize = symbolSize;
        propertyChangeSupport.firePropertyChange(PROP_SYMBOL_SIZE, oldVal, symbolSize );
    }

    public final static String PROP_LINE_WIDTH= "lineWidth";
    
    private double lineWidth= 1.0;

    public double getLineWidth() {
        return this.lineWidth;
    }
    
    public void setLineWidth(double lineWidth) {
        Object oldVal= this.lineWidth;
        this.lineWidth = lineWidth;
        propertyChangeSupport.firePropertyChange( PROP_LINE_WIDTH, oldVal, lineWidth );
    }

    
    protected Color color = Color.BLACK;
    public static final String PROP_COLOR = "color";

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        Color oldColor = this.color;
        this.color = color;
        propertyChangeSupport.firePropertyChange(PROP_COLOR, oldColor, color);
    }

    protected Color fillColor = Color.GRAY;
    public static final String PROP_FILLCOLOR = "fillColor";

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        Color oldFillColor = this.fillColor;
        this.fillColor = fillColor;
        propertyChangeSupport.firePropertyChange(PROP_FILLCOLOR, oldFillColor, fillColor);
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

    public final static String PROP_PLOT_SYMBOL= "plotSymbol";
    
    private PlotSymbol plotSymbol= DefaultPlotSymbol.CIRCLES;

    public PlotSymbol getPlotSymbol() {
        return this.plotSymbol;
    }
    

    public void setPlotSymbol(PlotSymbol plotSymbol) {
        Object oldVal= this.plotSymbol;
        this.plotSymbol = plotSymbol;
        propertyChangeSupport.firePropertyChange(PROP_PLOT_SYMBOL, oldVal, plotSymbol );
    }
    
    public final static String PROP_SYMBOL_CONNECTOR= "symbolConnector" ;
    
    private PsymConnector symbolConnector= PsymConnector.NONE;
    
    public PsymConnector getSymbolConnector() {
        return this.symbolConnector;
    }
    
    public void setSymbolConnector(PsymConnector symbolConnector) {
        Object oldVal= this.symbolConnector;
        this.symbolConnector = symbolConnector;
        propertyChangeSupport.firePropertyChange(PROP_SYMBOL_CONNECTOR, oldVal, symbolConnector );
    }
    
    
    public final static String PROP_REFERENCE= "reference";
    
    private Datum reference= Units.dimensionless.createDatum(0);
    
    public Datum getReference() {
        return this.reference;
    }
    
    public void setReference(Datum reference) {
        Object oldVal= this.reference;
        this.reference = reference;
        propertyChangeSupport.firePropertyChange(PROP_REFERENCE, oldVal, reference );
    }
    
    private boolean fillToReference= false;
    
    public boolean isFillToReference() {
        return this.fillToReference;
    }
    
    public void setFillToReference(boolean fillToReference) {
        boolean oldVal= this.fillToReference;
        this.fillToReference = fillToReference;
        propertyChangeSupport.firePropertyChange( PROP_FILL_TO_REFERENCE, oldVal, fillToReference );
    }

    
    protected RebinnerEnum rebinMethod = RebinnerEnum.binAverage;
    public static final String PROP_REBINMETHOD = "rebinMethod";

    public RebinnerEnum getRebinMethod() {
        return rebinMethod;
    }

    public void setRebinMethod(RebinnerEnum rebinMethod) {
        RebinnerEnum oldRebinMethod = this.rebinMethod;
        this.rebinMethod = rebinMethod;
        propertyChangeSupport.firePropertyChange(PROP_REBINMETHOD, oldRebinMethod, rebinMethod);
    }

    
    protected boolean antiAliased = true;
    public static final String PROP_ANTIALIASED = "antiAliased";

    public boolean isAntiAliased() {
        return antiAliased;
    }

    public void setAntiAliased(boolean antiAliased) {
        boolean oldAntiAliased = this.antiAliased;
        this.antiAliased = antiAliased;
        propertyChangeSupport.firePropertyChange(PROP_ANTIALIASED, oldAntiAliased, antiAliased);
    }
    
    /*  DomNode Stuff ******************/

    @Override
    public void syncTo( DomNode node ) {
        syncTo(node,new ArrayList<String>());
    }

    @Override
    public void syncTo( DomNode node, List<String> exclude ) {
        super.syncTo(node,exclude);
        PlotElementStyle that= ( PlotElementStyle )node;
        if ( !exclude.contains(PROP_COLORTABLE ) ) this.setColortable( that.colortable );
        if ( !exclude.contains(PROP_FILL_TO_REFERENCE ) )this.setFillToReference( that.fillToReference );
        if ( !exclude.contains(PROP_COLOR ) )this.setColor( that.getColor() );
        if ( !exclude.contains(PROP_FILLCOLOR ) ) this.setFillColor( that.getFillColor() );
        if ( !exclude.contains(PROP_REFERENCE ) )this.setReference( that.getReference() );
        if ( !exclude.contains(PROP_LINE_WIDTH ) )this.setLineWidth( that.getLineWidth() );
        if ( !exclude.contains(PROP_PLOT_SYMBOL ) )this.setPlotSymbol( that.getPlotSymbol() );
        if ( !exclude.contains(PROP_SYMBOL_SIZE ) )this.setSymbolSize( that.getSymbolSize() );
        if ( !exclude.contains(PROP_SYMBOL_CONNECTOR ) )this.setSymbolConnector( that.getSymbolConnector() );
        if ( !exclude.contains(PROP_REBINMETHOD ) ) this.setRebinMethod(that.getRebinMethod());
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        PlotElementStyle that= (PlotElementStyle)node;

        List<Diff> result = super.diffs(node);

        boolean b;
        b=  that.color.equals(this.color) ;
        if ( !b ) result.add( new PropertyChangeDiff( "color", that.color , this.color )) ;
        b=  that.fillColor.equals(this.fillColor) ;
        if ( !b ) result.add( new PropertyChangeDiff( "fillColor", that.fillColor , this.fillColor ) );
        
        b=  that.colortable.equals(this.colortable) ;
        if ( !b ) result.add( new PropertyChangeDiff( "colortable", that.colortable , this.colortable ) );
        b=  that.lineWidth==this.lineWidth ;
        if ( !b ) result.add( new PropertyChangeDiff( "lineWidth", that.lineWidth,this.lineWidth) );
        b=  that.symbolSize==this.symbolSize ;
        if ( !b ) result.add( new PropertyChangeDiff( "symbolSize", that.symbolSize,this.symbolSize) );

        b= that.plotSymbol.equals( this.plotSymbol ) ;
        if ( !b ) result.add( new PropertyChangeDiff( "plotSymbol", that.plotSymbol, this.plotSymbol ));
        b= that.symbolConnector.equals( this.symbolConnector ) ;
        if ( !b ) result.add( new PropertyChangeDiff( "symbolConnector", that.symbolConnector, this.symbolConnector ));

        b= that.fillToReference==this.fillToReference;
        if ( !b ) result.add( new PropertyChangeDiff( "fillToReference", that.fillToReference, this.fillToReference ));

        b= that.rebinMethod==this.rebinMethod;
        if ( !b ) result.add( new PropertyChangeDiff( "rebinMethod", that.rebinMethod, this.rebinMethod ) );

        b= that.reference.equals( this.reference );
        if ( !b ) result.add( new PropertyChangeDiff( "reference",  that.reference, this.reference ));

        return result;
    }

    
}
