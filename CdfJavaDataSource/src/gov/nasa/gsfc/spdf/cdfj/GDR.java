package gov.nasa.gsfc.spdf.cdfj;

import java.nio.ByteBuffer;

/**
 * Class for modelling the Global Descriptor Record (GDR).
 * @see https://spdf.gsfc.nasa.gov/pub/software/cdf/doc/cdf371/cdf37ifd.pdf section 2.3.
 * @author jbf
 */
public class GDR {

    ByteBuffer record = ByteBuffer.allocate(8 + 4 + 8 + 8 + 8
            + 8 + 4 + 4 + 4 + 4 + 4 + 8 + 4 + 4 + 4); // see Figure 2.5 
    protected long position;
    long zVDRHead;
    long aDRHead;
    long eof;
    int numAttr;
    int nzVars;
    int lastLeapSecondId;

    /**
     * Signed 8-byte integer, big-endian byte ordering.  The file offset of the 
     * first zVariable Descriptor Record (zVDR). The first zVDR contains a file 
     * offset to the next zVDR and so on.  A zVDR will exist for each zVariable 
     * in the CDF. Because zVariables were not supported by CDF until CDF V2.2, 
     * prior to CDF V2.2 this field is undefined.  Beginning with CDF V2.2 this 
     * field will contain either a file offset to the first zVDR or 
     * 0x0000000000000000 if the CDF contains no zVariables.  The last zVDR will 
     * always contain 0x0000000000000000 for the file offset of the next zVDR  
     * (to indicate the end of the zVDRs
     * @param l 
     */
    public void setZVDRHead(long l) {
        zVDRHead = l;
    }

    /**
     * Signed 8-byte integer, big-endian byte ordering.The file offset of the 
     * first Attribute Descriptor Record (ADR).  The first ADR contains a file 
     * offset to the next ADR and so on.  An ADR will exist for each attribute 
     * in the CDF.  This field will contain 0x0000000000000000 if the CDF 
     * contains no attributes.  Beginning with CDF V2.1 the last ADR will 
     * contain a file offset of 0x0000000000000000 for the file offset of 
     * the next ADR (to indicate the end of the ADRs).
     * @param l 
     */
    public void setADRHead(long l) {
        aDRHead = l;
    }

    /**
     * Signed 8-byte integer, big-endian byte ordering.The end-of-file (EOF) 
     * position in the dotCDF file.  This is the file offset of the byte that 
     * is one beyond the last byte of the last internal record.  (This value is 
     * also the total number of bytes used in the dotCDF file.)  
     * 
     * @param l 
     */
    public void setEof(long l) {
        eof = l;
    }

    /**
     * Signed 4-byte integer, big-endian byte ordering.The number of attributes 
     * in the CDF. This will correspond to the number of ADRs in the dotCDF 
     * file.
     * @param n 
     */
    public void setNumAttr(int n) {
        numAttr = n;
    }

    /**
     * Signed 4-byte integer, big-endian byte ordering.The number of zVariables 
     * in the CDF. This will correspond to the number of zVDRs in the dotCDF 
     * file. 
     * @param n 
     */
    public void setNzVars(int n) {
        nzVars = n;
    }

    /**
     * Signed 4-byte integer, big-endian byte ordering.The date of the last 
     * entry in the leap secondtable (in YYYYMMDD form). It is negative one 
     * (-1) for the previous version.A value of zero (0) is also accepted, which 
     * means a CDF was not created based on a leap second table. This field is 
     * applicable to CDFs with CDF_TIME_TT2000 data type
     * @param n 
     */
    public void setLastLeapSecondId(int n) {
        lastLeapSecondId = n;
    }

    public ByteBuffer get() {
        record.position(0);
        record.putLong((long) (record.capacity()));
        record.putInt(2);
        record.putLong(0); // no rvars
        record.putLong(zVDRHead);
        record.putLong(aDRHead);
        record.putLong(eof);
        record.putInt(0); // no rvars
        record.putInt(numAttr);
        record.putInt(-1);
        record.putInt(0);
        record.putInt(nzVars);
        record.putLong(0);
        record.putInt(0);
        record.putInt(lastLeapSecondId);
        record.putInt(0);
        record.position(0);
        return record;
    }

    public int getSize() {
        return record.limit();
    }
}
