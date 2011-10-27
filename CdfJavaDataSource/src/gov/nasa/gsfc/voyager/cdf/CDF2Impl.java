package gov.nasa.gsfc.voyager.cdf;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
public class CDF2Impl extends CDFImpl implements CDF2, java.io.Serializable {
    public int GDROffset;
    public int VAR_OFFSET_NAME;
    public int OFFSET_zNumDims;
    public int VAR_OFFSET_NUM_ELEMENTS;
    public int OFFSET_NUM;
    public CDF2Impl(ByteBuffer buf, int release) throws Throwable {
        super(buf);
        if (release < 5) {
            VAR_OFFSET_NAME = 192;
            VAR_OFFSET_NUM_ELEMENTS = 48 + 128;
            OFFSET_NUM = 52 + 128;
        } else {
            VAR_OFFSET_NAME = 64;
            VAR_OFFSET_NUM_ELEMENTS = 48;
            OFFSET_NUM = 52;
        }
        OFFSET_zNumDims = VAR_OFFSET_NAME + MAX_STRING_SIZE;
        setOffsets();
        thisCDF = this;
        IntBuffer ibuf = buf.asIntBuffer();
        ByteBuffer _buf = getRecord(0);
        ibuf.position(2);
        ibuf.get(); //int recordSize = 
        ibuf.position(3);
        ibuf.get();// int recordType =
        GDROffset = ibuf.get();
        version = ibuf.get();
        if (version != CDF_VERSION) {
            throw new Throwable("Version " + version +
            "is not accepted by this reader.");
        }
        ibuf.get(); // release=
        encoding = ibuf.get();
        byteOrder = DataTypes.getByteOrder(encoding);
        setByteOrder(byteOrder);
        flags = ibuf.get();
        ibuf.get();
        ibuf.get();
        increment = ibuf.get();
        // validate and extract GDR info
        int pos = GDROffset + 4;
        buf.position(pos);
        int x;
        if ((x = buf.getInt()) != GDR_RECORD) {
            throw new Throwable("Bad GDR type " + x);
        }
        rVDRHead = buf.getInt();
        zVDRHead = buf.getInt();
        ADRHead = buf.getInt();
        buf.getInt(); //int CDFSize = 
        numberOfRVariables = buf.getInt();
        numberOfAttributes = buf.getInt();
        buf.getInt(); // skip rMaxRec
        int numberOfRDims = buf.getInt();
        numberOfZVariables = buf.getInt();
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
        return buf.getInt();
    }

    public int lowOrderInt(ByteBuffer buf, int offset) {
        return buf.getInt(offset);
    }
}
