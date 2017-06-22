/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.imagedatasource;

/**
 *
 * @author jbf
 */
public class Util {
    /**
     * returns 45.50833333333333 from 45deg30'30", where the text "deg" is
     *    a placeholder for the unicode degree symbol (its not replicated
     *    here since non-ASCII characters choke some development tools)
     * 
     * @param s a GPS coordinate with degrees minutes and seconds 
     * @return decimal version of string
     */
    public static double parseGPSString( String s ) {
        double[] d= new double[3];
        int i=0;
        int oldi= i;
        i= s.indexOf("\u00b0"); // degree symbol
        if ( i>-1 ) {
            d[0]= Double.parseDouble( s.substring(oldi,i) );
            oldi=i+1;
        }
        i= s.indexOf("'"); // '
        if ( i>-1 ) {
            d[1]= Double.parseDouble( s.substring(oldi,i) );
            oldi=i+1;
        }
        i= s.indexOf("\""); // "
        if ( i>-1 ) {
            d[2]= Double.parseDouble( s.substring(oldi,i) );
        }

        return d[0] + d[1]/60 + d[2]/(60*60);
    }
}
