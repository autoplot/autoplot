package gov.nasa.gsfc.voyager.cdf;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.zip.*;
public abstract class CDFImpl implements java.io.Serializable {
    static final double JANUARY_1_1970;
    static {
        int offset = 0;
        for (int year=0; year < 1970; year++) {
            int days = 365;
            if ((year%4 == 0)) {
                days++;
                if ((year%100 == 0)) {
                    days--;
                    if ((year%400 == 0)) days++;
                }
            }
            offset += days;
        }
        JANUARY_1_1970 = offset*8.64e7;
    }
    /**
     * CDF constants
     */
    public static final int GDR_RECORD = 2;
    public static final int FLAGS_MAJORITY_MASK = 0x01;
    public static final int ROW_MAJOR = 1;
    public static final int VVR_RECORD_TYPE = 7;
    public static final int CVVR_RECORD_TYPE = 13;
    public static final long longInt = ((long)1) << 32;
    /**
     * CDF offsets
     */
    int offset_NEXT_VDR;
    int offset_NEXT_ADR;
    int offset_ATTR_NAME;
    int offset_SCOPE;
    int offset_AgrEDRHead;
    int offset_AzEDRHead;
    int offset_NEXT_AEDR;
    int offset_ENTRYNUM;
    int offset_ATTR_DATATYPE;
    int offset_ATTR_NUM_ELEMENTS;
    int offset_VALUE;
    int offset_VAR_NAME;
    int offset_VAR_NUM_ELEMENTS;
    int offset_NUM;
    int offset_FLAGS;
    int offset_BLOCKING_FACTOR;
    int offset_VAR_DATATYPE;
    int offset_zNumDims;
    int offset_FIRST_VXR;
    int offset_NEXT_VXR;
    int offset_NENTRIES;
    int offset_NUSED;
    int offset_FIRST;
    int offset_RECORD_TYPE;
    int offset_RECORDS;
    int offset_CSIZE;
    int offset_CDATA;
    /**
     * CDF metadata
     */
    int version;
    int release;
    int encoding;
    int flags;
    int increment;
    transient ByteOrder byteOrder;
    boolean bigEndian;
    long GDROffset;
    /**
     * Extracted from GDR
     */
    long rVDRHead;
    long zVDRHead;
    long ADRHead;
    int numberOfRVariables;
    int numberOfAttributes;
    int numberOfZVariables;
    int[] rDimSizes;

    transient ByteBuffer buf;
    protected String[] varNames;
    protected HashMap variableTable;
    HashMap attributeTable;
    protected CDF thisCDF;
    protected HashMap timesMap = new HashMap();
    protected HashMap extendedPrecisionTimesMap = new HashMap();
    protected HashMap timeVariableMap = new HashMap();

    protected CDFImpl(ByteBuffer buf) {
        this.buf = buf;
    }

    protected ByteBuffer getRecord(int offset)  {
        ByteBuffer _buf = buf.duplicate();
        _buf.position(offset);
        return _buf.slice();
    }

    public ByteOrder getByteOrder() {return byteOrder;}

    public boolean rowMajority() {
        return ((flags & FLAGS_MAJORITY_MASK) == ROW_MAJOR);
    }

    /**
     * returns name to Variable map
     */
    protected HashMap variables()  {
        if (variableTable != null) return variableTable;
        int [] offsets = new int[] {(int)zVDRHead, (int)rVDRHead};
        String [] vtypes = {"z", "r"};
        HashMap table = new HashMap();
        Vector v = new Vector();
        for (int vtype = 0; vtype < 2; vtype++) {
            int offset = offsets[vtype];
            if (offset == 0) continue;
            ByteBuffer _buf = getRecord(offset);
            while (true) {
                _buf.position(offset_NEXT_VDR);
                int next = lowOrderInt(_buf);
                CDFVariable cdfv = new CDFVariable(offset, vtypes[vtype]);
                String name = cdfv.getName();
                v.add(name);
                table.put(name, cdfv);
                if (next == 0) break;
                offset = next;
                _buf = getRecord(offset);
            }
        }
        varNames = new String[v.size()];
        for (int i = 0; i < v.size(); i++) {
            varNames[i] = (String)v.elementAt(i);
        }
        variableTable = table;
        return table;
    }

    /**
     * returns variable names in a String[]
     */
    public String [] getVariableNames() {
        String [] sa = new String [varNames.length];
        for (int i = 0; i < sa.length; i++) {
            sa[i] = varNames[i];
        }
        return sa;
    }

    /**
     * returns the object that implements the Variable interface for
     * the named variable
     */
    public Variable getVariable(String name) {
        return (Variable)variableTable.get(name);
    }

    /**
     * returns variable names of a given VAR_TYPE in a String[]
     */
    public String [] getVariableNames(String type) {
        Vector vars = new Vector();
        for (int i = 0; i < varNames.length; i++) {
            Vector v = (Vector)getAttribute(varNames[i],"VAR_TYPE");
            if (v == null) continue;
            if (v.size() == 0) continue;
            String s = (String)v.elementAt(0);
            if (s.equals(type)) vars.add(varNames[i]);
        }
        String [] sa = new String[vars.size()];
        for (int i = 0; i < sa.length; i++) {
            sa[i] = (String)vars.elementAt(i);
        }
        return sa;
    }

    /**
     * returns names of global attributes in a String[]
     */
    public String [] globalAttributeNames() {
        Vector vec = new Vector();
        if (attributeTable == null) return new String[0];
        Set set = attributeTable.keySet();
        Iterator iter = set.iterator();
        while (iter.hasNext()) {
            CDFAttribute attr = (CDFAttribute)attributeTable.get(iter.next());
            if (attr.isGlobal()) {
                vec.add(attr.name);
            }
        }
        String [] sa = new String [vec.size()];
        for (int i = 0; i < vec.size(); i++) {
            sa[i] = (String)vec.elementAt(i);
        }
        return sa;
    }

    /**
     * returns names of variable attributes in a String[]
     */
    public String [] variableAttributeNames(String name) {
        CDFVariable var = (CDFVariable)variableTable.get(name);
        if (var == null) return null;
        String [] sa = new String [var.attributes.size()];
        for (int i = 0; i < sa.length; i++) {
            AttributeEntry ae = (AttributeEntry)var.attributes.elementAt(i);
            sa[i] = ae.attribute;
        }
        return sa;
    }

    /**
     * returns value of the named global attribute
     */
    public Object getAttribute(String atr) {
        CDFAttribute a = (CDFAttribute)attributeTable.get(atr);
        if (a == null) return null;
        if (!a.isGlobal()) return null;
        if (a.gEntries.size() == 0) return null;
        AttributeEntry ae = (AttributeEntry)a.gEntries.elementAt(0);
        if (ae.stringValue != null) {
            String [] sa = new String[a.gEntries.size()];
            for (int i = 0; i < a.gEntries.size(); i++) {
                ae = (AttributeEntry)a.gEntries.elementAt(i);
                sa[i] = ae.stringValue;
            }
            return sa;
        }
        return ae.value;
    }

    /**
     * returns value of the named attribute for the named variable
     */
    public Object getAttribute(String var, String atr) {
        CDFVariable c = (CDFVariable)variableTable.get(var);
        if (c == null) return null;
        Vector attrs = c.attributes;
        Vector values = new Vector();
        for (int i = 0; i < attrs.size(); i++) {
            AttributeEntry ae = (AttributeEntry)attrs.elementAt(i);
            if (ae.attribute.equals(atr)) {
                if (ae.stringValue != null) values.add(ae.stringValue);
                if (ae.stringValue == null) values.add(ae.value);
            }
        }
        return values;
    }

    /**
     * returns Variable object associated with a given type at a given number
     */
    Variable getCDFVariable(String vtype, int number) {
        Set set = variableTable.keySet();
        Iterator iter = set.iterator();
        while (iter.hasNext()) {
            CDFVariable var = (CDFVariable)variableTable.get(iter.next());
            if (var.vtype.equals(vtype)) {
                if (var.number == number) return var;
            }
        }
        return null;
    }

    /**
     * returns name to Attribute object map
     */
    HashMap attributes()  {
        if (attributeTable != null) return attributeTable;
        int offset = (int)ADRHead;
        if (offset == 0) return null;
        HashMap table = new HashMap();
        ByteBuffer _buf = getRecord(offset);
        while (true) {
            _buf.position(offset_NEXT_ADR);
            int next = lowOrderInt(_buf);
            CDFAttribute cdfa = new CDFAttribute(offset);
            table.put(cdfa.getName(), cdfa);
            if (next == 0) break;
            offset = next;
            _buf = getRecord(offset);
        }
        attributeTable = table;
        return table;
    }
    /**
     * CDFAttribute class
     */
    public class CDFAttribute implements java.io.Serializable, Attribute {
        String name;
        int scope;
        Vector zEntries = new Vector();
        Vector gEntries = new Vector();
        public CDFAttribute(int offset)  {
            name = getString(offset + offset_ATTR_NAME);
            ByteBuffer _buf = getRecord(offset);
            _buf.position(offset_SCOPE);
            scope = _buf.getInt();
            _buf.position(offset_AgrEDRHead);
            int n = lowOrderInt(_buf);
            if (n > 0) {
                gEntries = getAttributeEntries(n);
                if ((scope == 2) || (scope == 4)) { // variable scope
                    linkToVariables(gEntries, "r");
                }
            }
            _buf.position(offset_AzEDRHead);
            n = lowOrderInt(_buf);
            if (n > 0) {
                zEntries = getAttributeEntries(n);
                linkToVariables(zEntries, "z");
            }
        }

        /**
         * returns name of the attribute
         */
        public String getName() {return name;}

        /**
         * returns attribute entries
         */
        public Vector getAttributeEntries(int offset) {
            if (offset == 0) return null;
            Vector list = new Vector();
            ByteBuffer _buf = getRecord(offset);
            while (true) {
                _buf.position(offset_NEXT_AEDR);
                int next = lowOrderInt(_buf);
                _buf.position(0);
                AttributeEntry ae = new AttributeEntry(_buf, name);
                list.add(ae);
                if (next == 0) break;
                _buf = getRecord(next);
            }
            return list;
        }
        /**
         * link variable attribute entries to the appropriate variable
         */
        public void linkToVariables(Vector entries, String type) {
            for (int e = 0; e < entries.size(); e++) {
                AttributeEntry ae = (AttributeEntry)entries.elementAt(e);
                CDFVariable var = (CDFVariable)
                       getCDFVariable(type, ae.variableNumber);
                var.attributes.add(ae);
            }
        }

        /**
         * is this a global attribute?
         */
        public boolean isGlobal() {
                return !((scope == 2) || (scope == 4));
        }
    }

    /**
     * AttributeEntry class
     */
    public class AttributeEntry implements java.io.Serializable {
        transient ByteBuffer _buf;
        int variableNumber;
        int type;
        int nelement;
        String attribute;
        String stringValue;
        double [] value;
        public AttributeEntry(ByteBuffer buf, String name) {
            attribute = name;
            _buf = buf.duplicate();
            _buf.position(offset_ENTRYNUM);
            variableNumber = _buf.getInt();
            _buf.position(offset_ATTR_DATATYPE);
            type = _buf.getInt();
            _buf.position(offset_ATTR_NUM_ELEMENTS);
            nelement = _buf.getInt();
            _buf.position(offset_VALUE);
            if (type > 50) {
                byte [] ba = new byte[nelement];
                int i = 0;
                for (; i < nelement; i++) {
                    ba[i] = _buf.get();
                    if (ba[i] == 0) break;
                }
                stringValue = new String(ba, 0, i);
            } else {
                value = getNumberAttribute(type, nelement, _buf, byteOrder);
            }
        }
    }

    /**
     * CDFVariable class
     */
    public class CDFVariable extends java.io.Serializable, Variable {
        int DIMENSION_VARIES = -1;
        public Vector attributes = new Vector();
        String name;
        public int number;
        String vtype;
        int flags;
        int type;
        int numberOfElements;
        protected int numberOfValues;
        public int [] dimensions;
        public boolean [] varies;
        public double[] padValue;
        int offset;
        transient ByteBuffer _buf;
        int dataItemSize;
        int blockingFactor;
        DataLocator locator;
        public CDFVariable(int offset, String vtype) {
            this.offset = offset;
            this.vtype = vtype;
            _buf = getRecord(offset);
            name = getString(offset + offset_VAR_NAME);
            _buf.position(offset_VAR_NUM_ELEMENTS);
            numberOfElements = _buf.getInt();
            _buf.position(offset_NUM);
            number = _buf.getInt();
            _buf.position(offset_FLAGS);
            flags = _buf.getInt();
            _buf.position(offset_BLOCKING_FACTOR);
            blockingFactor = _buf.getInt();
            _buf.position(offset_VAR_DATATYPE);
            type = _buf.getInt();
            numberOfValues = _buf.getInt() + 1;
            _buf.position(offset_zNumDims);
            if (vtype.equals("r")) dimensions = rDimSizes;
            if (vtype.equals("z")) {
                dimensions = new int[_buf.getInt()];
                for (int i = 0; i < dimensions.length; i++) {
                    dimensions[i] = _buf.getInt();
                }
            }
            if (type == DataTypes.EPOCH16) dimensions = new int[] {2};
            varies = new boolean[dimensions.length];
            for (int i = 0; i < dimensions.length; i++) {
                varies[i] = (_buf.getInt() == DIMENSION_VARIES);
            }
            if (type == DataTypes.EPOCH16) varies = new boolean[] {true};
            // PadValue immediately follows DimVarys
            if (padValueSpecified()) {
                padValue = new double[numberOfElements];
                if (type == DataTypes.EPOCH16) padValue = new double[2];
                padValue = getNumberAttribute(type, numberOfElements, _buf, byteOrder);
            }
            dataItemSize = DataTypes.size[type];
            // ignore numberOfElements for numeric data types
            if (DataTypes.isStringType(type)) dataItemSize *= numberOfElements;
            if (numberOfValues == 0) return;
            locator = new DataLocator(_buf, numberOfValues, ((flags & 4) != 0));
        }
        public VariableDataLocator getLocator() {
            return locator;
        }

        /**
         * returns whether row major ordering is in use
         */
        public boolean rowMajority() {
            return CDFImpl.this.rowMajority();
        }

        /**
         * returns whether value of this variable can vary from record to record
         */
        public boolean recordVariance() {
            return ((flags & 1) != 0);
        }

        /**
         * returns whether pad value is specified for this variable
         */
        public boolean padValueSpecified() {
            return ((flags & 2) != 0);
        }

        /**
         * returns whether variable values have been compressed
         */
        public boolean isCompressed() {
            return locator.isReallyCompressed();
        }

        /**
         * returns pad value
         */
        public double[] getPadValue() {
            double [] da = new double[padValue.length];
            System.arraycopy(padValue, 0, da, 0, padValue.length);
            return da;
        }

        /**
         * returns type of values of this variable
         */
        public int getType() {return type;}

        /**
         * returns blocking factor used in compression
         */
        public int getBlockingFactor() {return blockingFactor;}

        /**
         * returns effective rank
         */
        public int getEffectiveRank() {
            int rank = 0;
            for (int i = 0; i < dimensions.length; i++) {
                if (!varies[i]) continue;
                if (dimensions[i] == 1) continue;
                rank++;
            }
            return rank;
        }

        /**
         * returns size of value of this variable
         */
        public int getDataItemSize() {
            int size = dataItemSize;
            for (int i = 0; i < dimensions.length; i++) {
                if (varies[i]) size *= dimensions[i];
            }
            return size;
/*
            switch (dimensions.length) {
                case 0:
                    return size;
                case 1:
                    if (varies[0]) size *= dimensions[0];
                    return size;
                case 2:
                    for (int i = 0; i < 2; i++) {
                        if (varies[i]) size *= dimensions[i];
                    }
                    return size;
                default:
                    return 0;
            }
*/
        }

        /**
         * returns number of elements in the value of this variable
         */
        public int getNumberOfElements() {return numberOfElements;}

        /**
         * returns number of values
         */
        public int getNumberOfValues() {return numberOfValues;}


        public String getName() {return name;}
        public int getNumber() {return number;}
        public int getDataType() {return type;}
        public int[] getDimensions() {
            int [] ia = new int[dimensions.length];
            System.arraycopy(dimensions, 0, ia, 0, dimensions.length);
            return ia;
        }
        public boolean[] getVarys() {
            boolean [] ba = new boolean[varies.length];
            System.arraycopy(varies, 0, ba, 0, varies.length);
            return ba;
        }
    }
    public class DataLocator implements VariableDataLocator,
        java.io.Serializable {
        private transient ByteBuffer _buf;
        private int numberOfValues;
        private boolean compressed;
        protected Vector locations = new Vector();
        protected DataLocator(ByteBuffer b, int n, boolean compr) {
            _buf = b;
            numberOfValues = n;
            compressed = compr;
            _buf.position(offset_FIRST_VXR);
            int offset = lowOrderInt(_buf);
            ByteBuffer bx = getRecord(offset);
            Vector v =  _getLocations(bx);
            // we need to check whether the record points to a compressed
            // record
            int vrtype = VVR_RECORD_TYPE;
            if (compressed) vrtype = CVVR_RECORD_TYPE;
            if (v.size() > 0) {
                int [] loc = (int [])v.elementAt(0);
                ByteBuffer bb = getRecord(loc[2]);
                int rtype = bb.getInt(offset_RECORD_TYPE);
                if (rtype != vrtype) {
                    if (compressed) {
                        System.out.println("setting record type to " + 
                        "uncompressed");
                        compressed = false;
                    } else {
                        System.out.println("invalid record type " + rtype);
                        return;
                    }
                }
            }
            registerNodes(bx, v);
        }

        public boolean isReallyCompressed() {return compressed;}
        public int[][] getLocations() {
            int[][] loc = new int[locations.size()][3];
            for (int i = 0; i < locations.size(); i++) {
                int[] ia = (int [])locations.elementAt(i);
                loc[i][0] = ia[0];
                loc[i][1] = ia[1];
                loc[i][2] = ia[2];
            }
            return loc;
        }

        Vector _getLocations(ByteBuffer bx) {
            Vector locations = new Vector();
            while (true) {
                bx.position(offset_NEXT_VXR);
                int next = lowOrderInt(bx);
                bx.position(offset_NENTRIES);
                int nentries = bx.getInt();
                bx.position(offset_NUSED);
                int nused = bx.getInt();
                bx.position(offset_FIRST);
                ByteBuffer bf = bx.slice();
                bx.position(offset_FIRST + nentries*4);
                ByteBuffer bl = bx.slice();
                bx.position(offset_FIRST + 2*nentries*4);
                ByteBuffer bo = bx.slice();
                for (int entry = 0; entry < nused; entry++) {
                    int first = bf.getInt();
                    int last = bl.getInt();
                    if (last > (numberOfValues - 1)) {
                        last = (numberOfValues - 1);
                    }
                    int off = lowOrderInt(bo);
                    locations.add(new int[] {first, last, off});
                }
                if (next == 0) break;
                bx = getRecord(next);
            }
            return locations;
        }

        void registerNodes(ByteBuffer bx, Vector v) {
            int vrtype = VVR_RECORD_TYPE;
            if (compressed) vrtype = CVVR_RECORD_TYPE;
            
            for (int i = 0; i < v.size(); i++) {
                int [] loc = (int [])v.elementAt(i);
                ByteBuffer bb = getRecord(loc[2]);
                if (bb.getInt(offset_RECORD_TYPE) != vrtype) {
                    Vector vin =  _getLocations(bb);
                    registerNodes(bb, vin);
                } else {
                    locations.add(loc);
                }
            }
        }
    }

    ByteBuffer getValueBuffer(int offset) {
        ByteBuffer bv = getRecord(offset);
        bv.position(offset_RECORDS);
        return bv;
    }

    ByteBuffer getValueBuffer(int offset, int size, int number) {
        ByteBuffer bv = getRecord(offset);
        int clen = lowOrderInt(bv, offset_CSIZE);
        byte [] work = new byte[clen];
        bv.position(offset_CDATA);
        bv.get(work);
        byte [] udata = new byte[size*number];
        int n = 0;
        try {
            GZIPInputStream gz =
                new GZIPInputStream(new ByteArrayInputStream(work));
            int toRead = udata.length;
            int off = 0;
            while (toRead > 0) {
                n = gz.read(udata, off, toRead);
                if (n == -1) break;
                off += n;
                toRead -= n;
            }
        } catch (IOException ex) {
            System.out.println(ex.toString());
            return null;
        }
        if (n < 0) return null;
        return ByteBuffer.wrap(udata);
    }
    public Object get(String varName) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Series");
        if (method == null) throw new Throwable("getSeries not " +
           "implemented for " + varName);
        return method.invoke(null, new Object [] {thisCDF, var});
    }
    public Object get(String varName, int index) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Element");
        if (method == null) throw new Throwable("getElement not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index)});
    }
    public Object get(String varName, int[] elements) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Elements");
        if (method == null) throw new Throwable("getElements not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, elements});
    }
    public Object get(String varName, int index1, int index2) throws
        Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Element");
        if (method == null) throw new Throwable("getElement(i, j) not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2)});
    }
    public Object get(String varName, int index1, int index2, int element)
        throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "RangeForElement");
        if (method == null) throw new Throwable("getRangeForElement not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2), new Integer(element)});
    }
    public Object get(String varName, int index1, int index2, int[] elements)
        throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "RangeForElements");
        if (method == null) throw new Throwable("getRangeForElements not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2), elements});
    }
    public Object getPoint(String varName, int index) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Point");
        if (method == null) throw new Throwable("getPoint not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index)});
    }
    public Object getRange(String varName, int index1, int index2) throws
        Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "Range");
        if (method == null) throw new Throwable("getRange not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2)});
    }
    public Object getRange(String varName, int index1, int index2,
        int element) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "RangeElement");
        if (method == null) throw new Throwable("getRangeElement not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2), new Integer(element)});
    }
    public Object getRange(String varName, int index1, int index2,
        int[] elements) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "RangeElements");
        if (method == null) throw new Throwable("getRangeElements not " +
           "implemented for " + varName);
        return method.invoke(null,
            new Object[] {thisCDF, var, new Integer(index1),
            new Integer(index2), elements});
    }

    public double[] get1D(String varName) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        return Extractor.get1DSeries(thisCDF, var, null);
    }

    public double[] get1D(String varName, int point) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        return Extractor.get1DSeries(thisCDF, var, new int[] {point});
    }

    public double[] get1D(String varName, int first, int last) throws
        Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        return Extractor.get1DSeries(thisCDF, var, new int[] {first, last});
    }

    public Object getTimeSeries(String varName) throws Throwable {
        return getTimeSeries(varName, true, null);
    }

    public Object getTimeSeries(String varName, int element) throws Throwable {
        return getTimeSeries(varName, element, true, null);
    }

    public Object getTimeSeries(String varName, boolean ignoreFill) throws
        Throwable {
        return getTimeSeries(varName, ignoreFill, null);
    }

    public Object getTimeSeries(String varName, int element,boolean ignoreFill)
        throws Throwable {
        return getTimeSeries(varName, element, true, null);
    }

    public Object getTimeSeries(String varName, boolean ignoreFill,
        double[] timeRange) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "TimeSeries");
        if (method == null) throw new Throwable("getTimeSeries not " +
           "implemented for " + varName);
        return method.invoke(null, new Object [] 
            {thisCDF, var, new Boolean(ignoreFill), timeRange});
    }

    public Object getTimeSeries(String vname, boolean ignoreFill,
        double startTime, double stopTime) throws Throwable {
        return getTimeSeries(vname, ignoreFill,
            new double[] {startTime, stopTime});
    }

    public Object getTimeSeries(String vname, boolean ignoreFill,
        Date startDate, Date stopDate) throws Throwable {
        long l1 = startDate.getTime();
        long l2 = stopDate.getTime();
        return getTimeSeries(vname, ignoreFill,
            new double[] {(double)l1, (double)l2});
    }

    public Object getTimeSeries(String varName, int element,boolean ignoreFill,
        double[] timeRange) throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        Method method = Extractor.getMethod(var, "TimeSeries");
        if (method == null) throw new Throwable("getTimeSeries not " +
           "implemented for " + varName);
        return method.invoke(null, new Object [] 
            {thisCDF, var, new Integer(element), new Boolean(ignoreFill),
            timeRange});
    }
    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        double startTime, double stopTime) throws Throwable {
        return getTimeSeries(vname, element, ignoreFill,
            new double[] {startTime, stopTime});
    }

    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        Date startDate, Date stopDate) throws Throwable {
        long l1 = startDate.getTime();
        long l2 = stopDate.getTime();
        return getTimeSeries(vname, element, ignoreFill,
            new double[] {(double)l1, (double)l2});
    }

    /**
     * returns dimensions of the named variable.
     */
    public int [] variableDimensions(String name) {
        Variable var = (Variable)variableTable.get(name);
        if (var == null) return null;
        int [] dims = var.getDimensions();
        int [] ia = new int[dims.length];
        System.arraycopy(ia, 0, dims, 0, dims.length);
        return ia;
    }

    protected abstract int lowOrderInt(ByteBuffer buf);
    protected abstract int lowOrderInt(ByteBuffer buf, int offset);
    protected abstract String getString(int offset);
    protected String getString(int offset, int max)  {
        return getString(getRecord(offset), max);
    }
    protected String getString(ByteBuffer _buf, int max)  {
        byte [] ba = new byte[max];
        int i = 0;
        for (; i < max; i++) {
            ba[i] = _buf.get();
            if (ba[i] == 0) break;
        }
        return new String(ba, 0, i);
    }
    public static double[] getNumberAttribute(int type, int nelement,
        ByteBuffer vbuf, ByteOrder byteOrder) {
        double [] value = new double[nelement];        
        ByteBuffer vbufLocal = vbuf.duplicate();
        vbufLocal.order(byteOrder);

        try {
            if ((type > 20) || (type < 10)) {
                for (int i = 0; i < nelement; i++) {
                    Number num = (Number)DataTypes.method[type].invoke(vbufLocal,
                        new Object [] {});
                    value[i] = num.doubleValue();
                }
            } else {
                for (int i = 0; i < nelement; i++) {
                    Number num = (Number)DataTypes.method[type].invoke(vbufLocal,
                        new Object [] {});
                    int n = num.intValue();
                    value[i] = (n >= 0)?(double)n:(double)(longInt + n);
                }
            }
        } catch(Exception ex) {
            return null;
        }
        return value;
    }

    public TimeVariable getCDFTimeVariable(String vname) throws Throwable {
        Variable var = (Variable)variableTable.get(vname);
        int precision = -1;
        String tname = null;
        if (var == null) {
            throw new Throwable("Bad variable name " + vname);
        } else {
            Vector v = (Vector)getAttribute(var.getName(), "DEPEND_0");
            if (v.size() > 0) tname = (String)v.elementAt(0);
            if (tname == null) tname = "Epoch";
            Variable tvar = (Variable)variableTable.get(tname);
            if (tvar == null) {
                throw new Throwable("Time variable not found for " + vname);
            }
            if (tvar.getType() == DataTypes.EPOCH16) {
                if (tvar.getNumberOfValues() > 0) {
                    precision = PICOSECOND_PRECISION;
                }
            }
            if (precision < 0) {
                precision = MILLISECOND_PRECISION;
                if (tvar.getNumberOfValues() == 0) { //themis like
                    v = (Vector)getAttribute(var.getName(), "DEPEND_TIME");
                    tname = (String)v.elementAt(0);
                    precision = MICROSECOND_PRECISION;
                }
            }
        }
        TimeVariable tvar = (TimeVariable)timeVariableMap.get(tname);
        if (tvar == null) {
            tvar = new CDFTimeVariable(tname, precision);
            timeVariableMap.put(tname, tvar);
        }
        return tvar;
    }

    public double [] getTimes(String vname) throws Throwable {
        return getCDFTimeVariable(vname).getTimes();
    }

    protected double [] getTimes(Variable var, boolean copy) throws Throwable {
        String vname = var.getName();
        if (!copy) return ((CDFTimeVariable)getCDFTimeVariable(vname)).times;
        return getCDFTimeVariable(vname).getTimes();
    }

    public double [] getTimes(String vname, int[] recordRange) throws
        Throwable {
        return getCDFTimeVariable(vname).getTimes(recordRange);
    }

    public double [] getTimes(String vname, int[] recordRange,
        int[] stride) throws Throwable {
        double[] times = ((CDFTimeVariable)getCDFTimeVariable(vname)).times;
        Stride strideObject = new Stride(stride);
        int nv = recordRange[1] - recordRange[0] + 1;
        int _stride = strideObject.getStride(nv);
        if (_stride > 1) {
            int numpt = nv/_stride;
            if (numpt*_stride < nv) numpt++;
            nv = numpt;
        }
        double[] stimes = new double[nv];
        int n = recordRange[0];
        for (int i = 0; i < nv; i++) {
            stimes[i] = times[n];
            n += _stride;
        }
        return stimes;
    }

    public double [] getTimes(String vname, int firstRecord,
        int lastRecord) throws Throwable {
        return getTimes(vname, new int[] {firstRecord, lastRecord});
    }

    public double [] getAvailableTimeRange(String vname) throws Throwable {
        CDFTimeVariable tv = (CDFTimeVariable)getCDFTimeVariable(vname);
        int last = tv.times.length - 1;
        return new double[] {tv.times[0], tv.times[last]};
    }

    public static int MILLISECOND_PRECISION = 0;
    public static int MICROSECOND_PRECISION = 1;
    public static int PICOSECOND_PRECISION = 2;
    public class CDFTimeVariable implements TimeVariable, Serializable {
        protected double[] times;
        protected double[] savedTimes;
        double[][] epoch16;
        int precision;
        String name;
        public CDFTimeVariable(String tname, int precision) throws Throwable {
            name = tname;
            this.precision = precision;
            if (precision == MILLISECOND_PRECISION) {
                double[] epoch = (double[])get(tname);
                times = new double[epoch.length];
                for (int i = 0; i < epoch.length; i++) {
                    times[i] = epoch[i] - JANUARY_1_1970;
                }
            }
            if (precision == MICROSECOND_PRECISION) {
                double[] seconds = (double[])get(tname);
                times = new double[seconds.length];
                for (int i = 0; i < seconds.length; i++) {
                    times[i] = seconds[i]*1.0e3;
                }
            }
            if (precision == PICOSECOND_PRECISION) {
                epoch16 = (double [][])get(tname);
                times = new double [epoch16.length];
                for (int i = 0; i < epoch16.length; i++) {
                    double t = epoch16[i][0] - JANUARY_1_1970;
                    times[i] = t*1000 + Math.floor(epoch16[i][1]/1.0e6);
                }
            }
        }

        public String getName() {return name;}

        public int getPrecision() {return precision;}

        protected synchronized void setTimes(double[] modTimes) {
            savedTimes = times;
            times = modTimes;
        }
        protected synchronized void restoreTimes() {
            times = savedTimes;
        }
        public double [] getTimes() {
            double [] da = new double[times.length];
            System.arraycopy(times, 0, da, 0, times.length);
            return da;
        }

        public double [] getTimes(int[] recordRange) {
            double [] stimes = new double[recordRange[1] - recordRange[0] + 1];
            System.arraycopy(times, recordRange[0], stimes, 0, stimes.length);
            return stimes;
        }

        public double[][] getExtendedPrecisionTimes() throws Throwable {
            if (precision == PICOSECOND_PRECISION) {
                return (double [][])get(name);
            }
            return null;
        }

        public double[][] getExtendedPrecisionTimes(int[] recordRange) 
            throws Throwable {
            if (precision != PICOSECOND_PRECISION) return null;
            return (double [][])getRange(name, recordRange[0], recordRange[1]);
        }

        public int[] getRecordRange(double[] timeRange) {
            double start = timeRange[0];
            double stop = timeRange[1];
            int i = 0;
            for (; i < times.length; i++) {
                if (start > times[i]) continue;
                break;
            }
            if (i == times.length) return null;
            int low = i;
            for (; i < times.length; i++) {
                if (stop <= times[i]) break;
            }
            if (i == 0) return null;
            return new int[] {low, i - 1};
        }

        public double [] getTimes(double[] timeRange) {
            int [] rr = getRecordRange(timeRange);
            if (rr == null) return null;
            return getTimes(rr);
        }
    }
    public int[] getRecordRange(String vname, double[] timeRange) throws
        Throwable {
        return getCDFTimeVariable(vname).getRecordRange(timeRange);
    }
    public int[] getRecordRange(String vname, double startTime,
        double stopTime) throws Throwable {
        return getRecordRange(vname, new double[] {startTime, stopTime});
    }
    public int[] getRecordRange(String vname, Date startDate,
        Date stopDate) throws Throwable {
        long l1 = startDate.getTime();
        long l2 = stopDate.getTime();
        return getRecordRange(vname, new double[] {(double)l1, (double)l2});
    }
    void setByteOrder(ByteOrder bo) {
        bigEndian = bo.equals(ByteOrder.BIG_ENDIAN);
    }
    public void setByteOrder(boolean  _bigEndian) {
        byteOrder = (_bigEndian)?ByteOrder.BIG_ENDIAN:ByteOrder.LITTLE_ENDIAN;
        setByteOrder(byteOrder);
    }
    public boolean isBigEndian() {return bigEndian;}
    public void setBuffer(ByteBuffer b) {buf = b;}
    public void extractBytes(int bufOffset, byte[] ba, int offset, int len) {
        ((ByteBuffer)buf.duplicate().position(bufOffset)).get(ba, offset, len);
    }
    protected int getRecordOffset() {return offset_RECORDS;}
    public Object getTimeSeries(String vname, boolean ignoreFill,
        double[] timeRange, int[] stride) throws Throwable {
        Variable var = (Variable)variableTable.get(vname);
        if (var == null) throw new Throwable("No such variable " + vname);
        Method method = Extractor.getMethod(var, "SampledTimeSeries");
        if (method == null) throw new Throwable("getSampledTimeSeries not " +
           "implemented for " + vname);
        return method.invoke(null, new Object [] 
            {thisCDF, var, new Boolean(ignoreFill), timeRange, stride});
    }
    public Object getTimeSeries(String vname, int element, boolean ignoreFill,
        double[] timeRange, int[] stride) throws Throwable {
        Variable var = (Variable)variableTable.get(vname);
        if (var == null) throw new Throwable("No such variable " + vname);
        Method method = Extractor.getMethod(var, "SampledTimeSeries");
        if (method == null) throw new Throwable("getSampledTimeSeries not " +
           "implemented for " + vname);
        return method.invoke(null, new Object [] 
            {thisCDF, var, new Integer(element), new Boolean(ignoreFill),
            timeRange, stride});
    }
    public double[] get1D(String varName, int first, int last, int[] stride)
        throws Throwable {
        Variable var = (Variable)variableTable.get(varName);
        if (var == null) throw new Throwable("No such variable " + varName);
        return Extractor.get1DSeries(thisCDF, var, new int[] {first, last},
            stride);
    }
}
