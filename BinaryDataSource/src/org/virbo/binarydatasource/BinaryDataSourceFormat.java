/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.binarydatasource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format data to binary file.
 * @author jbf
 */
public class BinaryDataSourceFormat implements DataSourceFormat {

    /**
     * copy the dataset into a DoubleBuffer by wrapping a DoubleBuffer with
     * a DoubleBufferDataSet, then copy the data into the dataset.
     * @param data
     * @param mon
     * @return byteBuffer view of the dataset.
     */
    private ByteBuffer formatRank2( QDataSet data, ProgressMonitor mon) {

        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        //dep1 is ignored.
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int recSize=  dep0Len + data.length(0) ;
        int size= data.length() * recSize;
        int typeSize= 8;
        
        ByteBuffer result= ByteBuffer.allocate(size*typeSize);
        result.order( ByteOrder.LITTLE_ENDIAN );
        
        DoubleBuffer dbuf= result.asDoubleBuffer();
        
        Double ddata= new Double( 2, 
                data.length(), recSize, dep0Len, 
                data.length(0), 1, 0,
                dbuf );
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            Double ddep0= new Double( 1,
                data.length(), recSize, 0, 
                data.length(0), 1, 0, dbuf );
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
        
    }

    private ByteBuffer formatRank1( QDataSet data, ProgressMonitor mon ) {
        
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
                
        int dep0Len= ( dep0==null ? 0 : 1 );
        int recSize=  dep0Len + 1 ;
        int size= data.length() * recSize;
        int typeSize= 8;
        
        ByteBuffer result= ByteBuffer.allocate(size*typeSize);
        result.order( ByteOrder.LITTLE_ENDIAN );
        
        DoubleBuffer dbuf= result.asDoubleBuffer();
        
        Double ddata= new Double( 2, 
                data.length(), recSize, dep0Len, 
                1, 1, 0,
                dbuf );
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            Double ddep0= new Double( 1,
                data.length(), recSize, 0, 
                1, 1, 0, dbuf );
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
    }

    public void formatData(File url, java.util.Map<String,String> params, QDataSet data, ProgressMonitor mon) throws IOException {
        
        ByteBuffer result;
        if (data.rank() == 2) {
            result= formatRank2( data, mon);
        } else if (data.rank() == 1) {
            result= formatRank1( data, mon);
        } else {
            throw new IllegalArgumentException("rank not supported");
        }
        
        WritableByteChannel channel= Channels.newChannel( new FileOutputStream(url) );
        channel.write(result);
        
        channel.close();
        
    }


}
