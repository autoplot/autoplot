
package org.das2.jythoncompletion;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.monitor.CancelledOperationException;

/**
 * Provide a class for keeping track of the Javadoc lookups, 
 * so this is not buried in code (so much).
 * @author faden@cottagesystems.com
 */
public class JavadocLookup {
    
    private static JavadocLookup instance;
    
    private static final Logger logger= LoggerManager.getLogger("jython.editor.completion");
    
    /**
     * get the one instance
     * @return the instance
     */
    public static synchronized JavadocLookup getInstance() {
        if ( instance==null ) {
            instance= new JavadocLookup();
            // note the order is important, so that org.w3c.dom is matched before org.
            instance.setLinkForJavaSignature("javax","https://docs.oracle.com/javase/8/docs/api/");  // Java 7 docs look terrible in the popup, so there's work to be done.
            instance.setLinkForJavaSignature("java","https://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/w3c/dom","https://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/xml/sax","https://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/jdesktop","https://docs.oracle.com/javase/8/docs/api/");
            instance.setLinkForJavaSignature("org/apache/commons/math3", "https://commons.apache.org/proper/commons-math/javadocs/api-3.6/" );
            instance.setLinkForJavaSignature("org/apache/commons/math", "https://commons.apache.org/proper/commons-math/javadocs/api-2.0/" );
            instance.setLinkForJavaSignature("gov/nasa/gsfc/spdf/cdfj", "https://cottagesystems.com/~jbf/autoplot/cdf/doc/" );
            instance.setLinkForJavaSignature("org/json", "https://stleary.github.io/JSON-java/" );
            instance.setLinkForJavaSignature("org/autoplot", JythonCompletionProvider.getInstance().settings.getDocHome() );
            instance.setLinkForJavaSignature("org/das2", JythonCompletionProvider.getInstance().settings.getDocHome() );
            instance.setLinkForJavaSignature("com/matio", JythonCompletionProvider.getInstance().settings.getDocHome() );
            instance.setLinkForJavaSignature("ProGAL", JythonCompletionProvider.getInstance().settings.getDocHome() );
            instance.setLinkForJavaSignature("external", JythonCompletionProvider.getInstance().settings.getDocHome() );
        }
        return instance;
    }
    
    private final LinkedHashMap<String,String> lookups= new LinkedHashMap<>();
    
    /**
     * lookupVersions maps from URL to javadoc version.
     */
    private final LinkedHashMap<String,String> lookupVersions= new LinkedHashMap<>();
    
    /**
     * return a link to the documentation for a java signature.  For standard library
     * things, this goes to Oracle's website.  For other things, this goes
     * to the Autoplot/Das2 javadocs.
     * Java8 http://docs.oracle.com/javase/8/docs/api/java/io/File.html#createTempFile-java.lang.String-java.lang.String-java.io.File-
     * @param signature signature like javax.swing.JCheckBox#paramString()
     * @return null or the link, like http://docs.oracle.com/javase/6/docs/api/javax/swing/JCheckBox#paramString()
     * TODO: it appears that this needs /x/y/z.html!
     */
    public String getLinkForJavaSignature(String signature) {
        if ( signature==null ) return null;
        String lookfor= signature.replaceAll("\\.","/");
        Set<Entry<String,String>> entries= lookups.entrySet();
        for ( Entry<String,String> entry : entries ) {
            String key= entry.getKey();
            if ( lookfor.startsWith(key) ) {
                String s= entry.getValue();
                if ( s.startsWith("http://docs.oracle.com/javase/8/docs/api/") ) {
                    return s + signature.replaceAll("[\\(\\)\\,]", "-");
                } else if ( s.startsWith("http://www-pw.physics.uiowa.edu/~jbf/autoplot/javadoc2018/") ) {
                    return s + signature.replaceAll("[\\(\\)\\,]", "-");
                } else if ( s.startsWith("http://www-pw.physics.uiowa.edu/~jbf/autoplot/doc")
                        || s.startsWith("https://jfaden.net/~jbf/autoplot/doc" )
                        || s.startsWith("https://cottagesystems.com/~jbf/autoplot/doc" )
                        || s.startsWith("https://cottagesystems.com/jenkins/job/autoplot-javadoc/") ) {
                    int i= signature.indexOf('(');
                    if ( i>0 ) {
                        signature= signature.substring(0,i);
                    }
                    if ( signature.startsWith("org/das2/qds/ops/Ops.html") && signature.length()>26 ) {
                        char let= signature.charAt(26);
                        return s + signature.substring(0,20) + "_" + let + signature.substring(20);
                    } else {
                        return s + signature;
                    }
                } else {
                    String v= lookupVersions.get(s);
                    if ( v==null || v.startsWith("1.8") ) { //v==null when there is no internet (Dallas flight) It might be nice to have offline message.
                        return s + signature.replaceAll("[\\(\\)\\,]", "-");
                    } else {
                        if ( s.endsWith("/") ) {
                            if ( s.startsWith("file:") ) {
                                if ( !signature.endsWith(".html") ) {
                                    signature= signature+ ".html";
                                }
                                return s + signature.replaceAll(",", ", ");
                            } else {
                                return s + signature.replaceAll(",", ", ");
                            }
                        } else {
                            // Note .zip files do not work!  I thought they did...
                            return s + "/" + signature.replaceAll(",", ", ");
                        }
                    }
                }
            }
        }
        return null;

        // old code where it's not clear what I was trying to support:
                //String docHome= JythonCompletionProvider.getInstance().settings().getDocHome();
                //docHome= docHome.replaceAll("AUTOPLOT_HOME", FileSystem.settings().getLocalCacheDir().toString() );
                //link = JythonCompletionProvider.getInstance().settings().getDocHome() + signature;
    }
    
    /**
     * add the location of the javadocs for the given path.  The path should
     * be / separated, not period separated.
     * @param signatureStart e.g. "gov/nasa/gsfc/spdf/cdfj"
     * @param link e.g. "https://cdaweb.sci.gsfc.nasa.gov/~nand/cdfj/docs/"
     */
    public void setLinkForJavaSignature( String signatureStart, String link ) {
        if ( signatureStart.startsWith("/") ) {
            signatureStart= signatureStart.substring(1);
        }
        if ( signatureStart.endsWith("/") ) {
            signatureStart= signatureStart.substring(0,signatureStart.length()-1);
        }
        if ( link.endsWith(".jar") ) {
            logger.fine("link cannot end with .jar, skipping.");
            return;
        }

        lookups.put( signatureStart+"/", link );
        String tversion= lookupVersions.get(link);
        if ( tversion==null ) {
            try {
                String htmls= HtmlUtil.readToString( new URL(link) );
                String[] ss= htmls.split("\n");
                int iline=0;
                String version="";
                Pattern p= Pattern.compile("Generated by javadoc \\((.*)\\)");
                for ( String s: ss ) {
                    iline++;
                    Matcher m= p.matcher(s);
                    if ( m.find() ) {
                        version= m.group(1);
                        break;
                    }
                    if ( iline>50 ) {
                        break;
                    }
                }
                lookupVersions.put( link, version );

            } catch ( IOException | CancelledOperationException ex ) {

            }
        }
    }

    /**
     * given a class name, what are fully-qualified class names which match?
     * 
     * @param clas
     * @return 
     */
    public List<String> searchForSignature(String clas) {
        if ( clas.startsWith("URIT") ) {
            return Collections.singletonList("org.hapiserver.URITemplate");
        } else {
            for ( Entry<String,String> e: lookups.entrySet() ) {
                // look for list file that has all classnames in each library.
            }
            return Collections.emptyList();
        }
    }
}
