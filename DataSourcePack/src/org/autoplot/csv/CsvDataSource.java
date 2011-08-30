/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import com.csvreader.CsvReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.URI;
import java.text.ParseException;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class CsvDataSource extends AbstractDataSource {

    public CsvDataSource(URI uri) {
        super(uri);
    }

    private static Units getUnits( String header, String u ) {
        try {
            Units.dimensionless.parse(u);
            return Units.dimensionless;
        } catch ( Exception ex ) {
        }
        try {
            Units.us2000.parse(u);
            return Units.us2000;
        } catch ( Exception ex ) {
        }
        return EnumerationUnits.create("enum");
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        File f= getFile(uri, mon);
        CsvReader reader= new CsvReader( new FileReader(f) );

        int ncol=-1;

        String[] headers= null;

        if ( reader.readHeaders() ) {
            ncol= reader.getHeaderCount();
            headers= reader.getHeaders();
        }

        String column= getParam( "column", null );
        int icolumn;
        if ( column==null ) {
            icolumn= ncol==-1 ? -1 : ncol-1;
        } else {
            if ( column.startsWith("field") ) {
                icolumn= Integer.parseInt(column.substring(5));
            } else {
                icolumn= reader.getIndex(column);
            }
            if ( icolumn==-1 ) {
                throw new IllegalArgumentException("column not found: "+column);
            }
        }

        String dep0column= getParam( "depend0column", null );
        int idep0column;
        if ( dep0column==null ) {
            idep0column= -1;
        } else {
            idep0column= reader.getIndex(dep0column);
            if ( idep0column==-1 ) {
                throw new IllegalArgumentException("column not found: "+dep0column);
            }
        }

        DataSetBuilder builder= new DataSetBuilder( 1, 100 );
        DataSetBuilder tbuilder= new DataSetBuilder( 1, 100 );

        Units dep0u= Units.dimensionless;
        Units u= Units.dimensionless;

        boolean init= true;

        while ( reader.readRecord() ) {
            if ( init ) {
                if ( icolumn==-1 ) {
                    icolumn= reader.getColumnCount()-1;
                    headers= new String[reader.getColumnCount()];
                    for ( int i=0; i<reader.getColumnCount(); i++ ) {
                        headers[i]= "column_"+i;
                    }
                }
                if ( idep0column>=0 ) dep0u= getUnits( reader.getHeader(idep0column),reader.get(idep0column) );
                u= getUnits( reader.getHeader(icolumn),reader.get(icolumn) );
                init= false;
            }
            try {
                if ( idep0column>=0 ) {
                    if ( dep0u instanceof EnumerationUnits ) {
                        tbuilder.putValue( -1, 0, ((EnumerationUnits)dep0u).createDatum( reader.get(idep0column) ).doubleValue(dep0u) ) ;
                    } else {
                        tbuilder.putValue( -1, 0, dep0u.parse(reader.get(idep0column)).doubleValue(dep0u) );
                    }
                    tbuilder.nextRecord();
                }
                if ( u instanceof EnumerationUnits ) {
                    builder.putValue( -1, 0, ((EnumerationUnits)u).createDatum( reader.get(icolumn) ).doubleValue(u) ) ;
                } else {
                    builder.putValue( -1, 0, u.parse(reader.get(icolumn)).doubleValue(u) );
                }
                builder.nextRecord();
            } catch ( ParseException ex ) {
                System.err.println("skipping line: "+reader.get(idep0column));
            }
        }

        DDataSet ds= builder.getDataSet();
        if ( idep0column>=0 ) {
            DDataSet tds= tbuilder.getDataSet();
            tds.putProperty(QDataSet.UNITS,dep0u);
            tds.putProperty(QDataSet.NAME,Ops.safeName(headers[idep0column]));
            tds.putProperty(QDataSet.LABEL,headers[idep0column]);
            ds.putProperty(QDataSet.DEPEND_0, tds);
        }
        ds.putProperty(QDataSet.UNITS,u);
        ds.putProperty(QDataSet.NAME,Ops.safeName(headers[icolumn]));
        ds.putProperty(QDataSet.LABEL,headers[icolumn]);

        return ds;
    }

}
