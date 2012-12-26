/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.excel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSourceUtil;

/**
 * Utilties.
 * @author jbf
 */
public class ExcelUtil {
    
    public static short getColumnNumber( HSSFSheet sheet, String id, int firstRow ) {
        HSSFRow row = sheet.getRow(firstRow);
        if ( row==null ) throw new IllegalArgumentException("no such row "+(firstRow+1)+" in sheet");
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
        } else if (id.length() == 2 && Character.isUpperCase(id.charAt(0)) && Character.isLetter(id.charAt(0)) 
                && Character.isUpperCase(id.charAt(1)) && Character.isLetter(id.charAt(1)) ) {
            return (short) ( (id.charAt(0) - 'A' + 1 ) * 26 + (id.charAt(1) - 'A') );
        } else {
            throw new IllegalArgumentException("unable to find column " + id);
        }
    }
    
    public static List<String> getSheets( HSSFWorkbook wb, CompletionContext cc, ProgressMonitor mon) throws IOException {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String s = wb.getSheetName(i);
            result.add(s);
        }
        return result;
    }
    
    /**
     * inspect the first row for columns.  Strings may be picked up as labels if the
     * next row contains values.
     * @param firstRowString the firstRow or null.
     * @param ssheet the sheet name
     * @return
     * @throws java.io.IOException
     * @throws IllegalArgumentException when the sheet contains no records
     */
    public static Map<Integer,String> getColumns( HSSFWorkbook wb, String ssheet, String firstRowString, ProgressMonitor mon) throws IOException {
        
        Map<Integer,String> result = new LinkedHashMap<Integer, String>();
        HSSFSheet sheet;
        if (ssheet == null) {
            sheet = wb.getSheetAt(0);
            ssheet = wb.getSheetName(0);
        } else {
            sheet = wb.getSheet(ssheet);
        }

        if (sheet == null) {
            throw new IllegalArgumentException("no such sheet \"" + ssheet + "\"");
        }

        int firstRow = firstRowString == null ? 0 : Integer.parseInt(firstRowString) - 1;
        HSSFRow row = sheet.getRow(firstRow);

        if (row == null) {
            if ( firstRow==0 ) {
                throw new IllegalArgumentException("(sheet \"" + ssheet + "\" contains no records)");
            } else {
                throw new IllegalArgumentException("(sheet \"" + ssheet + "\" doesn't have a row at "+(firstRow+1)+")");
            }
        }

        int inextRow = ExcelSpreadsheetDataSource.findFirstRow(sheet, firstRow);
        HSSFRow nextRow;        // first row of data        
        nextRow = sheet.getRow(inextRow);
        
        if (nextRow != null) {
            int n = nextRow.getLastCellNum();
            for (int i = nextRow.getFirstCellNum(); i < n; i++) {
                String name= getNameForColumn(nextRow, row, i);
                result.put(i, name);
            }
        }

        return result;
    }

    private static String getNameForColumn( HSSFRow nextRow, HSSFRow row, int icol ) {
        HSSFCell nextCell = nextRow.getCell((short) icol);
        if (nextCell != null && nextCell.getCellType() == 0) {
            HSSFCell cell = row.getCell((short) icol);
            if (cell == null) {
                return "" + (char) (icol + 'A');
            } else {
                if (cell.getCellType() == 0) {
                    // 1=String
                    return "" + (char) (icol + 'A');
                } else {
                    return DataSourceUtil.toJavaIdentifier(cell.getRichStringCellValue().toString());
                }
            }
        } else {
            return "" + (char) (icol + 'A');
        }
    }
    
}
