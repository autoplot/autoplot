/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

/**
 *
 * @author jbf
 */
public class CompletionContext {
    public static final String METHOD_NAME= "method";
    public static final String ATTRIBUTE_NAME= "attr";
    public static final String PACKAGE_NAME= "package";
    public static final String MODULE_NAME= "module";
    public static final String DEFAULT_NAME= "default"; // interpreter's namespace
    /** a string literal argument for a command like getDataSet.  
     * We can delegate completion to an engine for this command.
     */
    public static final String STRING_LITERAL_ARGUMENT= "stringLiteralArgument";
    
    String contextType;
    String contextString;  // depends on type
    String completable;
    
    public CompletionContext( String contextType, String contextString, String completable ) {
        this.contextType= contextType;
        this.contextString= contextString;
        this.completable= completable;
    }
    
}
