package gov.nasa.gsfc.voyager.cdf;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
public class CDF3Impl extends CDFImpl implements CDF3, java.io.Serializable {
    public long GDROffset;
    public CDF3Impl(ByteBuffer buf) throws Throwable {
        super(buf);
        setOffsets();
        thisCDF = this;
        IntBuffer ibuf = buf.asIntBuffer();
        getRecord(0); // _buf
        ibuf.position(2); // skip magic numbers
        ibuf.get(); // Record Size
        ibuf.get(); // Record Size
        ibuf.get(); // Record Type
        GDROffset = (long)ibuf.get();
        GDROffset = (GDROffset << 32) + ibuf.get();
        version = ibuf.get();
        if (version != CDF_VERSION) {
            throw new Throwable("Version " + version +
            "is not accepted by this reader.");
        }
        release = ibuf.get();
        encoding = ibuf.get();
        byteOrder = DataTypes.getByteOrder(encoding);
        setByteOrder(byteOrder);
        flags = ibuf.get();
        ibuf.get();
        ibuf.get();
        increment = ibuf.get();
        // validate and extract GDR info
        int pos = (int)GDROffset + 8;
        buf.position(pos);
        int x;
        if ((x = buf.getInt()) != GDR_RECORD) {
            throw new Throwable("Bad GDR type " + x);
        }
        rVDRHead = buf.getLong();
        zVDRHead = buf.getLong();
        ADRHead = buf.getLong();
        long CDFSize = buf.getLong();
        numberOfRVariables = buf.getInt();
        numberOfAttributes = buf.getInt();
        buf.getInt(); // skip rMaxRec
        int numberOfRDims = buf.getInt();
        numberOfZVariables = buf.getInt();
        buf.getInt(); // skip UIRhead
        buf.getInt(); // skip UIRhead
        rDimSizes = new int[numberOfRDims];
        if (numberOfRDims > 0) { // skip next 3 integer fields
            buf.getInt();
            buf.getInt();
            buf.getInt();
            for (int i = 0; i < rDimSizes.length; i++) {
                rDimSizes[i] = buf.getInt();
            }
        }
        buf.position(0);
        variableTable = variables();
        attributeTable = attributes();
    }

    void setOffsets() {
        offset_NEXT_VDR = OFFSET_NEXT_VDR;
        offset_NEXT_ADR = OFFSET_NEXT_ADR;
        offset_ATTR_NAME = ATTR_OFFSET_NAME;
        offset_SCOPE = OFFSET_SCOPE;
        offset_AgrEDRHead = AgrEDRHead_OFFSET;
        offset_AzEDRHead = AzEDRHead_OFFSET;
        offset_NEXT_AEDR = OFFSET_NEXT_AEDR;
        offset_ENTRYNUM = OFFSET_ENTRYNUM;
        offset_ATTR_DATATYPE = ATTR_OFFSET_DATATYPE;
        offset_ATTR_NUM_ELEMENTS = ATTR_OFFSET_NUM_ELEMENTS;
        offset_VALUE = OFFSET_VALUE;
        offset_VAR_NAME = VAR_OFFSET_NAME;
        offset_VAR_NUM_ELEMENTS = VAR_OFFSET_NUM_ELEMENTS;
        offset_NUM = OFFSET_NUM;
        offset_FLAGS = OFFSET_FLAGS;
        offset_BLOCKING_FACTOR = OFFSET_BLOCKING_FACTOR;
        offset_VAR_DATATYPE = VAR_OFFSET_DATATYPE;
        offset_zNumDims = OFFSET_zNumDims;
        offset_FIRST_VXR = OFFSET_FIRST_VXR;
        offset_NEXT_VXR = OFFSET_NEXT_VXR;
        offset_NENTRIES = OFFSET_NENTRIES;
        offset_NUSED = OFFSET_NUSED;
        offset_FIRST = OFFSET_FIRST;
        offset_RECORD_TYPE =  OFFSET_RECORD_TYPE;
        offset_RECORDS = OFFSET_RECORDS;
        offset_CSIZE = OFFSET_CSIZE;
        offset_CDATA = OFFSET_CDATA;
    }
    public String getString(int offset)  {
        return getString(offset, MAX_STRING_SIZE);
    }

    public int lowOrderInt(ByteBuffer buf) {
        return (int)buf.getLong();
    }

    public int lowOrderInt(ByteBuffer buf, int offset) {
        return (int)buf.getLong(offset);
    }
}
