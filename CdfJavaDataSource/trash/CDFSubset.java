package gov.nasa.gsfc.voyager.cdf;
import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;
/**
 * CDFSubset
 */
public class CDFSubset implements Serializable {
    CDF cdf;
    double[] times;
    byte[] values;
    Hashtable offsets = new Hashtable();
    String[] vnames;
    int numpt;
    CDFImpl impl;
    public CDFSubset(CDF cdf, double start, double stop, String[] vnames) 
    throws Throwable {
        this.cdf = cdf;
        impl = (CDFImpl)cdf;
        int rheader = impl.getRecordOffset();
        this.vnames = vnames;
        int[] rr;
        try {
            rr = cdf.getRecordRange(vnames[0], new double[]{start, stop});
            times = cdf.getTimes(vnames[0], rr);
        } catch (Throwable th) {
            throw new Throwable("subset for " + vnames[0]);
        }
        // compute size of values array
        numpt = rr[1] - rr[0] + 1;
        int offset = 0;
        for (int i = 0; i < vnames.length; i++) {
            Variable v = cdf.getVariable(vnames[i]);
            int size = v.getDataItemSize();
            offsets.put(vnames[i], new Integer(offset));
            offset += rheader + size*numpt;
        }
        values = new byte[offset];
        for (int i = 0; i < vnames.length; i++) {
            offset = ((Integer)offsets.get(vnames[i])).intValue() + rheader;
            Variable v = cdf.getVariable(vnames[i]);
            int size = v.getDataItemSize();
            VariableDataLocator locator = v.getLocator();
            int[][] locations = locator.getLocations();
            int e = 0;
            for (; e < locations.length; e++) {
                if (rr[0] <= locations[e][1]) break;
            }
            if (e == locations.length) {
                continue;
            }
            int begin = (rr[0] - locations[e][0]);
            if (begin < 0) begin = 0;
            for (; e < locations.length; e++) {
                int bufOffset = locations[e][2] + begin*size + rheader;
                int end = locations[e][1];
                boolean last = (rr[1] <= locations[e][1]);
                if (last) end = rr[1];
                int len = size*(1 + end - (locations[e][0] + begin));
                ((CDFImpl)cdf).extractBytes(bufOffset, values, offset, len);
                //extractBytes(bufOffset, values, offset, len);
                if (last) break;
                offset += len;
                begin = 0;
            }
        }
    }
    public CDF getCDF() {return cdf;}
    private synchronized void writeObject(java.io.ObjectOutputStream out)
    throws IOException {
        Hashtable locators = new Hashtable();
        Hashtable _variableTable = new Hashtable();;
        Hashtable savedVariableTable = impl.variableTable;
        String[] savedVarNames = impl.varNames;
        double[] _times;
        int _numberOfValues = 0;
        CDFImpl.CDFTimeVariable tvar;
        try {
           tvar = (CDFImpl.CDFTimeVariable)impl.getCDFTimeVariable(vnames[0]);
        } catch (Throwable th) {
           th.printStackTrace();
           throw new IOException("could not get time variable");
        }
        _times = tvar.times;
        tvar.times = times;
        _variableTable.put(tvar.getName(),
            impl.variableTable.get(tvar.getName()));
        // set DataLocator
        for (int i = 0; i < vnames.length; i++) {
            String s = vnames[i];
            CDFImpl.CDFVariable var =
                (CDFImpl.CDFVariable)cdf.getVariable(s);
            _numberOfValues = var.numberOfValues;
            var.numberOfValues =  numpt;
            CDFImpl.DataLocator locator = (CDFImpl.DataLocator)var.getLocator();
            locators.put(s, locator.locations);
            Vector locvec = new Vector();
            int offset = ((Integer)offsets.get(s)).intValue();
            locvec.add(new int[] {0, numpt - 1, offset});
            locator.locations = locvec;
            _variableTable.put(s, impl.variableTable.get(s));
        }
        impl.varNames = vnames;
        impl.variableTable = _variableTable;
        out.defaultWriteObject();
        for (int i = 0; i < vnames.length; i++) {
            CDFImpl.CDFVariable var =
                (CDFImpl.CDFVariable)cdf.getVariable(vnames[i]);
            var.numberOfValues =  _numberOfValues;
            CDFImpl.DataLocator locator = (CDFImpl.DataLocator)var.getLocator();
            locator.locations = (Vector)locators.get(vnames[i]);
        }
        //tvar.restoreTimes();
        tvar.times = _times;
        impl.variableTable = savedVariableTable;
        impl.varNames = savedVarNames;
    }
    private synchronized void readObject(java.io.ObjectInputStream in)
    throws IOException {
        try {
            in.defaultReadObject();
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        ((CDFImpl)cdf).setBuffer(ByteBuffer.wrap(values));
        ((CDFImpl)cdf).setByteOrder(((CDFImpl)cdf).isBigEndian());
    }
}
