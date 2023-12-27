
package org.autoplot.pds;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A FilePointer is a reference to a URL and then the number of records into
 * the URL. For example, xpath.evaluate("/LABEL/POINTER/text()",doc,XPathConstants.NODESET);
 * resolves to "JED_090_HIERSESP_CDR_2016366_V03.TAB, 1" where 1 is the record
 * number to begin parsing.
 * @author jbf
 */
public class FilePointer {
    
    public FilePointer( URL url, int line ) {
        this.url= url;
        this.line= line;
    }
    
    /**
     * create the label from the specification within the LABEL, like
     * "JED_090_HIERSESP_CDR_2016366_V03.TAB, 1"
     * @param labelFile
     * @param f 
     */
    public FilePointer( URL labelFile, String f) {
        try {
            Pattern p= Pattern.compile("([^,]+)(,\\s*(\\d+))?");
            Matcher m= p.matcher(f);
            if ( !m.matches() ) {
                throw new IllegalArgumentException("LABEL/POINTER should match ([^,]+)(,\\s*(\\d+))?");
            }
            URL url= new URL(labelFile,m.group(1));
            this.url= url;
            if ( m.group(3)!=null ) {
                line= Integer.parseInt(m.group(3));
            }
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public String toString() {
        return this.url.toString() + ( this.line!=1 ? ", " + this.line : "" ); 
    }
    private URL url;

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    private int line;

    public static final String PROP_LINE = "line";

    public int getOffset() {
        return line;
    }

    public void setOffset(int offset) {
        this.line = offset;
    }
    

}
