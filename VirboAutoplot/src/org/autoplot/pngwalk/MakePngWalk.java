/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.ScriptContext;
import org.virbo.datasource.DataSetURI;

/**
 * class to make pngwalks in headless environment
 * @author jbf
 */
public class MakePngWalk {


    /**
     * run the png walk by reading in the python script indicated by value.
     * @param value a python script URI defining "vap" and "params".
     */
    public static void doMakePngWalk(String value) {
        try {
            File f = DataSetURI.getFile(DataSetURI.getResourceURI(value), new NullProgressMonitor());

            PythonInterpreter interp= JythonUtil.createInterpreter( true, true );

            InputStream in = new FileInputStream( f );
            interp.execfile( in, f.getName() );
            in.close();

            String vap= (String) interp.get( "vap", String.class );
            ScriptContext.load(vap);

            interp.set("dom", ScriptContext.getDocumentModel() );

            URL script= MakePngWalk.class.getResource("/scripts/pngwalk/makePngWalk.jy");
            in= script.openStream();

            interp.execfile( in, f.getName() );
            in.close();

        } catch (IOException ex) {
            Logger.getLogger(AutoplotUtil.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void main( String[] args ) {

        try {
            doMakePngWalk( args[0] );
        } catch ( Exception e ) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
