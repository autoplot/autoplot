/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

/**
 * Marker for an unrecognized DataSource, such as JunoWaves when the
 * plugin is not plugged in.
 * @author jbf
 */
public class UnrecognizedDataSourceException extends Exception {
    public UnrecognizedDataSourceException( String msg ) {
        super(msg);
    }
}
