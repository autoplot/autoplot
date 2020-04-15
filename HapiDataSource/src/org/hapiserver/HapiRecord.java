
package org.hapiserver;

/**
 * Interface for conveying a single HAPI record, regardless of its source
 * (binary, csv, etc).
 * @author jbf
 */
public interface HapiRecord {

    /**
     * returns the time as ISO-8601 encoded string
     * @param i the index of the column
     * @return the time as ISO-8601 encoded string
     * @see toSecondsSince1970 to get the long milliseconds.
     * @see toArray to decompose the time.
     */
    String getIsoTime(int i);

    /**
     * get the string value
     * @param i the index of the column
     * @return the string
     */
    String getString(int i);

    /**
     * get the double data
     * @param i the index of the column
     * @return
     */
    double getDouble(int i);

    /**
     * return the data as a 1-D array.  Note that a [n,m] element array
     * will be a n*m element array.
     * @param i
     * @return a 1D array
     */
    double[] getDoubleArray(int i);

    /**
     * get the integer
     * @param i
     * @return 
     */
    int getInteger(int i);

    /**
     * return the number of items.
     * @return 
     */
    int length();
    
}
