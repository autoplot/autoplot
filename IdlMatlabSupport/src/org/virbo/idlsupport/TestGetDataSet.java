/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.idlsupport;

import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class TestGetDataSet extends QDataSetBridge {
    
    @Override
    QDataSet getDataSet( ProgressMonitor mon ) {
        System.err.println("enter get dataset");
        MutablePropertyDataSet ds= (MutablePropertyDataSet) Ops.findgen(25);
        ds.putProperty( QDataSet.NAME, "Data" );
        System.err.println("enter get dataset");
        MutablePropertyDataSet dep0= (MutablePropertyDataSet) Ops.linspace( 0, 1, 25 );
        dep0.putProperty( QDataSet.NAME, "Time" );
        ds.putProperty( QDataSet.DEPEND_0, dep0 );
        System.err.println("enter get dataset");
        return ds;
    }

    public static void main( String[] args ) {
        TestGetDataSet qds= new TestGetDataSet();
        qds.doGetDataSet( new NullProgressMonitor() );
        
        String n= qds.name();
        
        System.err.println(n);
        
    }
}
