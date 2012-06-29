
package org.virbo.netCDF;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.ParsedSectionSpec;
import ucar.nc2.Variable;
import ucar.nc2.iosp.IOServiceProvider;
import ucar.nc2.util.CancelTask;
import ucar.unidata.io.RandomAccessFile;

/**
 * APIOServiceProvider adapts Autoplot's data model to NetCDF to allow Autoplot
 * URIs to be read into NetCDF.  The property vapuri in the NCML file contains
 * an Autoplot URI.  It's been a while since we've played with this (to prove
 * the idea), and I'm not sure where example NCML files are found.
 *
 * @author jbf
 */
public class APIOServiceProvider extends AbstractIOSP implements IOServiceProvider {

    public boolean isValidFile(RandomAccessFile arg0) throws IOException {
        return false; // must refer via iosp="org.virbo.netcdf.APIOServiceProvider"
    }

    QDataSet result;
    String dep0name;

    public void open(RandomAccessFile raf, NetcdfFile ncfile, CancelTask arg2) throws IOException {
        try {

            String suri= getProperty("vapuri");

            if ( suri==null ) throw new IllegalArgumentException("vapuri must be specified in iospParams");
            
            result= DataSetURI.getDataSource( suri ).getDataSet( new NullProgressMonitor() );

            Dimension dim = null;

            String name = (String) result.property(QDataSet.NAME);
            if ( name==null ) name="data";

            int n = result.length();

            dim = new Dimension(name, n, true, true, false);

            ncfile.addDimension( null, dim );

            QDataSet dep0= (QDataSet) result.property( QDataSet.DEPEND_0 );

            dep0name=null;
            if ( dep0!=null ) {
                dep0name = (String) dep0.property(QDataSet.NAME);
                if ( name==null ) dep0name="dep0";

                dim = new Dimension(dep0name, n, true, true, false);

                ncfile.addDimension( null, dim );
            }

            if ( dep0!=null ) {
                Variable var = new Variable( ncfile, null, null, dep0name );
                var.setDataType( DataType.DOUBLE ); //TODO: support other types, use info from ncml
                var.setDimensionsAnonymous( DataSetUtil.qubeDims(result) );
                ncfile.addVariable(null, var);
            }

            Variable var = new Variable( ncfile, null, null, name );
            var.setDataType( DataType.DOUBLE ); //TODO: support other types, use info from ncml
            var.setDimensionsAnonymous( DataSetUtil.qubeDims(result) );
            ncfile.addVariable(null, var);

        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException("APIOSP.open() failed: "+t.getMessage()); //, t);
        }


    }


    public Array readData(Variable variable, Section section) throws IOException, InvalidRangeException {
        String vname = variable.getName();
        DataType type = variable.getDataType();

        double[] data = null;
        String[] sdata = null;

        Array array;

        QDataSet co= result;
        if ( vname.equals(dep0name) ) {
            co= (QDataSet) result.property(QDataSet.DEPEND_0);
        }
        //Construct the Array.
        if (type.isString()) {
            array = Array.factory(type, DataSetUtil.qubeDims(result), sdata);
            throw new RuntimeException("whoops");
        } else {
            data= new double[result.length()];
            for ( int i=0; i<result.length(); i++ ) {
                data[i]= result.value(i);
            }
            array = Array.factory(type, DataSetUtil.qubeDims(result), data);
        }

        return array;
    }

    public String getFileTypeId() {
        return "Autoplot-URI";
    }

    public String getFileTypeDescription() {
        return "Autoplot Data Access URI.  Autoplot's libraries are used to access an Autoplot URI via QDataSet, which" +
                "is then copied into NetCDF";
    }

    public long readToByteChannel(Variable arg0, Section arg1, WritableByteChannel arg2) throws IOException, InvalidRangeException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Array readSection(ParsedSectionSpec arg0) throws IOException, InvalidRangeException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() throws IOException {
        // resources should already be closed.
    }

    public boolean syncExtend() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean sync() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String toStringDebug(Object arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getDetailInfo() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getFileTypeVersion() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
