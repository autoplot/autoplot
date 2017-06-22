/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.cefdatasource;

import java.nio.ByteBuffer;

/**
 *
 * @author jbf
 */
public class DoubleFieldParser implements FieldParser {
    public double parseField(ByteBuffer bbuf, int offset, int length) {
        return DoubleParser.parseDoubleByteArray(bbuf, offset, length);
    }

}
