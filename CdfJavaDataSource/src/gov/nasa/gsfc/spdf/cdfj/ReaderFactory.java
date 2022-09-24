package gov.nasa.gsfc.spdf.cdfj;
import java.nio.*;
import java.nio.channels.*;
import java.io.*;
import java.net.*;
/**
 * ReaderFactory creates an instance of CDFReader from a CDF source.
 * Uses array backed ByteBuffer for CDFReader.
 * The source CDF can  be a file,  or a URL.
 */
public final class ReaderFactory {
    /**
     * creates  CDFReader object from a file using array backed ByteBuffer.
     */
    static int preamble = 3000;
    
    /**
     * Return a reader for the CDF file.  This looks at the CDF file internals
     * to calculate the version and returns an implementation.
     * @param fname
     * @return a reader for the CDF file
     * @throws CDFException.ReaderError if there is an issue while reading the file
     * @throws IllegalArgumentException if the file is not a CDF file
     */
    public static CDFReader getReader(String fname) throws
        CDFException.ReaderError {
        return getReader( fname, false );
    }

    /**
     * creates  CDFReader object from a URL using array backed ByteBuffer.
     */
    public static CDFReader getReader(URL url) throws
        CDFException.ReaderError {
        CDFImpl cdf = null;
        try {
            URLConnection con = new CDFUrl(url).openConnection();
            int remaining = con.getContentLength();
            InputStream is = con.getInputStream();
            byte [] ba = new byte[remaining];
            int offset = 0;
            while (remaining > 0) {
                int got = is.read(ba, offset, remaining);
                offset += got;
                remaining -= got;
            }
            ((HttpURLConnection)con).disconnect();
            ByteBuffer buf = ByteBuffer.wrap(ba);
            cdf = CDFFactory.getVersion(buf);
            if ( cdf==null ) {
                throw new IllegalArgumentException("File is not a CDF-format file.");
            }
        } catch (Throwable th) {
            throw new CDFException.ReaderError("I/O Error reading " + url,th);
        }
        CDFReader rdr = new CDFReader();
        rdr.setImpl(cdf);
        final String _url = url.toString();
        cdf.setSource(new CDFFactory.CDFSource() {
            public String getName() {return _url;};
            public boolean isFile() {return false;};
        });
        return rdr;
    }

    /**
     * returns an implementation for reading the CDF, or null if the file
     * does not have magic numbers indicating it is a CDF.
     * @param buf the buffer containing the CDF data.
     * @param ch the file channel for the data.
     * @return the implementation for reading the CDF, or null.
     * @throws Throwable 
     */
    static CDFImpl getVersion(ByteBuffer buf, FileChannel ch) throws
        Throwable {
        LongBuffer lbuf = buf.asLongBuffer();
        long magic = lbuf.get();
        if (magic == CDFFactory.CDF3_MAGIC) {
            return new CDF3Impl(buf, ch);
        }
        if (magic == CDFFactory.CDF3_COMPRESSED_MAGIC) {
            ByteBuffer mbuf = CDFFactory.uncompressed(buf, 3);
            return new CDF3Impl(mbuf);
        }
        if (magic == CDFFactory.CDF2_MAGIC_DOT5) {
            int release = buf.getInt(24);
            return new CDF2Impl(buf, release, ch);
        } else {
            ShortBuffer sbuf = buf.asShortBuffer();
            if (sbuf.get() == (short)0xcdf2) {
                if (sbuf.get() == (short)0x6002) {
                    short x = sbuf.get();
                    if (x == 0) {
                        if (sbuf.get() == -1) {
                            return new CDF2Impl(buf, 6, ch);
                        }
                    } else {
                        if ((x == (short)0xcccc) && (sbuf.get() == 1)) {
                            // is compressed - positioned at CCR
                            ByteBuffer mbuf = CDFFactory.uncompressed(buf, 2);
                            return new CDF2Impl(mbuf, 6, ch);
                        }
                    }
                        
                }
            }
        }
        return null;
    }
    
    /**
     * get a reader, forcing that all is read into memory in one heap byte array.
     * This has the limitation that the file cannot be greater than 2GB in length.
     * @param fname the file name.
     * @param map read the file into a memory block on the heap
     * @return a CDFReader
     * @throws gov.nasa.gsfc.spdf.cdfj.CDFException.ReaderError 
     */
    public static CDFReader getReader(String fname, boolean map) throws
        CDFException.ReaderError {
        CDFImpl cdf = null;
        File file = new File(fname);
        try {
            if ( map ) {
                int len = (int)file.length();
                byte[] ba = new byte[len];
                int rem = len;
                try (FileInputStream fis = new FileInputStream(file)) {
                    int n = 0;
                    while (rem > 0) {
                        len = fis.read(ba, n, rem);
                        n += len;
                        rem -= len;
                    }
                }
                cdf = CDFFactory.getCDF(ba);
            } else {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                long len = raf.length(); 
                if (len > preamble) len = preamble;
                byte[] ba = new byte[(int)len];
                raf.readFully(ba);
                ByteBuffer buf = ByteBuffer.wrap(ba);                
                cdf = getVersion(buf, raf.getChannel());
            }
            if ( cdf==null ) {
                throw new IllegalArgumentException("File is not a CDF-format file: "+fname);
            }
        } catch (Throwable th) {
            throw new CDFException.ReaderError("I/O Error reading " + fname);
        }
        final String _fname = file.getPath();
        cdf.setSource(new CDFFactory.CDFSource() {
            public String getName() {return _fname;};
            public boolean isFile() {return true;};
        });
        CDFReader rdr = new CDFReader();
        rdr.setImpl(cdf);
        return rdr;
    }
}
