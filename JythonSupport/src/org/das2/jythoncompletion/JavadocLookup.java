
package org.das2.jythoncompletion;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Provide a class for keeping track of the Javadoc lookups, 
 * so this is not buried in code (so much).
 * @author faden@cottagesystems.com
 */
public class JavadocLookup {
    
    private static JavadocLookup instance;
    
    /**
     * get the one instance
     * @return the instance
     */
    public static synchronized JavadocLookup getInstance() {
        if ( instance==null ) {
            instance= new JavadocLookup();
            // note the order is important, so that org.w3c.dom is matched before org.
            instance.setLinkForJavaSignature("javax","http://docs.oracle.com/javase/8/docs/api/");  // Java 7 docs look terrible in the popup, so there's work to be done.
            instance.setLinkForJavaSignature("java","http://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/w3c/dom","http://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/xml/sax","http://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("gov/nasa/gsfc/spdf/cdfj","https://cdaweb.sci.gsfc.nasa.gov/~nand/cdfj/docs/" );
            instance.setLinkForJavaSignature("org", JythonCompletionProvider.getInstance().settings.getDocHome() );
        }
        return instance;
    }
    
    private final LinkedHashMap<String,String> lookups= new LinkedHashMap<String,String>();
    
    /**
     * return a link to the documentation for a java signature.  For standard library
     * things, this goes to Oracle's website.  For other things, this goes
     * to the Autoplot/Das2 javadocs.
     * Java8 http://docs.oracle.com/javase/8/docs/api/java/io/File.html#createTempFile-java.lang.String-java.lang.String-java.io.File-
     * @param signature signature like javax.swing.JCheckBox#paramString()
     * @return null or the link, like http://docs.oracle.com/javase/6/docs/api/javax/swing/JCheckBox#paramString()
     */
    public String getLinkForJavaSignature(String signature) {
        if ( signature==null ) return null;
        String lookfor= signature.replaceAll("\\.","/");
        Set<String> keys= lookups.keySet();
        for ( String key : keys ) {
            if ( lookfor.startsWith(key) ) {
                String s= lookups.get(key);
                if ( s.startsWith("http://docs.oracle.com/javase/8/docs/api/") ) {
                    return lookups.get(key) + signature.replaceAll("[\\(\\)\\,]", "-");
                } else if ( s.startsWith("http://www-pw.physics.uiowa.edu/~jbf/autoplot/javadoc/") ) {
                    return lookups.get(key) + signature.replaceAll("[\\(\\)\\,]", "-");
                } else {
                    return lookups.get(key) + signature.replaceAll(",", ", ");
                }
            }
        }
        return null;

        // old code where it's not clear what I was trying to support:
                //String docHome= JythonCompletionProvider.getInstance().settings().getDocHome();
                //docHome= docHome.replaceAll("AUTOPLOT_HOME", FileSystem.settings().getLocalCacheDir().toString() );
                //link = JythonCompletionProvider.getInstance().settings().getDocHome() + signature;
    }
    
    public void setLinkForJavaSignature( String signatureStart, String link ) {
        lookups.put( signatureStart+"/", link );
    }
}
