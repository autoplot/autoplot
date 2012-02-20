package org.virbo.cdf;

/**
 * This class defines the constants used by the CDF library and CDF Java 
 * APIs, and it mimics the cdf.h include file from the cdf distribution.  
 *
 * @version 1.0
 * @author Phil Williams, QSS Group Inc/RITSS <BR>
 *         Mike Liu, RSTX
 */
public interface CDFConstants {

    /*--------*/
    /* Limits */
    /*--------*/

    public static final long CDF_MIN_DIMS = 0;
    public static final long CDF_MAX_DIMS = 10;


    /*---------*/
    /* Lengths */
    /*---------*/

    public static final long CDF_VAR_NAME_LEN   = 64;
    public static final long CDF_ATTR_NAME_LEN  = 64;
    public static final long CDF_COPYRIGHT_LEN  = 256;
    public static final long CDF_STATUSTEXT_LEN = 80;
    public static final long CDF_PATHNAME_LEN   = 128;

    public static final long EPOCH_STRING_LEN   = 24;
    public static final long EPOCH1_STRING_LEN  = 16;
    public static final long EPOCH2_STRING_LEN  = 14;
    public static final long EPOCH3_STRING_LEN  = 24;
    public static final long EPOCHx_STRING_MAX  = 50;
    public static final long EPOCHx_FORMAT_MAX  = 68;

    public static final long EPOCH_STRING_LEN_EXTEND   = 36;
    public static final long EPOCH1_STRING_LEN_EXTEND  = 24;
    public static final long EPOCH2_STRING_LEN_EXTEND  = 14;
    public static final long EPOCH3_STRING_LEN_EXTEND  = 36;

    /*----------------*/
    /* CDF data types */
    /*----------------*/

    public static final long CDF_INT1   = 1;
    public static final long CDF_INT2   = 2;
    public static final long CDF_INT4   = 4;
    public static final long CDF_UINT1  = 11;
    public static final long CDF_UINT2  = 12;
    public static final long CDF_UINT4  = 14;
    public static final long CDF_REAL4  = 21;
    public static final long CDF_REAL8  = 22;
    public static final long CDF_EPOCH  = 31;    // Standard style
    public static final long CDF_EPOCH16= 32;    // Standard style
    public static final long CDF_TT2000=  33;
    public static final long CDF_BYTE   = 41;    // Same as CDF_INT1 (signed)
    public static final long CDF_FLOAT  = 44;    // Same as CDF_REAL4
    public static final long CDF_DOUBLE = 45;    // Same as CDF_REAL8
    public static final long CDF_CHAR   = 51;    // a "string" data type
    public static final long CDF_UCHAR  = 52;    // a "string" data type

    /*-------------------------------------------------------------*/
    /* Encoding (for data only, everything else is network encoding */
    /*-------------------------------------------------------------*/

    public static final long NETWORK_ENCODING      =  1;
    public static final long SUN_ENCODING          =  2;
    public static final long VAX_ENCODING          =  3;
    public static final long DECSTATION_ENCODING   =  4;
    public static final long SGi_ENCODING          =  5;
    public static final long IBMPC_ENCODING        =  6;
    public static final long IBMRS_ENCODING        =  7;
    public static final long HOST_ENCODING         =  8;
    public static final long MAC_ENCODING          =  9;
    public static final long HP_ENCODING           =  11;
    public static final long NeXT_ENCODING         =  12;
    public static final long ALPHAOSF1_ENCODING    =  13;
    public static final long ALPHAVMSd_ENCODING    =  14;
    public static final long ALPHAVMSg_ENCODING    =  15;
    public static final long ALPHAVMSi_ENCODING    =  16;

    /*----------*/
    /* Decoding */
    /*----------*/

    public static final long NETWORK_DECODING        = NETWORK_ENCODING;
    public static final long SUN_DECODING            = SUN_ENCODING;
    public static final long VAX_DECODING            = VAX_ENCODING;
    public static final long DECSTATION_DECODING     = DECSTATION_ENCODING;
    public static final long SGi_DECODING            = SGi_ENCODING;
    public static final long IBMPC_DECODING          = IBMPC_ENCODING;
    public static final long IBMRS_DECODING          = IBMRS_ENCODING;
    public static final long HOST_DECODING           = HOST_ENCODING;
    public static final long MAC_DECODING            = MAC_ENCODING;
    public static final long HP_DECODING             = HP_ENCODING;
    public static final long NeXT_DECODING           = NeXT_ENCODING;
    public static final long ALPHAOSF1_DECODING      = ALPHAOSF1_ENCODING;
    public static final long ALPHAVMSd_DECODING      = ALPHAVMSd_ENCODING;
    public static final long ALPHAVMSg_DECODING      = ALPHAVMSg_ENCODING;
    public static final long ALPHAVMSi_DECODING      = ALPHAVMSi_ENCODING;

    /*------------------------------------*/
    /* Record or dimension variance flags */
    /*------------------------------------*/

    public static final long VARY   = -1;
    public static final long NOVARY = 0;

    /*------------*/
    /* Majorities */
    /*------------*/

    public static final long ROW_MAJOR    = 1;
    public static final long COLUMN_MAJOR = 2;

    /*------------------*/
    /* CDF file formats */
    /*------------------*/

    public static final long SINGLE_FILE = 1;
    public static final long MULTI_FILE  = 2;

    /*------------------*/
    /* Attribute scopes */
    /*------------------*/

    public static final long GLOBAL_SCOPE   = 1;
    public static final long VARIABLE_SCOPE = 2;

    /*----------------*/
    /* Readonly modes */
    /*----------------*/

    public static final long READONLYon  = -1;
    public static final long READONLYoff = 0;

    /*--------*/
    /* zMODEs */
    /*--------*/

    public static final long zMODEoff = 0;
    public static final long zMODEon1 = 1;
    public static final long zMODEon2 = 2;

    /*--------------------------------------------------*/
    /*  Negative to positive floating point zero modes. */
    /*--------------------------------------------------*/

    public static final long NEGtoPOSfp0on   = -1;
    public static final long NEGtoPOSfp0off  = 0; 

    /*---------------------*/
    /* Backward file modes */
    /*---------------------*/

    public static final long BACKWARDFILEon  = 1;
    public static final long BACKWARDFILEoff = 0;

    /*---------------------*/
    /* Checksum modes */
    /*---------------------*/

    public static final long NO_CHECKSUM = 0;
    public static final long NONE_CHECKSUM = 0;
    public static final long MD5_CHECKSUM  = 1;
    public static final long OTHER_CHECKSUM  = 2;

    /*-----------------------------------*/
    /* Compression/sparseness constants. */
    /*-----------------------------------*/

    public static final long CDF_MAX_PARMS           = 5;
    public static final long NO_COMPRESSION          = 0;
    public static final long RLE_COMPRESSION         = 1;
    public static final long HUFF_COMPRESSION        = 2;
    public static final long AHUFF_COMPRESSION       = 3;

    // Compression `4' used to be RICE.  Do not reuse!

    public static final long GZIP_COMPRESSION        = 5;

    public static final long RLE_OF_ZEROs            = 0;
    public static final long OPTIMAL_ENCODING_TREES  = 0;
    public static final long NO_SPARSEARRAYS         = 0; 
    public static final long NO_SPARSERECORDS        = 0; 
    public static final long PAD_SPARSERECORDS       = 1; 
    public static final long PREV_SPARSERECORDS      = 2; 

    /*-------------------------------------------*/
    /* The default pad values for each data type */ 
    /*-------------------------------------------*/

    public static final byte   DEFAULT_BYTE_PADVALUE	= (byte)0;
    public static final byte   DEFAULT_INT1_PADVALUE	= (byte)0;
    public static final short  DEFAULT_UINT1_PADVALUE	= (short)0;
    public static final short  DEFAULT_INT2_PADVALUE	= (short)0;
    public static final int    DEFAULT_UINT2_PADVALUE	= 0;
    public static final int    DEFAULT_INT4_PADVALUE	= 0;
    public static final long   DEFAULT_UINT4_PADVALUE	= 0L;
    public static final float  DEFAULT_REAL4_PADVALUE	= (float)0.0;
    public static final float  DEFAULT_FLOAT_PADVALUE	= (float)0.0;
    public static final double DEFAULT_REAL8_PADVALUE	= (double)0.0;
    public static final double DEFAULT_DOUBLE_PADVALUE	= (double)0.0;
    public static final char   DEFAULT_CHAR_PADVALUE	= ' ';
    public static final char   DEFAULT_UCHAR_PADVALUE	= ' ';
    public static final double DEFAULT_EPOCH_PADVALUE	= (double)0.0;

    /*-------------------*/
    /* Invalid constants */ 
    /*-------------------*/

    public static final long ILLEGAL_EPOCH_VALUE     = -1;

    /*------------------------------------*/
    /* Status Codes - informational codes */
    /*------------------------------------*/

    public static final long VIRTUAL_RECORD_DATA          = 1001;
    public static final long DID_NOT_COMPRESS             = 1002;
    public static final long VAR_ALREADY_CLOSED           = 1003;
    public static final long SINGLE_FILE_FORMAT           = 1004;
    public static final long NO_PADVALUE_SPECIFIED        = 1005;
    public static final long NO_VARS_IN_CDF               = 1006;
    public static final long MULTI_FILE_FORMAT            = 1007;
    public static final long SOME_ALREADY_ALLOCATED       = 1008;
    public static final long PRECEEDING_RECORDS_ALLOCATED = 1009;

    public static final long CDF_OK                       = 0;

    public static final long ATTR_NAME_TRUNC              = -1001;
    public static final long CDF_NAME_TRUNC               = -1002;
    public static final long VAR_NAME_TRUNC               = -1003;
    public static final long NEGATIVE_FP_ZERO             = -1004;
					/* -1005 unused. */
    public static final long FORCED_PARAMETER             = -1006;
    public static final long NA_FOR_VARIABLE              = -1007;

    public static final long CDF_WARN                     = -2000;

    // CDF Errors

    public static final long ATTR_EXISTS                  = -2001;
    public static final long BAD_CDF_ID                   = -2002;
    public static final long BAD_DATA_TYPE                = -2003;
    public static final long BAD_DIM_SIZE                 = -2004;
    public static final long BAD_DIM_INDEX                = -2005;
    public static final long BAD_ENCODING                 = -2006;
    public static final long BAD_MAJORITY                 = -2007;
    public static final long BAD_NUM_DIMS                 = -2008;
    public static final long BAD_REC_NUM                  = -2009;
    public static final long BAD_SCOPE                    = -2010;
    public static final long BAD_NUM_ELEMS                 = -2011;
    public static final long CDF_OPEN_ERROR               = -2012;
    public static final long CDF_EXISTS                   = -2013;
    public static final long BAD_FORMAT                   = -2014;
    public static final long BAD_ALLOCATE_RECS            = -2015;
    public static final long BAD_CDF_EXTENSION            = -2016;
    public static final long NO_SUCH_ATTR                 = -2017;
    public static final long NO_SUCH_ENTRY                = -2018;
    public static final long NO_SUCH_VAR                  = -2019;
    public static final long VAR_READ_ERROR               = -2020;
    public static final long VAR_WRITE_ERROR              = -2021;
    public static final long BAD_ARGUMENT                 = -2022;
    public static final long IBM_PC_OVERFLOW              = -2023;
    public static final long TOO_MANY_VARS                = -2024;
    public static final long VAR_EXISTS                   = -2025;
    public static final long BAD_MALLOC                   = -2026;
    public static final long NOT_A_CDF                    = -2027;
    public static final long CORRUPTED_V2_CDF             = -2028;
    public static final long VAR_OPEN_ERROR               = -2029;
    public static final long BAD_INITIAL_RECS             = -2030;
    public static final long BAD_BLOCKING_FACTOR          = -2031;
    public static final long END_OF_VAR                   = -2032;
					/* -2033 unused. */
    public static final long BAD_CDFSTATUS                = -2034;
    public static final long CDF_INTERNAL_ERROR           = -2035;
    public static final long BAD_NUM_VARS                 = -2036;
    public static final long BAD_REC_COUNT                = -2037;
    public static final long BAD_REC_INTERVAL             = -2038;
    public static final long BAD_DIM_COUNT                = -2039;
    public static final long BAD_DIM_INTERVAL             = -2040;
    public static final long BAD_VAR_NUM                  = -2041;
    public static final long BAD_ATTR_NUM                 = -2042;
    public static final long BAD_ENTRY_NUM                = -2043;
    public static final long BAD_ATTR_NAME                = -2044;
    public static final long BAD_VAR_NAME                 = -2045;
    public static final long NO_ATTR_SELECTED             = -2046;
    public static final long NO_ENTRY_SELECTED            = -2047;
    public static final long NO_VAR_SELECTED              = -2048;
    public static final long BAD_CDF_NAME                 = -2049;
					/* -2050 unused. */
    public static final long CANNOT_CHANGE                = -2051;
    public static final long NO_STATUS_SELECTED           = -2052;
    public static final long NO_CDF_SELECTED              = -2053;
    public static final long READ_ONLY_DISTRIBUTION       = -2054;
    public static final long CDF_CLOSE_ERROR              = -2055;
    public static final long VAR_CLOSE_ERROR              = -2056;
					/* -2057 unused. */
    public static final long BAD_FNC_OR_ITEM              = -2058;
					/* -2059 unused. */
    public static final long ILLEGAL_ON_V1_CDF            = -2060;
					/* -2061 unused. */
					/* -2062 unused. */
    public static final long BAD_CACHE_SIZE               = -2063;
					/* -2064 unused. */
					/* -2065 unused. */
    public static final long CDF_CREATE_ERROR             = -2066;
    public static final long NO_SUCH_CDF                  = -2067;
    public static final long VAR_CREATE_ERROR             = -2068;
					/* -2069 unused. */
    public static final long READ_ONLY_MODE               = -2070;
    public static final long ILLEGAL_IN_zMODE             = -2071;
    public static final long BAD_zMODE                    = -2072;
    public static final long BAD_READONLY_MODE            = -2073;
    public static final long CDF_READ_ERROR               = -2074;
    public static final long CDF_WRITE_ERROR              = -2075;
    public static final long ILLEGAL_FOR_SCOPE            = -2076;
    public static final long NO_MORE_ACCESS               = -2077;
					/* -2078 unused. */
    public static final long BAD_DECODING                 = -2079;
					/* -2080 unused. */
    public static final long BAD_NEGtoPOSfp0_MODE         = -2081;
    public static final long UNSUPPORTED_OPERATION        = -2082;
    public static final long CDF_SAVE_ERROR		  = -2083;
    public static final long VAR_SAVE_ERROR		  = -2084;
					/* -2085 unused. */
    public static final long NO_WRITE_ACCESS              = -2086;
    public static final long NO_DELETE_ACCESS             = -2087;
    public static final long CDF_DELETE_ERROR             = -2088;
    public static final long VAR_DELETE_ERROR             = -2089;
    public static final long UNKNOWN_COMPRESSION          = -2090;
    public static final long CANNOT_COMPRESS              = -2091;
    public static final long DECOMPRESSION_ERROR          = -2092;
    public static final long COMPRESSION_ERROR            = -2093;
					/* -2094 unused. */
					/* -2095 unused. */
    public static final long EMPTY_COMPRESSED_CDF         = -2096;
    public static final long BAD_COMPRESSION_PARM         = -2097;
    public static final long UNKNOWN_SPARSENESS           = -2098;
    public static final long CANNOT_SPARSERECORDS         = -2099;
    public static final long CANNOT_SPARSEARRAYS          = -2100;
    public static final long TOO_MANY_PARMS               = -2101;
    public static final long NO_SUCH_RECORD               = -2102;
    public static final long CANNOT_ALLOCATE_RECORDS      = -2103;
    public static final long CANNOT_COPY		  = -2104;
					/* -2105 unused. */
    public static final long SCRATCH_DELETE_ERROR         = -2106;
    public static final long SCRATCH_CREATE_ERROR         = -2107;
    public static final long SCRATCH_READ_ERROR           = -2108;
    public static final long SCRATCH_WRITE_ERROR          = -2109;
    public static final long BAD_SPARSEARRAYS_PARM        = -2110;
    public static final long BAD_SCRATCH_DIR              = -2111;
    public static final long DATATYPE_MISMATCH            = -2112;
    public static final long NOT_A_CDF_OR_NOT_SUPPORTED   = -2113;
    public static final long CORRUPTED_V3_CDF             = -2223;
    public static final long ILLEGAL_EPOCH_FIELD          = -2224;
    public static final long BAD_CHECKSUM                 = -2225;
    public static final long CHECKSUM_ERROR               = -2226;
    public static final long CHECKSUM_NOT_ALLOWED         = -2227;

    /*----------------------------------------------------------------*/
    /*  Functions (for INTERNAL interface).                           */
    /*  NOTE: These values must be different from those of the items. */
    /*----------------------------------------------------------------*/

    public static final long CREATE_ =			1001;
    public static final long OPEN_ =			1002;
    public static final long DELETE_ =			1003;
    public static final long CLOSE_ =			1004;
    public static final long SELECT_ =			1005;
    public static final long CONFIRM_ =		        1006;
    public static final long GET_ =			1007;
    public static final long PUT_ =			1008;
    public static final long SAVE_ =			1009;
    public static final long BACKWARD_ =                1010;
    public static final long GETCDFFILEBACKWARD_ =      1011;
    public static final long CHECKSUM_ =                1012;
    public static final long GETCDFCHECKSUM_ =          1013;

    public static final long NULL_ =			1000;

    /*-------------------------------------------------------------------*/
    /* Items on which functions are performed (for INTERNAL interface).  */
    /* NOTE: These values must be different from those of the functions. */
    /*-------------------------------------------------------------------*/
    
    public static final long CDF_ =                    1;
    public static final long CDF_NAME_ =               2;
    public static final long CDF_ENCODING_ =           3;
    public static final long CDF_DECODING_ =	       4;
    public static final long CDF_MAJORITY_ =           5;
    public static final long CDF_FORMAT_ =             6;
    public static final long CDF_COPYRIGHT_ =          7;
    public static final long CDF_NUMrVARS_ =           8;
    public static final long CDF_NUMzVARS_ =           9;
    public static final long CDF_NUMATTRS_ =           10;
    public static final long CDF_NUMgATTRS_ =          11;
    public static final long CDF_NUMvATTRS_ =          12;
    public static final long CDF_VERSION_ =            13;
    public static final long CDF_RELEASE_ =            14;
    public static final long CDF_INCREMENT_ =          15;
    public static final long CDF_STATUS_ =             16;
    public static final long CDF_READONLY_MODE_ =      17;
    public static final long CDF_zMODE_ =              18;
    public static final long CDF_NEGtoPOSfp0_MODE_ =   19;
    public static final long LIB_COPYRIGHT_ =          20;
    public static final long LIB_VERSION_ =            21;
    public static final long LIB_RELEASE_ =            22;
    public static final long LIB_INCREMENT_ =          23;
    public static final long LIB_subINCREMENT_ =       24;
    public static final long rVARs_NUMDIMS_ =          25;
    public static final long rVARs_DIMSIZES_ =         26;
    public static final long rVARs_MAXREC_ =           27;
    public static final long rVARs_RECDATA_ =	       28;
    public static final long rVARs_RECNUMBER_ =        29;
    public static final long rVARs_RECCOUNT_ =         30;
    public static final long rVARs_RECINTERVAL_ =      31;
    public static final long rVARs_DIMINDICES_ =       32;
    public static final long rVARs_DIMCOUNTS_ =        33;
    public static final long rVARs_DIMINTERVALS_ =     34;
    public static final long rVAR_ =                   35;
    public static final long rVAR_NAME_ =              36;
    public static final long rVAR_DATATYPE_ =          37;
    public static final long rVAR_NUMELEMS_ =          38;
    public static final long rVAR_RECVARY_ =           39;
    public static final long rVAR_DIMVARYS_ =          40;
    public static final long rVAR_NUMBER_ =            41;
    public static final long rVAR_DATA_ =              42;
    public static final long rVAR_HYPERDATA_ =         43;
    public static final long rVAR_SEQDATA_ =           44;
    public static final long rVAR_SEQPOS_ =            45;
    public static final long rVAR_MAXREC_ =            46;
    public static final long rVAR_MAXallocREC_ =       47;
    public static final long rVAR_DATASPEC_ =          48;
    public static final long rVAR_PADVALUE_ =          49;
    public static final long rVAR_INITIALRECS_ =       50;
    public static final long rVAR_BLOCKINGFACTOR_ =    51;
    public static final long rVAR_nINDEXRECORDS_ =     52;
    public static final long rVAR_nINDEXENTRIES_ =     53;
    public static final long rVAR_EXISTENCE_ =	       54;
    public static final long zVARs_MAXREC_ =	       55;
    public static final long zVARs_RECDATA_ =	       56;
    public static final long zVAR_ =                   57;
    public static final long zVAR_NAME_ =              58;
    public static final long zVAR_DATATYPE_ =          59;
    public static final long zVAR_NUMELEMS_ =          60;
    public static final long zVAR_NUMDIMS_ =           61;
    public static final long zVAR_DIMSIZES_ =          62;
    public static final long zVAR_RECVARY_ =           63;
    public static final long zVAR_DIMVARYS_ =          64;
    public static final long zVAR_NUMBER_ =            65;
    public static final long zVAR_DATA_ =              66;
    public static final long zVAR_HYPERDATA_ =         67;
    public static final long zVAR_SEQDATA_ =           68;
    public static final long zVAR_SEQPOS_ =            69;
    public static final long zVAR_MAXREC_ =            70;
    public static final long zVAR_MAXallocREC_ =       71;
    public static final long zVAR_DATASPEC_ =          72;
    public static final long zVAR_PADVALUE_ =          73;
    public static final long zVAR_INITIALRECS_ =       74;
    public static final long zVAR_BLOCKINGFACTOR_ =    75;
    public static final long zVAR_nINDEXRECORDS_ =     76;
    public static final long zVAR_nINDEXENTRIES_ =     77;
    public static final long zVAR_EXISTENCE_ =         78;
    public static final long zVAR_RECNUMBER_ =         79;
    public static final long zVAR_RECCOUNT_ =          80;
    public static final long zVAR_RECINTERVAL_ =       81;
    public static final long zVAR_DIMINDICES_ =        82;
    public static final long zVAR_DIMCOUNTS_ =         83;
    public static final long zVAR_DIMINTERVALS_ =      84;
    public static final long ATTR_ =                   85;
    public static final long ATTR_SCOPE_ =             86;
    public static final long ATTR_NAME_ =              87;
    public static final long ATTR_NUMBER_ =            88;
    public static final long ATTR_MAXgENTRY_ =         89;
    public static final long ATTR_NUMgENTRIES_ =       90;
    public static final long ATTR_MAXrENTRY_ =         91;
    public static final long ATTR_NUMrENTRIES_ =       92;
    public static final long ATTR_MAXzENTRY_ =         93;
    public static final long ATTR_NUMzENTRIES_ =       94;
    public static final long ATTR_EXISTENCE_ =	       95;
    public static final long gENTRY_ =                 96;
    public static final long gENTRY_EXISTENCE_ =       97;
    public static final long gENTRY_DATATYPE_ =        98;
    public static final long gENTRY_NUMELEMS_ =        99;
    public static final long gENTRY_DATASPEC_ =        100;
    public static final long gENTRY_DATA_ =            101;
    public static final long rENTRY_ =                 102;
    public static final long rENTRY_NAME_ =	       103;
    public static final long rENTRY_EXISTENCE_ =       104;
    public static final long rENTRY_DATATYPE_ =        105;
    public static final long rENTRY_NUMELEMS_ =        106;
    public static final long rENTRY_DATASPEC_ =        107;
    public static final long rENTRY_DATA_ =            108;
    public static final long zENTRY_ =                 109;
    public static final long zENTRY_NAME_ =	       110;
    public static final long zENTRY_EXISTENCE_ =       111;
    public static final long zENTRY_DATATYPE_ =        112;
    public static final long zENTRY_NUMELEMS_ =        113;
    public static final long zENTRY_DATASPEC_ =        114;
    public static final long zENTRY_DATA_ =            115;
    public static final long STATUS_TEXT_ =            116;
    public static final long CDF_CACHESIZE_ =	       117;
    public static final long rVARs_CACHESIZE_ =        118;
    public static final long zVARs_CACHESIZE_ =	       119;
    public static final long rVAR_CACHESIZE_ =	       120;
    public static final long zVAR_CACHESIZE_ =	       121;
    public static final long zVARs_RECNUMBER_ =	       122;
    public static final long rVAR_ALLOCATERECS_ =	123;
    public static final long zVAR_ALLOCATERECS_ =	124;
    public static final long DATATYPE_SIZE_ =		125;
    public static final long CURgENTRY_EXISTENCE_ =	126;
    public static final long CURrENTRY_EXISTENCE_ =	127;
    public static final long CURzENTRY_EXISTENCE_ =	128;
    public static final long CDF_INFO_ =		129;
    public static final long CDF_COMPRESSION_ =	        130;
    public static final long zVAR_COMPRESSION_ =	131;
    public static final long zVAR_SPARSERECORDS_ =	132;
    public static final long zVAR_SPARSEARRAYS_ =	133;
    public static final long zVAR_ALLOCATEBLOCK_ =	134;
    public static final long zVAR_NUMRECS_ =		135;
    public static final long zVAR_NUMallocRECS_ =	136;
    public static final long rVAR_COMPRESSION_ =	137;
    public static final long rVAR_SPARSERECORDS_ =	138;
    public static final long rVAR_SPARSEARRAYS_ =	139;
    public static final long rVAR_ALLOCATEBLOCK_ =	140;
    public static final long rVAR_NUMRECS_ =		141;
    public static final long rVAR_NUMallocRECS_ =	142;
    public static final long rVAR_ALLOCATEDFROM_ =	143;
    public static final long rVAR_ALLOCATEDTO_ =	144;
    public static final long zVAR_ALLOCATEDFROM_ =	145;
    public static final long zVAR_ALLOCATEDTO_ =	146;
    public static final long zVAR_nINDEXLEVELS_ =	147;
    public static final long rVAR_nINDEXLEVELS_ =	148;
    public static final long CDF_SCRATCHDIR_ =		149;
    public static final long rVAR_RESERVEPERCENT_ =	150;
    public static final long zVAR_RESERVEPERCENT_ =	151;
    public static final long rVAR_RECORDS_ =		152;
    public static final long zVAR_RECORDS_ =		153;
    public static final long STAGE_CACHESIZE_ =	        154;
    public static final long COMPRESS_CACHESIZE_ =	155;
    public static final long CDF_CHECKSUM_ =            156;

    public static final long CDFwithSTATS_ = 200;   /* For CDF internal use only! */
    public static final long CDF_ACCESS_ =   201;   /* For CDF internal use only! */
  
}
