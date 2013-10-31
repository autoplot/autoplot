/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.io.File;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.EnumerationDatumFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.datum.format.TimeDatumFormatterFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSourceFormat;
import org.das2.datum.format.FormatStringFormatter;
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dsops.Ops;

/**
 * Format the QDataSet into Ascii tables.  
 * @author jbf
 */
public class AsciiTableDataSourceFormat extends AbstractDataSourceFormat {

    private static final Logger logger= Logger.getLogger("apdss.ascii");

    private DatumFormatter getTimeFormatter( String ft ) {
        DatumFormatter tformat;
        String ft0= ft;
        ft= ft.toLowerCase();
        if (ft.equals("iso8601")) {
            tformat = TimeDatumFormatterFactory.getInstance().defaultFormatter();
        } else if ( ft0.startsWith("%")
                || ft.startsWith("$") ) {
            if ( ft0.startsWith("$") ) { // provide convenient URI-friendly spec
                ft0= ft0.replaceAll("\\$", "%");
            }
            try {
                tformat = new TimeDatumFormatter(ft0);
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                try {
                    tformat = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                } catch (ParseException ex1) {
                    throw new RuntimeException(ex1);
                }
            }
        } else {
            try {
                if (ft.equals("day")) {
                    tformat = new TimeDatumFormatter("%Y-%m-%d");
                } else if (ft.equals("hour")) {
                    tformat = new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("min")) {
                    tformat =  new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("sec")) {
                    tformat =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%SZ");
                } else if (ft.startsWith("millisec")) {
                    tformat =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}Z");
                } else if (ft.startsWith("microsec")) {
                    tformat =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}%{micro}Z");
                } else {
                    logger.log(Level.FINE, "not implemented: {0}", ft);
                    tformat = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                }

            } catch (ParseException ex) {
                ex.printStackTrace();
                tformat = TimeDatumFormatterFactory.getInstance().defaultFormatter();
                
            }
        }
        return tformat;

    }

    private DatumFormatter getDataFormatter( String df, Units u ) {
        try {
            if ( !df.contains("%") ) df= "%"+df;
            //TODO: would be nice if we could verify formatter.  I had %f5.2 instead of %5.2f and it wasn't telling me.
            return new FormatStringFormatter( df, false );
        } catch ( RuntimeException ex ) {
            ex.printStackTrace();
            return u.getDatumFormatterFactory().defaultFormatter();
        }
    }

    private void maybeOutputProperty(PrintWriter out, QDataSet data, String property) {
        Object v = data.property(property);
        if (v != null) {
            out.println("# " + property + ": " + v);
        }
    }

    /**
     * Insert the property at i the index, or -1 for non-indexed property.
     * @param jo1 object to collect properties
     * @param ds dataset which can be a bundle
     * @param prop the property name
     * @param i  -1 or the index of the bundled dataset.
     * @return
     * @throws JSONException 
     */
    private boolean jsonProp( JSONObject jo1, QDataSet ds, String prop, int i ) throws JSONException {
        Object o;
        if ( i>-1 ) {
            o= ds.property(prop,i);
        } else {
            o= ds.property(prop);
        }
        if ( prop.equals(QDataSet.START_INDEX) ) {
            prop= "START_COLUMN";
        }
        if ( o!=null ) {
            if ( o instanceof QDataSet ) {
                jo1.put( prop, o.toString() );
            } else if ( o instanceof Number ) {
                jo1.put( prop, (Number)o );
            } else {
                jo1.put( prop, String.valueOf(o) );
            }
            return true;
        } else {
            return false;
        }
    }

    private void formatBundleDesc(PrintWriter out, QDataSet bds, QDataSet bundleDesc ) throws JSONException {
        QDataSet dep0=null;
        if ( bds!=null ) {
            dep0= (QDataSet) bds.property(QDataSet.DEPEND_0);
        }

        JSONObject jo= new JSONObject();
        JSONObject jo1= new JSONObject();
        
        int startColumn= dep0==null ? 0 : 1;  // first column of the vector
        
        String name;
        if ( dep0!=null ) {
            name= (String) Ops.guessName(dep0);
            jsonProp( jo1, dep0, QDataSet.LABEL, -1 );
            if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0) ) ) {
                jo1.put("UNITS", "UTC" );
            } else {
                jsonProp( jo1, dep0, QDataSet.UNITS, -1 );
            }
            jo1.put( "START_COLUMN", 0 );
            jo.put( name, jo1 );
        }

        String[] elementNames= new String[bundleDesc.length()];
        String[] elementLabels= new String[bundleDesc.length()];
        if ( bundleDesc.length()==1 && bundleDesc.length(0)==1 ) {
            elementNames= new String[(int)bundleDesc.value(0,0)];
            for ( int i=0; i<bundleDesc.value(0,0); i++ ) {
                elementNames[i]= "ch_"+i;
            }
            elementLabels= null;
        } else {
            for ( int i=0; i<bundleDesc.length(); i++ ) {
                name= (String) bundleDesc.property( QDataSet.NAME,i );
                if ( name==null ) {
                    logger.info("unnamed dataset!");
                    name= "field"+i;
                }
                jo1= new JSONObject();
                jsonProp( jo1, bundleDesc, QDataSet.LABEL, i );
                if ( bds!=null && !jsonProp( jo1, bundleDesc, QDataSet.UNITS, i ) ) {
                    jsonProp( jo1, bds, QDataSet.UNITS, -1 );
                }
                jsonProp( jo1, bundleDesc, QDataSet.VALID_MIN, i );
                jsonProp( jo1, bundleDesc, QDataSet.VALID_MAX, i );
                jsonProp( jo1, bundleDesc, QDataSet.FILL_VALUE, i );
                jsonProp( jo1, bundleDesc, QDataSet.DEPEND_0, i );
                jsonProp( jo1, bundleDesc, QDataSet.START_INDEX, i );
                jo1.put("START_COLUMN", startColumn + i );
                //jo.put( name, jo1 ); // only output the bundle for now.
                elementNames[i]= name;
                elementLabels[i]= (String)bundleDesc.property(QDataSet.LABEL,i);
            }
        }

        for ( int i=0; elementLabels!=null && i<bundleDesc.length(); i++ ) {
            if ( elementLabels[i]==null ) elementLabels=null;
        }
        
        jo1= new JSONObject();
        jo1.put( "START_COLUMN", startColumn  );
        if ( bundleDesc.length()==1 ) { // bundle of 1 rank 2
            jo1.put( "DIMENSION", new int[] { (int)bundleDesc.value(0,0)} );            
        } else {                        // bundle of N rank 1
            jo1.put( "DIMENSION", new int[] { bundleDesc.length()} );
        }
        jo1.put( "ELEMENT_NAMES", elementNames );
        if ( elementLabels!=null ) {
            jo1.put( "ELEMENT_LABELS", elementLabels );
        }
        if ( bds!=null ) { // I don't think the bds is ever null, but findbugs points out inconsistent code.
            jsonProp( jo1, bds, QDataSet.VALID_MIN, -1 );
            jsonProp( jo1, bds, QDataSet.VALID_MAX, -1 );
            jsonProp( jo1, bds, QDataSet.FILL_VALUE, -1 );
            jo.put( Ops.guessName(bds), jo1 );
        }
        
        
        String json= jo.toString( 3 );

        String[] lines= json.split("\n");
        StringBuilder sb= new StringBuilder();

        for ( int i=0; i<lines.length; i++ ) {
            sb.append("# ").append(lines[i]).append( "\n");
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

        String head= getParam( "header", "" ); // could be "rich"

        boolean haveRich= false;
        if ( bundleDesc!=null && "rich".equals( head ) ) {
            try {
                formatBundleDesc( out, data, bundleDesc );
                haveRich= true;
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        DatumFormatter tf= getTimeFormatter( getParam( "timeformat", "ISO8601" ) );
        String df= getParam( "format", "" );

        DatumFormatter[] formats= new DatumFormatter[data.length(0)];
        Units[] uu= new Units[data.length(0)];

        if ( bundleDesc==null ) {
            throw new IllegalArgumentException("expected to find bundleDesc in dataset!");
        }

        int jj=0; // index into rank2 array
        for ( int i=0; i<bundleDesc.length(); i++ ) {
            jj= i;
            uu[jj] = (Units) bundleDesc.property(QDataSet.UNITS,i);
            if (uu[jj] == null) uu[jj] = Units.dimensionless;
            if ( !( uu[jj] instanceof EnumerationUnits ) ) {
                String ff= (String) bundleDesc.property(QDataSet.FORMAT,jj);
                if ( df.equals("") ) {
                    if ( ff==null ) {
                        formats[jj]= uu[jj].createDatum(data.value(0,jj)).getFormatter();
                    } else {
                        formats[jj]= getDataFormatter( ff, uu[jj] );
                    }
                } else {
                    if ( UnitsUtil.isTimeLocation( uu[jj] ) ) {
                        formats[jj]= tf;
                    } else {
                        if ( ff==null ) {
                            formats[jj]= getDataFormatter( df, uu[jj] );
                        } else {
                            formats[jj]= getDataFormatter( ff, uu[jj] ); //TODO: what is user wants to override format? 
                        }
                    }
                }
            } else {
                formats[jj]= uu[jj].createDatum(data.value(0,jj)).getFormatter();
            }
            //if ( formats[jj] instanceof EnumerationDatumFormatter ) {
                //((EnumerationDatumFormatter)formats[i]).setAddQuotes(true);
            //}
            jj++;
        }
        
        if (bundleDesc != null) {
            if (dep0 != null) {
                String l = (String) Ops.guessName(dep0);
                if ( l==null ) {
                    if ( Units.t2000.isConvertableTo( SemanticOps.getUnits(dep0) ) ) {
                        l= "time(UTC)";
                    } else {
                        l= "dep0";
                    }
                } else {
                    if ( Units.t2000.isConvertableTo( SemanticOps.getUnits(dep0) ) ) {
                        l= l+" (UTC)";
                    }
                }
                out.print(" " + l + ", ");
            }

            int i;
            for (  i = 0; i < bundleDesc.length(); i++) {
                String l1=null;
                if ( haveRich ) {
                    l1= (String) bundleDesc.property(QDataSet.NAME,i);
                } else {
                    l1= (String) bundleDesc.property(QDataSet.LABEL,i);
                }
                if ( l1==null ) {
                    l1= (String) bundleDesc.property(QDataSet.NAME,i);
                    if ( l1==null ) {
                        throw new IllegalArgumentException("unnamed dataset in bundle at index "+i);
                    }
                }
                if ( l1.trim().length()==0 ) {
                    Units u1=  (Units) bundleDesc.property(QDataSet.UNITS,i);
                    if ( u1!=null && Units.t2000.isConvertableTo( u1 ) ) {
                        l1= "time(UTC)";
                    } else {
                        l1= "field"+i;
                    }
                }
                if ( uu[i]!=null && uu[i]!=Units.dimensionless ) {
                    if ( uu[i] instanceof EnumerationUnits ) {
                        
                    } else {
                        l1+="("+uu[i]+")";
                    }
                }
                int nelements= 1;
                for ( int k=0; k<bundleDesc.length(i); k++ ) {
                    nelements*= bundleDesc.value(i,k);
                }
                for ( int k=0; k<nelements; k++ ) {
                    out.print( l1  );
                    if ( i==bundleDesc.length()-1 && k==nelements-1 ) {
                        out.print( "\n" );
                    } else {
                        out.print( ", " );
                    }
                }
            }
        }

        DatumFormatter cf0= tf;
        Units u0 = null;
        if (dep0 != null) {
            u0 = (Units) dep0.property(QDataSet.UNITS);
            if (u0 == null) u0 = Units.dimensionless;
            if ( !UnitsUtil.isTimeLocation(u0) && dep0.length()>0 ) {
                if ( df.equals("") ) {
                    cf0= u0.createDatum( dep0.value(0)).getFormatter();
                } else {
                    cf0= getDataFormatter( df, uu[jj] );
                }
            }
        }

        mon.setTaskSize(data.length());
        mon.started();

        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            if (dep0 != null) {
                out.print("" + cf0.format( u0.createDatum(dep0.value(i)) ) + ", ");
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
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        boolean okay= DataSetUtil.checkQube(data); // some RBSP/ECT data has rank 2 DEPEND_1 when it is really a qube.
        
        if ( !okay ) {
            throw new IllegalArgumentException("Data is not a qube.  Each record must have the same DEPEND_1.");
        }
        
        if ( dep1!=null && dep1.rank()==2 ) {
            dep1= dep1.slice(0);
        }
        
        DatumFormatter format=null;

        String head= getParam( "header", "" ); // could be "rich"
        if ( "rich".equals( head ) ) {
            try {
                BundleDataSet bds= BundleDataSet.createRank1Bundle();  //TODO: this is just so it does something.  Fill this out.
                DDataSet ds= DDataSet.createRank1(1);
                ds.putProperty( QDataSet.TITLE, 0, data.property(QDataSet.TITLE) );
                ds.putProperty( QDataSet.LABEL, 0, data.property(QDataSet.LABEL) );
                ds.putProperty( QDataSet.NAME, 0, data.property(QDataSet.NAME) );
                ds.putProperty( QDataSet.UNITS, 0, data.property(QDataSet.UNITS) );
                ds.putProperty( QDataSet.VALID_MAX, 0, data.property(QDataSet.VALID_MAX) );
                ds.putProperty( QDataSet.VALID_MIN, 0, data.property(QDataSet.VALID_MIN) );
                ds.putProperty( QDataSet.FILL_VALUE, 0, data.property(QDataSet.FILL_VALUE) );
                ds.putValue( 0, data.length(0) );
                bds.bundle( ds );
                formatBundleDesc( out, data, bds );
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        Units u = (Units) data.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;

        if ( u!=Units.dimensionless && !"rich".equals( head )  ) maybeOutputProperty( out, data, QDataSet.UNITS );
        
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
            
            if ( dep1.rank()>1 ) {
                
            }
            
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

        String ft= getParam( "tformat", "ISO8601" );
        DatumFormatter tf= ft.equals("") ? Units.us2000.getDatumFormatterFactory().defaultFormatter() : getTimeFormatter(ft);

        String dfs= getParam( "format", "" );
        DatumFormatter df= dfs.equals("") ? u.getDatumFormatterFactory().defaultFormatter() : getDataFormatter( dfs, u );

        DatumFormatter cf0= dep0==null ? null : ( UnitsUtil.isTimeLocation(u0) ? tf : df );
        DatumFormatter cf1= UnitsUtil.isTimeLocation(u) ? tf : df;

        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            if (dep0 != null) {
                out.print("" + cf0.format( u0.createDatum(dep0.value(i)),u0 ) + ", ");
            }

            int j;
            for ( j = 0; j < data.length(i) - 1; j++) {
                out.print( cf1.format( u.createDatum(data.value(i,j)), u ) + ", ");
            }
            out.println( cf1.format( u.createDatum(data.value(i,j)), u )  );
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

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        Units u0=null;
        Units u;

        List<QDataSet> planes= new ArrayList<QDataSet>();
        List<Units> planeUnits= new ArrayList<Units>();
//        List<DatumFormatter> planeFormats= new ArrayList<DatumFormatter>();

        String head= getParam( "header", "" ); // could be "rich"
        if ( "rich".equals( head ) ) {
            try {
                BundleDataSet bds= BundleDataSet.createRank1Bundle();  //TODO: this is just so it does something.  Fill this out.
                DDataSet ds= DDataSet.createRank1(1);
                ds.putProperty( QDataSet.TITLE, data.property(QDataSet.TITLE) );
                ds.putProperty( QDataSet.LABEL, data.property(QDataSet.LABEL) );
                ds.putProperty( QDataSet.NAME, data.property(QDataSet.NAME) );
                ds.putProperty( QDataSet.UNITS, data.property(QDataSet.UNITS) );
                ds.putProperty( QDataSet.VALID_MAX, data.property(QDataSet.VALID_MAX) );
                ds.putProperty( QDataSet.VALID_MIN, data.property(QDataSet.VALID_MIN) );
                ds.putProperty( QDataSet.FILL_VALUE, data.property(QDataSet.FILL_VALUE) );
                formatBundleDesc( out,data,bds );
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        StringBuilder buf= new StringBuilder();

        String l;
        if (dep0 != null) {
            l = dataSetLabel( dep0, "dep0" );
            buf.append(", ").append( l);
            u0= (Units) dep0.property(QDataSet.UNITS);
            if ( u0==null ) u0= Units.dimensionless;
        }

        l = dataSetLabel( data, "data" );
        buf.append(", ").append( l);
        u= (Units) data.property(QDataSet.UNITS);
        if ( u==null ) u= Units.dimensionless;

        if (  !"rich".equals( head ) ) {
            maybeOutputProperty(out, data, QDataSet.TITLE);
            if ( u!=Units.dimensionless ) maybeOutputProperty( out, data, QDataSet.UNITS );
        }
        
        for ( int i=0; i<QDataSet.MAX_PLANE_COUNT; i++ ) {
            QDataSet plane= (QDataSet) data.property( "PLANE_"+i );
            if ( plane!=null ) {
                planes.add(plane);
                planeUnits.add((Units)plane.property(QDataSet.UNITS));
                if ( planeUnits.get(i)==null ) planeUnits.add(i, Units.dimensionless );
                l= dataSetLabel( plane, "data"+i );
                buf.append(", ").append( l);
            }
        }

        out.println( buf.substring(2) );
        
        mon.setTaskSize(data.length());
        mon.started();

//        if ( data.length()>0 ) {
//            planeFormats= new ArrayList<DatumFormatter>(planes.size());
//            for ( int i=0; i<planes.size(); i++ ) {
//                planeFormats.add(i, planeUnits.get(i).createDatum(planes.get(i).value(i)).getFormatter() );
//            }
//        }

        String ft= getParam( "tformat", "ISO8601" );
        DatumFormatter tf= ft.equals("") ? Units.us2000.getDatumFormatterFactory().defaultFormatter() : getTimeFormatter(ft);

        String dfs= getParam( "format", "" );
        DatumFormatter df= dfs.equals("") ? u.getDatumFormatterFactory().defaultFormatter() : getDataFormatter( dfs, u );

        DatumFormatter cf0= dep0==null ? null : ( UnitsUtil.isTimeLocation(u0) ? tf : df );
        DatumFormatter cf1= UnitsUtil.isTimeLocation(u) ? tf : df;
        for (int i = 0; i < data.length(); i++ ) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;

            if (dep0 != null) {
                out.print("" + cf0.format( u0.createDatum(dep0.value(i)),u0 ) + ", ");
            }

            out.print( cf1.format(u.createDatum(data.value(i)), u) );

            for ( int j=0; j<planes.size(); j++) {
                out.print( ", " + cf1.format( planeUnits.get(j).createDatum(planes.get(j).value(i)), planeUnits.get(j) ) );
            }
            out.println();
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

        setUri(uri);

        File f= new File( getResourceURI() );
//        if ( !f.createNewFile() ) {
//            if ( f.exists() ) {
//                throw new IOException( "Unable to write to existing file: "+f );
//            } else {
//                throw new IOException( "Unable to write file: "+f );
//            }
//        }
        PrintWriter out = new PrintWriter( f ); //TODO: it would be nice to support a preview, this assumes file.

        String head= getParam( "header", "" ); // could be "rich"
        if ( !"rich".equals( head ) ) {
            out.println("# Generated by Autoplot on " + new Date());
        }

        if (data.rank() == 2) {
            if ( SemanticOps.isBundle(data) ) {
                formatBundle( out, data, mon ); // data should have property BUNDLE_1 because of isBundle==true
            } else {
                formatRank2(out, data, mon);
            }
        } else if (data.rank() == 1) {
            formatRank1(out, data, mon);
        } else {
            throw new IllegalArgumentException("only rank 1 and rank 2 data are supported");
        }
        out.close();
    }

   public boolean canFormat(QDataSet ds) {
        return ( ds.rank()>0 && ds.rank()<3 );
    }

    public String getDescription() {
        return "ASCII Table";
    }

}
