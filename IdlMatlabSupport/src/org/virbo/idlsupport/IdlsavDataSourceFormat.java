/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSourceFormat;
import org.virbo.dsops.Ops;

/**
 * Export to idlsav support.
 * @author jbf
 */
public class IdlsavDataSourceFormat extends AbstractDataSourceFormat {

    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        String su= getParam( "tunits", "t1970" );

        if ( data.rank()!=1 ) {
            throw new IllegalArgumentException("not supported, rank "+data.rank() );
        }

        QDataSet wds= Ops.valid(data);

        double[] dd= new double[data.length()];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
        }
        
        WriteIDLSav write= new WriteIDLSav();
        write.addVariable( Ops.guessName(data,"data"), dd );

        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            Units dep0u= SemanticOps.getUnits(dep0);
            Units targetUnits= SemanticOps.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            double[] dep0dd= new double[dep0.length()];
            for ( int i=0; i<dep0dd.length; i++ ) {
                dep0dd[i]= uc.convert( dep0.value(i) );
            }
            String dep0name= Ops.guessName(dep0,"dep0");
            write.addVariable( dep0name, dep0dd );
            //write.addVariable( dep0name+"__units", ""+targetUnits );
        }


        QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
        if ( dep1!=null ) {
            double[] dep1dd= new double[dep1.length()];
            for ( int i=0; i<dep1dd.length; i++ ) {
                dep1dd[i]= dep1.value(i);
            }
            write.addVariable( Ops.guessName(dep1,"dep1"), dep1dd );
        }


        setUri(uri);

        File f= new File( getResourceURI().toURL().getFile() );
        FileOutputStream fos= new FileOutputStream(f);
        try {
            write.write( fos );
        } finally {
            fos.close();
        }

    }

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1;
    }

    public String getDescription() {
        return "IDL Saveset";
    }

}
