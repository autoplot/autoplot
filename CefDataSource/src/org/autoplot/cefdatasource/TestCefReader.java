/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cefdatasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class TestCefReader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, ParseException {
        System.err.println( new File( "../.." ).toURI().toURL().toString() );
        
        String file1= "C1_CP_EDI_EGD__20050212_V03.cef";  // 0.045 MB
        String file2= "C1_CP_EDI_EGD__20050217_V03.cef"; // 8.200 MB
        String file3= "C1_PP_CIS__20020707_V03.cef";      // 4.5   MB, contains vectors
        String file4= "C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_150000_V061018.cef";  // 155MB, contains rank 4
        String file5= "C1_CP_PEA_CP3DXPH_DNFlux__20020811_140000_20020811_141000_V061018.cef";  // 5MB, contains rank 4
        
        File file= new File( "../../../data/cef/"+file5 );
        long fileSize= file.length();
        
        InputStream in= new FileInputStream(file );
        ReadableByteChannel channel= Channels.newChannel(in);
        
        long t0= System.currentTimeMillis();
                
        CefReaderHeader reader= new CefReaderHeader();
        
        Cef cef= reader.read(channel);
        
        System.err.println("time to read Cef header (ms): "+(System.currentTimeMillis()-t0) );
        
        QDataSet result= new CefReaderData().cefReadData( channel, cef ) ;
        
        long dt= System.currentTimeMillis()-t0;
        System.err.println("time to read Cef data (ms): "+dt+ "  ("+(fileSize/dt)+" KB/sec)" );
        
        System.err.println( result );
        
        QDataSet ds= DataSetOps.slice1( result,1 );
        //GraphUtil.visualize( VectorDataSetAdapter.create(ds) );
        
    }

}
