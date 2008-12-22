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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.dsutil.AsciiParser;
import org.das2.util.TimeParser;
import java.text.ParseException;
import org.virbo.dataset.DataSetUtil;
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

        // combine times if necessary
        if (timeColumns > 1) {
            final Units u = Units.t2000;
            // replace the first column with the datum time
            for (int i = 0; i < ds.length(); i++) {
                timeParser.resetSeconds();
                for (int j = 0; j < timeColumns; j++) {
                    double d= ds.value(i, timeColumn + j);
                    double fp= d-(int)Math.floor(d);
                    timeParser.setDigit(timeFormats[j], (int) d );
                    if ( fp==0 ) {
                        timeParser.setDigit(timeFormats[j], (int) d );
                    } else {
                        timeParser.setDigit(timeFormats[j], d );
                    }
                }
                ds.putValue(i, timeColumn, timeParser.getTime(Units.t2000) );                   
            }
            parser.setUnits(timeColumn, Units.t2000);
        }

        DDataSet vds = null;
        DDataSet dep0 = null;

        if ( ( column==null ) && ( timeColumn!=-1 ) ) {
            column= parser.getFieldNames()[timeColumn];
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
            }
            vds = DDataSet.copy(DataSetOps.slice1(ds, icol));
            vds.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            if (validMax != Double.POSITIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MAX, validMax);
            }
            if (validMin != Double.NEGATIVE_INFINITY) {
                vds.putProperty(QDataSet.VALID_MIN, validMin);
            }
        }

        if (depend0 != null) {
            int icol = parser.getFieldIndex(depend0);
            dep0 = DDataSet.copy(DataSetOps.slice1(ds, icol));
            dep0.putProperty(QDataSet.UNITS, parser.getUnits(icol));
            if (DataSetUtil.isMonotonic(dep0)) {
                dep0.putProperty(DDataSet.MONOTONIC, Boolean.TRUE);
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
            return DataSetOps.leafTrim(ds, rank2[0], rank2[1]);
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
        file = DataSetURL.getFile(url, mon);

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
        
        o= params.get("skipLines");
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
            parser.setCommentPrefix(o);
        }

        o = params.get("delim");
        if (o != null) {
            delim = o;
        } else {
            delim = null;
        }
        if (delim == null) {
            String line = parser.readFirstRecord(file.toString());
            if (line == null) {
                throw new IllegalArgumentException("no records found");
            }
            AsciiParser.DelimParser p = parser.guessDelimParser(line);
            columnCount = p.fieldCount();
            delim = p.getDelim();
        } else {
            if (delim.equals("+")) {
                delim = " ";
            }
            columnCount = parser.setDelimParser(file.toString(), delim).fieldCount();
        }
        //parser.setPropertyPattern( Pattern.compile("^#\\s*(.+)\\s*\\:\\s*(.+)\\s*") );
        parser.setPropertyPattern(AsciiParser.NAME_COLON_VALUE_PATTERN);

        o = params.get("fixedColumns");
        if (o != null) {
            String s = o;
            AsciiParser.RecordParser p = parser.setFixedColumnsParser(file.toString(), "\\s+");
            if (o.equals("")) {
                columnCount = p.fieldCount();
            } else if (s.contains(",")) { // 0-10,20-34
                String[] ss = s.split(",");
                int[] starts = new int[ss.length];
                int[] widths = new int[ss.length];
                AsciiParser.FieldParser[] fparsers = new AsciiParser.FieldParser[ss.length];
                for (int i = 0; i < ss.length; i++) {
                    String[] ss2 = ss[i].split("-");
                    starts[i] = Integer.parseInt(ss2[0]);
                    widths[i] = Integer.parseInt(ss2[1]) - starts[i];
                    fparsers[i] = AsciiParser.DOUBLE_PARSER;
                }
                p = parser.setFixedColumnsParser(starts, widths, fparsers);

            } else {
                columnCount = Integer.parseInt(o);
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
                if ( line==null ) throw new IllegalArgumentException("file contains no parseable records.");
                String[] ss = parser.getRecordParser().fields(line);
                int i = parser.getFieldIndex(timeColumnName);
                if (i == -1) {
                    i = 0;
                }
                String atime = ss[i];
                timeFormat = TimeParser.iso8601String(atime.trim());
                timeParser = TimeParser.create( timeFormat);
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
                if (ib != -1) {
                    int monthColumn = timeFormat.substring(0, ib).split("%", -2).length - 1;
                    AsciiParser.FieldParser monthNameFieldParser = new AsciiParser.FieldParser() {
                        public double parseField(String field, int columnIndex) throws ParseException {
                            return TimeUtil.monthNumber(field);
                        }
                    };
                    parser.setFieldParser(monthColumn, monthNameFieldParser);
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
            String s = o;
            int first = 0;
            int last = columnCount;
            if (s.contains(":")) {
                String[] ss = s.split(":");
                if (ss[0].length() > 0) {
                    first = Integer.parseInt(ss[0]);
                    if (first < 0) {
                        first = columnCount + first;
                    }
                }
                if (ss.length > 1 && ss[1].length() > 0) {
                    last = Integer.parseInt(ss[1]);
                    if (last < 0) {
                        last = columnCount + last;
                    }
                }
            }
            rank2 = new int[]{first, last};
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

        // check to see if the depend0 column appears to be times.  I Promise I won't open the file again until it's read in.
        if (timeColumn == -1 && depend0 != null) {
            String s = parser.readFirstParseableRecord(file.toString());
            if (s != null) {
                String[] fields = parser.getRecordParser().fields(s);
                int idep0 = parser.getFieldIndex(depend0);
                if (idep0 != -1) { // deal with -1 later
                    String field = fields[idep0];
                    try {
                        Units.us2000.parse(field);
                        parser.setUnits(idep0, Units.us2000);
                        parser.setFieldParser(idep0, parser.UNITS_PARSER);
                    } catch (ParseException ex) {
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
        DDataSet ds1 = (DDataSet) parser.readFile(file.toString(), mon); //DANGER

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
}
