/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.fits;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.das2.util.monitor.ProgressMonitor;
import org.eso.fits.FitsColumn;
import org.eso.fits.FitsData;
import org.eso.fits.FitsFile;
import org.eso.fits.FitsHDUnit;
import org.eso.fits.FitsKeyword;
import org.eso.fits.FitsMatrix;
import org.eso.fits.FitsTable;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.FDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;

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
        Map<String, Integer> plottable = FitsDataSourceFactory.getPlottable(resourceURI, mon);

        String name = (String) getParams().get("arg_0");
        if (name != null) {
            ihdu = plottable.get(name);
        } else {
            Iterator<Entry<String,Integer>> a=  plottable.entrySet().iterator();
            while ( a.hasNext() ) {
                ihdu= a.next().getValue(); // pick the last one
            }
        }

        FitsFile file = new FitsFile(getFile(mon));
        FitsHDUnit hdu = file.getHDUnit(ihdu);
        FitsData fd= hdu.getData();

        ArrayDataSet result;

        if ( fd instanceof FitsMatrix ) {
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
        } else if ( fd instanceof FitsTable ) {
            final FitsTable ft= (FitsTable)fd;

            MutablePropertyDataSet mpds= new AbstractDataSet() {
                @Override
                public int rank() {
                    return 2;
                }

                @Override
                public double value( int i0, int i1 ) {
                    return ft.getColumn(i1).getReal(i0);
                }

                @Override
                public int length() {
                    return ft.getNoRows();
                }

                @Override
                public int length(int i0) {
                    return ft.getNoColumns();
                }

            };
            mpds.putProperty(QDataSet.QUBE,Boolean.TRUE);
            QDataSet bds= new AbstractDataSet() {
                @Override
                public int rank() {
                    return 2;
                }
                @Override
                public double value( int i0, int i1 ) {
                    return 1;
                }
                @Override
                public int length() {
                    return ft.getNoColumns();
                }
                @Override
                public int length(int i) {
                    return 0;
                }
                @Override
                public Object property( String name, int i ) {
                    if ( name.equals(QDataSet.LABEL) ) {
                        return ft.getColumn(i).getLabel();
                    } else if ( name.equals(QDataSet.NAME) ) {
                        return Ops.safeName(ft.getColumn(i).getLabel());
                    } else {
                        return super.property(name);
                    }
                }
            };
            mpds.putProperty(QDataSet.BUNDLE_1,bds);

            if ( bds.length()==1 ) {
                return DataSetOps.unbundle(mpds,0); // scalar
            } else {
                return mpds;
            }

        } else {
            throw new IllegalArgumentException("fitsdata type not supported: "+fd.getClass() );

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
