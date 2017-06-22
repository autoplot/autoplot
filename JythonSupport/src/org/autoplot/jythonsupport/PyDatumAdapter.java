
package org.autoplot.jythonsupport;

import org.das2.datum.Datum;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;

/**
 * adapts Datums to PyDatum
 * @author jbf
 */
public class PyDatumAdapter implements PyObjectAdapter {

    @Override
    public boolean canAdapt(Object o) {
        return o instanceof Datum;
    }

    @Override
    public PyObject adapt(Object o) {
        return new PyDatum((Datum)o);
    }
    
}
