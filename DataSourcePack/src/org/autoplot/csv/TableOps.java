/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.util.regex.Pattern;

/**
 * I'd still like to refactor all the table-type sources to get the common codes.
 * These include:<ul>
 *   <li> html tables
 *   <li> xls, csv
 *   <li> dat
 * </ul>
 * @author jbf
 */
public class TableOps {

    /**
     * returns the index of the field.  Supports the name, or field0, or 0, etc.
     * @param string the field for which we want to identify the index
     * @param fieldNames the field names for each column.
     * @return the field index, or -1 if the column doesn't exist.
     */
    public static int getFieldIndex(String string, String[] fieldNames) {
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equalsIgnoreCase(string)) {
                return i;
            }
        }
        int icol= -1;
        if (Pattern.matches("field[0-9]+", string )) {
            icol= Integer.parseInt(string.substring(5));
        } else if (Pattern.matches("[0-9]+", string )) {
            icol= Integer.parseInt(string);
        }
        if ( icol>=fieldNames.length ) {
            throw new IllegalArgumentException("bad column parameter: the record parser only expects "+fieldNames.length +" columns");
        }

        return icol;
    }

   /**
     * returns the field index of the name, which can be:<ul>
     *   <li>a column name
     *   <li>an implicit column name "field1"
     *   <li>a column index (0 is the first column)
     *   <li>a negative column index (-1 is the last column)
     * </ul>
     * @param name
     * @param fieldNames the field names for each column.
     * @return the index of the field, or -1 if the column doesn't exist.
     */
    public static int columnIndex( String name, String[] fieldNames ) {
        if ( Pattern.matches( "\\d+", name) ) {
            return Integer.parseInt(name);
        } else if ( Pattern.matches( "-\\d+", name) ) {
            return fieldNames.length + Integer.parseInt(name);
        } else if ( Pattern.matches( "field\\d+", name) ) {
            return Integer.parseInt( name.substring(5) );
        } else {
            int idx= getFieldIndex(name,fieldNames);
            return idx;
        }
    }

    /**
     * parse range strings like "3:6", "3:-5", and "Bx_gsm-Bz_gsm"
     * if the delimiter is colon, then the end is exclusive.  If it is "-",
     * then it is inclusive.  For example,<ul>
     * <li>3:6 -> [3,6]
     * <li>3-5 -> [3,6]
     * </ul>
     * @param o the range string or field names, etc.
     * @param fieldNames the field names for each column.
     * @return the two-element range, where first index is inclusive, second is exclusive.
     * @throws java.lang.NumberFormatException
     */
    public static int[] parseRangeStr(String o, String[] fieldNames ) throws NumberFormatException {
        String s = o;
        int first = 0;
        int last = fieldNames.length;
        if (s.contains(":")) {
            String[] ss = s.split(":",-2);
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],fieldNames);
            }
            if ( ss[1].length() > 0 ) {
                last = columnIndex(ss[1],fieldNames);
            }
        } else if ( s.contains("--") ) {
            int isplit= s.indexOf("--",1);
            if ( isplit > 0 ) {
                first = columnIndex( s.substring(0,isplit),fieldNames);
            }
            if ( isplit < s.length()-2 ) {
                last = 1 + columnIndex( s.substring(isplit+1),fieldNames);
            }
        } else if ( s.contains("-") ) {
            String[] ss = s.split("-",-2);
            if ( ss[0].length() > 0 ) {
                first = columnIndex(ss[0],fieldNames);
            }
            if ( ss[1].length() > 0 ) {
                last = 1 + columnIndex(ss[1],fieldNames);
            }
        }
        return new int[]{first, last};
    }
    
    /**
     * get the delimiter, either a comma or semicolon, by looking at the first
     * few lines of the file.  The pushbackInputStream should be returned at 
     * the zeroth byte.
     * @param thein the PushbackInputStream, which will be at the zeroth byte to start and the zeroth byte when this is done.
     * @return the delimiter.
     * @throws IOException 
     */
    public static char getDelim( PushbackInputStream thein ) throws IOException {
        char delimiter=',';
        BufferedReader read= new BufferedReader(new InputStreamReader(thein));
        String l= read.readLine();
        if ( l!=null ) {
            if ( l.split(";",-2).length > l.split(",",-2).length ) delimiter=';';
            thein.unread( 10 );
            thein.unread(l.getBytes());
            return delimiter;
        } else {
            return ',';
        }
    }
}
