/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.cefdatasource;

import org.das2.datum.Units;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharacterCodingException;
//import java.nio.charset.Charset;
//import java.nio.charset.CharsetDecoder;
import java.text.ParseException;
import org.virbo.dataset.DDataSet;
import org.virbo.dsutil.DataSetBuilder;

/**
 * CEF reader based on Chris Perry's IDL CEF Reader code.
 * @author jbf
 */
public class CefReaderData {

    FieldParser[] parsers;
    //CharsetDecoder charsetDecoder;
    
    // *** Define the delimeters used in the CEF file
    byte eor;
    byte comma = (byte) ',';
    final Units u = Units.us2000;

    public static final int MAX_FIELDS=40000;
    boolean [] doParse= new boolean[MAX_FIELDS]; 
    
    public CefReaderData() {
        //Charset charset = Charset.availableCharsets().get("US-ASCII");
        //charsetDecoder = charset.newDecoder();
        for ( int i=0; i<doParse.length; i++ ) doParse[i]=true;
    }

    public void skipParse( int i ) {
        doParse[i]= false;
    }
    
    public void doParse( int i ) {
        doParse[i]= true;
    }
    
    private int countFields(ByteBuffer work_buffer) {
        int n_fields = 1;
        for (int k = 0;; k++) {

            if (work_buffer.get(k) == comma) {
                n_fields++;
            } else if (work_buffer.get(k) == eor) {
                break;
            }
        }
        return n_fields;
    }

    /**
     * returns the position of the last end-of-record, or -1 if one is not found.
     * @param work_buffer
     * @return the position of the last end-of-record, or -1 if one is not found.
     */
    private int getLastEor(ByteBuffer work_buffer) {
        int pos_eor;
        for (pos_eor = work_buffer.limit() - 1; pos_eor >= 0; pos_eor--) {
            if (work_buffer.get(pos_eor) == eor) {
                break;
            }
        }
        return pos_eor;
    }

    private void parseRecord(ByteBuffer bbuf, int irec, int[] fieldDelim, DataSetBuilder builder) throws CharacterCodingException, ParseException {
        for (int i = 0; i < fieldDelim.length - 1; i++) {
            if ( parsers[i]!=null ) {
                builder.putValue(irec, i, parsers[i].parseField(bbuf, fieldDelim[i], fieldDelim[i + 1] - fieldDelim[i] - 1));
            }
        }
    }

    private void removeComments(ByteBuffer work_buffer, int work_size) {
        // remove comments by replacing them with whitespace.  When the 
        // record delimiter is not EOL, replace EOLs with whitespace.

        byte comment = (byte) '!';
        byte eol = (byte) 10;

        int pos_comment;
        for (pos_comment = 0; pos_comment < work_size; pos_comment++) {
            byte ch = work_buffer.get(pos_comment);
            if (ch == comment) {
                int j = pos_comment;
                while (j < work_size && work_buffer.get(j) != eol) {
                    work_buffer.put(j, (byte) 32);
                    j = j + 1;
                }
                work_buffer.put(j, (byte) 32);
                pos_comment = j;
            } else if (ch == eol && eor != eol) {
                work_buffer.put(pos_comment, (byte) 32);
            } else if (ch == eor) {
            //work_buffer.put( pos_comment, comma ); //leave them in in this parser
            }
        }
    }

    /**
     * do an initial split of the first record, and use this information to 
     * identify parsers for each field.  Note this just looks at the length of
     * the time tags field.
     * @param work_buffer
     * @param work_size
     * @param parsers
     */
    private void getParsers(ByteBuffer work_buffer, int work_size, FieldParser[] parsers) {
        int n_fields = parsers.length;
        int[] fieldDelim = new int[n_fields + 1]; // +1 is for record delim position
        splitRecord(work_buffer, 0, work_size, fieldDelim);
        for (int i = 0; i < n_fields; i++) {
            if ( this.doParse[i] ) parsers[i] = new DoubleFieldParser(); else parsers[i]=null;
        }

        final FieldParser timeParser;

        if (fieldDelim[1] > 27) {
            timeParser = new IsoTimeParser(6);
        } else {
            timeParser = new IsoTimeParser(3);
        }

        parsers[0] = timeParser;

    }

    /**
     * scan the record to find the field delimiters and the end of record.  Field
     * delimiter positions are inserted into fieldDelim array.
     * @param work_buffer
     * @param irec record counter, the number of records read in.  This is useful for debugging, and is 
     *    needed by the DataSetBuilder.
     * @param recPos the position of the beginning of the record.
     * @param work_size the limit of the useable data in work_buffer.  Processing will stop when the 
     *    record deliter is encountered, or when this point is reached.
     * @param fieldDelim used to return the position of the delimiters.
     * @param parsers 
     * @return
     */
    private int splitRecord(ByteBuffer work_buffer, int recPos, int work_size, int[] fieldDelim) {
        int ifield = 0;

        fieldDelim[0] = recPos;
        while (recPos < work_size) {

            if (work_buffer.get(recPos) == eor) {
                break;
            }
            if (work_buffer.get(recPos) == comma) {
                ifield++;
                fieldDelim[ifield] = recPos + 1;
            }
            recPos++;
        }
        if (fieldDelim[0] > fieldDelim[1]) {
            System.err.println("here232");
        }
        return recPos;

    }

    public DDataSet cefReadData(ReadableByteChannel lun, Cef cef) throws IOException, ParseException {

        System.err.println("Reading data records, please wait...");

        // *** Define the read buffer that we'll use to pull in chunks
        // *** of the file. This is a trade off between memory footprint
        // *** and speed. Note that the working buffer is twice this size.
        // *** The buffer size should be much bigger than the typical
        // *** record size or perfromance will be worse than just reading
        // *** a records at a time.

        eor = (byte) cef.eor;

        int buffer_size = 200000;

        byte[] work_buf = new byte[2 * buffer_size];
        ByteBuffer read_buffer = ByteBuffer.wrap(new byte[buffer_size]);
        ByteBuffer work_buffer = ByteBuffer.wrap(work_buf);

        /**
         * useable limit in work_buffer.  This is the position of the end of the
         * last complete record
         */
        int work_size = 0;

        Cef cef1 = new Cef();

        cef1.nparam = 1;        //*** set nparam to 1 when it is added to the parameter data


        // *** Set the processing state flag (1=first record, 2=subsequent records, 0 = end of file )
        int trflag = 1;     //*** set to 0 if no more data required in requested time range
        int n_fields;     //*** number of fields per record

        boolean eof = false;

        int irec = 0;

        DataSetBuilder builder = null;

        //long totalBytesRead = 0;

        // *** Keep reading until we reach the end of the file.
        while (!eof && trflag > 0) {

            //*** read the next chunk of the file
            //*** catch errors to avoid warning message if we read past end of file
            read_buffer.rewind();

            int read_size = lun.read(read_buffer);

            if (read_size == -1) {
                eof = true;
                break;
            }

            //totalBytesRead += read_size;

            //*** transfer this onto the end of the work buffer and update size of work buffer
            if (read_size > 0) {
                read_buffer.flip();
                work_buffer.put(read_buffer);
                work_buffer.flip();
            }
            work_size = work_size + read_size;

            //*** look for delimeters, EOR, comments, EOL etc
            int pos_eor = getLastEor(work_buffer);

            if (pos_eor > -1) {
                work_size = pos_eor;
            } else {
                // go back and read some more
                break;
            }

            removeComments(work_buffer, work_size);
            pos_eor = getLastEor(work_buffer);
            if ( pos_eor > -1 ) {
                work_size = pos_eor;
            } else {
                throw new IllegalArgumentException("the entire work buffer was a comment, this is not handled.");
            }
            
            // count the number of fields before the first record
            n_fields = countFields(work_buffer);


            if (builder == null) {
                builder = new DataSetBuilder(2, 100, n_fields, 1);
                parsers = new FieldParser[n_fields];

                getParsers(work_buffer, work_size, parsers);
            }

            int[] fieldDelim = new int[n_fields + 1]; // +1 is for record delim position
            fieldDelim[0] = 0;
            int pos = 0;

            while (pos < work_size) {
                int recPos = pos;

                recPos = splitRecord(work_buffer, recPos, work_size, fieldDelim);

                if (recPos <= work_size) {
                    fieldDelim[n_fields] = recPos + 1;
                    parseRecord(work_buffer, irec, fieldDelim, builder);
                    pos = recPos + 1;
                    work_buffer.position(pos);
                    builder.nextRecord();
                    irec = irec + 1;
                } else {
                    break;
                }
            } // while ( pos < work_size )

            //*** we want to keep the part of the buffer not yet processed

            work_buffer.compact();
            work_size = work_buffer.position();

            //*** keep going until there is no more file to read
            if (read_size < buffer_size) {
                //flag = 0;
            }

        } // while

        //*** Release memory used by the work and read buffers
        //work_buffer = null;
        //read_buffer = null;


        System.err.println("Reading of data complete");

        return builder==null ? null : builder.getDataSet();
    }
}
