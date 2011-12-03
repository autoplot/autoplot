/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.dods;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.Vector;
import opendap.dap.BaseType;
import opendap.dap.DAP2Exception;
import opendap.dap.DArray;
import opendap.dap.DConnect;
import opendap.dap.DDSException;
import opendap.dap.DFloat32;
import opendap.dap.DFloat64;
import opendap.dap.DSequence;
import opendap.dap.DStructure;
import opendap.dap.DataDDS;
import opendap.dap.StatusUI;
import opendap.dap.parser.ParseException;

/**
 *
 * @author jbf
 */
public class TestNewOpenDAP21 {

    private static void printValue( int j, int i, BaseType value) {
        if (value instanceof DFloat64) {
            System.err.printf( "%d=%9.3e", j, ((DFloat64) value).getValue() );
        } else if ( value instanceof DFloat32 ) {
            System.err.printf( "%d=%9.3e", j, ((DFloat32) value).getValue() );
        } else if (value instanceof DArray) {
            throw new IllegalArgumentException("not supported: "+ value); // is supported in Autoplot implementation
        } else {
            throw new IllegalArgumentException("not supported: " + value);
        }
    }

    public static void main( String[] args ) throws FileNotFoundException, ParseException, MalformedURLException, IOException, DDSException, DAP2Exception {
        String source= "http://tsds.net/tsds/test/Scalar";
        DConnect url = new DConnect(source, true);


        StatusUI sui = new StatusUI() {

            long byteCount = 0;

            public void incrementByteCount(int bytes) {
                byteCount += bytes;
            }

            public boolean userCancelled() {
                return false;
            }

            public void finished() {
            }
        };

        /**
         *?sst[0:100:1811][0:10:35][0:10:71]
        */
        String constraint= "?TimeSeries";

        DataDDS dds;

        dds = url.getData(constraint, sui);

        System.err.println("here: "+dds);

        BaseType btvar = dds.getVariable("TimeSeries");
        String type = btvar.getTypeName();

        if ( type.equals("Sequence") ) {

            String t= "scalars";
            DSequence dseq = (DSequence) btvar;
            int cols = dseq.elementCount(true);
            int rows = dseq.getRowCount();

            for (int i = 0; i < rows; i++) {
                Vector v = dseq.getRow(i);
                int j = 0;
                System.err.printf("%d\t",i);
                for (Object ele : v) {
                    if (ele instanceof DStructure) {
                        DStructure ds = (DStructure) ele;
                        Enumeration enume = ds.getVariables();
                        while (enume.hasMoreElements()) {
                            Object k = enume.nextElement();
                            printValue( j, i, (BaseType) k);
                            j++;
                        }
                    } else if (ele instanceof BaseType) {
                        printValue( j, i, (BaseType) ele);
                        j++;
                    } else {
                        throw new IllegalArgumentException("huh");
                    }
                    System.err.print("\t");
                }
                System.err.print("\n");
            }

            if ( cols>2 && t.equals("scalars") ) {
                t= "vectors";
            }

        }
    }
}
