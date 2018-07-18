
package org.autoplot.binarydatasource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;

/**
 * Format data to binary file.
 * @author jbf
 */
public class BinaryDataSourceFormat extends AbstractDataSourceFormat {

    /**
     * copy the dataset into a DoubleBuffer by wrapping a DoubleBuffer with
     * a DoubleBufferDataSet, then copy the data into the dataset.
     * @param data
     * @param mon
     * @return byteBuffer view of the dataset.
     */
    private ByteBuffer formatRank2( QDataSet data, ProgressMonitor mon, Map<String,String> params ) {

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        //dep1 is ignored.
        //QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        
        String type= params.get("type");
        if ( type==null ) type= "double";
        
        String dep0Type= params.get("depend0Type" );
        if ( dep0Type==null ) dep0Type= "double";
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        
        int dep0TypeSize= BufferDataSet.byteCount(dep0Type);
        int recSize=  dep0Len*dep0TypeSize + data.length(0) * typeSize;
        
        int size= data.length() * recSize;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 2, recSize, dep0Len * dep0TypeSize, 
                data.length(), data.length(0), 1, 1,
                result, type );
        /*Double ddata= new Double( 2, 
                recSize, dep0Len * typeSize, 
                data.length(), data.length(0), 1, 
                result );*/
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null && dep0.rank()==1 ) {
            BufferDataSet ddep0= BufferDataSet.makeDataSet( 1, recSize, 0, 
                dep0.length(), 1, 1, 1,
                result, dep0Type );

            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
        
    }

    private ByteBuffer formatRank1( QDataSet data, ProgressMonitor mon, Map<String,String> params ) {
        
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
                
        String type= params.get("type");
        if ( type==null ) type= "double";
                
        String dep0Type= params.get("depend0Type" );
        if ( dep0Type==null ) dep0Type= "double";
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        int dep0TypeSize= BufferDataSet.byteCount(dep0Type);
        int recSize=  dep0Len*dep0TypeSize + typeSize;
        int size= data.length() * recSize ;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 1, 
                recSize, dep0Len*dep0TypeSize, 
                data.length(), 1, 1, 1,
                result, type );
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            BufferDataSet ddep0= BufferDataSet.makeDataSet( 1,
                recSize, 0, 
                data.length(), 1, 1, 1, 
                result, dep0Type );
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
    }

    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
        
        super.setUri(uri);
        super.maybeMkdirs();

        String doDep= getParam("doDep", "");
        if ( doDep.length()>0 && doDep.toUpperCase().charAt(0)=='F' ) {
            MutablePropertyDataSet mpds= DataSetOps.makePropertiesMutable(data);
            mpds.putProperty( QDataSet.DEPEND_0, null );
            mpds.putProperty( QDataSet.DEPEND_1, null );
            mpds.putProperty( QDataSet.BUNDLE_1, null );
            data= mpds;
        }

        URISplit split= URISplit.parse(uri);
        java.util.Map<String,String> params= URISplit.parseParams(split.params);

        ByteBuffer result;
        switch (data.rank()) {
            case 2:
                result= formatRank2( data, mon, params );
                break;
            case 1:
                result= formatRank1( data, mon, params );
                break;
            default:
                throw new IllegalArgumentException("rank not supported");
        }
                        
        File outFile= new File( split.resourceUri );        
        try (WritableByteChannel channel = Channels.newChannel( new FileOutputStream( outFile ) )) {
            channel.write(result);
        }
        
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()<3 && ( ! ( ds.rank()==0  || SemanticOps.isJoin(ds) ) );
    }

    @Override
    public String getDescription() {
        return "Binary Table";
    }
    
}
