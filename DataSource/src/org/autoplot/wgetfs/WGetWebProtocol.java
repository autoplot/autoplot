/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.wgetfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.filesystem.WebFileObject;
import org.das2.util.filesystem.WebProtocol;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public class WGetWebProtocol implements WebProtocol {

    private static final Logger logger= Logger.getLogger("das2.filesystem.wget");
    
    URL root;
    
    public WGetWebProtocol( URL root ) {
        if ( root.toString().endsWith("/") ) {
            this.root= root;
        } else {
            try {
                this.root= new URL( root+"/" );
            } catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public InputStream getInputStream(WebFileObject fo, ProgressMonitor mon) throws IOException {
        String[] cmd;
        if ( WGetFileSystemFactory.useCurl ) {
            cmd= new String[] { WGetFileSystemFactory.exe, "-o", "-", root.toString() + fo.getNameExt() };
        } else {
            cmd= new String[] { WGetFileSystemFactory.exe, "-O", "-", root.toString() + fo.getNameExt() };
        }
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        return p.getInputStream();
        
    }

    @Override
    public Map<String, String> getMetadata(WebFileObject fo) throws IOException {
        String[] cmd;
        if ( WGetFileSystemFactory.useCurl ) {
            return Collections.emptyMap(); //TODO: do this
        } else {
            cmd= new String[] { WGetFileSystemFactory.exe, "--server-response", "--spider", root.toString() + fo.getNameExt()  };
        }
        logger.log(Level.FINE, "cmd: {0} {1} {2} {3}", new Object[]{cmd[0], cmd[1], cmd[2], cmd[3]});
        
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        LinkedHashMap<String,String> result= new LinkedHashMap<>();
        
        Pattern pattern= Pattern.compile("  (.*): (.*)");
        try ( BufferedReader reader= new BufferedReader(new InputStreamReader( p.getErrorStream() ) ) ) {
            String line= reader.readLine();
            while ( line!=null ) {
                Matcher m= pattern.matcher(line);
                if ( m.matches() ) {
                    result.put( m.group(1), m.group(2) );
                }
                line= reader.readLine();
            }
        } finally {
            // is there clean-up to do?
        }
        return result;
    }
    
}
