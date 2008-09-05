/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.datum.UnitsConverter;
import edu.uiowa.physics.pw.das.datum.UnitsUtil;
import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.datasource.DataSourceFormat;
import static gsfc.nssdc.cdf.CDFConstants.*;

/**
 * Format the QDataSet into CDF tables.  
 * @author jbf
 */
public class CdfDataSourceFormat implements DataSourceFormat {

    CDF cdf;
    Attribute depend_0, depend_1;
    Map<QDataSet,String> names;

    public CdfDataSourceFormat() {
        names= new HashMap<QDataSet,String>();
    }
    
    private synchronized String nameFor(QDataSet dep0) {
        String name= names.get(dep0);
        
        if ( name==null ) name = (String) dep0.property(QDataSet.NAME);
        
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if (name == null) {
            if ( units!=null && UnitsUtil.isTimeLocation(units)) {
                name = "Epoch";
            } else {
                name = "Variable_" + names.size();
            }
        }
        
        names.put(dep0, name);
        
        return name;
    }

    public void formatData(File url, java.util.Map<String, String> params, QDataSet data, ProgressMonitor mon) throws IOException, CDFException {

        url.delete();
        cdf = CDF.create(url.toString());

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        if (dep0 != null) {
            String name= nameFor(dep0);
            addVariableRank1(dep0, name);
            depend_0 = Attribute.create(cdf, "DEPEND_0", VARIABLE_SCOPE);
        }
        
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);

        if (dep1 != null) {
            String name= nameFor(dep1);
            addVariableRank1NoVary(dep1, name);
            depend_1 = Attribute.create(cdf, "DEPEND_1", VARIABLE_SCOPE);
        }

        Variable var= addVariableRank1(data, nameFor(data) );
        
        if ( depend_0!=null ) Entry.create( depend_0, var.getID(), CDF_CHAR, nameFor(dep0) );
        if ( depend_1!=null ) Entry.create( depend_1, var.getID(), CDF_CHAR, nameFor(dep1) );
        
        cdf.close();

    }

    private Variable addVariableRank1NoVary( QDataSet ds, String name ) throws CDFException {
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
        return var;
    }
    
    private Variable addVariableRank1(QDataSet ds, String name) throws CDFException {
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
                1L, 0L, new long[]{1}, VARY, new long[]{NOVARY});

            double[] dexport = new double[ds.length()];

            QubeDataSetIterator iter = new QubeDataSetIterator(ds);
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = uc.convert(iter.getValue(ds));
            }
            var.putHyperData(0, ds.length(), 1L, 
                    new long[0], 
                    new long[0], 
                    new long[0], 
                    dexport );
        } else if ( ds.rank()==2 ) {
            var = Variable.create(cdf, name, type,
                1L, 1L, new long[]{ds.length(0)}, VARY, new long[]{VARY});

            double[][] dexport = new double[ds.length()][];

            for ( int i=0; i<ds.length(); i++ ) {
                dexport[i]= new double[ds.length(i)];
                for ( int j=0; j<ds.length(i); j++ ) {
                    dexport[i][j]= uc.convert(ds.value(i,j));
                }
            }

            var.putHyperData( 0, ds.length(), 1L, 
                    new long[] { 0 }, 
                    new long[] { ds.length(0) },
                    new long[] { 1 }, 
                    dexport );
            
        } else {
            throw new IllegalArgumentException("rank limit");
        }

        return var;
    }
}
