/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import org.virbo.datasource.DataSourceUtil;

/**
 *
 * @author jbf
 */
public class TestMakeAggregation {
    public static void main( String[] args ) {
        String s,t;
        
//        t= "file:///data/2014/data.2014_041.dat";
//        s= DataSourceUtil.makeAggregation(t);
//        System.err.println(s); // logger okay

        t= "ftp://papco@mrfrench.lanl.gov/lanl/geo/cdf/l3_mpa/1990-095/1993/lanl_1990-095_l3_mpa_19930210_v01.cdf";
        s= DataSourceUtil.makeAggregation(t);
        System.err.println(s); // logger okay


        t= "file:///data/1990-010/2014/data.2014_041.dat";
        s= DataSourceUtil.makeAggregation(t);
        System.err.println(s); // logger okay

        //file:/tmp/20091102.dat -> file:/tmp/$Y$m$d.dat?timerange=20091102
    }
}
