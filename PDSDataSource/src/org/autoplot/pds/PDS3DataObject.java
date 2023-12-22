
package org.autoplot.pds;

/**
 * 
 * @author jbf
 */
public class PDS3DataObject {
    String name;    
    String uri;
    String dataType;
    int startByte;
    int items;
    int itemBytes;
    int bytes;
    double validMinimum;
    double validMaximum;
    double missingConstant;
    String unit;
    String description;
      /*      
            <START_BYTE>86365</START_BYTE>
            <ITEMS>3</ITEMS>
            <ITEM_BYTES>4</ITEM_BYTES>
            <BYTES>12</BYTES>
            <VALID_MINIMUM>-1600000.0</VALID_MINIMUM>
            <VALID_MAXIMUM>1600000.0</VALID_MAXIMUM>
            <MISSING_CONSTANT>9990000.0</MISSING_CONSTANT>
            <UNIT>nT</UNIT>
            <DESCRIPTION>MAG vector in J
                    */
}
