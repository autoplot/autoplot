
package org.autoplot.hapi;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Allow iteration over JSONArrays.
 * @author jbf
 */
public class JSONArrayIterator implements Iterable<JSONObject>, Iterator<JSONObject> {

    int index;
    JSONArray array;
    
    public JSONArrayIterator( JSONArray array ) {
        this.array= array;
        this.index= 0;
    }
    
    @Override
    public boolean hasNext() {
        return this.index<this.array.length();
    }

    @Override
    public JSONObject next() {
        try {
            return this.array.getJSONObject(this.index++);
        } catch (JSONException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public Iterator<JSONObject> iterator() {
        return this;
    }

    @Override
    public void remove() {
        // support Java 7.
    }
    
}
