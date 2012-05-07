/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.File;
import java.io.IOException;

/**
 * Allow special exception to be thrown when an empty file is found.
 * @author jbf
 */
public class EmptyFileException extends IOException {

    /**
     * @param s a human readable message
     * @param url a URL, or null.
     */
    public EmptyFileException( File f ) {
        super( "File is empty: "+f );
    }

}
