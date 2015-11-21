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
    
    /** 
     * a string literal argument for a command like getDataSet.  
     * We can delegate completion to an engine for this command.
     */
    public static final String STRING_LITERAL_ARGUMENT= "stringLiteralArgument";
    
    /**
     * we are within the body of a command such as "plot" and we want to see
     * what the arguments and named parameters are.
     */
    public static final String COMMAND_ARGUMENT="commandArgument";
    
    /**
     * the context type, such as COMMAND_ARGUMENT or STRING_LITERAL_ARGUMENT.
     * In ds= getDataSet('/hom&lt;C&gt;'), this is STRING_LITERAL_ARGUMENT
     */
    public String contextType;
    
    /**
     * the context string, such as a command name.
     * In ds= getDataSet('/hom&lt;C&gt;'), this is 'getDataSet'
     */
    public String contextString;  // depends on type
    
    /**
     * the item on which completion was triggered.
     * In ds= getDataSet('/hom&lt;C&gt;'), this is '/hom'
     */
    public String completable;
    
    /**
     * 
     * @param contextType
     * @param contextString
     * @param completable 
     */
    public CompletionContext( String contextType, String contextString, String completable ) {
        this.contextType= contextType;
        this.contextString= contextString;
        this.completable= completable;
    }

    @Override
    public String toString() {
        return "" + this.contextType + ": " + this.contextString + " " + this.completable;
    }
}
