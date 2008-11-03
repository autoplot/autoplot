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
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URLSplit;

/**
 *
 * @author jbf
 */
public class ExcelSpreadsheetDataSource extends AbstractDataSource {

    POIFSFileSystem fs;
    HSSFSheet sheet;
    ExcelSpreadsheetDataSet data;

    /** Creates a new instance of ExcelSpreadsheetDataSource.
     * file://c:/myfile.xls?column=N&depend0=G
     *   parameters:
     *     column=id  the column ID for the data
     *     depend0=id the column ID for the x tags
     * file://C:/iowaCitySales2004-2006.latlong.xls?column=M[2:]&depend0=B[2:]&plane0=C[2:]
     */
    public ExcelSpreadsheetDataSource(URL url) throws IOException {
        super(url);
    }

    public QDataSet getDataSet(ProgressMonitor mon) throws IOException {
        File file = DataSetURL.getFile(url, mon);
        InputStream in = new FileInputStream(file);
        fs = new POIFSFileSystem(in);
        HSSFWorkbook wb = new HSSFWorkbook(fs);

        String query = url.getQuery(); // the part after the ?
        Map m = URLSplit.parseParams(query);

        String ssheet = (String) m.get("sheet");
        if (ssheet == null) {
            sheet = wb.getSheetAt(0);
        } else {
            sheet = wb.getSheet(ssheet);
        }
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + ssheet);
        }

        String firstRowString = (String) m.get("firstRow");
        int firstRow = firstRowString == null ? -1 : Integer.parseInt(firstRowString) - 1;

        
        String d = (String) m.get("column");

        int[] spec = parseDataSetSpec(d, firstRow, -1);
        
        firstRow= spec[1];
        
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
        
        data = new ExcelSpreadsheetDataSet((short) spec[0], spec[1], spec[2], labels );        
        if ( d.length()>1 ) data.putProperty( QDataSet.NAME, d );

        d = (String) m.get("depend0");
        if (d != null) {
            spec = parseDataSetSpec(d, firstRow, -1);
            ExcelSpreadsheetDataSet depend0 = new ExcelSpreadsheetDataSet((short) spec[0], spec[1], spec[2], labels );
            if ( d.length()>1 ) depend0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.DEPEND_0, depend0);
        }

        d = (String) m.get("plane0");
        if (d != null) {
            spec = parseDataSetSpec(d, firstRow, -1);
            ExcelSpreadsheetDataSet p0 = new ExcelSpreadsheetDataSet((short) spec[0], spec[1], spec[2], labels );
            if ( d.length()>1 ) p0.putProperty( QDataSet.NAME, d );        
            data.putProperty(QDataSet.PLANE_0, p0);
        }
        return data;
    }

    /**
     * Returns [ columnNumber, first, last ] 
     * @param spec
     * @return
     */
    private int[] parseDataSetSpec(String spec, int firstRow, int lastRow) {
        Pattern p = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*)(\\[(\\d+):(\\d+)?\\])?");
        Matcher m = p.matcher(spec);

        short columnNumber;

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
                firstRow = Integer.parseInt(m.group(3));
                if (m.group(4) == null) {
                    if (lastRow == -1) {
                        lastRow = sheet.getLastRowNum();
                    }
                } else {
                    lastRow = Integer.parseInt(m.group(4));
                }
            }
            columnNumber = getColumnNumber(col, firstRow);
            return new int[]{columnNumber, firstRow, lastRow};
        }
    }

    private short getColumnNumber(String id, int firstRow) {
        HSSFRow row = sheet.getRow(firstRow);
        for (short i = 0; i < row.getLastCellNum(); i++) {
            HSSFCell cell = row.getCell(i);
            if (cell != null && cell.getCellType() == 1) {
                String label = cell.getStringCellValue();
                String id1 = null;
                if (label.charAt(0) == id.charAt(0)) {
                    id1 = DataSourceUtil.toJavaIdentifier(label);
                }
                if (id.equals(id1)) {
                    return i;
                }
            }
        }
        if (id.length() == 1) {
            return (short) (id.charAt(0) - 'A');
        } else if (id.length() == 2) {
            throw new IllegalArgumentException("unable to find column " + id + ", two-digits implicit IDs not supported");
        } else {
            throw new IllegalArgumentException("unable to find column " + id);
        }
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

    class ExcelSpreadsheetDataSet implements QDataSet {

        short columnNumber;
        int firstRow;
        int length;
        boolean isDate;
        Map properties = new HashMap();

        /**
         * @param firstRow is the first row to read.  0 is the first row.
         * @param lastRow is the last row number, exclusive.
         */
        ExcelSpreadsheetDataSet( short columnNumber, int firstRow, int lastRow, boolean firstRowIsLabels ) {
            if ( firstRowIsLabels ) {
                firstRow= findFirstRow(sheet, firstRow);
            }
            
            this.columnNumber = columnNumber;
            this.firstRow = firstRow;
            this.length = lastRow - firstRow;
            HSSFRow row= sheet.getRow(this.firstRow);
            while ( row==null && firstRow<lastRow ) {
                firstRow++;
                row= sheet.getRow(firstRow);
            }
            this.firstRow= firstRow;
            HSSFCell cell = row.getCell(columnNumber);
            isDate = HSSFDateUtil.isCellDateFormatted(cell);
            if (isDate) {
                properties.put(QDataSet.UNITS, Units.t1970);
            }
        }

        public int rank() {
            return 1;
        }

        public double value(int i) {
            HSSFRow row = null;
            HSSFCell cell = null;
            try {
                row = sheet.getRow(i + firstRow);
                cell = row.getCell(columnNumber);
                if (isDate) {
                    Date d = cell.getDateCellValue();
                    return d.getTime() / 1000;
                } else {
                    double d = cell.getNumericCellValue();
                    return d;
                }
            } catch (RuntimeException e) {
                String cellID = String.valueOf((char) ('A' + columnNumber)) + (firstRow + i);
                return -1e31;
            }
        }

        public double value(int i0, int i1) {
            throw new IllegalArgumentException("rank");
        }

        public double value(int i0, int i1, int i2) {
            throw new IllegalArgumentException("rank");
        }

        public Object property(String name) {
            return properties.get(name);
        }

        public Object property(String name, int i) {
            return properties.get(name);
        }

        public Object property(String name, int i0, int i1) {
            return properties.get(name);
        }

        public void putProperty(String name, Object value) {
            properties.put(name, value);
        }

        public int length() {
            return length;
        }

        public int length(int i) {
            throw new IllegalArgumentException("ranklimit");
        }

        public int length(int i, int j) {
            throw new IllegalArgumentException("ranklimit");
        }

        public String toString() {
            return DataSetUtil.toString(this);
        }
    }
}
