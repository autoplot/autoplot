
package org.autoplot.pds;

import java.net.MalformedURLException;
import java.net.URL;
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
        this.offset= line;
    }
    
    /**
     * create the label from the specification within the LABEL, like
     * "JED_090_HIERSESP_CDR_2016366_V03.TAB, 1"
     * @param labelFile
     * @param f a string like "JED_090_HIERSESP_CDR_2016366_V03.TAB, 1" or "JED_090_HIERSESP_CDR.TAB, 50 BYTES"
     */
    public FilePointer( URL labelFile, String f) {
        try {
            Pattern p= Pattern.compile("([^,]+)(,\\s*(\\d+)(\\s*\\<BYTES\\>)?)?",Pattern.CASE_INSENSITIVE);
            Matcher m= p.matcher(f);
            if ( !m.matches() ) {
                throw new IllegalArgumentException("LABEL/POINTER should match ([^,]+)(,\\s*(\\d+)(\\s*\\<BYTES\\>)?)?");
            }
            URL url= new URL(labelFile,m.group(1));
            this.url= url;
            if ( m.group(3)!=null ) {
                if ( m.group(4)!=null && m.group(4).toUpperCase().endsWith("<BYTES>") ) {
                    offsetUnits= Unit.BYTES;
                } else {
                    offsetUnits= Unit.LINES;
                }
                offset= Integer.parseInt(m.group(3));
            }
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public String toString() {
        return this.url.toString() + ( this.offset!=1 ? ", " + this.offset : "" ); 
    }
    private URL url;

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    private int offset;

    public static final String PROP_LINE = "line";

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
    
    public enum Unit {
        BYTES,
        LINES
    }
    
    private Unit offsetUnits = Unit.LINES;

    public Unit getOffsetUnits() {
        return offsetUnits;
    }

    public void setOffsetUnits(Unit offsetUnits) {
        this.offsetUnits = offsetUnits;
    }


}
