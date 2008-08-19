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
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
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

    private int byteCount(String type) {
        if (type.equals(DOUBLE)) {
            return 8;
        } else if (type.equals(FLOAT)) {
            return 4;
        } else if (type.equals(LONG)) {
            return 8;
        } else if (type.equals(INT)) {
            return 4;
        } else if (type.equals(SHORT)) {
            return 2;
        } else if (type.equals(BYTE)) {
            return 1;
        } else if (type.equals(UBYTE)) {
            return 1;
        } else {
            throw new IllegalArgumentException("bad type: " + type);
        }
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File f = DataSetURL.getFile(url, mon);

        FileChannel fc = new FileInputStream(f).getChannel();

        final int offset = getIntParameter("byteOffset", 0);

        int length = getIntParameter("byteLength", (((int) f.length()) - offset));

        int fieldCount = getIntParameter("fieldCount", params.get("depend0") == null ? 1 : 2);

        ByteBuffer buf = fc.map(MapMode.READ_ONLY, offset, length);

        int dep0 = getIntParameter("depend0", -1);

        int defltcol;
        if (dep0 == -1) {
            defltcol = 0;
        } else {
            if (dep0 > 0) {
                defltcol = 0;
            } else {
                defltcol = 1;
            }
        }

        int col = getIntParameter("column", defltcol);

        String columnType = getParameter("type", BYTE );

        String encoding = getParameter("byteOrder", "little");
        if (encoding.equals("big")) {
            buf.order(ByteOrder.BIG_ENDIAN);
        } else {
            buf.order(ByteOrder.LITTLE_ENDIAN);
        }
        
        final int recSizeBytes=  byteCount(columnType) * fieldCount;
        final int recCount= length / recSizeBytes;
        MutablePropertyDataSet ds = makeDataSet(1, recCount, fieldCount, col, 1, 1, 0, buf, columnType);

        if (dep0 > -1) {
            String dep0Type = getParameter("depend0Type", columnType);

            QDataSet dep0ds = makeDataSet(1, recCount, fieldCount, dep0, 1, 1, 0, buf, dep0Type);
            ds.putProperty(QDataSet.DEPEND_0, dep0ds);
        } else {
            IndexGenDataSet dep0ds= new IndexGenDataSet(recCount) {
                @Override
                public double value(int i) {
                    return offset + i * recSizeBytes;
                }
            };
            dep0ds.putProperty( QDataSet.CADENCE, recSizeBytes );
            ds.putProperty(QDataSet.DEPEND_0, dep0ds);
        }

        return ds;
    }

    private MutablePropertyDataSet makeDataSet(int rank, int len0, int reclen0, int recoffs0, int len1, int reclen1, int recoffs1, ByteBuffer buf, String type) {
        if (type.equals(DOUBLE)) {
            DoubleBuffer dbuf = buf.asDoubleBuffer();
            return new Double(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, dbuf);
        } else if (type.equals(FLOAT)) {
            FloatBuffer fbuf = buf.asFloatBuffer();
            return new Float(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf);
        } else if (type.equals(LONG)) {
            LongBuffer fbuf = buf.asLongBuffer();
            return new Long(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf);
        } else if (type.equals(INT)) {
            IntBuffer fbuf = buf.asIntBuffer();
            return new Int(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf);
        } else if (type.equals(SHORT)) {
            ShortBuffer fbuf = buf.asShortBuffer();
            return new Short(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, fbuf);
        } else if (type.equals(BYTE)) {
            return new Byte(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, buf);
        } else if (type.equals(UBYTE)) {
            return new UByte(rank, len0, reclen0, recoffs0, len1, reclen1, recoffs1, buf);
        } else {
            throw new IllegalArgumentException("bad type: " + type);
        }
    }
}
