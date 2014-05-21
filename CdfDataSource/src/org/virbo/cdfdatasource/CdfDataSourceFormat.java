/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFConstants;
import static gsfc.nssdc.cdf.CDFConstants.*;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.DataSourceFormat;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;

/**
 * Format the QDataSet into CDF tables.
 *
 * Tested:
 *   Works for file:///media/mini/data.backup/examples/cdf/po_h0_tim_19960317_v03.cdf?Flux_H
 * @author jbf
 */
public class CdfDataSourceFormat implements DataSourceFormat {

    CDF cdf;
    Attribute depend_0, depend_1, depend_2;
    Attribute unitsAttr, lablAxisAttr, catdescAttr, validmaxAttr, validminAttr, fillvalAttr, scalemaxAttr, scaleminAttr, lablAttr;
    Attribute formatAttr, displayTypeAttr;

    Map<QDataSet,String> names;
    Map<String,QDataSet> seman;

    private static final Logger logger= LoggerManager.getLogger("apdss.cdf");
    
    public CdfDataSourceFormat() {
        names= new HashMap<QDataSet,String>();
        seman= new HashMap<String,QDataSet>();
    }

    private synchronized String nameFor(QDataSet dep0) {
        String name= names.get(dep0);
        
        if ( name==null ) {
            name = (String) dep0.property(QDataSet.NAME);
            if ( name!=null ) {
                String s= Ops.safeName(name);
                if ( !s.equals(name) ) {
                    logger.log(Level.WARNING, "making invalid name valid: {0}", name);
                    name= s;
                }
            }
        }
        
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if (name == null) {
            if ( units!=null && UnitsUtil.isTimeLocation(units)) {
                name = "Epoch";
            } else {
                name = "Variable_" + names.size();
            }
        }
        
        if (seman.containsKey(name) && !names.containsKey(dep0) ) {
            String oname= name;
            int i=1;
            name= oname+"_"+i;
            while ( seman.containsKey(name) ) {
                i=i+1;
                name= oname+"_"+i;
            }
        }
        
        names.put(dep0,name);
        seman.put(name,dep0);
        
        return name;
    }

    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException, CDFException {

        URISplit split= URISplit.parse( uri );
        java.util.Map<String, String> params= URISplit.parseParams( split.params );

        File file= new File( split.resourceUri );
        boolean append= "T".equals( params.get("append") ) ;
        
        if ( ! append ) {
            if ( file.exists() && !file.delete() ) {
                throw new IllegalArgumentException("Unable to delete file"+file);
            }
            logger.log(Level.FINE, "create CDF file {0}", file);
            cdf = CDF.create( file.toString() );
            unitsAttr= Attribute.create( cdf, "UNITS", VARIABLE_SCOPE );
            lablAxisAttr= Attribute.create( cdf, "LABLAXIS", VARIABLE_SCOPE );
            catdescAttr= Attribute.create( cdf, "CATDESC", VARIABLE_SCOPE );
            displayTypeAttr=  Attribute.create( cdf, "DISPLAY_TYPE", VARIABLE_SCOPE );
            validmaxAttr= Attribute.create( cdf, "VALIDMAX", VARIABLE_SCOPE );
            validminAttr= Attribute.create( cdf, "VALIDMIN", VARIABLE_SCOPE );
            fillvalAttr= Attribute.create( cdf, "FILLVAL", VARIABLE_SCOPE );
            scalemaxAttr= Attribute.create( cdf, "SCALEMAX", VARIABLE_SCOPE );
            scaleminAttr= Attribute.create( cdf, "SCALEMIN", VARIABLE_SCOPE );
            formatAttr=  Attribute.create( cdf, "FORMAT", VARIABLE_SCOPE );
            lablAttr= Attribute.create(cdf,"LABL_PTR_1", VARIABLE_SCOPE );

        } else {
            if ( file.exists() ) {
                logger.log(Level.FINE, "open CDF file {0}", file);
                cdf= CDF.open(file.toString());
                unitsAttr= cdf.getAttribute( "UNITS" );
                lablAxisAttr= cdf.getAttribute( "LABLAXIS" );
                catdescAttr= cdf.getAttribute( "CATDESC" );
                displayTypeAttr=  cdf.getAttribute( "DISPLAY_TYPE" );
                validmaxAttr= cdf.getAttribute( "VALIDMAX" );
                validminAttr= cdf.getAttribute( "VALIDMIN" );
                fillvalAttr= cdf.getAttribute( "FILLVAL" );
                scalemaxAttr= cdf.getAttribute( "SCALEMAX" );
                scaleminAttr= cdf.getAttribute( "SCALEMIN" );
                formatAttr=  cdf.getAttribute( "FORMAT" );
                lablAttr=  cdf.getAttribute( "LABL_PTR_1" );
            } else {
                cdf = CDF.create( file.toString() );
                unitsAttr= Attribute.create( cdf, "UNITS", VARIABLE_SCOPE );
                lablAxisAttr= Attribute.create( cdf, "LABLAXIS", VARIABLE_SCOPE );
                catdescAttr= Attribute.create( cdf, "CATDESC", VARIABLE_SCOPE );
                displayTypeAttr=  Attribute.create( cdf, "DISPLAY_TYPE", VARIABLE_SCOPE );
                validmaxAttr= Attribute.create( cdf, "VALIDMAX", VARIABLE_SCOPE );
                validminAttr= Attribute.create( cdf, "VALIDMIN", VARIABLE_SCOPE );
                fillvalAttr= Attribute.create( cdf, "FILLVAL", VARIABLE_SCOPE );
                scalemaxAttr= Attribute.create( cdf, "SCALEMAX", VARIABLE_SCOPE );
                scaleminAttr= Attribute.create( cdf, "SCALEMIN", VARIABLE_SCOPE );
                formatAttr=  Attribute.create( cdf, "FORMAT", VARIABLE_SCOPE );
                lablAttr= Attribute.create(cdf,"LABL_PTR_1", VARIABLE_SCOPE );

            }
        }
        
        String name1= params.get( "arg_0" );

        if ( name1!=null ) {
            names.put(data,name1);
            seman.put(name1,data);
        }
        
        nameFor(data); // allocate a good name

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        if ( dep0 != null ) {
            if ( !append ) {
                String name= nameFor(dep0);
                Map<String,String> params1= new HashMap<String,String>();
                params1.put( "timeType",params.get("timeType") );
                addVariableRankN( dep0, name, params1, mon );
            }
            try {
                depend_0 = Attribute.create(cdf, "DEPEND_0", VARIABLE_SCOPE);
            } catch ( CDFException ex ) {
                depend_0 = cdf.getAttribute("DEPEND_0");
            }
        }
        
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);

        if (dep1 != null) {
            if ( !append ) {
                String name= nameFor(dep1);
                if ( data.rank()==1 ) {
                    addVariableRank1NoVary(dep1, name, new HashMap<String,String>(), new NullProgressMonitor() );
                } else {
                    addVariableRankN(data, name, new HashMap<String,String>(), mon );
                }
            }
            try {
                depend_1 = Attribute.create(cdf, "DEPEND_1", VARIABLE_SCOPE);
            } catch ( CDFException ex ) {
                depend_1 = cdf.getAttribute("DEPEND_1");
            }
        }

        QDataSet dep2 = (QDataSet) data.property(QDataSet.DEPEND_2);

        if (dep2 != null) {
            if ( !append ) {
                String name= nameFor(dep2);
                addVariableRank1NoVary(dep2, name, new HashMap<String,String>(), new NullProgressMonitor() );
            }
            try {
                depend_2 = Attribute.create(cdf, "DEPEND_2", VARIABLE_SCOPE);
            } catch ( CDFException ex ) {
                depend_2 = cdf.getAttribute("DEPEND_2");
            }
        }

        Variable var= addVariableRankN(data, nameFor(data), params, mon );
        
        try {
            if ( dep0!=null ) Entry.create( depend_0, var.getID(), CDF_CHAR, nameFor(dep0) );
            if ( dep1!=null ) Entry.create( depend_1, var.getID(), CDF_CHAR, nameFor(dep1) );
            if ( dep2!=null ) Entry.create( depend_2, var.getID(), CDF_CHAR, nameFor(dep2) );
        } catch ( CDFException ex ) {
            logger.log(Level.SEVERE,ex.getMessage(),ex);
        }

        cdf.close();

    }

    private Variable addVariableRank1NoVary( QDataSet ds, String name, Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon ) throws CDFException {
        Units units = (Units) ds.property(QDataSet.UNITS);
        long type = CDF_DOUBLE;

        UnitsConverter uc = UnitsConverter.IDENTITY;

        if (units != null && UnitsUtil.isTimeLocation(units)) {
            type = CDF_EPOCH;
            uc = units.getConverter(Units.cdfEpoch);
        }

        Variable var;
        
        if ( ds.rank()==1 ) {
            var = Variable.create(cdf, name, type,
                1L, 1L, new long[]{ds.length()}, NOVARY, new long[]{VARY});

            double[][] dexport = new double[1][];

            dexport[0]= new double[ds.length()];
            for ( int j=0; j<ds.length(); j++ ) {
                dexport[0][j]= uc.convert(ds.value(j));
            }

            var.putHyperData( 0, 1, 1L, 
                    new long[] { 0 }, 
                    new long[] { ds.length() },
                    new long[] { 1 }, 
                    dexport );
        } else {
            throw new IllegalArgumentException("not supported!");
        }
        copyMetadata(units, var, ds);
        return var;
    }

    /**
     * convert the rank 1 dataset to a native array.
     * @param ds rank 1 dataset
     * @param uc units converter to convert the type.
     * @param type type code, such as CDF_DOUBLE indicating how the data should be converted.
     * @return array of this type.
     */
    private Object doIt1( QDataSet ds, UnitsConverter uc, long type ) {
        Object export;
        QubeDataSetIterator iter = new QubeDataSetIterator(ds);
        if ( type==CDF_DOUBLE || type==CDF_EPOCH ) {
            double[] dexport= new double[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = uc.convert(iter.getValue(ds));
            }
            export= dexport;
       } else if ( type==CDF_TIME_TT2000 ) {
            long[] dexport= new long[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = (long)uc.convert(iter.getValue(ds));
            }
            export= dexport;
            
        } else if ( type==CDF_FLOAT ) {
            float[] fexport= new float[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                fexport[i++] = (float)uc.convert(iter.getValue(ds));
            }
            export= fexport;

        } else if ( type==CDF_INT4 ) {
            int[] bexport= new int[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (int)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDF_INT2 ) {
            short[] bexport= new short[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDF_BYTE ) {
            byte[] bexport= new byte[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (byte)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        return export;

    }

    /**
     * cdf library needs array in double or triple arrays.  
     * 
     * @param ds
     * @param uc UnitsConverter in case we need to handle times.
     * @param type
     * @return
     */
    private Object dataSetToArray( QDataSet ds, UnitsConverter uc, long type, ProgressMonitor mon ){
        Object oexport;

        if ( ds.rank()==1 ) {
            return doIt1( ds, uc, type );
            
        } else if ( ds.rank()==2 ) {

            if ( type==CDF_DOUBLE ) {
                oexport= new double[ds.length()][];
            } else if ( type==CDF_TIME_TT2000 ) {
                oexport= new long[ds.length()][];
            } else if ( type==CDF_FLOAT ) {
                oexport= new float[ds.length()][];
            } else if ( type==CDF_INT4 ) {
                oexport= new int[ds.length()][];
            } else if ( type==CDF_INT2 ) {
                oexport= new short[ds.length()][];
            } else if ( type==CDF_BYTE ) {
                oexport= new byte[ds.length()][];
            } else {
                throw new IllegalArgumentException("type not supported: "+type);
            }
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else if ( ds.rank()==3 ) {

            if ( type==CDF_DOUBLE ) {
                oexport= new double[ds.length()][][];
            } else if ( type==CDF_TIME_TT2000 ) {
                oexport= new long[ds.length()][][];                
            } else if ( type==CDF_FLOAT ) {
                oexport= new float[ds.length()][][];
            } else if ( type==CDF_INT4 ) {
                oexport= new int[ds.length()][];
            } else if ( type==CDF_INT2 ) {
                oexport= new short[ds.length()][];
            } else if ( type==CDF_BYTE ) {
                oexport= new byte[ds.length()][][];
            } else {
                throw new IllegalArgumentException("type not supported"+type);
            }
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else if ( ds.rank()==4 ) {

            if ( type==CDF_DOUBLE ) {
                oexport= new double[ds.length()][][][];
            } else if ( type==CDF_TIME_TT2000 ) {
                oexport= new long[ds.length()][][][];
            } else if ( type==CDF_FLOAT ) {
                oexport= new float[ds.length()][][][];
            } else if ( type==CDF_INT4 ) {
                oexport= new int[ds.length()][];
            } else if ( type==CDF_INT2 ) {
                oexport= new short[ds.length()][];
            } else if ( type==CDF_BYTE ) {
                oexport= new byte[ds.length()][][][];
            } else {
                throw new IllegalArgumentException("type not supported"+type);
            }
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
    }

    private Variable addVariableRankN(QDataSet ds, String name, Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon) throws CDFException {
        Units units = (Units) ds.property(QDataSet.UNITS);
        long type = CDF_DOUBLE;

        String t= params.get("type");
        if ( t!=null ) {
            if ( t.equals("float") ) {
                type= CDF_FLOAT;
            } else if ( t.equals("byte")) {
                type= CDF_BYTE;
            } else if ( t.equals("int2")) {
                type= CDF_INT2;
            } else if ( t.equals("int4")) {
                type= CDF_INT4;
            } else if ( t.equals("double")) {
                type= CDF_DOUBLE;
            }
        } else {
            if ( ds.rank()<3 ) {
                type= CDF_DOUBLE;
            } else {
                type= CDF_FLOAT;
            }
        }

        UnitsConverter uc = UnitsConverter.IDENTITY;

        if (units != null && UnitsUtil.isTimeLocation(units)) {
            boolean tt2000= !( "epoch".equals( params.get("timeType") ) );
            if ( tt2000 ) {                
                type = CDF_TIME_TT2000;
                uc = units.getConverter(Units.cdfTT2000);
                units= Units.cdfTT2000;
            } else {
                type = CDF_EPOCH;
                uc = units.getConverter(Units.cdfEpoch);
                units= Units.cdfEpoch;
            }
        }

        Variable var;
        
        if ( ds.rank()==1 ) {
            var = Variable.create(cdf, name, type,
                1L, 0L, new long[]{1}, VARY, new long[]{NOVARY});

            Object oexport= dataSetToArray( ds, uc, type, mon );

            var.putHyperData(0, ds.length(), 1L, 
                    new long[0], 
                    new long[0], 
                    new long[0], 
                    oexport );
            
        } else if ( ds.rank()==2 ) {
            var = Variable.create(cdf, name, type,
                1L, 1L, new long[]{ds.length(0)}, VARY, new long[]{VARY});

            Object oexport= dataSetToArray( ds, uc, type, mon );

            var.putHyperData( 0, ds.length(), 1L, 
                    new long[] { 0 }, 
                    new long[] { ds.length(0) },
                    new long[] { 1 }, 
                    oexport );
            
        } else if ( ds.rank()==3 ) {

            var = Variable.create(cdf, name, type,
                1L, 2L, new long[]{ds.length(0),ds.length(0,0)}, VARY, new long[]{VARY,VARY});

            mon.setTaskSize(ds.length());
            mon.started();

            for ( int i=0; i<ds.length(); i++ ) {

                Object oexport= dataSetToArray( ds.slice(i), uc, type, mon );

                var.putHyperData( i, 1L, 1L,
                    new long[] { 0,0 },
                    new long[] { ds.length(0), ds.length(0,0) },
                    new long[] { 1, 1 },
                    oexport );
                mon.setTaskProgress(i);

            }
            mon.finished();

        } else if ( ds.rank()==4 ) {

            var = Variable.create(cdf, name, type,
                1L, 3L, new long[]{ds.length(0),ds.length(0,0),ds.length(0,0,0)}, VARY, new long[]{VARY,VARY,VARY});

            mon.setTaskSize(ds.length());
            mon.started();

            for ( int i=0; i<ds.length(); i++ ) {

                Object oexport= dataSetToArray( ds.slice(i), uc, type, mon );

                var.putHyperData( i, 1L, 1L,
                    new long[] { 0,0,0 },
                    new long[] { ds.length(0), ds.length(0,0), ds.length(0,0,0) },
                    new long[] { 1,1,1 },
                    oexport );
                mon.setTaskProgress(i);

            }
            mon.finished();



        } else {
            throw new IllegalArgumentException("rank limit");
        }
        
        if ( ds.rank()==2 ) {
            QDataSet bds= (QDataSet) ds.property(QDataSet.BUNDLE_1);
            if ( bds!=null ) {
                
                String[] arr= new String[bds.length()];
                
                long len= 0;
                for ( int i=0; i<bds.length(); i++ ) {
                    String label= (String) bds.property(QDataSet.NAME,i);
                    if ( label==null ) label= "ch_"+i;
                    len= Math.max( len, label.length() );
                    arr[i]= label;
                }
                Variable lvar = Variable.create(cdf, name+"_LABELS", CDFConstants.CDF_CHAR,
                    len, 1L, new long[]{bds.length()}, VARY, new long[]{VARY});
                
                lvar.putRecord(0,arr);
                                            
                var.putEntry( lablAttr, CDF_CHAR, name+"_LABELS" );
            }
        }
        copyMetadata(units, var, ds);
        return var;
    }

    private void copyMetadata(Units units, Variable var, QDataSet ds) throws CDFException {
        if ( units!=null ) {
            if (units != Units.cdfEpoch) {
                var.putEntry(unitsAttr, CDF_CHAR, units.toString());
            } else {
                var.putEntry(unitsAttr, CDF_CHAR, "ms");
            }
        }
        String label = (String) ds.property(QDataSet.LABEL);
        if (label != null && label.length()>0 ) {
            if ( label.endsWith("("+units+")") ) {
                label= label.substring(0,label.length()-units.toString().length()-2);
            }
            var.putEntry(lablAxisAttr, CDF_CHAR, label);
        }
        String title = (String) ds.property(QDataSet.TITLE);
        if (title != null && title.length()>0 ) {
            var.putEntry(catdescAttr, CDF_CHAR, title);
        }
        Number vmax= (Number) ds.property( QDataSet.VALID_MAX );
        Number vmin= (Number) ds.property( QDataSet.VALID_MIN );
        if ( vmax!=null || vmin !=null ) {
            if ( units==Units.cdfEpoch ) {
                UnitsConverter uc= ((Units)ds.property(QDataSet.UNITS)).getConverter(units);
                if ( vmax==null ) vmax= 1e38; else vmax= uc.convert(vmax);
                if ( vmin==null ) vmin= -1e38; else vmin= uc.convert(vmin);
                var.putEntry(validminAttr, CDF_DOUBLE, vmin.doubleValue() );
                var.putEntry(validmaxAttr, CDF_DOUBLE, vmax.doubleValue() );
            } else {
                if ( vmax==null ) vmax= 1e38;
                if ( vmin==null ) vmin= -1e38;
                var.putEntry(validminAttr, CDF_DOUBLE, vmin.doubleValue() );
                var.putEntry(validmaxAttr, CDF_DOUBLE, vmax.doubleValue() );
            }
        }
        Number fillval= (Number) ds.property( QDataSet.FILL_VALUE );
        if ( fillval!=null ) {
            var.putEntry(fillvalAttr,CDF_DOUBLE,fillval.doubleValue());
        } else {
            var.putEntry(fillvalAttr,CDF_DOUBLE,-1e31);
        }
        Number smax= (Number) ds.property( QDataSet.TYPICAL_MAX );
        Number smin= (Number) ds.property( QDataSet.TYPICAL_MIN );
        if ( smax!=null || smin !=null ) {
            if ( units==Units.cdfEpoch ) {
                UnitsConverter uc= ((Units)ds.property(QDataSet.UNITS)).getConverter(units);
                if ( smax==null ) smax= 1e38; else smax= uc.convert(smax);
                if ( smin==null ) smin= -1e38; else smin= uc.convert(smin);
                var.putEntry(scaleminAttr, CDF_DOUBLE, smin.doubleValue() );
                var.putEntry(scalemaxAttr, CDF_DOUBLE, smax.doubleValue() );
            } else {
                if ( smax==null ) smax= 1e38;
                if ( smin==null ) smin= -1e38;
                var.putEntry(scaleminAttr, CDF_DOUBLE, smin.doubleValue() );
                var.putEntry(scalemaxAttr, CDF_DOUBLE, smax.doubleValue() );
            }
        }
        String format= (String) ds.property( QDataSet.FORMAT );
        if ( format!=null ) {
            var.putEntry(formatAttr,CDF_CHAR,format);
        }

        String displayType= (String)ds.property( QDataSet.RENDER_TYPE );
        if ( displayType==null || displayType.length()==0 ) {
            displayType= DataSourceUtil.guessRenderType(ds);
        }
        if ( displayType.equals("nnSpectrogram") || displayType.equals("spectrogram") ) {
            displayType= "spectrogram";
        } else if ( displayType.equals("image")) {
            displayType= "image";
        } else if ( displayType.equals("series") || displayType.equals("scatter") || displayType.equals("hugeScatter") ) {
            displayType= "time_series";
        }
        var.putEntry( displayTypeAttr,CDF_CHAR,displayType);

    }

    public boolean canFormat(QDataSet ds) {
        return ! ( ds.rank()==0  || SemanticOps.isJoin(ds) );
    }

    public String getDescription() {
        return "NASA Common Data Format";
    }

}
