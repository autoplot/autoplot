
package org.autoplot.scriptconsole;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.ExceptionHandler;

/**
 * This dumps the error report to the current working directory.  
 * This was motivated by hudson tests that would fail and a person couldn't
 * see where it was failing.
 * 
 * @author faden@cottagesystems.com
 */
public class DumpRteExceptionHandler implements ExceptionHandler {

    @Override
    public void handle(Throwable t) {
        Map<String,Object> data= new HashMap();
        data.put( GuiExceptionHandler.THROWABLE, t );
        data.put( GuiExceptionHandler.EMAIL, "otto@autoplot.org" );
        data.put( GuiExceptionHandler.INCLDOM, Boolean.TRUE );
        String s= GuiExceptionHandler.formatReport( data, true, "DumpRteExceptionHandler generated this" );
        
        int rteHash;
        rteHash= GuiExceptionHandler.hashCode( t );
        
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String eventId= sdf.format( now );
        
        String id;
        id= System.getProperty("user.name");
        
        String fname= String.format( "rte_%010d_%s_%s.xml", Integer.valueOf(rteHash), eventId, id );
        
        File file= new File(fname).getAbsoluteFile();
        
        try ( FileWriter f = new FileWriter(file) ) {
            f.write(s);
        } catch (IOException ex) {
            Logger.getLogger(DumpRteExceptionHandler.class.getName()).log(Level.SEVERE, null, ex);
        } 
        System.err.println("##### Exception written to "+file);
    }

    @Override
    public void handleUncaught(Throwable t) {
        handle(t);
    }

}
