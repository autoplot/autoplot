/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetUtil;
import org.das2.qds.DataSetWrapper;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.URISplit;

/**
 * Format the QDataSet into Excel spreadsheets (1990s format).  
 * @author jbf
 */
public class ExcelSpreadsheetDataSourceFormat implements DataSourceFormat {

    /**
     * get a label for the dataset.  This is a human-readable string that can contain spaces.
     * @param ds
     * @param deft
     * @return 
     */
    private String labelFor( QDataSet ds, String deft ) {
        String l = (String) ds.property(QDataSet.LABEL);
        if ( l==null ) l= (String) ds.property(QDataSet.NAME);
        if ( l==null ) l= deft;
        return l;
    }
    
    private void formatRank2( HSSFSheet sheet, String cellName, QDataSet data, ProgressMonitor mon) throws IOException {

        int irow;
        short icell;
        HSSFRow row=null;
        HSSFCell cell;
        
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        short icell0;
        if ( dep0==null ) {
            if ( cellName!=null ) {
                icell0= (short)(cellName.charAt(0)-'A');
            } else {
                icell0= 0;
            }
        } else {
            if ( cellName!=null ) {
                icell0= (short)(cellName.charAt(0)-'A');
            } else {
                icell0= 1;
            }
        }
        
        icell= icell0;
        
        if ( dep1!=null ) {
            if ( cellName!=null ) {
                irow= (short) (Short.parseShort( cellName.substring(1) )- 1 );
            } else {
                irow= 0;
            }
        } else {
            if ( cellName!=null ) {
                irow= (short) (Short.parseShort( cellName.substring(1) )- 1 );
            } else {
                irow= 1;
            }
        }
               
        if ( irow>0 ) {
            row= sheet.createRow(irow-1);
        }
        
        boolean okay= DataSetUtil.checkQube(data); // some RBSP/ECT data has rank 2 DEPEND_1 when it is really a qube.
        
        if ( !okay ) {
            throw new IllegalArgumentException("Data is not a qube.  Each record must have the same DEPEND_1.");
        }
        
        if ( dep1!=null && dep1.rank()==2 ) {
            dep1= dep1.slice(0);
        }
                
        if ( dep1 != null && row!=null ) {
            if (dep0 != null && icell>0 ) {
                String l = labelFor(dep0,"");
                if ( icell>0 ) {
                    cell= row.createCell((short)(icell-1));
                    cell.setCellValue( new HSSFRichTextString( l ) );
                }
            }
            Units u = (Units) dep1.property(QDataSet.UNITS);
            if (u == null) {
                u = Units.dimensionless;
            }
            int i;
            for (  i = 0; i < dep1.length(); i++) {
                cell= row.createCell(icell++);
                Datum d= u.createDatum(dep1.value(i));
                setCellValue( cell, d );
            }
        }

        Units u0 = null;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) {
                u0 = Units.dimensionless;
            }
        }

        Units u = (Units) data.property(QDataSet.UNITS);
        if ( u==null ) {
            u= Units.dimensionless;
        }
        
        mon.setTaskSize(data.length());
        mon.started();
        
        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            
            row= sheet.createRow(irow++);
            icell= icell0;
            
            if ( dep0 != null && icell>0 ) {
                assert u0!=null;
                cell= row.createCell((short)(icell-1));
                setCellValue( cell, u0.createDatum(dep0.value(i)) );
            }

            int j;
            for ( j = 0; j < data.length(i); j++) {
                cell= row.createCell(icell++);
                setCellValue( cell, u.createDatum(data.value(i, j)) );
            }
        }
        
        mon.finished();
    }

    private void formatRank1( HSSFSheet sheet, String cellName, QDataSet data, ProgressMonitor mon) throws IOException {
                
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        int irow= 0;
        HSSFRow row;
        HSSFCell cell;
        short icell;

        short icell0;
        if ( dep0==null ) {
            if ( cellName!=null ) {
                icell0= (short)(cellName.charAt(0)-'A');
            } else {
                icell0= 0;
            }
        } else {
            if ( cellName!=null ) {
                icell0= (short)(cellName.charAt(0)-'A');
            } else {
                icell0= 1;
            }
        }
        
        if ( cellName!=null ) {
            irow= (short) (Short.parseShort( cellName.substring(1) )- 1 );
        } else {
            irow= 0;
        }

        icell= icell0;
                   
        if ( irow>0 ) {
            row= sheet.createRow(irow-1);
            if (dep0 != null) {
                String label = labelFor( dep0, "" );
                cell= row.createCell(icell++);                    
                cell.setCellValue( new HSSFRichTextString( label ) );
            }

            {
                String label = labelFor( data, "" );
                cell= row.createCell(icell++);
                cell.setCellValue( new HSSFRichTextString( (label == null ? "" : label) ) );
            }
        }
        
        Units u0 = Units.dimensionless;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) {
                u0 = Units.dimensionless;
            }
        }
        Units u = (Units) data.property(QDataSet.UNITS);
        if ( u==null ) u= Units.dimensionless;
        
        mon.setTaskSize(data.length());
        mon.started();
        
        for (int i = 0; i < data.length(); i++ ) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            
            row= sheet.createRow(irow++);
            icell= icell0;
            
            if (dep0 != null) {
                cell= row.createCell(icell++);
                setCellValue( cell, u0.createDatum(dep0.value(i)) );
            }

            cell= row.createCell(icell++);
            setCellValue( cell, u.createDatum(data.value(i)) );

        }
        
        mon.finished();
    }

    HSSFCellStyle dateCellStyle;
    
    Calendar c= Calendar.getInstance( TimeZone.getTimeZone("GMT") ); // This doesn't seem to affect the result, which is still in the local timezone.
    
    private void setCellValue( HSSFCell cell, Datum datum ) {
        Units u= datum.getUnits();
        if ( UnitsUtil.isTimeLocation(u) ) {
            c.setTimeInMillis( (long)( datum.doubleValue(Units.t1970) * 1000 ) );
            cell.setCellValue( c );
            cell.setCellStyle(dateCellStyle);
        } else if ( UnitsUtil.isNominalMeasurement(u) ) {
            cell.setCellValue( new HSSFRichTextString( datum.toString() ) );
        } else {
            cell.setCellValue( datum.doubleValue(u) );
        }
    }
    
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        
        boolean append= "T".equals( params.get("append") );
        String sheetName= params.get("sheet");
        if ( sheetName==null ) sheetName= "sheet1";
        
        String cellName= params.get("cell"); // note reader is column, firstRow.
        String nodep= params.get("nodep");
        if ( "T".equals(nodep) ) {
            DataSetWrapper dsw= new DataSetWrapper(data);
            dsw.putProperty( QDataSet.DEPEND_0, null );
            dsw.putProperty( QDataSet.DEPEND_1, null );
        }
        
        int irow=0;
        if ( cellName!=null ) {
            irow= (short) (Short.parseShort( cellName.substring(1) )- 1 );
        }
        if ( data.length() > (65534+irow) ) {
            throw new IllegalArgumentException("Data contains too many records to format to Excel spreadsheet.");
        }
            
        HSSFWorkbook wb;
        if ( append ) {
            FileInputStream in=null;
            try {
                in= new FileInputStream( new File( split.resourceUri ) );
                wb= new HSSFWorkbook( in );
            } finally {
                if ( in!=null ) in.close();
            }
        } else {
            wb= new HSSFWorkbook();
        }
        
        FileOutputStream out = new FileOutputStream( new File( split.resourceUri ) );
        try {
            HSSFSheet sheet;
            sheet= wb.getSheet(sheetName);
            if ( sheet==null ) {
                sheet= wb.createSheet(sheetName);
            }
            
            dateCellStyle= wb.createCellStyle();
            dateCellStyle.setDataFormat( HSSFDataFormat.getBuiltinFormat("m/d/yy h:mm") ); // Note this list is limited to https://poi.apache.org/apidocs/org/apache/poi/ss/usermodel/BuiltinFormats.html

            if (data.rank() == 2) {
                formatRank2(sheet, cellName, data, mon);
            } else if (data.rank() == 1) {
                formatRank1(sheet, cellName, data, mon);
            }
            
            HSSFRow lastRow=  sheet.getRow(sheet.getLastRowNum());
            short ncol= lastRow.getLastCellNum();
            for ( short i=0; i<ncol; i++ ) {
                if ( lastRow.getCell(i)!=null && dateCellStyle.equals( lastRow.getCell(i).getCellStyle() ) ) {
                    sheet.autoSizeColumn(i);
                }
            }
            
            wb.write(out);
        } finally {
            out.close();
        }
    }

    public boolean canFormat(QDataSet ds) {
        return ( ds.rank()==1 || ds.rank()==2 ) && ds.length()<=65000;
    }

    public String getDescription() {
        return "Excel Spreadsheet";
    }
}
