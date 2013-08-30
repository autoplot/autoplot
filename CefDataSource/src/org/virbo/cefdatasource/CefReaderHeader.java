/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jbf
 */
public class CefReaderHeader {

    enum State {

        TOP, END, DATA_READ, GLOBAL, PARAM
    }

    class Record {

        String data;
    }

    class KeyValue {

        String key;
        String[] val;
    }

    class GlobalStruct {

        //String name;
        //List<String> entries;
        String valueType;
    }

    class ParamStruct {

        String name;
        int[] sizes;
        int recType;
        int[] cefFieldPos;  // start, end inclusive
        Map<String, Object> entries = new LinkedHashMap<String, Object>();
    }
    private byte eol = 10;

    private boolean cefReadHeadRec(ReadableByteChannel c, Record record) throws IOException {


        boolean status = false;     // *** Status flag, set to 1 if complete record found
        boolean readFlag = true;     // *** used to flag multi-line records
        StringBuilder recordBuf = new StringBuilder();

        boolean eofReached = false;

        byte[] buf = new byte[1];
        ByteBuffer b1 = ByteBuffer.wrap(buf);

        //*** Keep reading unit until got complete entry or end of file ***
        while (readFlag && !eofReached) {

            StringBuilder sbuf = new StringBuilder();

            //*** read next record ***
            while (true) {
                b1.rewind();
                if ( c.read(b1)==-1 ) {
                    eofReached= true;
                    break;
                }
                //c.read(b1);
                sbuf.append((char) buf[0]);
                if (buf[0] == eol) {
                    break;
                }
            }
            String tempRecord = sbuf.toString();

            if (tempRecord == null) {
                eofReached = true;
                break;
            }
            tempRecord = tempRecord.trim();

            //*** skip comment lines ***
            if (tempRecord.length() > 0 && tempRecord.charAt(0) == '!') {
            //; PRINT, tempRecord
            } else if (tempRecord.length() > 0 && tempRecord.charAt(tempRecord.length() - 1) == '\\') {
                recordBuf.append( tempRecord.substring(0,tempRecord.length() - 1) );
            } else {
                recordBuf.append(tempRecord);
                // *** if not blank then finish read  of this record ***
                if (recordBuf.length() > 0) {
                    readFlag = false;
                    status = true;
                    record.data = recordBuf.toString();
                } else {
                    record.data = "";
                }
            }

        } // WHILE

        return status;
    }

    private boolean cefSplitRec(String record, KeyValue kv) {

        boolean status = false;     //*** Set default status

        // *** look for comment
        int pos = record.lastIndexOf('!');
        if (pos > -1) {
            record = record.substring(0, pos);
        }

        // *** look for key/value delimiter ***
        pos = record.indexOf('=');
        if (pos > -1) {
            status = true;

            //*** Extract the key ***
            kv.key = record.substring(0, pos).trim().toUpperCase();

            //*** Extract the value ***
            String val = record.substring(pos + 1).trim();

            //*** Split value into separate array elements
            //*** Handle quoted string elements

            if (val.charAt(0) == '"') {
                //STRSPLIT(STRMID(val,1,STRLEN(val)-2), $
                //    '"[ '+STRING(9B)+']*,[ '+STRING(9B)+']*"',/REGEX, /EXTRACT)
                String tab = new String(new byte[]{9});
                kv.val = val.substring(1, val.length() - 1).split("\"[ " + tab + "]*,[ " + tab + "]*\"");
            } else {
                kv.val = val.split(",");
                for (int i = 0; i < kv.val.length; i++) {
                    kv.val[i] = kv.val[i].trim();
                }
            }
        }

        return status;
    }

    public Cef read(ReadableByteChannel c) throws IOException {

        Cef cef = new Cef();

        State state = State.TOP;

        int pdata = 0; // data index

        int eCount = 0;
        List<String> elements = null;
        int[] data_idx;

        GlobalStruct gStru = null;
        String gName = null;

        ParamStruct pStru = null;
        String pName = null;

        Record record = new Record();
        KeyValue kv = new KeyValue();

        //int recordNumber = 0;

        // *** Keep reading until end of header information or no more records ***
        while (state != State.DATA_READ && state != State.END) {

            //*** Try to read header record
            if (!cefReadHeadRec(c, record)) {
                break;
            }
            //recordNumber++;

            if ( record.data.length()>2 && ( record.data.startsWith("19") || record.data.startsWith("20") ) ) { //CFA has a bug that they don't output the "DATA_UNTIL" delimiter.
                // C1_CP_WHI_ACTIVE__20020221_000000_20020221_050000_V120201.cef doesn't have delimiter, so trigger on a date.
                break;
            }

            //*** Get the keyword/value(s) for this record            
            if (cefSplitRec(record.data, kv)) {

                String key = kv.key.intern();
                String[] value = kv.val;

                //*** Use the parser state to check what we are looking for
                switch (state) {

                    case TOP:
                         {

                            //*** Use the keyword to determine the action
                            if (key.equals("START_META")) {     //*** New global metadata item ***
                                state = State.GLOBAL;
                                gStru = new GlobalStruct();
                                gName = value[0];
                                //gStru.name = value[0];
                                gStru.valueType = "CHAR";
                                eCount = 0;
                            } else if (key.equals("START_VARIABLE")) {     //*** New parameter ***
                                state = State.PARAM;
                                pName = value[0];
                                pStru = new ParamStruct();
                                pStru.name = value[0];
                                pStru.recType = 1;
                            } else if (key.equals("INCLUDE")) {
                                throw new IllegalArgumentException("not yet supported");
                            //if ( !value[0].equals(readFile) ) {
                            //   param = cef_read( findinclude(value[0]), cef );
                            // }
                            } else if (key.equals("DATA_UNTIL")) {     //*** Start of data ***
                                state = State.DATA_READ;
                                //cef.dataUntil = value[0];
                            } //*** Special CEF defined items at the top level ***
                            else if (key.equals("FILE_NAME")) {
                                //cef.fileName = value[0];
                            } else if (key.equals("FILE_FORMAT_VERSION")) {
                                //cef.fileFormatVersion = value[0];
                            } else if (key.equals("END_OF_RECORD_MARKER")) {
                                cef.eor = (byte) value[0].charAt(0);
                            } else {
                                throw new IllegalArgumentException("Unsupported key " + key);
                            }

                        }
                        break;


                    case GLOBAL:
                         {        //*** Global metadata handling

                            if (value.length > 1) {
                                throw new IllegalArgumentException("Global entry not allowed multiple values per entry : " + gName);
                            }

                            if (key.equals("END_META")) {
                                state = State.TOP;
                                if (!kv.val[0].equals(gName)) {
                                    throw new IllegalArgumentException("END_VARIABLE expected " + gName + "  got " + kv.val[0]);
                                }
                                //gStru.entries = elements;

                                cef.nglobal = cef.nglobal + 1;
                                if (gStru.valueType.equals("CHAR")) {
                                    cef.globals.put(gName, gStru);
                                } else {
                                    cef.globals.put(gName, gStru);
                                }
                            } else if (key.equals("VALUE_TYPE")) {

                                //*** WARNING: In theory CEF allows a different VALUE_TYPE for each entry
                                //*** this is a 'feature' from CDF but I can't think of a situation where
                                //*** it is useful. This feature is not currently supported by this
                                //*** software and so we just assign a type based on the last specification
                                //*** of the VALUE_TYPE.

                                gStru.valueType = value[0];

                            } else if (key.equals("ENTRY")) {
                                //*** if this is the second entry then must be multi entry global ***
                                if (eCount == 0) {
                                    elements = new ArrayList();
                                }
                                elements.add(value[0]);

                                eCount = eCount + 1;

                            } else {
                                throw new IllegalArgumentException("Unsupported global key " + key);

                            }
                        }
                        break;

                    case PARAM:        //*** Parameter description handling
                         {
                            if (key.equals("END_VARIABLE")) {
                                //*** Set some defaults if not provided in the file
                                int[] sizes = pStru.sizes;
                                if (sizes == null) {
                                    pStru.sizes = new int[]{1};
                                }

                                if (pStru.recType == 0) {
                                    data_idx = new int[]{-1, -1};
                                } else {
                                    sizes = pStru.sizes;
                                    int n = pStru.sizes[0];
                                    for (int i = 1; i < sizes.length; i++) {
                                        n = n * sizes[i];
                                    }
                                    data_idx = new int[]{pdata, pdata + n - 1};
                                    pdata = pdata + n;
                                }
                                pStru.cefFieldPos = data_idx;
                                //*** Change parser state
                                state = State.TOP;
                                //*** Check this is the end of the correct parameter!
                                if (!value[0].equals(pStru.name)) {
                                    throw new IllegalArgumentException("END_VARIABLE expected " + pName + "  got " + value[0]);
                                }

                                //*** Update the number of parameters
                                cef.nparam = cef.nparam + 1;

                                cef.parameters.put(pName, pStru);

                            } else {

                                if (key.equals("DATA")) {
                                    pStru.entries.put(key, value);
                                    pStru.recType = 0;   //*** Flag non-record varying data
                                //********************************
                                //;At the moment we just add non-record varying data as string array
                                //;we should really check SIZES and VALUE_TYPE and reform and retype
                                //;data as we do for the real data fields. Something for the next release?
                                //********************************
                                } else if (key.equals("SIZES")) {
                                    if (value.length > 1) {
                                        String[] rev = new String[value.length];
                                        for (int k = 0; k < value.length; k++) {
                                            rev[k] = value[value.length - k - 1];
                                        }
                                        value= rev;
                                    }
                                    pStru.entries.put(key, value);
                                    
                                    int[] isizes= new int[value.length];
                                    for ( int i=0; i<value.length; i++ ) {
                                        isizes[i]= Integer.parseInt(value[i]);
                                    }
                                    pStru.sizes= isizes;

                                } else {

                                    pStru.entries.put(key, value[0]);

                                }

                            }
                        } // case PARAM
                        break;
                } // switch
            } else {
                throw new IllegalArgumentException("Bad record?  " + record.data);
            }
        } // while


        //*** Return the result
        return cef;
    }
}
