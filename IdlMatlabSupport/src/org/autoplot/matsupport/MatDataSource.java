
package org.autoplot.matsupport;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLNumericArray;
import com.jmatio.types.MLStructure;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Read data from legacy .mat file.
 * @author jbf
 */
public class MatDataSource extends AbstractDataSource {

    public MatDataSource(URI uri) {
        super(uri);        
    }

    private static Object bufferDataSetType( int mltype ) {
        switch ( mltype ) {
            case 0: return null;
            case 1: return null;
            case 2: return null;              
            case 3: return null;               
            case 4: return null;               //4 // char
            case 5: return null;               
            case 6: return BufferDataSet.DOUBLE;        //6
            case 7: return BufferDataSet.FLOAT;     //7
            case 8: return BufferDataSet.BYTE;     //8
            case 9: return BufferDataSet.UBYTE;       //9
            case 10: return BufferDataSet.SHORT;      //10
            case 11: return BufferDataSet.USHORT;     //11
            case 12: return BufferDataSet.INTEGER;      //12
            case 13: return BufferDataSet.UINT;     //13
            case 14: return BufferDataSet.LONG;      //14
            case 15: return null; //15 ULONG not supported
        }
        throw new IllegalArgumentException("mltype should be between 0 and 15.");
    }
    
    private MLArray getArray( MatFileReader reader, MLArray s, String name ) {
        if ( name.contains(".") ) {
            int n= name.indexOf(".");
            String root= name.substring(0,n);
            MLArray s1= reader.getMLArray(root);
            if ( s1 instanceof MLNumericArray ) {
                return s1; // shouldn't get here
            } else if ( s1 instanceof MLStructure ) {
                String tagname= name.substring(n+1);
                MLStructure mls= (MLStructure)s1;
                return getArray( reader, mls.getField(tagname), tagname );
            } else {
                throw new IllegalArgumentException("not supported (l62): "+s1);
            }
        } else {
            if ( s==null ) {
                return reader.getMLArray(name);
            } else if ( s instanceof MLNumericArray ) {
                return s;
            } else {
                return null;
            }
        }
    }
            
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File f= getFile( uri, mon );
        MatFileReader reader= new MatFileReader(f);
        String name = getParam( "arg_0", "" );
        if ( name.length()==0 ) {
            throw new IllegalArgumentException("name must be set");
        }
        MLArray array= getArray( reader, null, name);
        
        if ( array instanceof MLNumericArray ) {
            MLNumericArray mlna= (MLNumericArray)array;
            ByteBuffer buffer= mlna.getRealByteBuffer();
            Object type= bufferDataSetType(array.getType());
            int[] qube= array.getDimensions();
            int reclen;
            switch (qube.length) {
                case 2:
                    int t= qube[0];
                    qube[0]= qube[1];
                    qube[1]= t;
                    reclen= qube[1] * BufferDataSet.byteCount(type);
                    QDataSet result= 
                            BufferDataSet.makeDataSet( qube.length, reclen, 0, 
                            qube, buffer, type );
                    if ( result.length(0)==6 && result.length()>0 ) {
                        double yr= result.value(0,0);
                        if ( Math.floor(yr)==yr && yr>1900 && yr<2200 ) {
                            result= Ops.toTimeDataSet( Ops.slice1(result,0),
                                    Ops.slice1(result,1), 
                                    Ops.slice1(result,2),
                                    Ops.slice1(result,3),
                                    Ops.slice1(result,4),
                                    Ops.slice1(result,5), null );
                        }
                    }
                    return result;
                case 1:
                    reclen= qube[0]*BufferDataSet.byteCount(type);
                    return BufferDataSet.makeDataSet( qube.length, reclen, 0,
                            qube, buffer, type );
                default:
                    throw new IllegalArgumentException("rank 3 and up is not supported");
            }
            
        }
        throw new IllegalArgumentException("unexpected type, should be MLArray");
    }

    
}
