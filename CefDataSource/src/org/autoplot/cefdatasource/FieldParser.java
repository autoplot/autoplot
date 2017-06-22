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
public interface FieldParser {
    double parseField( ByteBuffer buf, int offset, int length ) throws java.text.ParseException;
}
