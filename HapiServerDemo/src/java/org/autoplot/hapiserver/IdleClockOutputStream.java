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

    private OutputStream out;
    long maxIdleTime;
    long t0;

    public IdleClockOutputStream( OutputStream out ) {
        this.out= out;
        this.maxIdleTime= 0;
        this.t0= System.currentTimeMillis();
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
    }
    
    /**
     * return the maximum time elapsed between write calls, in milliseconds.
     */
    public long getMaxIdleTime() {
        return this.maxIdleTime;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
    
    
}
