
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Variable;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jbf
 */
public class DemoColMajority {
    
    static {
       loadCdfLibraries();
    }

    private static void loadCdfLibraries() {
        String cdfLib1 = System.getProperty("cdfLib1");
        String cdfLib2 = System.getProperty("cdfLib2");

        if (cdfLib1 == null && cdfLib2 == null) {
            System.err.println("System properties for cdfLib not set, setting up for debugging");
            String os = System.getProperty("os.name");
            if (os.startsWith("Windows")) {
                cdfLib1 = "dllcdf";
                cdfLib2 = "cdfNativeLibrary";
            } else {
                cdfLib2 = "cdfNativeLibrary";
            }
        }

        if (cdfLib1 != null) {
            System.loadLibrary(cdfLib1);
        }
        if (cdfLib2 != null) {
            System.loadLibrary(cdfLib2);
        }
    }

    private static void doIt(CDF cdf) throws CDFException, IndexOutOfBoundsException {
        if (  cdf.getMajority()==CDF.ROW_MAJOR ) System.err.println( "Majority: ROW_MAJOR"  );
        if (  cdf.getMajority()==CDF.COLUMN_MAJOR ) System.err.println( "Majority: COLUMN_MAJOR"  );
        Variable v= cdf.getVariable("rank3float");

        long[] dimSizes = v.getDimSizes(); // getDimSizes can return null, but this is just for testing.

        boolean swapDimSizesTest= false;
        if ( swapDimSizesTest && cdf.getMajority()==CDF.COLUMN_MAJOR ) {
            long t= dimSizes[0];
            dimSizes[0]= dimSizes[1];
            dimSizes[1]= t;
        }

        System.err.println("dimSizes=["+dimSizes[0]+","+dimSizes[1]+"]");
        
        float[][][] data= (float[][][]) v.getHyperData( 0, 7, 1, new long[] {0,0}, dimSizes, new long[] { 1,1 } );

        if ( cdf.getMajority()==CDF.ROW_MAJOR ) {
            System.err.println("data[0][0][0]="+data[0][0][0]);
            System.err.println("data[0][1][1]="+data[0][1][1]);
            System.err.println("data[0][4][1]="+data[0][4][1]);
        } else {
            System.err.println("data[0][0][0]="+data[0][0][0]);
            System.err.println("data[0][1][1]="+data[0][1][1]);
            System.err.println("data[0][4][1]="+data[0][4][1]);
            System.err.println("data[0][1][4]="+data[0][1][4]);
        }

    }

    public static void main(String[] args) throws Exception {

        System.err.println("java.library.path=" + System.getProperty("java.library.path"));

        String file1= "/tmp/testCdfRowMajor.cdf";
        String file2= "/tmp/testCdfColMajor.cdf";
        CDF cdf;

        System.err.println("CDF version= " + CDF.getLibraryVersion());

        System.err.println("---"+file1+"---");
        cdf = CDF.open(file1, CDF.READONLYon);
        try {
            doIt(cdf);
        } catch (CDFException ex) {
        } catch (IndexOutOfBoundsException ex) {
        }
        cdf.close();

        System.err.println("---"+file2+"---");
        cdf = CDF.open(file2, CDF.READONLYon);
        try {
            doIt(cdf);
        } catch (CDFException ex) {
        } catch (IndexOutOfBoundsException ex) {
        }

        cdf.close();
    }

}
