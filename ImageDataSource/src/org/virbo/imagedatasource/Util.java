/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.imagedatasource;

/**
 *
 * @author jbf
 */
public class Util {
    /**
     * returns 45.50833333333333 from 45°30'30"
     * @param s a GPS coordinate with degrees minutes and seconds 
     * @return decimal version of string
     */
    public static double parseGPSString( String s ) {
        double[] d= new double[3];
        int i=0;
        int oldi= i;
        i= s.indexOf("°"); // degree symbol
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
            oldi=i+1;
        }

        return d[0] + d[1]/60 + d[2]/(60*60);
    }
}
