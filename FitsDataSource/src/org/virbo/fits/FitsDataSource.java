/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.fits;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.eso.fits.FitsFile;
import org.eso.fits.FitsHDUnit;
import org.eso.fits.FitsKeyword;
import org.eso.fits.FitsMatrix;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.FDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class FitsDataSource extends AbstractDataSource {

    FitsDataSource(URI uri) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        int ihdu = 0;
        Map<String, Integer> plottable = FitsDataSourceFactory.getPlottable(uri, mon);

        String name = (String) getParams().get("arg_0");
        if (name != null) {
            ihdu = plottable.get(name);
        }

        FitsFile file = new FitsFile(getFile(mon));
        FitsHDUnit hdu = file.getHDUnit(ihdu);
        FitsMatrix dm = (FitsMatrix) hdu.getData();

        int naxis[] = dm.getNaxis();
        double crval[] = dm.getCrval();
        double crpix[] = dm.getCrpix();
        double cdelt[] = dm.getCdelt();


        float[] fdata = new float[dm.getNoValues()];
        dm.getFloatValues(0, dm.getNoValues(), fdata);

        if (naxis.length == 3) {
            naxis = new int[]{naxis[2], naxis[0], naxis[1]};
        }

        FDataSet result;
        result = FDataSet.wrap(fdata, naxis);

        int rank = result.rank();


        MutablePropertyDataSet xx;
        if ( rank==2 ) {
            xx= DataSetUtil.tagGenDataSet(naxis[0], crval[0] - cdelt[0] * crpix[0], cdelt[0]);
            xx.putProperty( QDataSet.NAME, "axis0" );
            result.putProperty( "DEPEND_0", xx);
            xx= DataSetUtil.tagGenDataSet(naxis[1], crval[1] - cdelt[1] * crpix[1], cdelt[1]);
            xx.putProperty( QDataSet.NAME, "axis1" );
            result.putProperty( "DEPEND_1", xx);      
            return DataSetOps.transpose2(result);
        } else {
            xx= DataSetUtil.tagGenDataSet(naxis[2], crval[1] - cdelt[0] * crpix[0], cdelt[0]);
            xx.putProperty( QDataSet.NAME, "axis0" );
            result.putProperty( "DEPEND_2", xx);
            xx= DataSetUtil.tagGenDataSet(naxis[1], crval[1] - cdelt[1] * crpix[1], cdelt[1]);
            xx.putProperty( QDataSet.NAME, "axis1" );
            result.putProperty( "DEPEND_1", xx);            
            xx = DataSetUtil.indexGenDataSet(naxis[0]);
            xx.putProperty( QDataSet.NAME, "bundle" );
            result.putProperty("DEPEND_0",xx);
            return result;
        }
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        int ihdu = 0;
        Map<String, Integer> plottable = FitsDataSourceFactory.getPlottable(uri, mon);

        String name = (String) getParams().get("arg_0");
        if (name != null) {
            ihdu = plottable.get(name);
        }

        FitsFile file = new FitsFile(getFile(mon));
        FitsHDUnit hdu = file.getHDUnit(ihdu);
        FitsMatrix dm = (FitsMatrix) hdu.getData();

        Map<String, Object> meta = new HashMap<String, Object>();

        Enumeration e = hdu.getHeader().getKeywords();
        while (e.hasMoreElements()) {
            FitsKeyword key = (FitsKeyword) e.nextElement();
            Object val;
            switch (key.getType()) {
                case FitsKeyword.BOOLEAN:
                    val = key.getBool();
                    break;
                case FitsKeyword.COMMENT:
                    val = key.getComment();
                    break;
                case FitsKeyword.DATE:
                    val = key.getDate();
                    break;
                case FitsKeyword.INTEGER:
                    val = key.getInt();
                    break;
                case FitsKeyword.NONE:
                    val = "NONE";
                    break;
                case FitsKeyword.REAL:
                    val = key.getReal();
                    break;
                case FitsKeyword.STRING:
                    val = key.getString();
                    break;
                default:
                    val = "????";
            }
            meta.put(key.getName(), val);
        }
        return meta;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return new FitsMetadataModel();
    }
}
