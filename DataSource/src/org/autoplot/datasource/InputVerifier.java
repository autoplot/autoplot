/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.datasource;

/**
 * interface for verifying text values that must be parsed.  This
 * is currently used for validating timerange strings.
 * @author jbf
 */
public interface InputVerifier {

    /**
     * return true if the string is valid for your
     * application.
     * 
     * @param value the string value
     * @return true if the string is valid,
     */
    public boolean verify(String value);
    
}
