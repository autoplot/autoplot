package org.autoplot.pds4;

import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * PDS4 field/element type names. Enum identifiers match NASA's FieldType names; this independent enumeration has no NASA library
 * dependency. Recognition of a name does not imply the reader can decode that encoding.
 */
public enum FieldType {
    ASCII_ANYURI("ASCII_AnyURI"),
    ASCII_BIBCODE("ASCII_BibCode"),
    ASCII_BOOLEAN("ASCII_Boolean"),
    ASCII_DOI("ASCII_DOI"),
    ASCII_DATE("ASCII_Date"),
    ASCII_DATE_DOY("ASCII_Date_DOY"),
    ASCII_DATE_TIME("ASCII_Date_Time"),
    ASCII_DATE_TIME_DOY("ASCII_Date_Time_DOY"),
    ASCII_DATE_TIME_DOY_UTC("ASCII_Date_Time_DOY_UTC"),
    ASCII_DATE_TIME_UTC("ASCII_Date_Time_UTC"),
    ASCII_DATE_TIME_YMD("ASCII_Date_Time_YMD"),
    ASCII_DATE_TIME_YMD_UTC("ASCII_Date_Time_YMD_UTC"),
    ASCII_DATE_YMD("ASCII_Date_YMD"),
    ASCII_DIRECTORY_PATH_NAME("ASCII_Directory_Path_Name"),
    ASCII_FILE_NAME("ASCII_File_Name"),
    ASCII_FILE_SPECIFICATION_NAME("ASCII_File_Specification_Name"),
    ASCII_INTEGER("ASCII_Integer"),
    ASCII_LID("ASCII_LID"),
    ASCII_LIDVID("ASCII_LIDVID"),
    ASCII_LIDVID_LID("ASCII_LIDVID_LID"),
    ASCII_MD5_CHECKSUM("ASCII_MD5_Checksum"),
    ASCII_NONNEGATIVE_INTEGER("ASCII_NonNegative_Integer"),
    ASCII_NUMERIC_BASE16("ASCII_Numeric_Base16"),
    ASCII_NUMERIC_BASE2("ASCII_Numeric_Base2"),
    ASCII_NUMERIC_BASE8("ASCII_Numeric_Base8"),
    ASCII_REAL("ASCII_Real"),
    ASCII_STRING("ASCII_String"),
    ASCII_TIME("ASCII_Time"),
    ASCII_VID("ASCII_VID"),
    COMPLEXLSB16("ComplexLSB16"),
    COMPLEXLSB8("ComplexLSB8"),
    COMPLEXMSB16("ComplexMSB16"),
    COMPLEXMSB8("ComplexMSB8"),
    IEEE754LSBDOUBLE("IEEE754LSBDouble"),
    IEEE754LSBSINGLE("IEEE754LSBSingle"),
    IEEE754MSBDOUBLE("IEEE754MSBDouble"),
    IEEE754MSBSINGLE("IEEE754MSBSingle"),
    SIGNEDBYTE("SignedByte"),
    SIGNEDLSB2("SignedLSB2"),
    SIGNEDLSB4("SignedLSB4"),
    SIGNEDLSB8("SignedLSB8"),
    SIGNEDMSB2("SignedMSB2"),
    SIGNEDMSB4("SignedMSB4"),
    SIGNEDMSB8("SignedMSB8"),
    UTF8_STRING("UTF8_String"),
    UNSIGNEDBYTE("UnsignedByte"),
    UNSIGNEDLSB2("UnsignedLSB2"),
    UNSIGNEDLSB4("UnsignedLSB4"),
    UNSIGNEDLSB8("UnsignedLSB8"),
    UNSIGNEDMSB2("UnsignedMSB2"),
    UNSIGNEDMSB4("UnsignedMSB4"),
    UNSIGNEDMSB8("UnsignedMSB8"),
    SIGNEDBITSTRING("SignedBitString"),
    UNSIGNEDBITSTRING("UnsignedBitString"),
    UNKNOWN("Unknown");

    private final String xmlType;
    private static final Map<String, FieldType> BY_XML_NAME;

    static {
        Map<String, FieldType> lookup = new HashMap<>();
        for (FieldType type : values()) {
            lookup.put(type.xmlType, type);
        }
        BY_XML_NAME = Collections.unmodifiableMap(lookup);
    }

    FieldType(String xmlType) {
        this.xmlType = xmlType;
    }

    /**
     * Return the exact case-sensitive spelling used in PDS4 XML labels.
     */
    public String getXMLType() {
        return xmlType;
    }

    /**
     * Resolve an XML data_type value, such as ASCII_Date_DOY or SignedMSB8. Unknown or null spellings throw
     * IllegalArgumentException; no guessing.
     */
    public static FieldType getFieldType(String xmlType) {
        FieldType result = BY_XML_NAME.get(xmlType);
        if (result == null) {
            throw new IllegalArgumentException("Unknown PDS4 data_type: " + xmlType);
        }
        return result;
    }

    /**
     * Date or time text, including legacy PDS4 date-type spellings.
     */
    public boolean isTime() {
        return xmlType.startsWith("ASCII_Date") || this == ASCII_TIME;
    }

    /**
     * Is this type a number (including recognized complex/bit encodings)?
     */
    public boolean isNumeric() {
        return xmlType.startsWith("Signed") || xmlType.startsWith("Unsigned")
                || xmlType.startsWith("IEEE754") || xmlType.startsWith("Complex")
                || this == ASCII_INTEGER || this == ASCII_NONNEGATIVE_INTEGER
                || this == ASCII_REAL || this == ASCII_NUMERIC_BASE2
                || this == ASCII_NUMERIC_BASE8 || this == ASCII_NUMERIC_BASE16;
    }

    /**
     * Physical byte width; zero means variable-width text or packed-bit metadata needed.
     */
    public int getByteWidth() {
        if (this == SIGNEDBYTE || this == UNSIGNEDBYTE) {
            return 1;
        }
        if (xmlType.matches("(?:Signed|Unsigned)(?:MSB|LSB)[248]")) {
            return xmlType.charAt(xmlType.length() - 1) - '0';
        }
        if (xmlType.startsWith("IEEE754")) {
            return xmlType.endsWith("Single") ? 4 : 8;
        }
        if (xmlType.startsWith("Complex")) {
            return xmlType.endsWith("16") ? 16 : 8;
        }
        return 0;
    }

    /**
     * Multi-byte numeric byte order; null when order is inapplicable/unspecified.
     */
    public ByteOrder getByteOrder() {
        if (xmlType.contains("LSB")) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        if (xmlType.contains("MSB")) {
            return ByteOrder.BIG_ENDIAN;
        }
        return null;
    }

    /**
     * Whether the current reader implements this encoding.
     */
    public boolean isSupported() {
        switch (this) {
            case COMPLEXLSB8:
            case COMPLEXLSB16:
            case COMPLEXMSB8:
            case COMPLEXMSB16:
            case SIGNEDBITSTRING:
            case UNSIGNEDBITSTRING:
            case UNKNOWN:
                return false;
            default:
                return true;
        }
    }
}
