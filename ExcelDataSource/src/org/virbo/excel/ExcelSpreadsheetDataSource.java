/*
 * ExcelSpreadsheetDataSource.java
 *
 * Created on April 1, 2007, 6:55 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.excel;

import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSource extends AbstractDataSource {

    POIFSFileSystem fs;
    HSSFSheet sheet;
    ExcelSpreadsheetDataSet data;
    static Logger logger= Logger.getLogger("org.autoplot.ExcelSpreadsheetDataSource");

    /** Creates a new instance of ExcelSpreadsheetDataSource.
     * file://c:/myfile.xls?column=N&depend0=G
     *   parameters:
     *     column=id  the column ID for the data
     *     depend0=id the column ID for the x tags
     * file://C:/iowaCitySales2004-2006.latlong.xls?column=M[2:]&depend0=B[2:]&plane0=C[2:]
     */
    public ExcelSpreadsheetDataSource(URI uri) throws IOException {
        super(uri);
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {
        File file = getFile(mon);
        InputStream in = new FileInputStream(file);
        fs = new POIFSFileSystem(in);
        HSSFWorkbook wb = new HSSFWorkbook(fs);

        String ssheet = (String) params.get("sheet");
        if (ssheet == null) {
            sheet = wb.getSheetAt(0);
        } else {
            ssheet= DataSetURI.maybePlusToSpace(ssheet);
            sheet = wb.getSheet(ssheet);
            if ( sheet==null ) throw new IllegalArgumentException("no such sheet: "+ssheet);
            logger.fine("found sheet "+ssheet+" with "+sheet.getLastRowNum() +" rows");
        }
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + ssheet);
        }

        String firstRowString = (String) params.get("firstRow");
        int firstRow = firstRowString == null ? -1 : Integer.parseInt(firstRowString) - 1;

        
        String d = (String) params.get("column");

        int[] spec = parseDataSetSpec(d, firstRow, -1);
        
        firstRow= spec[2];
        
        boolean labels=true;
        HSSFRow row= sheet.getRow(firstRow);
        for ( short i=0; labels && i<row.getLastCellNum(); i++ ) {
            HSSFCell cell= row.getCell(i);
            if ( cell==null ) continue;
            if ( cell.getCellType() == 0 ) {
                labels= false;
            }
            if ( i==spec[0] &&  cell.getCellType() != 1 ) {  // for the dataset column, this must be a string.
                labels= false;
            }
        }
        
        String recCountString= (String) params.get("recCount" ) ;
        if ( recCountString!=null ) {
            int recCount= Integer.parseInt(recCountString);
            spec[3]= Math.min( spec[3], spec[2]+recCount );
        }
        
        data = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
        if ( d.length()>1 ) data.putProperty( QDataSet.NAME, d );

        d = (String) params.get("depend0");
        if (d != null) {
            int[] spec2 = parseDataSetSpec(d, firstRow, -1);
            spec[0]= spec2[0];
            ExcelSpreadsheetDataSet depend0 = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
            if ( d.length()>1 ) depend0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.DEPEND_0, depend0);
            if ( data.getFirstRow()!=depend0.getFirstRow() ) {
                throw new IllegalArgumentException("rows must not contain empty cells in the first row");
            }
        }

        d = (String) params.get("plane0");
        if (d != null) {
            int[] spec2 = parseDataSetSpec(d, firstRow, -1);
            spec[0]= spec2[0];
            ExcelSpreadsheetDataSet p0 = new ExcelSpreadsheetDataSet( (short)spec[0], (short)spec[1], spec[2], spec[3], labels );
            if ( d.length()>1 ) p0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.PLANE_0, p0);
            if ( data.getFirstRow()!=p0.getFirstRow() ) {
                throw new IllegalArgumentException("rows must not contain empty cells in the first row");
            }
        }


        return data;
    }

    /**
     * parse spec into [icstart,icend,jrstart,jrend]
     * AC11:AC23
     * A1:10
     * A
     * Returns [ columnNumber, lastColumnNumber, first, last ]
     * @param spec
     * @return
     */
    private int[] parseDataSetSpec( String spec, int firstRow, int lastRow ) {
        Pattern p = Pattern.compile("([a-zA-Z][a-zA-Z]*)(\\d+):([a-zA-Z][a-zA-Z]*)?(\\d+)?");
        Matcher m = p.matcher(spec);


        short columnNumber;
        short columnNumber1;

        if (!m.matches()) {
            throw new IllegalArgumentException("bad spec!");
        } else {
            String col = m.group(1);
            if (m.group(3) == null) {
                if (firstRow == -1) {
                    firstRow = 0;
                }
                if (lastRow == -1) {
                    lastRow = sheet.getLastRowNum();
                }
            } else {
                firstRow = Integer.parseInt(m.group(2));
                if (m.group(4) == null) {
                    if (lastRow == -1) {
                        lastRow = sheet.getLastRowNum();
                    }
                } else {
                    lastRow = Integer.parseInt(m.group(4));
                }
            }
            if ( m.group(3)!=null ) {
                columnNumber1= getColumnNumber(m.group(3), firstRow);
            } else {
                columnNumber1= -1;
            }
            columnNumber = getColumnNumber(col, firstRow);
            return new int[]{columnNumber, columnNumber1, firstRow, lastRow};
        }
    }

    /**
     *
     * @param id
     * @param firstRow
     * @throws IllegalArgumentException if there is no row in the sheet.
     * @return
     */
    private short getColumnNumber(String id, int firstRow) {
        return ExcelUtil.getColumnNumber( sheet, id, firstRow);
    }
    


    protected static int findFirstRow( HSSFSheet sheet, int firstRow ) {
        int ilastRow= sheet.getPhysicalNumberOfRows();
        int inextRow = firstRow;
        HSSFRow nextRow;        // first row of data        

        int dataCellCount = 0;
        while (inextRow < ilastRow && inextRow < firstRow + 4) {
            nextRow = sheet.getRow(inextRow);
            if (nextRow != null) {
                int n = nextRow.getLastCellNum();
                for (int i = nextRow.getFirstCellNum(); i < n; i++) {
                    HSSFCell nextCell = nextRow.getCell((short) i);
                    if (nextCell != null && nextCell.getCellType() == 0) {
                        dataCellCount++;
                    }
                }
            }
            if (dataCellCount == 0) {
                inextRow++;
            } else {
                break;
            }
        }
        return inextRow;
    }

    class ExcelSpreadsheetDataSet extends AbstractDataSet {

        short columnNumber;
        int firstRow;
        int length;
        int length1;
        boolean isDate;
        Units units;
        boolean transpose= false;
        int rank;
        
        /**
         * @param lastColumnNumber if not -1, then the rank 2 column number.
         * @param firstRow is the first row to read.  0 is the first row.
         * @param lastRow is the last row number, exclusive.  If -1, then rank 1 row.
         */
        ExcelSpreadsheetDataSet( short columnNumber, short lastColumnNumber, int firstRow, int lastRow, boolean firstRowIsLabels ) {
            
            if ( firstRowIsLabels ) {
                firstRow= findFirstRow(sheet, firstRow);
            }

            this.columnNumber = columnNumber;
            this.firstRow = firstRow;
            if ( lastRow==-1 && lastColumnNumber!=-1 ) {
                this.length = lastColumnNumber - columnNumber;
                this.transpose= true;
            } else {
                this.length = lastRow - firstRow;
            }
            if ( lastRow>-1 && lastColumnNumber>-1 ) {
                this.rank=2;
                if ( this.transpose ) {
                    this.length1= lastRow - firstRow;
                } else {
                    if ( this.length==0 ) {
                        this.length= lastColumnNumber - columnNumber;
                        this.rank=1;
                        this.transpose= true;
                    } else {
                        this.length1= lastColumnNumber - columnNumber;
                    }
                }
            } else {
                this.rank=1;
            }

            HSSFRow row= sheet.getRow(this.firstRow);
            HSSFCell cell = row.getCell(columnNumber);
            if ( cell==null ) {
                row= null;
            }
            while ( row==null && firstRow<lastRow ) {
                firstRow++;
                row= sheet.getRow(firstRow);
                cell = row.getCell(columnNumber);
                if ( cell==null ) {
                    row= null;
                }
            }
            if ( row==null ) {
                throw new IllegalArgumentException("unable to identify first row");
            }

            this.firstRow= firstRow;
            cell = row.getCell(columnNumber);
            units= Units.dimensionless;
            if ( cell.getCellType()!=HSSFCell.CELL_TYPE_STRING ) {
                isDate = HSSFDateUtil.isCellDateFormatted(cell);
                if (isDate) {
                    properties.put(QDataSet.UNITS, Units.t1970);
                    units= Units.t1970;
                }
            } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                String s= cell.getStringCellValue();
                try {
                    Units.t1970.parse(s);
                    properties.put(QDataSet.UNITS, Units.t1970);
                    units= Units.t1970;
                } catch ( ParseException ex ) {
                    properties.put(QDataSet.UNITS, Units.dimensionless);
                }
            }
        }

        public int getFirstRow() {
            return firstRow;
        }
        
        public int rank() {
            return rank;
        }

        public double value(int i) {
            HSSFRow row = null;
            HSSFCell cell = null;
            try {
                if ( transpose ) {
                    row = sheet.getRow(firstRow);
                    cell = row.getCell(columnNumber + i );
                } else {
                    row = sheet.getRow(i + firstRow);
                    cell = row.getCell(columnNumber);
                }
                if (isDate) {
                    Date d = cell.getDateCellValue();
                    return d.getTime() / 1000.;
                } else {
                    if ( cell==null ) {
                        return Double.NaN;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_NUMERIC ) {
                        double d = cell.getNumericCellValue();
                        return d;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                        try {
                            double d= units.parse(cell.getStringCellValue()).doubleValue(units);
                            return d;
                        } catch ( ParseException ex ) {
                            return Double.NaN;
                        }
                    } else {
                        return Double.NaN;
                    }
                }
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        public double value(int i,int j) {
            HSSFRow row = null;
            HSSFCell cell = null;
            try {
                if ( transpose ) {
                    row = sheet.getRow( j + firstRow );
                    cell = row.getCell( i + columnNumber );
                } else {
                    row = sheet.getRow( i + firstRow );
                    cell = row.getCell( j + columnNumber );
                }
                if (isDate) {
                    Date d = cell.getDateCellValue();
                    return d.getTime() / 1000.;
                } else {
                    if ( cell==null ) {
                        return Double.NaN;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_NUMERIC ) {
                        double d = cell.getNumericCellValue();
                        return d;
                    } else if ( cell.getCellType()==HSSFCell.CELL_TYPE_STRING ) {
                        try {
                            double d= units.parse(cell.getStringCellValue()).doubleValue(units);
                            return d;
                        } catch ( ParseException ex ) {
                            return Double.NaN;
                        }
                    } else {
                        return Double.NaN;
                    }
                }
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        public int length() {
            return length;
        }

        public int length( int i ) {
            return length1;
        }

    }
}
