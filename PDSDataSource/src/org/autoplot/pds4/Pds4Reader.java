package org.autoplot.pds4;

import java.io.*;
import java.math.*;
import java.net.URI;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Reader using the JDK plus Das2Util for uncompressed PDS4 numeric arrays and binary, character, and delimited tables. No JAXB,
 * schema downloads, or other parser libraries. Offsets are zero-based bytes; field/group locations in labels are one-based. Objects
 * and fields are immutable metadata; each table cursor owns its file. This is a data reader, not a PDS4 schema/Schematron
 * validator.
 */
public final class Pds4Reader {

    private static final String NS = "http://pds.nasa.gov/pds4/pds/v1";
    private static final int MAX_FIELDS = 100000;

    private Pds4Reader() {
    }

    /**
     * Supplies a local/cache file for a resolved data resource.
     */
    public interface Resolver {

        Path resolve(URI resource) throws IOException;
    }

    /**
     * Progress callbacks may throw IOException to cancel a read.
     */
    public interface Progress {

        void update(long completed, long total) throws IOException;
    }
    public static final Progress NONE = (completed, total) -> {
    };

    /**
     * Open a local label; relative data paths are relative to that label.
     */
    public static Product open(Path label) throws IOException {
        return open(label, label.toAbsolutePath().toUri(), resource -> {
            if (!"file".equalsIgnoreCase(resource.getScheme())) {
                throw new IOException("A Resolver is required for " + resource);
            }
            return Paths.get(resource);
        });
    }

    /**
     * Open a cached label while retaining its original URI for relative files. XML external entities, DOCTYPEs, and external
     * schemas are disabled.
     */
    public static Product open(Path label, URI original, Resolver resolver) throws IOException {
        if (original == null || !original.isAbsolute() || resolver == null) {
            throw new IllegalArgumentException("Absolute original URI and resolver required");
        }
        Document doc;
        try (InputStream in = Files.newInputStream(label)) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            doc = b.parse(in);
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException e) {
            throw new IOException("Cannot parse PDS4 label " + label + ": " + e.getMessage(), e);
        }
        Element root = doc.getDocumentElement();
        if (!NS.equals(root.getNamespaceURI()) || !root.getLocalName().startsWith("Product_")) {
            throw new IOException("Expected a namespaced PDS4 Product_* label");
        }
        List<DataObject> objects = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        int index = 0;
        for (Element area : children(root)) {
            if (!area.getLocalName().startsWith("File_Area_")) {
                continue;
            }
            Element file = child(area, "File");
            if (file == null) {
                unsupported.add(area.getLocalName() + " has no File");
                continue;
            }
            String filename = required(file, "file_name");
            URI resource;
            try {
                // File names are paths, not URLs. Encode spaces, '#', '%' etc.
                URI relative = new URI(null, null, filename, null);
                if (relative.isAbsolute() || filename.startsWith("/") || filename.contains("\\")
                        || filename.matches("^[A-Za-z]:.*")) {
                    throw new IOException("Expected relative file_name: " + filename);
                }
                resource = original.resolve(relative);
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Invalid file_name", e);
            }
            for (Element e : children(area)) {
                String type = e.getLocalName();
                if (type.equals("File")) {
                    continue;
                }
                if (type.equals("Table_Binary") || type.equals("Table_Character") || type.equals("Table_Delimited")) {
                    objects.add(new Table(e, resource, resolver, ++index));
                } else if (type.equals("Array") || type.matches("Array_[1-9][0-9]*D(?:_.*)?")) {
                    objects.add(new Array(e, resource, resolver, ++index));
                } else if (!type.equals("Header")) {
                    unsupported.add(type);
                }
            }
        }
        return new Product(objects, unsupported, original, doc);
    }

    public static final class Product {

        public final List<DataObject> objects;
        public final List<String> unsupportedStructures;
        public final URI labelUri;
        private final Document labelDocument;

        /**
         * Full label, including discipline metadata and group structure; defensive copy.
         */
        public Document getLabelDocument() {
            return (Document) labelDocument.cloneNode(true);
        }

        private Product(List<DataObject> objects, List<String> unsupported, URI uri, Document doc) {
            labelUri = uri;
            labelDocument = doc;
            this.objects = Collections.unmodifiableList(objects);
            this.unsupportedStructures = Collections.unmodifiableList(unsupported);
        }

        /**
         * Resolve exact object name or local_identifier; ambiguous names fail.
         */
        public DataObject object(String id) throws IOException {
            DataObject found = null;
            for (DataObject o : objects) {
                if (o.name.equals(id) || o.id.equals(id)) {
                    if (found != null) {
                        throw new IOException("Ambiguous object: " + id);
                    }
                    found = o;
                }
            }
            if (found == null) {
                throw new IOException("No object '" + id + "'; available: " + names());
            }
            return found;
        }

        public List<String> names() {
            List<String> result = new ArrayList<>();
            for (DataObject o : objects) {
                result.add(o.id);
            }
            return result;
        }
    }

    public abstract static class DataObject {

        public final String name, id, description, structureType;
        /**
         * Parsing standards declared by headers in this object's file area.
         */
        public final List<String> fileParsingStandards;
        public final URI resource;
        public final long offset;
        final Resolver resolver;

        DataObject(Element e, URI resource, Resolver resolver, int index) throws IOException {
            structureType = e.getLocalName();
            List<String> standards = new ArrayList<>();
            for (Element header : children((Element) e.getParentNode())) {
                if (header.getLocalName().equals("Header")) {
                    String standard = text(header, "parsing_standard_id", "");
                    if (!standard.isEmpty()) {
                        standards.add(standard);
                    }
                }
            }
            fileParsingStandards = Collections.unmodifiableList(standards);
            this.name = text(e, "name", text(e, "local_identifier", "object" + index));
            this.id = text(e, "local_identifier", name);
            this.description = text(e, "description", "");
            this.resource = resource;
            this.resolver = resolver;
            this.offset = nonnegative(number(e, "offset"), "offset");
            requireByteUnit(child(e, "offset"));
        }

        RandomAccessFile file() throws IOException {
            return new RandomAccessFile(resolver.resolve(resource).toFile(), "r");
        }
    }

    /**
     * Element/field conversion metadata. Special constants are tested BEFORE scaling.
     */
    public static final class ValueType {

        public final FieldType dataType;
        public final String dataTypeName, unit, description;
        public final double scalingFactor, valueOffset;
        /**
         * Original unscaled label constants, including valid_minimum/valid_maximum.
         */
        public final Map<String, String> specialConstants;
        /**
         * Raw valid limits, or null when absent; strings preserve integer precision.
         */
        public final String validMinimum, validMaximum;
        public final String fieldFormat;
        private final List<String> special;

        ValueType(Element e) throws IOException {
            this(e, e);
        }

        ValueType(Element e, Element specialOwner) throws IOException {
            dataTypeName = required(e, "data_type");
            try {
                dataType = FieldType.getFieldType(dataTypeName);
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
            unit = text(e, "unit", "");
            description = text(e, "description", "");
            scalingFactor = decimal(e, "scaling_factor", 1);
            valueOffset = decimal(e, "value_offset", 0);
            if (!Double.isFinite(scalingFactor) || !Double.isFinite(valueOffset)) {
                throw new IOException("Non-finite scale or offset");
            }
            fieldFormat = text(e, "field_format", "");
            Map<String, String> namedConstants = new LinkedHashMap<>();
            List<String> s = new ArrayList<>();
            Element constants = child(specialOwner, "Special_Constants");
            if (constants != null) {
                for (Element c : children(constants)) {
                    namedConstants.put(c.getLocalName(), c.getTextContent().trim());
                    if (!c.getLocalName().equals("valid_minimum") && !c.getLocalName().equals("valid_maximum")) {
                        s.add(c.getTextContent().trim());
                    }
                }
            }
            special = Collections.unmodifiableList(s);
            specialConstants = Collections.unmodifiableMap(namedConstants);
            validMinimum = namedConstants.get("valid_minimum");
            validMaximum = namedConstants.get("valid_maximum");
        }

        public boolean isTime() {
            return dataType.isTime();
        }

        public boolean isNumeric() {
            return binaryWidth(dataTypeName) > 0 || dataTypeName.equals("ASCII_Integer")
                    || dataTypeName.equals("ASCII_NonNegative_Integer") || dataTypeName.equals("ASCII_Real")
                    || dataTypeName.matches("ASCII_Numeric_Base(?:2|8|16)");
        }

        /**
         * Decode raw bytes; integer values retain exact BigInteger precision.
         */
        Object raw(byte[] bytes, int pos, int length) throws IOException {
            if (pos < 0 || length < 1 || pos > bytes.length - length) {
                throw new IOException("Field outside record");
            }
            if (dataType == FieldType.UTF8_STRING) {
                try {
                    String text = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, pos, length)).toString().trim();
                    return text.isEmpty() ? null : text;
                } catch (CharacterCodingException ex) {
                    throw new IOException("Invalid UTF8_String bytes", ex);
                }
            }
            if (dataTypeName.startsWith("ASCII_")) {
                for (int i = pos; i < pos + length; i++) {
                    if ((bytes[i] & 255) > 127) {
                        throw new IOException("Non-ASCII byte in " + dataTypeName);
                    }
                }
                return parse(new String(bytes, pos, length, StandardCharsets.US_ASCII).trim());
            }
            int width = binaryWidth(dataTypeName);
            if (width == 0) {
                throw new IOException("Unsupported PDS4 type: " + dataTypeName);
            }
            if (length != width) {
                throw new IOException("Wrong field_length for " + dataTypeName);
            }
            byte[] b = Arrays.copyOfRange(bytes, pos, pos + length);
            boolean little = dataTypeName.contains("LSB");
            if (dataTypeName.startsWith("IEEE754")) {
                ByteBuffer buffer = ByteBuffer.wrap(b).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                return width == 4 ? (double) buffer.getFloat() : buffer.getDouble();
            }
            if (little) {
                for (int i = 0; i < b.length / 2; i++) {
                    byte tmp = b[i];
                    b[i] = b[b.length - 1 - i];
                    b[b.length - 1 - i] = tmp;
                }
            }
            return dataTypeName.startsWith("Unsigned") ? new BigInteger(1, b) : new BigInteger(b);
        }

        Object parse(String s) throws IOException {
            try {
                if (s.isEmpty()) {
                    return null;
                }
                if (dataTypeName.equals("ASCII_Integer") || dataTypeName.equals("ASCII_NonNegative_Integer")) {
                    BigInteger v = new BigInteger(s);
                    if (dataTypeName.equals("ASCII_NonNegative_Integer") && v.signum() < 0) {
                        throw new NumberFormatException("negative unsigned integer");
                    }
                    return v;
                }
                if (dataTypeName.startsWith("ASCII_Numeric_Base")) {
                    return new BigInteger(s, Integer.parseInt(dataTypeName.substring(18)));
                }
                if (dataTypeName.equals("ASCII_Real")) {
                    return Double.valueOf(s.replace('D', 'E').replace('d', 'e'));
                }
                if (isTime() || Arrays.asList("ASCII_String", "ASCII_Boolean", "ASCII_AnyURI", "ASCII_BibCode", "ASCII_DOI", "ASCII_Directory_Path_Name", "ASCII_File_Name", "ASCII_File_Specification_Name", "ASCII_LID", "ASCII_LIDVID", "ASCII_LIDVID_LID", "ASCII_MD5_Checksum", "ASCII_VID").contains(dataTypeName)) {
                    return s;
                }
                throw new IOException("Unsupported text type: " + dataTypeName);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid " + dataTypeName + " value: " + s, e);
            }
        }

        /**
         * Missing values become NaN. Integers that cannot fit exactly in double fail.
         */
        public double numeric(Object raw) throws IOException {
            if (missing(raw)) {
                return Double.NaN;
            }
            if (!(raw instanceof Number)) {
                throw new IOException("Not numeric: " + dataTypeName);
            }
            if (raw instanceof BigInteger) {
                double d = ((BigInteger) raw).doubleValue();
                if (!Double.isFinite(d) || !new BigDecimal(d).toBigIntegerExact().equals(raw)) {
                    throw new IOException("Integer cannot be represented exactly by QDataSet double: " + raw);
                }
            }
            return ((Number) raw).doubleValue() * scalingFactor + valueOffset;
        }

        public boolean missing(Object raw) throws IOException {
            if (raw == null) {
                return true;
            }
            for (String s : special) {
                if (raw instanceof BigInteger) {
                    try {
                        if (new BigDecimal(s).toBigIntegerExact().equals(raw)) {
                            return true;
                        }
                    } catch (ArithmeticException | NumberFormatException e) {
                        throw new IOException("Invalid integer Special_Constant: " + s, e);
                    }
                } else if (raw instanceof Number) {
                    double v;
                    try {
                        v = Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid Special_Constant: " + s, e);
                    }
                    if (dataTypeName.endsWith("Single")) {
                        v = (double) (float) v;
                    }
                    double d = ((Number) raw).doubleValue();
                    if (d == v || Double.isNaN(d) && Double.isNaN(v)) {
                        return true;
                    }
                } else if (s.equals(raw)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Axis {

        public final String name;
        public final int elements, sequence;

        Axis(Element e) throws IOException {
            name = required(e, "axis_name");
            elements = positiveInt(number(e, "elements"), "elements");
            sequence = positiveInt(number(e, "sequence_number"), "sequence_number");
        }
    }

    public static final class Array extends DataObject {

        public final List<Axis> axes;
        public final ValueType element;
        public final boolean firstIndexFastest;
        public final int[] shape;
        public final long elements;
        private final int width;

        Array(Element e, URI uri, Resolver resolver, int index) throws IOException {
            super(e, uri, resolver, index);
            element = new ValueType(requireChild(e, "Element_Array"), e);
            width = binaryWidth(element.dataTypeName);
            if (width == 0) {
                throw new IOException("Unsupported array encoding: " + element.dataTypeName);
            }
            String order = required(e, "axis_index_order");
            if (!order.equals("First Index Fastest") && !order.equals("Last Index Fastest")) {
                throw new IOException("Unknown axis_index_order: " + order);
            }
            firstIndexFastest = order.equals("First Index Fastest");
            List<Axis> list = new ArrayList<>();
            for (Element a : children(e)) {
                if (a.getLocalName().equals("Axis_Array")) {
                    list.add(new Axis(a));
                }
            }
            Collections.sort(list, Comparator.comparingInt(a -> a.sequence));
            int rank = positiveInt(number(e, "axes"), "axes");
            if (list.size() != rank) {
                throw new IOException("axes does not match Axis_Array count");
            }
            shape = new int[rank];
            long count = 1;
            for (int i = 0; i < rank; i++) {
                if (list.get(i).sequence != i + 1) {
                    throw new IOException("Axis sequence numbers must be 1..axes");
                }
                shape[i] = list.get(i).elements;
                count = multiply(count, shape[i]);
            }
            elements = count;
            axes = Collections.unmodifiableList(list);
        }

        /**
         * Read with a Das2 monitor; owns its started/finished lifecycle. Null means no UI.
         */
        public double[] read(long maxElements, ProgressMonitor monitor) throws IOException {
            ProgressMonitor mon = monitor == null ? new NullProgressMonitor() : monitor;
            mon.setTaskSize(elements);
            mon.started();
            try {
                checkCancelled(mon);
                return read(maxElements, (done, total) -> {
                    checkCancelled(mon);
                    mon.setTaskProgress(done);
                });
            } finally {
                mon.finished();
            }
        }

        /**
         * Read into canonical last-index-fastest order, regardless of disk order.
         */
        public double[] read(long maxElements, Progress progress) throws IOException {
            if (elements > maxElements || elements > Integer.MAX_VALUE) {
                throw new IOException("Array has " + elements + " elements; limit is " + maxElements);
            }
            double[] result = new double[(int) elements];
            long byteCount = multiply(elements, width);
            try (RandomAccessFile f = file()) {
                requireRange(f, offset, byteCount);
                f.seek(offset);
                byte[] b = new byte[width];
                progress.update(0, elements);
                for (int i = 0; i < result.length; i++) {
                    f.readFully(b);
                    int target = i;
                    if (firstIndexFastest) {
                        int q = i;
                        target = 0;
                        for (int dim : shape) {
                            target = Math.addExact(Math.multiplyExact(target, dim), q % dim);
                            q /= dim;
                        }
                    }
                    result[target] = element.numeric(element.raw(b, 0, width));
                    if ((i & 4095) == 0) {
                        progress.update(i, elements);
                    }
                }
                progress.update(elements, elements);
            }
            return result;
        }
    }

    public static final class Field {

        public final String name;
        public final ValueType type;
        /**
         * Fixed-width: zero-based byte offset in the RECORD, including group offsets. Delimited: zero-based expanded cell index.
         * length is zero for delimited fields.
         */
        public final int location, length;

        /**
         * Enum type for comparisons such as getType() == FieldType.ASCII_DATE_DOY.
         */
        public FieldType getType() {
            return type.dataType;
        }

        Field(String name, ValueType type, int location, int length) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.length = length;
        }
    }

    public static final class Table extends DataObject {

        public final List<Field> fields;
        public final long records;
        public final int recordLength;
        public final boolean delimited;
        /**
         * Actual delimiter character (NUL when not a delimited table).
         */
        public final char delimiter;
        /**
         * Actual record delimiter bytes as a String; empty when unspecified.
         */
        public final String recordDelimiter;
        /**
         * Original label spellings; empty when unspecified.
         */
        public final String fieldDelimiterName, recordDelimiterName;

        Table(Element e, URI uri, Resolver resolver, int index) throws IOException {
            super(e, uri, resolver, index);
            records = nonnegative(number(e, "records"), "records");
            String kind = e.getLocalName().substring(6);
            delimited = kind.equals("Delimited");
            Element record = requireChild(e, "Record_" + kind);
            recordLength = delimited ? 0 : positiveInt(number(record, "record_length"), "record_length");
            if (!delimited) {
                requireByteUnit(child(record, "record_length"));
            }
            recordDelimiterName = text(e, "record_delimiter", "");
            fieldDelimiterName = text(e, "field_delimiter", "");
            if (delimited && (recordDelimiterName.isEmpty() || fieldDelimiterName.isEmpty())) {
                throw new IOException("Delimited table needs record_delimiter and field_delimiter");
            }
            recordDelimiter = recordDelimiterName.isEmpty() ? "" : recordDelimiter(recordDelimiterName);
            delimiter = delimited ? delimiter(fieldDelimiterName) : '\0';
            List<Field> list = new ArrayList<>();
            expand(record, kind, 0, recordLength, "", list, 0);
            fields = Collections.unmodifiableList(list);
        }

        /**
         * Exact field name; repeated groups appear as Group[0]/Field, etc.
         */
        public int fieldIndex(String name) throws IOException {
            int found = -1;
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).name.equals(name)) {
                    if (found >= 0) {
                        throw new IOException("Ambiguous field: " + name);
                    }
                    found = i;
                }
            }
            if (found >= 0) {
                return found;
            }
            throw new IOException("No field '" + name + "' in " + id);
        }

        public Cursor cursor() throws IOException {
            return new Cursor(this);
        }

        /**
         * Read one numeric column with a Das2 monitor. Scaling and missing-value conversion are applied; use a cursor for text/date
         * fields. Owns the supplied monitor lifecycle, including on failure/cancellation.
         */
        public double[] read(int column, long maxElements, ProgressMonitor monitor) throws IOException {
            if (column < 0 || column >= fields.size()) {
                throw new IllegalArgumentException("Invalid column: " + column);
            }
            ProgressMonitor mon = monitor == null ? new NullProgressMonitor() : monitor;
            mon.setTaskSize(records);
            mon.started();
            try {
                checkCancelled(mon);
                if (records > maxElements || records > Integer.MAX_VALUE) {
                    throw new IOException("Table exceeds maxElements=" + maxElements);
                }
                double[] result = new double[(int) records];
                try (Cursor cursor = cursor()) {
                    for (int i = 0; i < result.length; i++) {
                        checkCancelled(mon);
                        mon.setTaskProgress(i);
                        result[i] = fields.get(column).type.numeric(cursor.next()[column]);
                    }
                    checkCancelled(mon);
                    mon.setTaskProgress(records);
                }
                return result;
            } finally {
                mon.finished();
            }
        }

    }

    private static void expand(Element parent, String kind, int base, int span, String prefix,
            List<Field> fields, int depth) throws IOException {
        if (depth > 16) {
            throw new IOException("Too many nested groups");
        }
        boolean delimited = kind.equals("Delimited");
        for (Element e : children(parent)) {
            if (e.getLocalName().equals("Field_" + kind)) {
                if (child(e, "Field_Bit") != null) {
                    throw new IOException("Field_Bit is unsupported");
                }
                int len = delimited ? 0 : positiveInt(number(e, "field_length"), "field_length");
                int loc = delimited ? fields.size() : positiveInt(number(e, "field_location"), "field_location") - 1;
                if (!delimited) {
                    requireByteUnit(child(e, "field_location"));
                    requireByteUnit(child(e, "field_length"));
                    if (loc > span - len) {
                        throw new IOException("Field outside containing record/group");
                    }
                }
                ValueType type = new ValueType(e);
                if (type.dataType != FieldType.UTF8_STRING && !type.dataTypeName.startsWith("ASCII_") && binaryWidth(type.dataTypeName) != len) {
                    throw new IOException("Unsupported type/width: " + type.dataTypeName);
                }
                if (fields.size() >= MAX_FIELDS) {
                    throw new IOException("Too many expanded fields");
                }
                fields.add(new Field(required(e, "name"), type, delimited ? loc : Math.addExact(base, loc), len));
            } else if (e.getLocalName().equals("Group_Field_" + kind)) {
                int repeats = positiveInt(number(e, "repetitions"), "repetitions");
                if (repeats > MAX_FIELDS) {
                    throw new IOException("Too many repetitions");
                }
                int loc = delimited ? 0 : positiveInt(number(e, "group_location"), "group_location") - 1;
                int len = delimited ? 0 : positiveInt(number(e, "group_length"), "group_length");
                if (!delimited && (loc > span - len || len % repeats != 0)) {
                    throw new IOException("Invalid group extent/repetitions");
                }
                String name = text(e, "name", text(e, "group_number", "group"));
                for (int i = 0; i < repeats; i++) {
                    expand(e, kind,
                            delimited ? 0 : Math.addExact(base, Math.addExact(loc, i * (len / repeats))),
                            delimited ? 0 : len / repeats, prefix + name + "[" + i + "]/", fields, depth + 1);
                }
            }
        }
    }

    /**
     * Streaming table cursor. close() is required even after an early stop.
     */
    public static final class Cursor implements Closeable {

        private final Table table;
        private final RandomAccessFile file;
        private final byte[] buffer;
        private long row;

        private Cursor(Table table) throws IOException {
            this.table = table;
            RandomAccessFile f = table.file();
            try {
                requireRange(f, table.offset, table.delimited ? 0 : multiply(table.records, table.recordLength));
                f.seek(table.offset);
                buffer = table.delimited ? null : new byte[table.recordLength];
            } catch (IOException | RuntimeException e) {
                f.close();
                throw e;
            }
            file = f;
        }

        public long rowNumber() {
            return row;
        }

        /**
         * Returns null at the declared record count; unexpected EOF is an error.
         */
        public Object[] next() throws IOException {
            if (row == table.records) {
                return null;
            }
            Object[] result = new Object[table.fields.size()];
            try {
                if (table.delimited) {
                    List<byte[]> cells = readDelimited(file, table.delimiter, table.recordDelimiter);
                    if (cells == null) {
                        throw new EOFException("Missing record");
                    }
                    if (cells.size() != result.length) {
                        throw new IOException("Expected " + result.length + " fields, got " + cells.size());
                    }
                    for (int i = 0; i < result.length; i++) {
                        byte[] cell = cells.get(i);
                        result[i] = cell.length == 0 ? null : table.fields.get(i).type.raw(cell, 0, cell.length);
                    }
                } else {
                    file.readFully(buffer);
                    for (int i = 0; i < result.length; i++) {
                        Field field = table.fields.get(i);
                        result[i] = field.type.raw(buffer, field.location, field.length);
                    }
                }
                row++;
                return result;
            } catch (IOException e) {
                throw new IOException(table.id + ", record " + (row + 1) + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    // RFC4180-style quotes, escaped quotes, embedded delimiters and newlines.
    // Limits bound malformed-record memory growth; fixed-width tables are byte-based.
    private static List<byte[]> readDelimited(RandomAccessFile f, char delimiter, String eol) throws IOException {
        List<byte[]> fields = new ArrayList<>();
        ByteArrayOutputStream cell = new ByteArrayOutputStream();
        boolean quoted = false, afterQuote = false, started = false;
        int size = 0;
        while (true) {
            int c = f.read();
            if (c == -1) {
                if (quoted) {
                    throw new EOFException("Unterminated quoted field");
                }
                if (!started && fields.isEmpty()) {
                    return null;
                }
                fields.add(cell.toByteArray());
                return fields;
            }
            started = true;
            if (++size > 16 * 1024 * 1024) {
                throw new IOException("Delimited record exceeds 16 MiB");
            }
            if (quoted) {
                if (c == '"') {
                    int next = f.read();
                    if (next == '"') {
                        cell.write('"');
                        size++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                        if (next != -1) {
                            f.seek(f.getFilePointer() - 1);
                        }
                    }
                } else {
                    cell.write((char) c);
                }
            } else if (c == delimiter) {
                fields.add(cell.toByteArray());
                cell.reset();
                afterQuote = false;
                if (fields.size() > MAX_FIELDS) {
                    throw new IOException("Too many delimited fields");
                }
            } else if (c == eol.charAt(0)) {
                for (int i = 1; i < eol.length(); i++) {
                    if (f.read() != eol.charAt(i)) {
                        throw new IOException("Wrong record delimiter");
                    }
                }
                fields.add(cell.toByteArray());
                return fields;
            } else if (c == '\r' || c == '\n') {
                throw new IOException("Wrong record delimiter");
            } else if (afterQuote) {
                throw new IOException("Characters after closing quote");
            } else if (c == '"') {
                if (cell.size() != 0) {
                    throw new IOException("Quote inside unquoted field");
                }
                quoted = true;
            } else {
                cell.write((char) c);
            }
        }
    }

    private static void checkCancelled(ProgressMonitor mon) throws InterruptedIOException {
        if (mon.isCancelled()) {
            throw new InterruptedIOException("PDS4 read cancelled");
        }
    }

    private static String delimiterName(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ");
    }

    private static char delimiter(String s) throws IOException {
        switch (delimiterName(s)) {
            case "comma":
                return ',';
            case "horizontal tab":
                return '\t';
            case "semicolon":
                return ';';
            case "vertical bar":
                return '|';
            default:
                throw new IOException("Unsupported field_delimiter: " + s);
        }
    }

    private static String recordDelimiter(String s) throws IOException {
        switch (delimiterName(s)) {
            case "carriage return line feed":
                return "\r\n";
            case "line feed":
                return "\n";
            case "carriage return":
                return "\r";
            default:
                throw new IOException("Unsupported record_delimiter: " + s);
        }
    }

    private static int binaryWidth(String s) {
        if (s.equals("SignedByte") || s.equals("UnsignedByte")) {
            return 1;
        }
        if (s.matches("(?:Unsigned|Signed)(?:MSB|LSB)[248]")) {
            return s.charAt(s.length() - 1) - '0';
        }
        if (s.matches("IEEE754(?:MSB|LSB)Single")) {
            return 4;
        }
        if (s.matches("IEEE754(?:MSB|LSB)Double")) {
            return 8;
        }
        return 0;
    }

    private static List<Element> children(Element e) {
        List<Element> result = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && NS.equals(n.getNamespaceURI())) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static Element child(Element e, String name) {
        for (Element c : children(e)) {
            if (c.getLocalName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static Element requireChild(Element e, String name) throws IOException {
        Element c = child(e, name);
        if (c == null) {
            throw new IOException("Missing " + name + " in " + e.getLocalName());
        }
        return c;
    }

    private static String text(Element e, String name, String fallback) {
        Element c = child(e, name);
        return c == null ? fallback : c.getTextContent().trim();
    }

    private static String required(Element e, String name) throws IOException {
        String s = requireChild(e, name).getTextContent().trim();
        if (s.isEmpty()) {
            throw new IOException("Empty " + name);
        }
        return s;
    }

    private static long number(Element e, String name) throws IOException {
        try {
            return Long.parseLong(required(e, name));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid " + name, ex);
        }
    }

    private static double decimal(Element e, String name, double fallback) throws IOException {
        try {
            return Double.parseDouble(text(e, name, Double.toString(fallback)));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid " + name, ex);
        }
    }

    private static long nonnegative(long n, String name) throws IOException {
        if (n < 0) {
            throw new IOException("Negative " + name);
        }
        return n;
    }

    private static int positiveInt(long n, String name) throws IOException {
        if (n < 1 || n > Integer.MAX_VALUE) {
            throw new IOException("Invalid " + name + ": " + n);
        }
        return (int) n;
    }

    private static long multiply(long a, long b) throws IOException {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            throw new IOException("Size overflow", ex);
        }
    }

    private static void requireByteUnit(Element e) throws IOException {
        if (e.hasAttribute("unit") && !e.getAttribute("unit").equals("byte") && !e.getAttribute("unit").equals("BYTE")) {
            throw new IOException("Expected byte unit on " + e.getLocalName());
        }
    }

    private static void requireRange(RandomAccessFile f, long offset, long count) throws IOException {
        if (offset > f.length() || count > f.length() - offset) {
            throw new EOFException("Data file shorter than label declares");
        }
    }
}
