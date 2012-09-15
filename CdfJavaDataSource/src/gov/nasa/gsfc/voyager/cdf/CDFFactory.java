package gov.nasa.gsfc.voyager.cdf;
import java.nio.*;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.logging.Logger;
import java.util.zip.*;
/**
 * CDFFactory creates an object which implements methods of interface
 * CDF to provide access to the contents of a CDF. The source CDF can
 * be a file, a byte array, or a URL.
 */
public class CDFFactory {

    private static final Logger logger= Logger.getLogger("apdss");
    
    public static final long CDF3_MAGIC =((long)0xcdf3 << 48) +
        ((long)0x0001 << 32) + 0x0000ffff;
    public static final long CDF3_COMPRESSED_MAGIC =((long)0xcdf3 << 48) +
        ((long)0x0001 << 32) + 0xcccc0001;
    public static final long CDF2_MAGIC =((long)0xcdf2 << 48) +
        ((long)0x0001 << 32) + 0x0000ffff;
    public static final long CDF2_MAGIC_DOT5 = ((long)0x0000ffff << 32) +
         0x0000ffff;

    private CDFFactory() {
    }

    /**
     * creates  CDF object from a byte array.
     */
    public static CDF getCDF(byte [] ba) throws Throwable {
        ByteBuffer buf = ByteBuffer.wrap(ba);
        return getVersion(buf);
    }

    public static CDF getCDF(ByteBuffer buf) throws Throwable {
        return getVersion(buf);
    }

    private static CDF getVersion(ByteBuffer buf) throws Throwable {
        LongBuffer lbuf = buf.asLongBuffer();
        long magic = lbuf.get();
        if (magic == CDF3_MAGIC) {
            return new CDF3Impl(buf);
        }
        if (magic == CDF3_COMPRESSED_MAGIC) {
            ByteBuffer mbuf = uncompressed(buf, 3);
            return new CDF3Impl(mbuf);
        }
        if (magic == CDF2_MAGIC_DOT5) {
            int release = buf.getInt(24);
            return new CDF2Impl(buf, release);
        } else {
            ShortBuffer sbuf = buf.asShortBuffer();
            if (sbuf.get() == (short)0xcdf2) {
                if (sbuf.get() == (short)0x6002) {
                    short x = sbuf.get();
                    if (x == 0) {
                        if (sbuf.get() == -1) {
                            return new CDF2Impl(buf, 6);
                        }
                    } else {
                        if ((x == (short)0xcccc) && (sbuf.get() == 1)) {
                            // is compressed - positioned at CCR
                            ByteBuffer mbuf = uncompressed(buf, 2);
                            return new CDF2Impl(mbuf, 6);
                        }
                    }
                        
                }
            }
        }
        return null;
    }
    /**
     * creates  CDF object from a file.
     */
    public static CDF getCDF(String fname) throws Throwable {
        File file = new File(fname);
        if ( file.length()>=Integer.MAX_VALUE ) {
            throw new IllegalArgumentException("file is too large: "+fname+", it's length is > "+Integer.MAX_VALUE );
        }
        FileInputStream fis = new FileInputStream(file);
        FileChannel ch = fis.getChannel();
        ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        return getVersion(buf);
    }
    /**
     * creates  CDF object from a URL.
     */
    public static CDF getCDF(URL url) throws Throwable {
        URLConnection con = url.openConnection();
        int remaining = con.getContentLength();
        InputStream is = con.getInputStream();
        byte [] ba = new byte[remaining];
        int offset = 0;
        while (remaining > 0) {
            int got = is.read(ba, offset, remaining);
            offset += got;
            remaining -= got;
        }
        return getCDF(ba);
    }
    static ByteBuffer uncompressed(ByteBuffer buf, int version) {
        int DATA_OFFSET = 8 + 20;
        if (version == 3) DATA_OFFSET = 8 + 32;
        byte[] ba;
        int offset;
        int len = buf.getInt(8) - 20;
        if (version == 3) len = (int)(buf.getLong(8) - 32);
        int ulen = buf.getInt(8 + 12);
        if (version == 3) ulen = (int)(buf.getLong(8 + 20));
        byte [] udata = new byte[ulen + 8];
        buf.get(udata, 0, 8); // copy the magic words
        if (!buf.hasArray()) { // read data into byte array
            ba = new byte[len];
            buf.position(DATA_OFFSET);
            buf.get(ba);
            offset = 0;
        } else {
            ba = buf.array();
            offset = DATA_OFFSET;
        }
        int n = 0;
        try {
            ByteArrayInputStream bais = 
                new ByteArrayInputStream(ba, offset, len);
            GZIPInputStream gz = new GZIPInputStream(bais);
            int toRead = udata.length - 8;
            int off = 8;
            while (toRead > 0) {
                n = gz.read(udata, off, toRead);
                if (n == -1) break;
                off += n;
                toRead -= n;
            }
        } catch (IOException ex) {
            logger.fine(ex.toString());
            return null;
        }
        if (n < 0) return null;
        return ByteBuffer.wrap(udata);
    }
}
