package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Debugging output stream which times idle periods 
 * between write calls.
 * 
 * @author jbf
 */
public class IdleClockOutputStream extends OutputStream {

    private final OutputStream out;
    long maxIdleTime;
    long t0;
    long totalBytes;
    long birthMilli;
    long firstByteMilli;

    public IdleClockOutputStream( OutputStream out ) {
        this.out= out;
        this.maxIdleTime= 0;
        this.birthMilli= System.currentTimeMillis();
        this.t0= this.birthMilli;
        this.totalBytes= 0;
        this.firstByteMilli= 0;
    }
    
    @Override
    public void write(int b) throws IOException {
        out.write(b);
        long t= System.currentTimeMillis();
        long dt= t-t0;
        if ( dt>this.maxIdleTime ) {
            this.maxIdleTime= dt;
        }
        this.t0= t;
        this.totalBytes++;
        if ( this.firstByteMilli==0 ) this.firstByteMilli=t;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        long t= System.currentTimeMillis();
        long dt= t-t0;
        if ( dt>this.maxIdleTime ) {
            this.maxIdleTime= dt;
        }
        this.t0= t;
        this.totalBytes+= len;
        if ( this.firstByteMilli==0 ) this.firstByteMilli=t;
    }
    
    /**
     * return the maximum time elapsed between write calls, in milliseconds.
     * @return the maximum time elapsed between write calls, in milliseconds.
     */
    public long getMaxIdleTime() {
        return this.maxIdleTime;
    }

    /**
     * return the maximum time elapsed between write calls, in milliseconds.
     * @return the maximum time elapsed between write calls, in milliseconds.
     */
    public long getTotalBytes() {
        return this.totalBytes;
    }
    
    /**
     * return the bits per second.
     * @return  the bits per second.
     */
    public long getBitsPerSecond() {
        return this.totalBytes * 8 * 1000 / ( this.t0-this.birthMilli );
    }
    
    /**
     * return time delay to the first byte sent.
     * @return time delay to the first byte sent.
     */
    public long getFirstByteMilli() {
        return this.firstByteMilli - this.birthMilli;
    }
    
    /**
     * return all the stats at once.
     * @return  all the stats at once.
     */
    public String getStatsOneLine() {
        return String.format("idleMax=%dms bps=%d first=%dms", getMaxIdleTime(), getBitsPerSecond(), getFirstByteMilli() );
    }
    
    @Override
    public void close() throws IOException {
        out.close();
    }
    
    
}
