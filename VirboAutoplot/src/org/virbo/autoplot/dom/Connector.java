/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

/**
 * Two plots are joined together to connect axes.  
 * The class is intended to be immutable, but because XMLDecoder is used it must be mutable.
 * @author jbf
 */
public class Connector  {
    
    public Connector( String plotA, String plotB ) {
        this.plotA= plotA;
        this.plotB= plotB;
    }
    
    protected String plotA = null;

    public String getPlotA() {
        return plotA;
    }

    protected String plotB;

    public String getPlotB() {
        return plotB;
    }


    @Override
    public boolean equals(Object obj) {
        if ( obj==null || !(obj instanceof Connector) ) return false;
        Connector that= (Connector)obj;
        return that.plotA.equals(this.plotA) && that.plotB.equals(this.plotB);
    }

    @Override
    public int hashCode() {
        return plotA.hashCode() + plotB.hashCode();
    }

    @Override
    public String toString() {
        return plotA + " to " + plotB;
    }
    
    
}
