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
        apds.setDataSetURI( "/home/jbf/project/das2/qstream/joinedSpecAndStreams.qds" );
        apds.doGetDataSet();
        System.err.println( apds.toString() );
        Object o= apds.slice("ds_2",0);
        System.err.println(o);
        // How to get PLANE_0 of a join?
    }
}
