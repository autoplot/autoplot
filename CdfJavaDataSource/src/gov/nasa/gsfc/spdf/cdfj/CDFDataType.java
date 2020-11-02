package gov.nasa.gsfc.spdf.cdfj;
/**
 * CDF Data types
 * @see SupportedTypes
 */
public final class CDFDataType {

    /**
     * INT1
     */
    public static final CDFDataType INT1 = new CDFDataType(1);

    /**
     * INT2
     */
    public static final CDFDataType INT2 = new CDFDataType(2);

    /**
     * INT4
     */
    public static final CDFDataType INT4 = new CDFDataType(4);

    /**
     * INT8
     */
    public static final CDFDataType INT8 = new CDFDataType(8);

    /**
     * UINT1
     */
    public static final CDFDataType UINT1 = new CDFDataType(11);

    /**
     * UINT2
     */
    public static final CDFDataType UINT2 = new CDFDataType(12);

    /**
     * UINT4
     */
    public static final CDFDataType UINT4 = new CDFDataType(14);

    /**
     * FLOAT
     */
    public static final CDFDataType FLOAT = new CDFDataType(21);

    /**
     * DOUBLE
     */
    public static final CDFDataType DOUBLE = new CDFDataType(22);

    /**
     * EPOCH
     */
    public static final CDFDataType EPOCH = new CDFDataType(31);

    /**
     * EPOCH16
     */
    public static final CDFDataType EPOCH16 = new CDFDataType(32);

    /**
     * CHAR
     */
    public static final CDFDataType CHAR = new CDFDataType(51);

    /**
     * TT2000
     */
    public static final CDFDataType TT2000 = new CDFDataType(33);
    int type;
    private CDFDataType(int type) {
        this.type = type;
    }
    public int getValue() {return type;}

    /**
     * Returns CDFDataType for a given CDFTimeType.
     */
    public static CDFDataType getType(CDFTimeType type) {
        if (type.getValue() == 31) return EPOCH;
        if (type.getValue() == 32) return EPOCH16;
        if (type.getValue() == 33) return TT2000;
        return null;
    }
        
    /**
     * Returns the name of the integer data type, for example, 8 is type
     * 8-byte integer (a.k.a. Java long), and 33 is CDF_TT2000.
     * @param type the code for data type
     * @return a name identifying the type.
     * @see https://spdf.gsfc.nasa.gov/pub/software/cdf/doc/cdf380/cdf38ifd.pdf page 33.
     */
    public static String nameForType(int type) {
        switch (type) {
            case 1:
                return "CDF_INT1";
            case 41:
                return "CDF_BYTE";  // 1-byte signed integer
            case 2:
                return "CDF_INT2"; 
            case 4:
                return "CDF_INT4";
            case 8:
                return "CDF_INT8";
            case 11:
                return "CDF_UINT1";
            case 12:
                return "CDF_UINT2";
            case 14:
                return "CDF_UINT4";
            case 21:
                return "CDF_REAL4";
            case 44:
                return "CDF_FLOAT"; 
            case 22:
                return "CDF_REAL8";
            case 45:
                return "CDF_DOUBLE"; 
            case 31:
                return "CDF_EPOCH";
            case 32:
                return "CDF_EPOCH16";  // make of two CDF_REAL8,
            case 33:
                return "CDF_TT2000";
            case 51:
                return "CDF_CHAR";
            case 52:
                return "CDF_UCHAR";
            default:
                return "???";
        }
    }
    
    @Override
    public String toString() {
        return type + "(" + nameForType(type) +")";
    }

}
