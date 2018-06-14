/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

/**
 * Test the IDL/Matlab interface
 * @author jbf
 */
public class TestAPDataSet {
    public static void main( String[] args ) {
        org.autoplot.idlsupport.APDataSet apds= new org.autoplot.idlsupport.APDataSet();
        if ( 0!=apds.loadDataSet("vap+cdaweb:ds=WI_K0_WAV&id=E_Average&timerange=2018-06-09") ) {
            throw new RuntimeException(apds.getStatusMessage());
        }
        float[][] ff= (float[][]) apds.values( "E_Average" );
        System.err.println(ff[1][2]);
    }
}
