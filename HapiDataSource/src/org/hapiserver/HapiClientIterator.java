
package org.hapiserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class HapiClientIterator implements Iterator<HapiRecord> {
    
    String nextLine;
    JSONObject info;
    BufferedReader reader;
    CSVHapiRecordConverter converter;
    
    /**
     * create an iterator for the CSV stream.
     * @param info
     * @param reader
     * @throws IOException 
     * @throws org.json.JSONException 
     */
    public HapiClientIterator(JSONObject info, BufferedReader reader) throws IOException, JSONException {
        this.info= info;
        this.reader = reader;
        this.nextLine = this.reader.readLine();
        this.converter= new CSVHapiRecordConverter(info);
    }

    @Override
    public boolean hasNext() {
        boolean result = nextLine != null;
        return result;
    }

    @Override
    public HapiRecord next() {
        if (this.nextLine == null) {
            throw new NoSuchElementException("No more records");
        }        
        HapiRecord result = this.converter.convert(this.nextLine);
        try {
            nextLine = reader.readLine();
            if ( nextLine==null ) {
                reader.close();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
        return (HapiRecord) result;
    }
    
}
