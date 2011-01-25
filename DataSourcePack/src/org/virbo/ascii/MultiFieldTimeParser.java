/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.ascii;

import java.text.ParseException;
import org.das2.datum.Units;
import org.das2.datum.TimeParser;
import org.virbo.dsutil.AsciiParser;

/**
 * Parse the record by recombining the separated fields, then parsing
 * the combined string.
 *
 * 2010/03/11: Indeterminate field length is used when one field is in a record.
 * 2010/03/11: The last field, if just one digit type (%S), can contain fractional part.
 *
 * @author jbf
 */
public class MultiFieldTimeParser implements AsciiParser.FieldParser {

    StringBuilder agg;
    String[] timeFormats;
    int firstColumn;
    int lastColumn;
    TimeParser parser;
    Units units;
    String lastDigitFormat;
    boolean[] isNumber;

    private boolean multiFieldAdjacent( String spec ) {
        return spec.length()>3 && spec.charAt(2)=='%' && spec.charAt(3)!='%';
    }

    private int fieldCount( String spec ) {
        int count=0;
        for ( int i=0; i<spec.length(); i++ ) {
            if ( spec.charAt(i)=='%' && spec.charAt(i+1)!='%' ) count++;
        }
        return count;
    }

    private boolean isNumber( String spec ) {
        if ( spec.equals("%{ignore}") ) {
            return false;
        } else if ( spec.equals("%b") ) { //TODO: %-1{b}, etc.
            return false;
        } else {
            return fieldCount( spec )==1;
        }
    }

    MultiFieldTimeParser( int firstColumn, String[] timeFormats, TimeParser parser, Units units ) {
        this.firstColumn= firstColumn;
        this.timeFormats= timeFormats;
        this.lastColumn= firstColumn + timeFormats.length - 1;
        String timeFormat;

        isNumber= new boolean[timeFormats.length];
        
        isNumber[0]= isNumber( timeFormats[0] );
        if ( timeFormats[0].length()>1 && timeFormats[0].charAt(1)!='(' ) {
            if ( multiFieldAdjacent(timeFormats[0]) ) {
                timeFormat= timeFormats[0]; // to have indeterminate length for first field, we need terminator.
            } else {
                timeFormat= "%-1" + timeFormats[0].substring(1); //kludge for whitespace
            }
        } else {
            timeFormat= timeFormats[0];
        }

        for ( int i=1; i<timeFormats.length-1; i++ ) {
            isNumber[i]= isNumber( timeFormats[i] );
            if ( multiFieldAdjacent(timeFormats[i]) ) {
                timeFormat= timeFormat + " "+ timeFormats[i]; // to have indeterminate length for first field, we need terminator.
            } else {
                timeFormat= timeFormat + " "+"%-1" + timeFormats[i].substring(1); //kludge for whitespace
            }
        }

        if ( timeFormats.length>1 && timeFormats[timeFormats.length-1].length()<3 ) {
            lastDigitFormat= timeFormats[timeFormats.length-1];
            isNumber[timeFormats.length-1]= true;
        } else {
            lastDigitFormat=null; // we can't use this feature
            timeFormat= timeFormat + " "+ timeFormats[timeFormats.length-1]; // to have indeterminate length for first field, we need terminator.
            isNumber[timeFormats.length-1]= false;
        }

        this.parser= TimeParser.create(timeFormat);
        //this.parser= parser;
        this.units= units;
    }

    public double parseField(String field, int columnIndex) throws ParseException {
        if ( isNumber[columnIndex-firstColumn] ) {
            Double.parseDouble(field); // attempt to parse the number
        }
        if ( columnIndex==firstColumn ) {
            agg= new StringBuilder(field);
            return 0;
        } else if ( columnIndex<lastColumn ) {
            if ( agg==null ) throw new ParseException("another field was not parseable",0);
            agg= agg.append(" ").append( field ); 
            return 0;
        } else {
            if ( agg==null ) throw new ParseException("another field was not parseable",0);
            if ( lastDigitFormat==null ) {
                agg= agg.append(" ").append( field );
                return parser.parse(agg.toString()).getTime(units);
            } else {
                parser.parse(agg.toString());
                parser.setDigit( lastDigitFormat, Double.parseDouble(field) );
                return parser.getTime(units);
            }
        }
    }

}
