/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.cefdatasource;

import java.nio.ByteBuffer;

/**
 * code borrowed from Javalution
 * @author jbf
 */
public class DoubleParser {

    private static boolean match(String str, ByteBuffer csq, int start, int length) {
        for (int i = 0; i < str.length(); i++) {
            if ((start + i >= length) || csq.get(start + i) != str.charAt(i)) {
                return false;
            }
        }
        return true;
    }
    

    public static double parseDoubleByteArray( ByteBuffer csq, final int start, final int length) throws NumberFormatException {
        // Parsing block identical for all CharSequences.

        int i = start;
        int fin= start+length;
        char c = (char)csq.get(i);

        while ( Character.isWhitespace(c) && i<fin ) c= (char)csq.get(++i);
        
        // Checks for NaN.
        if ((c == 'N') && match("NaN", csq, i, 3 )) {
            return Double.NaN;
        }

// Reads sign.
        boolean isNegative = (c == '-');
        if ((isNegative || (c == '+')) && (++i < fin )) {
            c = (char)csq.get(i);
        }

// Checks for Infinity.
        if ((c == 'I') && match("Infinity", csq, i, 8)) {

            return isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }

// Reads decimal and fraction (both merged to a long).
        long decimal = 0;
        int decimalPoint = -1;
        while (true) {
            int digit = c - '0';
            if ((digit >= 0) && (digit < 10)) {
                long tmp = decimal * 10 + digit;
                if (tmp < decimal) {
                    throw new NumberFormatException("Too many digits - Overflow");
                }

                decimal = tmp;
            } else if ((c == '.') && (decimalPoint < 0)) {
                decimalPoint = i;
            } else {
                break; // Done.
            }

            if (++i >= fin) {
                break;
            }

            c = (char)csq.get(i);
        }

        if (isNegative) {
            decimal = -decimal;
        }

        int fractionLength = (decimalPoint >= 0) ? i - decimalPoint - 1 : 0;

        // Reads exponent.
        int exp = 0;
        if ((i < fin) && ((c == 'E') || (c == 'e'))) {
            c = (char)csq.get(++i);
            boolean isNegativeExp = (c == '-');
            if ((isNegativeExp || (c == '+')) && (++i < fin)) {
                c = (char)csq.get(i);
            }

            while (true) {
                int digit = c - '0';
                if ((digit >= 0) && (digit < 10)) {
                    int tmp = exp * 10 + digit;
                    if (tmp < exp) {
                        throw new NumberFormatException("Exponent Overflow");
                    }

                    exp = tmp;
                } else {
                    break; // Done.
                }

                if (++i >= fin) {
                    break;
                }

                c = (char)csq.get(i);
            }

            if (isNegativeExp) {
                exp = -exp;
            }

        }

        return decimal * Math.pow( 10, exp - fractionLength );
    }

}
