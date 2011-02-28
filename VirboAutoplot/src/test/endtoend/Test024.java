/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.util.Arrays;

/**
 * Tests of the IDL/Matlab interface.
 * @author jbf
 */
public class Test024 {
    public static void main( String[] args )  {
        try {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURL("vap:file:///home/jbf/ct/hudson/data.backup/xls/hourlyForecast.xls?column=Temperature_F&depend0=Rel_Humidity_");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURL("vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?dataset=das2_1/cassini/cassiniLrfc&key=33696757&start_time=2010-01-11T11:15:00.000Z&end_time=2010-01-11T21:45:00.000Z&-lfdr+ExEw+-mfdr+ExEw+-mfr+13ExEw+-hfr+ABC12EuEvEx+-n+hfr_snd+-n+lp_rswp+-n+bad_data+-n+dpf_zero+-n+mfdr_mfr2+-n+mfr3_hfra+-n+hf1_hfrc+-a+-b+30+-bgday=" );
        
        try {
            apds.doGetDataSet();
            System.err.println( apds.toString() );

            Object o;
            o= apds.slice("ds_1", 0 );
            double[] d= (double[])o;
            System.err.println( String.format( "apds.slice(%s,0)=double[%d]", "ds_1", d.length ) );

            o= apds.slice("ds_2", 0 );
            d= (double[])o;
            System.err.println( String.format( "apds.slice(%s,0)=double[%d]", "ds_2", d.length ) );

            System.err.println( String.format("apds.rank()=%d", apds.rank() ) );
            System.err.println( String.format("apds.length()=%d", apds.length() ) );

            System.err.print( "apds.lengths(0)=" );
            int[] lens= apds.lengths(0);
            for ( int i:lens ) System.err.print ( i + "," );
            System.err.println("");
            
        } catch ( Exception ex ) {  // we're getting a headless exception because it can't query for user/password
            ex.printStackTrace();
        }

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
