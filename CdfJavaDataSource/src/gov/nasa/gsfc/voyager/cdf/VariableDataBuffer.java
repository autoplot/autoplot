package gov.nasa.gsfc.voyager.cdf;
import java.nio.*;
public class VariableDataBuffer {
    int firstRecord;
    int lastRecord;
    ByteBuffer buffer;
    public VariableDataBuffer(int first, int last, ByteBuffer buf) {
        firstRecord = first;
        lastRecord = last;
        buffer = buf;
    }
    public int getFirstRecord() {return firstRecord;}
    public int getLastRecord() {return lastRecord;}
    public ByteBuffer getBuffer() {return buffer;}
}
