/*
 * AsciiTableDataSource.java
 *
 * Created on March 31, 2007, 8:22 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.ascii;

import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.dsutil.AsciiParser;
import org.das2.util.TimeParser;
import java.text.ParseException;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import org.das2.util.ByteBufferInputStream;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;

/**
 *
 * @author jbf
 */
public class AsciiTableDataSource extends AbstractDataSource {

    AsciiParser parser;
    File file;
    String column = null;
    String depend0 = null;

    private final static Logger logger= Logger.getLogger("vap.asciiTableDataSource");

    public final static String PARAM_INTERVAL_TAG="intervalTag";

    /**
     * if non-null, then this is used to parse the times.  For a fixed-column parser, a field
     * handler is added to the parser.  For delim parser, then the
     */
    TimeParser timeParser;
    /**
     * the number of columns to combine into time
     */
    int timeColumns = -1;
    /**
     * time format of each digit
     */
    String[] timeFormats;
    /**
     * the column containing times, or -1.
     */
    int timeColumn = -1;
    DDataSet ds = null;
    /**
     * non-null indicates the columns should be interpretted as rank2.  rank2[0] is first column, rank2[1] is last column exclusive.
     */
    int[] rank2 = null;
    /**
     * non-null indicates the first record will provide the labels for the rows of the rank 2 dataset.
     */
    int[] depend1Labels = null;
    /**
     * non-null indicates the first record will provide the values for the rows of the rank 2 dataset.
     */
    int[] depend1Values = null;
    /**
     * limit the number of records.  Parsing will stop at this point.
     */
    int recCount = -1;
    private double validMin = Double.NEGATIVE_INFINITY;
    private double validMax = Double.POSITIVE_INFINITY;

    /** Creates a new instance of AsciiTableDataSource */
    public AsciiTableDataSource(URL url) throws FileNotFoundException, IOException {
        super(url);

    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {

        ds = doReadFile(mon);

/*        String o= params.get("tail");
        if ( o!=null ) {
            int itail= Integer.parseInt(o);
            int nrec= ds.length();
            if ( nrec>itail ) {
                ds= DDataSet.copy( DataSetOps.trim( ds, nrec-itail, itail ) );
            }
        }*/


        // combine times if necessary
        if (timeColumns > 1) {
            final Units u = Units.t2000;
            int warnCount=10;
            // replace the first column with the datum time
            for (int i = 0; i < ds.length(); i++) {
                try {
                    timeParser.resetSeconds();
                    for (int j = 0; j < timeColumns; j++) {
                        double d = ds.value(i, timeColumn + j);
                        double fp = d - (int) Math.floor(d);
                        if (fp == 0) {
                            timeParser.setDigit(timeFormats[j], (int) d);
                        } else {
                            timeParser.setDigit(timeFormats[j], d);
                        }
                    }
                    ds.putValue(i, timeColumn, timeParser.getTime(Units.t2000) );
                } catch ( IllegalArgumentException ex ) {
                    if ( warnCount>0 ) { // prevent errors from bogging down
                        new RuntimeException("failed to read time at record "+i, ex ).printStackTrace();
                        warnCount--;
                    }
                    ds.putValue( i, timeColumn, Units.t2000.getFillDouble() );
                }
            }
            parser.setUnits(timeColumn, Units.t2000);
        }

        DDataSet vds = null;
        DDataSet dep0 = null;

        if ((column == null) && (timeColumn != -1)) {
            column = parser.getFieldNames()[timeColumn];
        }

        if (column != null) {
            int icol = parser.getFieldIndex(column);
            if (icol == -1) {
                if (Pattern.matches("field[0-9]+", column)) {
                    icol = Integer.parseInt(column.substring(5));
                } else if (Pattern.matches("[0-9]+", column)) {
                    icol = Integer.parseInt(column);
                } else {
                    throw new IllegalArgumentException("bad column parameter: " + column + ", should be field1, or 1, or <name>");
                }
                int fieldCount= parser.getRecordParser().fieldCount();
                if ( icol>=fieldCount ) {
                    throw new IllegalArgumentException("bad column parameter: the record parser only expects "+fieldCount+" columns");
                }
            }
            vds = DDataSet.copy(DataSetOps.slice1(ds, icol));
            vds.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            if (validMax != Double.POSITIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MAX, validMax);
            }
            if (validMin != Double.NEGATIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MIN, validMin);
            }
            if ( column.length()>1 ) vds.putProperty( QDataSet.NAME, column );
            vds.putProperty( QDataSet.LABEL, parser.getFieldNames()[icol] );
        }

        if (depend0 != null) {
            int icol = parser.getFieldIndex(depend0);
            dep0 = DDataSet.copy(DataSetOps.slice1(ds, icol));
            dep0.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            if (DataSetUtil.isMonotonic(dep0)) {
                dep0.putProperty(DDataSet.MONOTONIC, Boolean.TRUE);
            }
            String intervalType= params.get( PARAM_INTERVAL_TAG );
            if ( intervalType!=null && intervalType.equals("start") ) {
                QDataSet cadence= DataSetUtil.guessCadenceNew( dep0, null );
                if ( cadence!=null && !"log".equals( cadence.property(QDataSet.SCALE_TYPE) ) ) {
                    double add= cadence.value()/2; //DANGER--should really check units.
                    logger.fine("adding half-interval width to dep0 because of "+PARAM_INTERVAL_TAG+": "+cadence);
                    for ( int i=0; i<dep0.length(); i++ ) {
                        dep0.putValue( i, dep0.value(i)+add );
                    }
                }
            }
        }

        if (rank2 != null) {
            if (dep0 != null) {
                ds.putProperty(QDataSet.DEPEND_0, dep0); // DANGER
            }
            Units u = parser.getUnits(rank2[0]);
            for (int i = rank2[0]; i < rank2[1]; i++) {
                if (u != parser.getUnits(i)) {
                    u = null;
                }
            }
            if (u != null) {
                ds.putProperty(QDataSet.UNITS, u);
            }
            if (validMax != Double.POSITIVE_INFINITY) {
                ds.putProperty(QDataSet.VALID_MAX, validMax);
            }
            if (validMin != Double.NEGATIVE_INFINITY) {
                ds.putProperty(QDataSet.VALID_MIN, validMin);
            }

            MutablePropertyDataSet mds = DataSetOps.leafTrim(ds, rank2[0], rank2[1]);

            if (depend1Labels != null) {
                QDataSet labels = Ops.labels(parser.getFieldLabels());
                labels = DataSetOps.leafTrim(labels, depend1Labels[0], depend1Labels[1]);
                mds.putProperty(QDataSet.DEPEND_1, labels);
            }

            if (depend1Values != null) {
                String[] fieldNames = parser.getFieldNames();
                String[] fieldUnits = parser.getFieldUnits();
                DDataSet dep1 = DDataSet.createRank1(depend1Values[1] - depend1Values[0]);
                for (int i = depend1Values[0]; i < depend1Values[1]; i++) {
                    Units u1 = Units.dimensionless;
                    double d;
                    try {
                        d = Double.parseDouble(fieldNames[i]);
                    } catch (NumberFormatException ex) {
                        try {
                            d = Double.parseDouble(fieldUnits[i]);
                        } catch (NumberFormatException ex2) {
                            d = i - depend1Values[0];
                        }
                    }
                    dep1.putValue(i-depend1Values[0], d);
                }

                mds.putProperty(QDataSet.DEPEND_1, dep1);
            }

            return mds;

        } else {
            if (vds == null) {
                throw new IllegalArgumentException("didn't find column: " + column);
            }
            if (dep0 != null) {
                vds.putProperty(QDataSet.DEPEND_0, dep0);
            }
            return vds;
        }

    }

    /**
     * returns the rank 2 dataset produced by the ascii table reader.
     * @param mon
     * @return
     * @throws java.lang.NumberFormatException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     */
    private DDataSet doReadFile(final ProgressMonitor mon) throws NumberFormatException, IOException, FileNotFoundException {

        String o;
        file = getFile(mon);

        parser = new AsciiParser();


        boolean fixedColumns = false;

        int columnCount = 0;

        /**
         * if non-null, this is the delim we are using to parse the file.
         */
        String delim;

        o = params.get("skip");
        if (o != null) {
            parser.setSkipLines(Integer.parseInt(o));
        }

        o = params.get("skipLines");
        if (o != null) {
            parser.setSkipLines(Integer.parseInt(o));
        }

        o = params.get("recCount");
        if (o != null) {
            parser.setRecordCountLimit(Integer.parseInt(o));
        }

        parser.setKeepFileHeader(true);

        o = params.get("comment");
        if (o != null) {
            if ( o.equals("") ) {
                parser.setCommentPrefix(null);
            } else {
                parser.setCommentPrefix(o);
            }
        }

        o = params.get("headerDelim");
        if (o != null) {
            parser.setHeaderDelimiter(o);
        }

        delim = params.get("delim");
        String sFixedColumns = params.get("fixedColumns");

        if (sFixedColumns == null) {
            if (delim == null) {
                AsciiParser.DelimParser p = parser.guessSkipAndDelimParser(file.toString());
                if ( p == null) {
                    throw new IllegalArgumentException("no records found");
                }
                columnCount = p.fieldCount();
                delim = p.getDelim();
            } else {
                if ( delim.equals(",") ) delim="COMMA";
                delim= delim.replaceAll("WHITESPACE", "\\s+");
                delim= delim.replaceAll("SPACE", " ");
                delim= delim.replaceAll("COMMA", ",");
                delim= delim.replaceAll("COLON", ":");
                delim= delim.replaceAll("TAB", "\t");
                delim= delim.replaceAll("whitespace", "\\s+");
                delim= delim.replaceAll("space", " ");
                delim= delim.replaceAll("comma", ",");
                delim= delim.replaceAll("colon", ":");
                delim= delim.replaceAll("tab", "\t");
                if (delim.equals("+")) {
                    delim = " ";
                }
                columnCount = parser.setDelimParser(file.toString(), delim).fieldCount();
            }
            //parser.setPropertyPattern( Pattern.compile("^#\\s*(.+)\\s*\\:\\s*(.+)\\s*") );
            parser.setPropertyPattern(AsciiParser.NAME_COLON_VALUE_PATTERN);
        }

        if (sFixedColumns != null) {
            String s = sFixedColumns;
            AsciiParser.RecordParser p = parser.setFixedColumnsParser(file.toString(), "\\s+");
            try {
                columnCount = Integer.parseInt(sFixedColumns);
            } catch ( NumberFormatException ex ) {
                if (sFixedColumns.equals("")) {
                    columnCount = p.fieldCount();
                } else { // 0-10,20-34
                    String[] ss = s.split(",");
                    int[] starts = new int[ss.length];
                    int[] widths = new int[ss.length];
                    AsciiParser.FieldParser[] fparsers = new AsciiParser.FieldParser[ss.length];
                    for (int i = 0; i < ss.length; i++) {
                        String[] ss2 = ss[i].split("-");
                        starts[i] = Integer.parseInt(ss2[0]);
                        widths[i] = Integer.parseInt(ss2[1]) - starts[i] + 1;
                        fparsers[i] = AsciiParser.DOUBLE_PARSER;
                    }
                    p = parser.setFixedColumnsParser(starts, widths, fparsers);
                    columnCount= p.fieldCount();
                }
            }

            parser.setPropertyPattern(null); // don't look for these for speed
            fixedColumns = true;
            delim = null;
        }

        o = params.get("columnCount");
        if (columnCount == 0) {
            if (o != null) {
                columnCount = Integer.parseInt(o);
            } else {
                columnCount = AsciiParser.guessFieldCount(file.toString());
            }
        }

        o = params.get("fill");
        if (o != null) {
            parser.setFillValue(Double.parseDouble(o));
        }

        o = params.get("validMin");
        if (o != null) {
            this.validMin = Double.parseDouble(o);
        }

        o = params.get("validMax");
        if (o != null) {
            this.validMax = Double.parseDouble(o);
        }

        /* recognize the column as parsable times, parse with slow general purpose time parser */
        o = params.get("time");
        if (o != null) {
            int i = parser.getFieldIndex(o);
            if (i == -1) {
                throw new IllegalArgumentException("field not found for time parameter: " + o);
            } else {
                parser.setFieldParser(i, parser.UNITS_PARSER);
                parser.setUnits(i, Units.t2000);

                depend0 = o;
                timeColumn = i;
            }
        }

        o = params.get("timeFormat");
        if (o != null) {
            String timeFormat = o;
            timeFormat = timeFormat.replaceAll("\\$", "%");
            timeFormat = timeFormat.replaceAll("\\(", "{");
            timeFormat = timeFormat.replaceAll("\\)", "}");
            String timeColumnName = params.get("time");
            timeColumn = timeColumnName == null ? 0 : parser.getFieldIndex(timeColumnName);

            if (timeFormat.equals("ISO8601")) {
                String line = parser.readFirstParseableRecord(file.toString());
                if (line == null) {
                    throw new IllegalArgumentException("file contains no parseable records.");
                }
                String[] ss = new String[ parser.getRecordParser().fieldCount() ];
                parser.getRecordParser().splitRecord(line,ss);
                int i = parser.getFieldIndex(timeColumnName);
                if (i == -1) {
                    i = 0;
                }
                String atime = ss[i];
                timeFormat = TimeParser.iso8601String(atime.trim());
                timeParser = TimeParser.create(timeFormat);
                final Units u = Units.t2000;
                parser.setUnits(i, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {
                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(i, timeFieldParser);

            } else if (delim != null && timeFormat.split(delim, -2).length > 1) {
                timeParser = TimeParser.create(timeFormat);
                // we've got a special case here: the time spans multiple columns, so we'll have to combine later.
                parser.setUnits(timeColumn, Units.dimensionless);
                timeFormats = timeFormat.split(delim, -2);
                timeColumns = timeFormats.length;
                int ib = timeFormat.indexOf("%b"); // real trouble: months are strings.  We can deal with this.
                while (ib != -1) {
                    int monthColumn = timeFormat.substring(0, ib).split("%", -2).length - 1;
                    AsciiParser.FieldParser monthNameFieldParser = new AsciiParser.FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            return TimeUtil.monthNumber(field);
                        }
                    };
                    parser.setFieldParser(monthColumn, monthNameFieldParser);
                    timeFormat= timeFormat.replaceFirst("%b","%m");
                    ib = timeFormat.indexOf("%b");  // support multiple
                }
                ib = timeFormat.indexOf("%j:%H:%M:%S"); // kludge number 1
                if (ib != -1) {
                    int theColumn = timeFormat.substring(0, ib).split("%", -2).length - 1;
                    final Pattern colonDelim= Pattern.compile(":");
                    AsciiParser.FieldParser theFieldParser = new AsciiParser.FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            String[] ss= colonDelim.split(field);
                            if ( ss.length<3 ) throw new ParseException("expected three colons: "+field,0);
                            return Integer.parseInt(ss[0]) + Integer.parseInt(ss[1]) / 24. + Integer.parseInt(ss[2]) / 1440. + Integer.parseInt(ss[3]) / 86400.;
                        }
                    };
                    parser.setFieldParser(theColumn, theFieldParser);
                    timeFormat= timeFormat.replaceFirst("%j:%H:%M:%S","%j");
                }
                ib = timeFormat.indexOf("%H:%M:%S"); // kludge number 2
                if (ib != -1) {
                    int theColumn = timeFormat.substring(0, ib).split("%", -2).length - 1;
                    final Pattern colonDelim= Pattern.compile(":");
                    AsciiParser.FieldParser theFieldParser = new AsciiParser.FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            String[] ss= colonDelim.split(field);
                            if ( ss.length<3 ) throw new ParseException("expected two colons: "+field,0);
                            return Integer.parseInt(ss[0]) + Integer.parseInt(ss[1]) / 60. + Integer.parseInt(ss[2]) / 3600.;
                        }
                    };
                    parser.setFieldParser(theColumn, theFieldParser);
                    timeFormat= timeFormat.replaceFirst("%H:%M:%S","%H");
                }
                timeFormats = timeFormat.split(delim, -2);
                ib = timeFormat.indexOf("%{ignore"); // arbitary skip must not have fields following but before delimiter
                while (ib != -1) {
                    int column = timeFormat.substring(0, ib).split("%", -2).length - 1;
                    AsciiParser.FieldParser nullFieldParser = new AsciiParser.FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            return -1e31;
                        }
                    };
                    parser.setFieldParser(column, nullFieldParser);
                    ib = timeFormat.indexOf("%{ignore",ib+1);
                }
            } else {
                timeParser = TimeParser.create(timeFormat);
                final Units u = Units.t2000;
                parser.setUnits(timeColumn, u);
                AsciiParser.FieldParser timeFieldParser = new AsciiParser.FieldParser() {

                    public double parseField(String field, int fieldIndex) throws ParseException {
                        return timeParser.parse(field).getTime(u);
                    }
                };
                parser.setFieldParser(timeColumn, timeFieldParser);

            }
        } else {
            timeParser = null;
        }

        o = params.get("depend0");
        if (o != null) {
            depend0 = o;
        }

        o = params.get("column");
        if (o != null) {
            column = o;
        }

        o = params.get("rank2");
        if (o != null) {
            rank2 = parseRangeStr(o, columnCount);
            column = null;
        }

        o = params.get("arg_0");
        if (o != null && o.equals("rank2")) {
            rank2 = new int[]{0, columnCount};
            column = null;
        }

        if (column == null && depend0 == null && rank2 == null) {
            if (parser.getFieldNames().length == 2) {
                depend0 = parser.getFieldNames()[0];
                column = parser.getFieldNames()[1];
            } else {
                column = parser.getFieldNames()[0];
            }
        }

        o = params.get("depend1Labels");
        if (o != null) {
            depend1Labels = parseRangeStr(o, columnCount);
        }

        o = params.get("depend1Values");
        if (o != null) {
            depend1Values = parseRangeStr(o, columnCount);
        }

        // check to see if the depend0 or data column appear to be times.  I Promise I won't open the file again until it's read in.
        if ( timeColumn == -1 ) {
            String s = parser.readFirstParseableRecord(file.toString());
            if (s != null) {
                String[] fields = new String[parser.getRecordParser().fieldCount()];
                parser.getRecordParser().splitRecord(s,fields);
                if ( depend0!=null ) {
                    int idep0 = parser.getFieldIndex(depend0);
                    if (idep0 != -1) { // deal with -1 later
                        String field = fields[idep0];
                        try {
                            TimeUtil.parseTime(field);
                            if ( new StringTokenizer( field, ":T-/" ).countTokens()>1 ) {
                                parser.setUnits(idep0, Units.us2000);
                                parser.setFieldParser(idep0, parser.UNITS_PARSER);
                            }
                        } catch (ParseException ex) {
                        }
                    }
                }
                if ( column!=null ) {
                    int icol = parser.getFieldIndex(column);
                    if (icol != -1) { // deal with -1 later
                        String field = fields[icol];
                        try {
                            TimeUtil.parseTime(field);
                            if ( new StringTokenizer( field, ":T-/" ).countTokens()>1 ) {
                                parser.setUnits(icol, Units.us2000);
                                parser.setFieldParser(icol, parser.UNITS_PARSER);
                            }
                        } catch (ParseException ex) {
                        }
                    }
                }
            }
        }

        o = params.get("units");
        if (o != null) {
            String sunits = o;
            Units u = MetadataUtil.lookupUnits(sunits);
            if (column != null) {
                int icol = parser.getFieldIndex(column);
                parser.setUnits(icol, u);
                parser.setFieldParser(icol, parser.UNITS_PARSER);
            }
        }

        // --- done configuration, now read ---
        DDataSet ds1;
        o = params.get("tail");
        if (o != null) {
            ByteBuffer buff= new FileInputStream( file ).getChannel().map( MapMode.READ_ONLY, 0, file.length() );
            int tailNum= Integer.parseInt(o);
            int tailCount=0;
            int ipos=(int)file.length();
            boolean foundNonEOL= false;
            while ( tailCount<tailNum && ipos>=0 ) {
                ipos--;
                byte ch= buff.get((int)ipos);
                if ( ch==10 ) {
                    if ( ipos>1 && buff.get(ipos-1)==13 ) ipos=ipos-1;
                    if ( foundNonEOL ) tailCount++;
                } else if ( ch==13 ) {
                    if ( foundNonEOL ) tailCount++;
                } else {
                    foundNonEOL= true;
                }
            }
            buff.position( tailCount<tailNum ? 0 : ipos+1 );
            InputStream in= new ByteBufferInputStream(buff);
            ds1 = (DDataSet) parser.readStream( new InputStreamReader(in), mon); //DANGER
        } else {
            ds1 = (DDataSet) parser.readFile(file.toString(), mon); //DANGER
        }

        return ds1;
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        if (ds == null) {
            return new HashMap<String, Object>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ds.property(QDataSet.USER_PROPERTIES);
        String header = (String) props.get("fileHeader");
        if (header != null) {
            header = header.replaceAll("\t", "\\\\t");
            props.put("fileHeader", header);
        }
        String firstRecord = (String) props.get("firstRecord");
        if (firstRecord != null) {
            firstRecord = firstRecord.replaceAll("\t", "\\\\t");
            props.put("firstRecord", firstRecord);
        }

        return props;
    }

    /**
     * returns the field index of the name, which can be:
     *   a column name
     *   an implicit column name "field1"
     *   a column index (0 is the first column)
     *   a negative column index (-1 is the last column)
     * @param name
     * @param count
     * @return the index of the field.
     */
    private int columnIndex( String name, int count ) {
        if ( Pattern.matches( "\\d+", name) ) {
            return Integer.parseInt(name);
        } else if ( Pattern.matches( "-\\d+", name) ) {
            return count + Integer.parseInt(name);
        } else if ( Pattern.matches( "field\\d+", name) ) {
            return Integer.parseInt( name.substring(5) );
        } else {
            int idx= parser.getFieldIndex(name);
            return idx;
        }
    }

    /**
     * parse range strings like "3:6", "3:-5", and "Bx_gsm-Bz_gsm"
     * if the delimiter is colon, then the end is exclusive.  If it is "-",
     * then it is inclusive.
     * @param o
     * @param columnCount
     * @return
     * @throws java.lang.NumberFormatException
     */
    private int[] parseRangeStr(String o, int columnCount) throws NumberFormatException {
        String s = o;
        int first = 0;
        int last = columnCount;
        if (s.contains(":")) {
            String[] ss = s.split(":");
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],columnCount);
            }
            if ( ss[1].length() > 0 ) {
                last = columnIndex(ss[1],columnCount);
            }
        } else if ( s.contains("--") ) {
            int isplit= s.indexOf("--",1);
            if ( isplit > 0 ) {
                first = columnIndex( s.substring(0,isplit),columnCount);
            }
            if ( isplit < s.length()-2 ) {
                last = 1 + columnIndex( s.substring(isplit+1),columnCount);
            }
        } else if ( s.contains("-") ) {
            String[] ss = s.split("-");
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],columnCount);
            }
            if ( ss[1].length() > 0 ) {
                last = 1 + columnIndex(ss[1],columnCount);
            }
        }
        return new int[]{first, last};
    }
}
