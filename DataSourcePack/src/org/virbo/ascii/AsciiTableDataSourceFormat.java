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
import org.das2.datum.format.EnumerationDatumFormatter;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.URISplit;
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

    private void jsonProp( JSONObject jo1, QDataSet ds, String prop, int i ) throws JSONException {
        Object o= ds.property(prop,i);
        if ( o!=null ) {
            if ( o instanceof QDataSet ) {
                jo1.put( prop, o.toString() );
            } else if ( o instanceof Number ) {
                jo1.put( prop, (Number)o );
            } else {
                jo1.put( prop, String.valueOf(o) );
            }
        }
    }

    private void formatBundleDesc(PrintWriter out, QDataSet bundleDesc) throws JSONException {
        JSONObject jo= new JSONObject();
        for ( int i=0; i<bundleDesc.length(); i++ ) {
            String name= (String) bundleDesc.property( QDataSet.NAME,i );
            if ( name==null ) {
                System.err.println("unnamed dataset!");
                name= "field"+i;
            }
            JSONObject jo1= new JSONObject();
            jsonProp( jo1, bundleDesc, QDataSet.UNITS, i );
            jsonProp( jo1, bundleDesc, QDataSet.VALID_MIN, i );
            jsonProp( jo1, bundleDesc, QDataSet.VALID_MAX, i );
            jsonProp( jo1, bundleDesc, QDataSet.FILL_VALUE, i );
            jsonProp( jo1, bundleDesc, QDataSet.DEPEND_0, i );
            jo.put( name, jo1 );
        }

        String json= jo.toString( 3 );

        String[] lines= json.split("\n");
        StringBuilder sb= new StringBuilder();

        for ( int i=0; i<lines.length; i++ ) {
            sb.append( "# "+ lines[i] + "\n" );
        }

        out.print( sb.toString() );

    }

    /**
     * format the rank 2 bundle of data.
     * @param out
     * @param data
     * @param mon
     */
    private void formatBundle(PrintWriter out, QDataSet data, ProgressMonitor mon) {

        QDataSet bundleDesc= (QDataSet) data.property(QDataSet.BUNDLE_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        if ( bundleDesc!=null ) {
            try {
                formatBundleDesc( out, bundleDesc );
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        DatumFormatter[] formats= new DatumFormatter[data.length(0)];
        Units[] uu= new Units[data.length(0)];

        for ( int i=0; i<bundleDesc.length(); i++ ) {
            uu[i] = (Units) bundleDesc.property(QDataSet.UNITS,i);
            if (uu[i] == null) uu[i] = Units.dimensionless;
            formats[i]= uu[i].createDatum(data.value(0,i)).getFormatter();
            if ( formats[i] instanceof EnumerationDatumFormatter ) {
                //((EnumerationDatumFormatter)formats[i]).setAddQuotes(true);
            }
        }

        if ( bundleDesc==null ) {
            throw new IllegalArgumentException("expected to find bundleDesc in dataset!");
        }
        
        if (bundleDesc != null) {
            if (dep0 != null) {
                String l = (String) dep0.property(QDataSet.LABEL);
                if ( l==null ) {
                    if ( Units.t2000.isConvertableTo( SemanticOps.getUnits(dep0) ) ) {
                        l= "time(UTC)";
                    } else {
                        l= "dep0";
                    }
                }
                out.print(" " + l + ", ");
            }

            int i;
            for (  i = 0; i < bundleDesc.length()-1; i++) {
                String l1= (String) bundleDesc.property(QDataSet.LABEL,i);
                if ( l1==null ) {
                    throw new IllegalArgumentException("unnamed dataset in bundle at index "+i);
                }
                if ( l1.trim().length()==0 ) {
                    Units u1=  (Units) bundleDesc.property(QDataSet.UNITS,i);
                    if ( u1!=null && Units.t2000.isConvertableTo( u1 ) ) {
                        l1= "time(UTC)";
                    } else {
                        l1= "field"+i;
                    }
                }
                out.print( l1 + ", " );
            }
            String l1= (String) bundleDesc.property(QDataSet.LABEL,i);
            out.println( l1 == null ? ("field"+i) : l1 );
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
                out.print( formats[j].format( uu[j].createDatum(data.value(i,j)), uu[j] ) + ", ");
            }
            out.println( formats[j].format( uu[j].createDatum(data.value(i,j)), uu[j] )  );
        }
        mon.finished();

    }

    private void formatRank2(PrintWriter out, QDataSet data, ProgressMonitor mon) {
        maybeOutputProperty(out, data, QDataSet.TITLE);
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        DatumFormatter format=null;


        Units u = (Units) data.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;
        format= u.createDatum(data.value(0,0)).getFormatter();

        if ( u!=Units.dimensionless ) maybeOutputProperty( out, data, QDataSet.UNITS );
        
        if (dep1 != null) {
            out.print("#");
            if (dep0 != null) {
                String l = (String) dep0.property(QDataSet.LABEL);
                if ( l==null ) {
                    if ( Units.t2000.isConvertableTo( SemanticOps.getUnits(dep0) ) ) {
                        l= "time(UTC)";
                    } else {
                        l= "dep0";
                    }
                }
                out.print( l + ", ");
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
            dep0Format= dep0!=null ? dep0Units.createDatum(dep0.value(0)).getFormatter() : null;
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
     * format the data to an ASCII table file.  No controls are provided presently, but this
     * may change.
     * @param uri
     * @param data
     * @param mon
     * @throws IOException
     */
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
        URISplit split= URISplit.parse(uri);
        //java.util.Map<String,String> params= URISplit.parseParams(split.params);

        PrintWriter out = new PrintWriter( new File( split.resourceUri ) );

        out.println("# Generated by Autoplot on " + new Date());

        if (data.rank() == 2) {
            if ( SemanticOps.isBundle(data) ) {
                formatBundle( out, data, mon );
            } else {
                formatRank2(out, data, mon);
            }
        } else if (data.rank() == 1) {
            formatRank1(out, data, mon);
        }
        out.close();
    }

}
