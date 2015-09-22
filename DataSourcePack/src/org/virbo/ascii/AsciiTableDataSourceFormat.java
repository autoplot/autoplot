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
import org.das2.datum.Datum;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.DefaultDatumFormatter;
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
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dsops.Ops;

/**
 * Format the QDataSet into Ascii tables.  
 * <ul>
 * <li>header=rich include "rich ascii" metadata.
 * <li>header=none don't include any headers.
 * <li>tformat=iso8601 use ISO8601 times (like 2015-01-01T00:00Z)
 * <li>tformat=hours+since+2015-01-01T00:00 use offsets. (timeformat and tformat are aliases)
 * <li>tformat=day
 * <li>format=%f5.2 use this formatter for data.
 * </ul>
 * @author jbf
 */
public class AsciiTableDataSourceFormat extends AbstractDataSourceFormat {

    private static final Logger logger= Logger.getLogger("apdss.ascii");

    private DatumFormatter getTimeFormatter( ) {
        DatumFormatter timeFormatter;
        String tformat= getParam( "tformat", "ISO8601" );
        String ft= tformat.toLowerCase();
        String depend0Units= getParam( "depend0Units", "" );
        Units dep0units= null;
        
        if ( depend0Units.length()>0 ) {
            try {
                dep0units= Units.lookupTimeUnits(depend0Units);
            } catch (ParseException ex) {
                Logger.getLogger(AsciiTableDataSourceFormat.class.getName()).log(Level.SEVERE, null, ex);
            }

            final Units tu= dep0units;
            if ( ft.equals("iso8601") ) ft=null;
            final String sformat= ft;
            timeFormatter= new DefaultDatumFormatter() {
                @Override
                public String format(Datum datum) {
                    return format(datum, tu);
                }
                @Override
                public String format(Datum datum, Units units) {
                    if ( datum.isFill() ) {
                        return "fill";
                    } else {
                        if ( sformat!=null && sformat.startsWith("%") ) {
                            return String.format( sformat, datum.doubleValue(tu) );
                        } else {
                            return String.valueOf( datum.doubleValue(tu) );
                        }
                    }
                }
            };
        } else if (ft.equals("iso8601")) {
            timeFormatter = TimeDatumFormatterFactory.getInstance().defaultFormatter();
            
        } else if ( tformat.startsWith("%")
                || ft.startsWith("$") ) {
            if ( tformat.startsWith("$") ) { // provide convenient URI-friendly spec
                tformat= tformat.replaceAll("\\$", "%");
            }
            try {
                timeFormatter = new TimeDatumFormatter(tformat);
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                try {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                } catch (ParseException ex1) {
                    throw new RuntimeException(ex1);
                }
            }
        } else {
            try {
                if (ft.equals("day")) {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%d");
                } else if (ft.equals("hour")) {
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("min")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%MZ");
                } else if (ft.startsWith("sec")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%SZ");
                } else if (ft.startsWith("millisec")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}Z");
                } else if (ft.startsWith("microsec")) {
                    timeFormatter =  new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S.%{milli}%{micro}Z");
                } else {
                    logger.log(Level.FINE, "not implemented: {0}", ft);
                    timeFormatter = new TimeDatumFormatter("%Y-%m-%dT%H:%M:%S");
                }

            } catch (ParseException ex) {
                ex.printStackTrace();
                timeFormatter = TimeDatumFormatterFactory.getInstance().defaultFormatter();
                
            }
        }
        return timeFormatter;

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

    /**
     * output the dataset property if it exists.  The form will be
     * name: value.  If header=none, simply return.
     * @param out the output stream that is writing the file.
     * @param data the dataset.
     * @param property the property name.
     */
    private void maybeOutputProperty(PrintWriter out, QDataSet data, String property) {
        if ( getParam("header","").equals("none") ) return;
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
            } else if ( o instanceof Units ) {
                if ( UnitsUtil.isTimeLocation((Units)o) ) {
                    jo1.put( prop, "UTC" );
                } else {
                    jo1.put( prop, String.valueOf(o) );
                }
            } else {
                jo1.put( prop, String.valueOf(o) );
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * format the depend dataset within the rich header.
     * @param ds
     * @return
     * @throws JSONException 
     */
    private JSONObject formatDataSetInline( QDataSet ds ) throws JSONException {
        JSONObject jo1= new JSONObject();
        jsonProp( jo1, ds, QDataSet.LABEL, -1 );
        jsonProp( jo1, ds, QDataSet.UNITS, -1 );
        jsonProp( jo1, ds, QDataSet.VALID_MIN, -1 );
        jsonProp( jo1, ds, QDataSet.VALID_MAX, -1 );
        jsonProp( jo1, ds, QDataSet.FILL_VALUE, -1 );
        jo1.put( "VALUES", DataSetUtil.asArrayOfDoubles(ds) );
        jo1.put( "DIMENSION", new int[] { ds.length() } );
        return jo1;
    }
    
    /**
     * format the data, using column descriptions in bundleDesc.  Note
     * that when data is Data[Dep0], that bundleDesc will have two columns.
     * @param out
     * @param data
     * @param bundleDesc
     * @throws JSONException 
     */
    private void formatBundleDescRichAscii(PrintWriter out, QDataSet data, QDataSet bundleDesc ) throws JSONException {
        
        assert data!=null;
        assert bundleDesc.length()==data.length(1);
        
        QDataSet dep0;
        dep0= (QDataSet) data.property(QDataSet.DEPEND_0);

        JSONObject jo= new JSONObject();
        JSONObject jo1= new JSONObject();
        
        int startColumn= dep0==null ? 0 : 1;  // first column of the vector
        int dep0inc; // 0 or 1, the amount to inc because of dep0 column.

        String name;
        String dep1Name= null;
        
        QDataSet dep= (QDataSet) data.property(QDataSet.DEPEND_1);
        if ( dep!=null && UnitsUtil.isRatioMeasurement( SemanticOps.getUnits(dep) ) && data.property(QDataSet.BUNDLE_1)==null ) {
            dep1Name= (String) Ops.guessName(dep);
            jo.put( dep1Name, formatDataSetInline( dep ) );
        }
        
        if ( dep0!=null ) {
            name= (String) Ops.guessName(dep0);
            jsonProp( jo1, dep0, QDataSet.LABEL, -1 );
            if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0) ) ) {
                jo1.put("UNITS", getTimeUnitLabel() );
            } else {
                jsonProp( jo1, dep0, QDataSet.UNITS, -1 );
            }
            jo1.put( "START_COLUMN", 0 );
            if ( data.rank()>1 ) {
                jo.put( name, jo1 );
                dep0inc=1;
            } else {
                dep0inc=0;
            }
        } else {
            dep0inc=0;
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
                if ( !jsonProp( jo1, bundleDesc, QDataSet.UNITS, i ) ) {
                    jsonProp( jo1, data, QDataSet.UNITS, -1 );
                }
                jsonProp( jo1, bundleDesc, QDataSet.VALID_MIN, i );
                jsonProp( jo1, bundleDesc, QDataSet.VALID_MAX, i );
                jsonProp( jo1, bundleDesc, QDataSet.FILL_VALUE, i );
                jsonProp( jo1, bundleDesc, QDataSet.DEPEND_0, i );
                jsonProp( jo1, bundleDesc, QDataSet.START_INDEX, i );
                jo1.put("START_COLUMN", i+dep0inc );
                if ( data.rank()==1 ) {
                    jo.put( name, jo1 ); // only output the bundle for now.
                }
                elementNames[i]= name;
                elementLabels[i]= (String)bundleDesc.property(QDataSet.LABEL,i);
            }
        }

        for ( int i=dep0inc; elementLabels!=null && i<bundleDesc.length(); i++ ) {
            if ( elementLabels[i]==null ) elementLabels=null;
        }
        
        jo1= new JSONObject();
        jo1.put( "START_COLUMN", startColumn  );
        if ( data.rank()>1 ) {
            if ( bundleDesc.length()==1 ) { // bundle of 1 rank 2
                jo1.put( "DIMENSION", new int[] { (int)bundleDesc.value(0,0)} );            
            } else {                        // bundle of N rank 1
                jo1.put( "DIMENSION", new int[] { bundleDesc.length()} );
            }
            jo1.put( "ELEMENT_NAMES", elementNames );
            if ( elementLabels!=null ) {
                jo1.put( "ELEMENT_LABELS", elementLabels );
            }
            if ( dep1Name!=null ) {
                jo1.put("DEPEND_1", dep1Name );
                jo1.put("RENDER_TYPE", "spectrogram" );
            }
            jo.put( Ops.guessName(data,"data"), jo1 );    
        }
        
        String json= jo.toString( 3 );

        String[] lines= json.split("\n");
        StringBuilder sb= new StringBuilder();

        for ( String line : lines ) {
            sb.append("# ").append(line).append("\n");
        }

        out.print( sb.toString() );

    }

    private String getTimeUnitLabel( ) {
        String depend0Units= getParam("depend0Units","");
        if ( depend0Units.equals("") ) {
            return "UTC";
        } else {
            try {
                Units u= Units.lookupTimeUnits(depend0Units);
                return u.toString();
            } catch (ParseException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }
    
    /**
     * format the rank 2 bundle of data.
     * @param out
     * @param data
     * @param mon
     */
    private void formatRank2Bundle(PrintWriter out, QDataSet data, ProgressMonitor mon) {

        QDataSet bundleDesc= (QDataSet) data.property(QDataSet.BUNDLE_1);
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        String head= getParam( "header", "" ); // could be "rich"

        boolean haveRich= false;
        if ( bundleDesc!=null && "rich".equals( head ) ) {
            try {
                formatBundleDescRichAscii( out, data, bundleDesc );
                haveRich= true;
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        DatumFormatter tf= getTimeFormatter( );
        
        
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
        
        if (dep0 != null) {
            String l = (String) Ops.guessName(dep0);
            if ( l==null ) {
                if ( Units.t2000.isConvertibleTo( SemanticOps.getUnits(dep0) ) ) {
                    l= "time("+getTimeUnitLabel()+")";
                } else {
                    l= "dep0";
                }
            } else {
                if ( Units.t2000.isConvertibleTo( SemanticOps.getUnits(dep0) ) ) {
                    l= l+" ("+getTimeUnitLabel()+")";
                }
            }
            if ( !"none".equals(head) ) out.print(" " + l + ", ");
        }

        int i;
        boolean startStopTime= false;
        for (  i = 0; i < bundleDesc.length(); i++) {
            String l1;
            if ( haveRich ) {
                l1= (String) bundleDesc.property(QDataSet.NAME,i);
            } else {
                l1= (String) bundleDesc.property(QDataSet.LABEL,i);
            }
            if ( l1==null ) {
                l1= (String) bundleDesc.property(QDataSet.NAME,i);
                if ( l1==null ) {
                    l1="";
                }
            }
            if ( l1.trim().length()==0 ) {
                Units u1=  (Units) bundleDesc.property(QDataSet.UNITS,i);
                if ( i==0 && UnitsUtil.isTimeLocation(u1) && bundleDesc.length()>1 ) {
                    Units u2= (Units) bundleDesc.property(QDataSet.UNITS,1);
                    if ( UnitsUtil.isTimeLocation(u2)) {
                        startStopTime= true;
                    }
                }
                if ( u1!=null && Units.t2000.isConvertibleTo( u1 ) ) {
                    if ( startStopTime ) {
                        l1= "time"+i;                            
                    } else {
                        l1= "time";
                    }
                } else {
                    l1= "field"+i;
                }
            }
            if ( uu[i]!=null && uu[i]!=Units.dimensionless ) {
                if ( uu[i] instanceof EnumerationUnits ) {
                } else if ( UnitsUtil.isTimeLocation(uu[i] ) ) {
                    l1+= "("+getTimeUnitLabel()+")";
                } else {
                    l1+="("+uu[i]+")";
                }
            }
            int nelements= 1;
            for ( int k=0; k<bundleDesc.length(i); k++ ) {
                nelements*= bundleDesc.value(i,k);
            }
            if ( !"none".equals(head) ) {
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

        for ( i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            if (dep0 != null) {
                assert u0!=null;
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
            throw new IllegalArgumentException("dep1 rank is 2, which is not supported.");
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
                formatBundleDescRichAscii( out, data, bds );
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        } else {
            maybeOutputProperty(out, data, QDataSet.TITLE);
        }

        Units u = (Units) data.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;

        if ( u!=Units.dimensionless && !"rich".equals( head )  ) maybeOutputProperty( out, data, QDataSet.UNITS );
        
        if (dep1 != null && !"none".equals(head) ) {
            out.print("#");
            if (dep0 != null) {
                String l = (String) dep0.property(QDataSet.LABEL);
                if ( l==null ) {
                    if ( Units.t2000.isConvertibleTo( SemanticOps.getUnits(dep0) ) ) {
                        l= "time("+getTimeUnitLabel()+")";
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

        DatumFormatter tf= getTimeFormatter();

        String dfs= getParam( "format", "" );
        DatumFormatter df= dfs.equals("") ? u.getDatumFormatterFactory().defaultFormatter() : getDataFormatter( dfs, u );

        DatumFormatter cf0= dep0==null ? null : ( UnitsUtil.isTimeLocation(u0) ? tf : df );
        DatumFormatter cf1= UnitsUtil.isTimeLocation(u) ? tf : df;

        for (int i = 0; i < data.length(); i++) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;
            if (dep0 != null) {
                assert dep0!=null;
                assert cf0!=null;
                assert u0!=null;
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

    /**
     * use the label if it's compact
     * @param ds
     * @param deft
     * @return 
     */
    private String dataSetLabel( QDataSet ds, String deft ) {
        String head= getParam( "header", "" ); // could be "rich"
        String name;
        if ( "rich".equals(head) ) {
            name = (String) ds.property(QDataSet.NAME);
        } else {
            name = (String) ds.property(QDataSet.LABEL);
            if ( name!=null && name.contains("(") ) {
                int i= name.indexOf("("); // Y (Hz)
                name= name.substring(0,i).trim();
            }
        }
        if ( name==null || !Ops.safeName(name).equals(name) ) {
            name = (String) ds.property(QDataSet.NAME);
        }
        if ( name==null || name.equals("") ) name= deft;
        String label= name;
        Units units= (Units)ds.property(QDataSet.UNITS);
        if ( units!=null && units!=Units.dimensionless ) {
            if ( UnitsUtil.isTimeLocation(units) ) {
                label= label + "("+getTimeUnitLabel()+")";
            } else {
                label= label+"("+units+")";
            }
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
                DDataSet ids= DDataSet.createRank1(1);
                ids.putProperty( QDataSet.TITLE, dep0.property(QDataSet.TITLE) );
                ids.putProperty( QDataSet.LABEL, dep0.property(QDataSet.LABEL) );
                ids.putProperty( QDataSet.NAME, dep0.property(QDataSet.NAME) );
                ids.putProperty( QDataSet.UNITS, dep0.property(QDataSet.UNITS) );
                ids.putProperty( QDataSet.VALID_MAX, dep0.property(QDataSet.VALID_MAX) );
                ids.putProperty( QDataSet.VALID_MIN, dep0.property(QDataSet.VALID_MIN) );
                ids.putProperty( QDataSet.FILL_VALUE, dep0.property(QDataSet.FILL_VALUE) );
                DDataSet ds= DDataSet.createRank1(1);
                ds.putProperty( QDataSet.TITLE, data.property(QDataSet.TITLE) );
                ds.putProperty( QDataSet.LABEL, data.property(QDataSet.LABEL) );
                String name=  (String)data.property(QDataSet.NAME);
                if ( name==null ) name= "data";
                ds.putProperty( QDataSet.NAME, name );
                ds.putProperty( QDataSet.UNITS, data.property(QDataSet.UNITS) );
                ds.putProperty( QDataSet.VALID_MAX, data.property(QDataSet.VALID_MAX) );
                ds.putProperty( QDataSet.VALID_MIN, data.property(QDataSet.VALID_MIN) );
                ds.putProperty( QDataSet.FILL_VALUE, data.property(QDataSet.FILL_VALUE) );
                QDataSet bds= Ops.join( ids, ds );
                formatBundleDescRichAscii( out,data,bds );
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
            } else {
                break;
            }
        }

        if ( !"none".equals(head) ) out.println( buf.substring(2) );
        
        mon.setTaskSize(data.length());
        mon.started();

//        if ( data.length()>0 ) {
//            planeFormats= new ArrayList<DatumFormatter>(planes.size());
//            for ( int i=0; i<planes.size(); i++ ) {
//                planeFormats.add(i, planeUnits.get(i).createDatum(planes.get(i).value(i)).getFormatter() );
//            }
//        }

        DatumFormatter tf= getTimeFormatter();

        Units dep0units= u0; // target output units.
        String depend0Units= getParam( "depend0Units", "" );
        if ( depend0Units.length()>0 ) {
            tf= Units.dimensionless.getDatumFormatterFactory().defaultFormatter();
            try {
                dep0units= Units.lookupTimeUnits(depend0Units);
            } catch (ParseException ex) {
                throw new IllegalArgumentException("unable to parse depend0Units");
            }
        }
        
        String format= getParam( "format", "" );
        DatumFormatter df= format.equals("") ? u.getDatumFormatterFactory().defaultFormatter() : getDataFormatter(format, u );

        DatumFormatter cf0= dep0==null ? null : ( UnitsUtil.isTimeLocation(u0) ? tf : df );
        DatumFormatter cf1= UnitsUtil.isTimeLocation(u) ? tf : df;
        for (int i = 0; i < data.length(); i++ ) {
            mon.setTaskProgress(i);
            if ( mon.isCancelled() ) break;

            if (dep0 != null) {
                assert cf0!=null;
                assert u0!=null;
                out.print("" + cf0.format( u0.createDatum(dep0.value(i)),dep0units ) + ", ");
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
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {

        setUri(uri);
        
        String doDep= getParam("doDep", "");
        if ( doDep.length()>0 && doDep.toUpperCase().charAt(0)=='F' ) {
            MutablePropertyDataSet mpds= DataSetOps.makePropertiesMutable(data);
            mpds.putProperty( QDataSet.DEPEND_0, null );
            mpds.putProperty( QDataSet.DEPEND_1, null );
            mpds.putProperty( QDataSet.BUNDLE_1, null );
            data= mpds;
        }

        File f= new File( getResourceURI() );
//        if ( !f.createNewFile() ) {
//            if ( f.exists() ) {
//                throw new IOException( "Unable to write to existing file: "+f );
//            } else {
//                throw new IOException( "Unable to write file: "+f );
//            }
//        }
        PrintWriter out = new PrintWriter( f ); //TODO: it would be nice to support a preview, this assumes file.

        String head= getParam( "header", "" ); // could be "rich" or "none"
        if ( !"rich".equals( head ) && !"none".equals(head)) {
            out.println("# Generated by Autoplot on " + new Date());
        }

        if (data.rank() == 2) {
            if ( SemanticOps.isBundle(data) ) {
                formatRank2Bundle( out, data, mon ); // data should have property BUNDLE_1 because of isBundle==true
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
