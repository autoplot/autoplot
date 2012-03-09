/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSourceFormat;
import org.virbo.dsops.Ops;

/**
 * Export to idlsav support.
 * @author jbf
 */
public class IdlsavDataSourceFormat extends AbstractDataSourceFormat {

    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {
        if ( data.rank()!=1 ) {
            throw new IllegalArgumentException("not supported, rank "+data.rank() );
        }

        QDataSet wds= Ops.valid(data);

        double[] dd= new double[data.length()];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
        }
        
        WriteIDLSav write= new WriteIDLSav();
        write.addVariable( Ops.guessName(data), dd );

        setUri(uri);

        File f= new File( getResourceURI().toURL().getFile().toString() );
        FileOutputStream fos= new FileOutputStream(f);
        write.write( fos );
        fos.close();

    }

}
