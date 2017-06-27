/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 *
 * @author jbf
 */
public class DemoFileWatcher {
    public static void main( String[] args ) throws IOException, InterruptedException {
        
        File file= new File("/home/jbf/tmp/externalModificationFile.txt");
        WatchService watcher = FileSystems.getDefault().newWatchService();
                
        Path fpath= file.toPath();
        
        Path parent= fpath.getParent();
        parent.register( watcher, StandardWatchEventKinds.ENTRY_MODIFY );
        
        while ( true ) {
            WatchKey key= watcher.take();
            for ( WatchEvent e : key.pollEvents() ) {
                
                WatchEvent<Path> ev = (WatchEvent<Path>)(e);
                Path name = ev.context();
                
                System.err.println( " " + name + " " + ev.kind() + " " + ev.context() );

            }
            
            if ( !key.reset() ) return;
            
        }

    }
            
        
}
