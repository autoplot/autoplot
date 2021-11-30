
package org.autoplot.fits;

import java.net.URI;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.das2.util.monitor.ProgressMonitor;
import org.eso.fits.FitsData;
import org.eso.fits.FitsFile;
import org.eso.fits.FitsHDUnit;
import org.eso.fits.FitsKeyword;
import org.eso.fits.FitsMatrix;
import org.eso.fits.FitsTable;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.FDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.MetadataModel;
import org.das2.datum.Units;
import org.das2.qds.ops.Ops;
import org.eso.fits.FitsColumn;

/**
 * Support for reading FITS files using eso.org's JFITS library.
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
        }
        
        if ( ihdu==0 && !plottable.containsValue(0) && plottable.size()>0 ) {
            ihdu= plottable.values().iterator().next();
        }

        FitsFile file = new FitsFile(getFile(mon));
        FitsHDUnit hdu = file.getHDUnit(ihdu);
        FitsData fd= hdu.getData();

        ArrayDataSet result;

        if ( fd instanceof FitsMatrix ) {
            FitsMatrix dm = (FitsMatrix) fd;
            int naxis[] = dm.getNaxis();
            double crval[] = dm.getCrval();
            double crpix[] = dm.getCrpix();
            double cdelt[] = dm.getCdelt();

            float[] fdata = new float[dm.getNoValues()];
            dm.getFloatValues(0, dm.getNoValues(), fdata);

            if (naxis.length == 3) {
                naxis = new int[]{naxis[2], naxis[0], naxis[1]};
                crval = new double[] { crval[2], crval[1], crval[0] };
                crpix = new double[] { crpix[2], crpix[1], crpix[0] };
                cdelt = new double[] { cdelt[2], cdelt[1], cdelt[0] };                
            } else if ( naxis.length==2 ) {
                naxis = new int[]{ naxis[1], naxis[0] };
                crval = new double[] { crval[1], crval[0] };
                crpix = new double[] { crpix[1], crpix[0] };
                cdelt = new double[] { cdelt[1], cdelt[0] };
            } else if ( naxis.length==0 ) {
                throw new IllegalArgumentException("Unable to use fits file");
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
            
            //ft.getNoColumns();
            //ft.getColumn(1);

            MutablePropertyDataSet mpds;
            if ( ft.getNoColumns()==2 ) {
                mpds= adaptColumn( ft.getColumn(1), ft.getNoRows() );
                mpds.putProperty(QDataSet.QUBE,Boolean.TRUE);
                mpds.putProperty( QDataSet.NAME, Ops.safeName(ft.getColumn(1).getLabel()) );
            
                MutablePropertyDataSet dep0= adaptColumn( ft.getColumn(0), ft.getNoRows() );
                dep0.putProperty( QDataSet.NAME, Ops.safeName(ft.getColumn(0).getLabel()) );
            
                mpds.putProperty( QDataSet.DEPEND_0, Ops.copy(dep0) );
            } else {
                mpds= adaptColumn( ft.getColumn(0), ft.getNoRows() );
                mpds.putProperty( QDataSet.NAME, Ops.safeName(ft.getColumn(0).getLabel()) );
            
            }
                        
            return Ops.copy( mpds );
            
        } else {
            throw new IllegalArgumentException("fitsdata type not supported: "+fd.getClass() );

        }
        
    }

    private MutablePropertyDataSet adaptColumn( final FitsColumn fc, final int len0 ) {
        
        final int rank= fc.getRepeat()==1 ? 1 : 2;
        final int len1= fc.getRepeat();
                
        AbstractDataSet result= new AbstractDataSet() {
            @Override
            public int rank() {
                return rank;
            }

            @Override
            public double value( int i0 ) {
                return fc.getReal(i0);
            }

            @Override
            public double value( int i0, int i1 ) {
                double[] dd= fc.getReals(i0);
                return dd[i1];
            }

            @Override
            public int length() {
                return len0;
            }

            @Override
            public int length(int i0) {
                return len1;
            }

        };
        
        String fcunit= fc.getUnit()!=null ? fc.getUnit() : "";
        if ( "s".equals(fcunit) ) {
            try {
                result.putProperty( QDataSet.UNITS, Units.lookupTimeUnits( "seconds since 2000-01-01T00:00Z") );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } else {
            result.putProperty( QDataSet.UNITS, Units.lookupUnits( fcunit ) );
        }
        
        return result;
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
