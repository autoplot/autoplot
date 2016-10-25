/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.idlsupport;

/**
 *
 * @author jbf
 */
public class TestGetPlane {
    public static void main( String[] args ) {
        APDataSet apds  = new APDataSet();
        apds.setDataSetURI( "http://www-pw.physics.uiowa.edu/~jbf/autoplot/data/qds/joinedSpecAndStreams.qds" );
        apds.doGetDataSet();
        System.err.println( apds.toString() );
        Object o= apds.slice("ds_4",0);
        System.err.println(o);
    }
}
