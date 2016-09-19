/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapiserver;

/**
 *
 * @author jbf
 */
public class Util {
    public static String getDurationForHumans( long dt ) {
        if ( dt<2*1000 ) {
            return dt+" ms";
        } else if ( dt<2*60000 ) {
            return String.format("%.1f",dt/1000.)+"s";
        } else if ( dt<2*3600000 ) {
            return String.format("%.1f",dt/60000.)+" min";
        } else if ( dt<2*86400000 ) {
            return String.format("%.1f",dt/36000000.)+" hr";
        } else {
            return String.format("%.1f",dt/86400000.)+" days";
        }
    }
}
