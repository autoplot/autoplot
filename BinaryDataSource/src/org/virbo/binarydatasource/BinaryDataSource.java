/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.binarydatasource;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.IndexGenDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;

/**
 *
 * @author jbf
 */
public class BinaryDataSource extends AbstractDataSource {

    public final static String DOUBLE = "double";
    public final static String FLOAT = "float";
    public final static String LONG = "long";
    public final static String INT = "int";
    public final static String SHORT = "short";
    public final static String BYTE = "byte";
    public final static String UBYTE = "ubyte";
    
    public BinaryDataSource(URL url) {
        super(url);
    }

    private int getIntParameter(String name, int deflt) {
        String sval = params.get(name);
        int result = sval == null ? deflt : Integer.parseInt(sval);
        return result;
    }

    private String getParameter(String name, String deflt) {
        String sval = params.get(name);
        String result = sval == null ? deflt : sval;
        return result;
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File f = DataSetURL.getFile(url, mon);

        FileChannel fc = new FileInputStream(f).getChannel();

        final int offset = getIntParameter("byteOffset", 0);

        int length = getIntParameter("byteLength", (((int) f.length()) - offset));

        int fieldCount = getIntParameter("fieldCount", params.get("depend0") == null ? 1 : 2);

        int recCount= getIntParameter("recCount", Integer.MAX_VALUE );
                
        ByteBuffer buf = fc.map(MapMode.READ_ONLY, offset, length);

        int dep0 = getIntParameter("depend0", -1);
        int dep0Offset= getIntParameter( "depend0Offset", -1 );
        
        int defltcol;
        if ( (dep0 == -1) && (dep0Offset==-1) ) {
            defltcol = 0;
        } else {
            if ( dep0 > 0 || dep0Offset>0 ) {
                defltcol = 0;
            } else {
                defltcol = 1;
            }
        }

        String columnType = getParameter("type", UBYTE );

        int col = getIntParameter("column", defltcol);
        int recOffset= getIntParameter( "recOffset", -1 );
        if ( recOffset==-1 ) recOffset= col * BufferDataSet.byteCount(columnType);

        String encoding = getParameter("byteOrder", "little");
        if (encoding.equals("big")) {
            buf.order(ByteOrder.BIG_ENDIAN);
        } else {
            buf.order(ByteOrder.LITTLE_ENDIAN);
        }
        
        int recSizeBytes= getIntParameter("recLength", -1 );
        if ( recSizeBytes==-1 ) recSizeBytes= BufferDataSet.byteCount(columnType) * fieldCount;
        
        final int frecCount= Math.min( length / recSizeBytes, recCount );
        MutablePropertyDataSet ds = BufferDataSet.makeDataSet( 1, recSizeBytes, recOffset, frecCount, 1, 1, buf, columnType );

        if (dep0 > -1 || dep0Offset > -1 ) {
            String dep0Type = getParameter("depend0Type", columnType);
            if ( dep0Offset==-1 ) dep0Offset= BufferDataSet.byteCount(dep0Type) * dep0;
            QDataSet dep0ds = BufferDataSet.makeDataSet( 1, recSizeBytes, dep0Offset, frecCount, 1, 1, buf, dep0Type );
            ds.putProperty(QDataSet.DEPEND_0, dep0ds);
        } else {
            final int finalRecSizeBytes= recSizeBytes;
            final int finalRecOffset= recOffset;
            IndexGenDataSet dep0ds= new IndexGenDataSet(frecCount) {
                @Override
                public double value(int i) {
                    return offset + finalRecOffset + i * finalRecSizeBytes;
                }
            };
            dep0ds.putProperty( QDataSet.CADENCE, (double)recSizeBytes );
            ds.putProperty(QDataSet.DEPEND_0, dep0ds);
        }

        String s;
        s= params.get( "validMin" );
        if ( s!= null ) {
            ds.putProperty( QDataSet.VALID_MIN, java.lang.Double.parseDouble(s) );
        }
        s= params.get( "validMax" );
        if ( s!= null ) {
            ds.putProperty( QDataSet.VALID_MAX, java.lang.Double.parseDouble(s) );
        }
        
        return ds;
    }

}
