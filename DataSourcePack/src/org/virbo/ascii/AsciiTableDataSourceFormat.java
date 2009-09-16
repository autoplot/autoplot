/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.io.File;
import org.das2.datum.Units;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.das2.datum.format.DatumFormatter;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format the QDataSet into Ascii tables.  
 * @author jbf
 */
public class AsciiTableDataSourceFormat implements DataSourceFormat {

    private void maybeOutputProperty(PrintWriter out, QDataSet data, String property) {
        Object v = data.property(property);
        if (v != null) {
            out.println("# " + property + ": " + v);
        }
    }

    private void formatRank2(PrintWriter out, QDataSet data, ProgressMonitor mon) {
        maybeOutputProperty(out, data, QDataSet.TITLE);
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        DatumFormatter format=null;


        Units u = (Units) data.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;
        format= u.createDatum(data.value(0)).getFormatter();

        if ( u!=Units.dimensionless ) maybeOutputProperty( out, data, QDataSet.UNITS );
        
        if (dep1 != null) {
            out.print("#");
            if (dep0 != null) {
                String l = (String) dep0.property(QDataSet.LABEL);
                out.print(" " + (l == null ? "dep0" : l) + ", ");
            }
            Units dep1units = (Units) dep1.property(QDataSet.UNITS);
            if (dep1units == null) dep1units = Units.dimensionless;
            
            int i;
            for (  i = 0; i < dep1.length()-1; i++) {
                out.print(dep1units == null ? "" + dep1.value(i) : dep1units.createDatum(dep1.value(i)) + ", " );
            }
            out.println(dep1units == null ? "" + dep1.value(i) : dep1units.createDatum(dep1.value(i)) );
        }

        Units u0 = null;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) u0 = Units.dimensionless;
            
        }

        mon.setTaskSize(data.length());
        mon.started();
        
        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            if (dep0 != null) {
                out.print("" + u0.createDatum(dep0.value(i)) + ", ");
            }

            int j;
            for ( j = 0; j < data.length(i) - 1; j++) {
                out.print( format.format( u.createDatum(data.value(i,j)), u ) + ", ");
            }
            out.println( format.format( u.createDatum(data.value(i,j)), u )  );
        }
        mon.finished();
    }

    private String dataSetLabel( QDataSet ds, String deft ) {
        String name = (String) ds.property(QDataSet.NAME);
        if ( name==null || name.equals("") ) name= deft;
        String label= name;
        Units units= (Units)ds.property(QDataSet.UNITS);
        if ( units!=null && units!=Units.dimensionless ) {
            label= label+" ("+units+")";
        }
        return label;
    }

    private void formatRank1(PrintWriter out, QDataSet data, ProgressMonitor mon) {
        maybeOutputProperty(out, data, QDataSet.TITLE);

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        Units dep0Units=null;
        DatumFormatter dep0Format=null;

        Units units;
        DatumFormatter format=null;

        List<QDataSet> planes= new ArrayList<QDataSet>();
        List<Units> planeUnits= new ArrayList<Units>();
        List<DatumFormatter> planeFormats= new ArrayList<DatumFormatter>();

        StringBuffer buf= new StringBuffer();

        String l;
        if (dep0 != null) {
            l = dataSetLabel( dep0, "dep0" );
            buf.append( ", " + l );
            dep0Units= (Units) dep0.property(QDataSet.UNITS);
            if ( dep0Units==null ) dep0Units= Units.dimensionless;
        }

        l = dataSetLabel( data, "data" );
        buf.append( ", " + l );
        units= (Units) data.property(QDataSet.UNITS);
        if ( units==null ) units= Units.dimensionless;

        for ( int i=0; i<QDataSet.MAX_PLANE_COUNT; i++ ) {
            QDataSet plane= (QDataSet) data.property( "PLANE_"+i );
            if ( plane!=null ) {
                planes.add(plane);
                planeUnits.add((Units)plane.property(QDataSet.UNITS));
                if ( planeUnits.get(i)==null ) planeUnits.add(i, Units.dimensionless );
                l= dataSetLabel( plane, "data"+i );
                buf.append( ", " + l );
            }
        }

        out.println( buf.substring(2) );
        
        mon.setTaskSize(data.length());
        mon.started();

        if ( data.length()>0 ) {
            dep0Format= dep0Units.createDatum(dep0.value(0)).getFormatter();
            format= units.createDatum(data.value(0)).getFormatter();
            planeFormats= new ArrayList<DatumFormatter>(planes.size());
            for ( int i=0; i<planes.size(); i++ ) {
                planeFormats.add(i, planeUnits.get(i).createDatum(planes.get(i).value(i)).getFormatter() );
            }
        }

        for (int i = 0; i < data.length(); i++ ) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;

            buf= new StringBuffer();
            if (dep0 != null) {
                buf.append( ", " + dep0Format.format(dep0Units.createDatum(dep0.value(i)), dep0Units ) );
            }

            buf.append( ", " + format.format( units.createDatum(data.value(i)), units) );

            for ( int j=0; j<planes.size(); j++ ) {
                buf.append( ", " + planeFormats.get(j).format( planeUnits.get(j).createDatum(planes.get(j).value(i)),
                        planeUnits.get(j) ) );
            }
            out.println( buf.substring(2) );
        }
        
        mon.finished();
    }

    /**
     * format the ascii table to the file.  No controls are provided presently, but this
     * may change.
     * @param uri
     * @param data
     * @param mon
     * @throws IOException
     */
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
        URLSplit split= URLSplit.parse(uri);
        //java.util.Map<String,String> params= URLSplit.parseParams(split.params);

        PrintWriter out = new PrintWriter( new File( split.resourceUri ) );

        out.println("# Generated by Autoplot on " + new Date());

        if (data.rank() == 2) {
            formatRank2(out, data, mon);
        } else if (data.rank() == 1) {
            formatRank1(out, data, mon);
        }
        out.close();
    }
}
