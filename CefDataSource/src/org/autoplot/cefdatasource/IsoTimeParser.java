/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.cefdatasource;

import java.nio.ByteBuffer;
import java.text.ParseException;

/**
 * Parser for iso times, that improves performance by caching the 
 * year, month, day offset.
 * @author jbf
 */
public class IsoTimeParser implements FieldParser {

    int decimalPlaces;
    int microsMult;
    byte[] cacheTag = new byte[10];
    long cacheMicros;

    public IsoTimeParser(int decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
        this.microsMult = (int) Math.pow(10, 6 - decimalPlaces);
    }

    private int readPositiveInt(byte[] buf, int offset, int length) {
        int result = 0;
        for (int i = 0; i < length; i++) {
            result = result * 10 + (buf[offset + i] - 48);
        }
        return result;
    }

    private long microsSince2000(int year, int month, int day) {
        int jd = 367 * year - 7 * (year + (month + 9) / 12) / 4 -
                3 * ((year + (month - 9) / 7) / 100 + 1) / 4 +
                275 * month / 9 + day + 1721029 - 2451545;
        return jd * 86400000000L;
    }

    public synchronized double parseField(ByteBuffer bbuf, int offset, int length) throws ParseException {
        byte[] buf = bbuf.array();
        //String check= new String(buf,offset,length);
        boolean useCache = true;
        
        // trim off whitespace at beginning
        int fin= offset+length;
        while ( offset<fin && Character.isWhitespace( buf[offset] )  ) offset++;
        
        if ( offset+20+decimalPlaces>fin ) {
            throw new ParseException( new String(buf,offset,length), 0 );
        }
        
        for (int i = 0; useCache &&
                i < 10; i++) {
            if (buf[offset + i] != cacheTag[i]) {
                useCache = false;
            }
        }
        if (!useCache) {
            int year = readPositiveInt(buf, offset, 4);
            int month = readPositiveInt(buf, offset + 5, 2);
            int day = readPositiveInt(buf, offset + 8, 2);
            for (int i = 0; i < 10; i++) {
                cacheTag[i] = buf[offset + i];
            }
            cacheMicros =
                    microsSince2000(year, month, day);
        }

        int hour = readPositiveInt(buf, offset + 11, 2);
        int minute = readPositiveInt(buf, offset + 14, 2);
        int second = readPositiveInt(buf, offset + 17, 2);
        int micros = microsMult * readPositiveInt(buf, offset + 20, decimalPlaces);
        long microsOffset = hour * 3600000000L + minute * 60000000L + second * 1000000L + micros;
        //Datum checkd = Units.us2000.createDatum(cacheMicros + microsOffset);
        return cacheMicros + microsOffset;

    }
}
