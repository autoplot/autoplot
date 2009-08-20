/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dsops.Ops;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.IndexListDataSetIterator;
import org.virbo.dataset.QubeDataSetIterator;
import org.python.core.Py;
import org.python.core.PyFloat;
import org.python.core.PyIterator;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.core.PySequence;
import org.python.core.PySlice;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.WritableDataSet;

/**
 * PyQDataSet wraps a QDataSet to provide Python operator overloading and
 * indexing.  For example, the Python plus "+" operator is implemented in the
 * method "__add__", and ds[0,:] is implemented in __getitem__ and __setitem__.
 * The class PyQDataSetAdapter is responsible for creating the PyQDataSet when
 * a QDataSet is brought into the Python interpretter.
 *
 * @author jbf
 */
public class PyQDataSet extends PyJavaInstance {

    WritableDataSet ds;
    QDataSet rods; // read-only dataset

    PyQDataSet(QDataSet ds) {
        super(ds);
        if (ds instanceof WritableDataSet) {
            this.ds = (WritableDataSet) ds;
        } else if (ds.rank() == 0) {
            this.ds = null;
        } else if ( DataSetUtil.isQube(ds) ) {
            this.ds = DDataSet.copy(ds);
        } else {
            this.ds= null;
        }
        this.rods = ds;

    }

    /* plus, minus, multiply, divide */
    @Override
    public PyQDataSet __add__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.add(rods, that));
    }

    @Override
    public PyObject __radd__(PyObject arg0) {
        return __add__(arg0);
    }

    @Override
    public PyObject __sub__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.subtract(rods, that));
    }

    @Override
    public PyObject __rsub__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.subtract(that, rods));
    }

    @Override
    public PyObject __mul__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(rods, that));
    }

    @Override
    public PyObject __rmul__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(that, rods));
    }

    @Override
    public PyObject __div__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.divide(rods, that));
    }

    @Override
    public PyObject __rdiv__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.divide(that, rods));
    }

    @Override
    public PyObject __floordiv__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.div(rods, that));
    }

    @Override
    public PyObject __mod__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.mod(rods, that));
    }

    @Override
    public PyObject __rfloordiv__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.div(that, rods));
    }

    @Override
    public PyObject __rmod__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.mod(that, rods));
    }

    /* unary negate and plus operator */
    @Override
    public PyObject __pos__() {
        return this;
    }

    @Override
    public PyObject __neg__() {
        return new PyQDataSet(Ops.negate(rods));
    }

    @Override
    public PyObject __abs__() {
        return new PyQDataSet(Ops.abs(rods));
    }

    /* pow operator (**) */
    @Override
    public PyObject __pow__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.pow(rods, that));
    }

    @Override
    public PyObject __rpow__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.pow(that, rods));
    }

    private static Map<String,PyReflectedFunction> binaryInfixMethods;
    static {
        binaryInfixMethods= new HashMap<String, PyReflectedFunction>();
        binaryInfixMethods.put( "gt", new PyReflectedFunction("gt") );
        
        for ( Method m: BinaryInfixOps.class.getMethods() ) {
            PyReflectedFunction func= binaryInfixMethods.get(m.getName());
            if ( func==null ) {
                func= new PyReflectedFunction(m.getName());
                binaryInfixMethods.put( m.getName(), func );
            }
            if ( func!=null ) {
                func.addMethod(m);
            }
        }
    }

    @Override
    public PyObject __findattr__(String name) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func;
        } else {
            return super.__findattr__(name);
        }
    }

    @Override
    public void __delattr__(String attr) {
        if ( binaryInfixMethods.remove(attr)==null ) {
            super.__delattr__(attr);
        }
    }

    @Override
    public void __setattr__(String name, PyObject value) {
        if ( binaryInfixMethods.containsKey(name) ) {
            binaryInfixMethods.remove(name);
        }
        super.__setattr__(name, value);
    }

    @Override
    public PyObject invoke(String name) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this);
        } else {
            return super.invoke(name);
        }
    }

    @Override
    public PyObject invoke(String name, PyObject arg1) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this,arg1);
        } else {
            return super.invoke(name,arg1);
        }
    }

    @Override
    public PyObject invoke(String name, PyObject arg1, PyObject arg2) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this,arg1,arg2);
        } else {
            return super.invoke(name,arg1,arg2);
        }
    }

    @Override
    public PyObject invoke(String name, PyObject[] args, String[] keywords) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this,args,keywords);
        } else {
            return super.invoke(name,args,keywords);
        }
    }

    @Override
    public PyObject invoke(String name, PyObject[] args) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this);
        } else {
            return super.invoke(name);
        }
    }

    private Number getNumber( PyObject po ) {
        Object result=  po.__tojava__( Number.class );
        if ( result==Py.NoConversion ) {
            throw Py.TypeError("can't convert to number: "+po.__repr__() );
        }
        return (Number) result;
    }

    @Override
    public int __len__() {
        return rods.length();
    }

    /* accessor and mutator */
    /**
     * This implements the Python indexing, such as data[4,:,3:5].  Note this
     * includes many QDataSet operations: a single index represents a slice, a 
     * range like 3:5 is a trim, an array is a sort, and a colon leaves a dimension
     * alone.
     *
     * TODO: preserve metadata
     * @param arg0
     * @return
     */
    @Override
    public PyObject __getitem__(PyObject arg0) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0 instanceof PySlice) {
                PySlice slice = (PySlice) arg0;
                QubeDataSetIterator iter = new QubeDataSetIterator(rods);
                QubeDataSetIterator.DimensionIteratorFactory fit;
                Number start = (Number) getNumber( slice.start  );
                Number stop = (Number) getNumber(slice.stop  );
                Number step = (Number) getNumber(slice.step  );
                fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);
                iter.setIndexIteratorFactory(0, fit);

                int qube[] = new int[iter.rank()];
                for (int i = 0; i < iter.rank(); i++) {
                    qube[i] = iter.length(i);
                }
                DDataSet result = DDataSet.create(qube);
                QubeDataSetIterator resultIter = new QubeDataSetIterator(result);
                while (iter.hasNext()) {
                    iter.next();
                    double d = iter.getValue(rods);
                    resultIter.next();
                    resultIter.putValue(result, d);
                }
                return new PyQDataSet(result);

            } else if (arg0.isNumberType()) {
                int idx = ((Number) arg0.__tojava__(Number.class)).intValue();
                if ( rods.rank()>1 ) {
                    return new PyQDataSet( DataSetOps.slice0(rods, idx) );
                } else {
                    return Py.java2py(rods.value(idx));
                }
                
            } else if (arg0.isSequenceType()) {
                QubeDataSetIterator iter = new QubeDataSetIterator(rods);
                PySequence slices = (PySequence) arg0;
                boolean[] reform = new boolean[slices.__len__()];
                for (int i = 0; i < slices.__len__(); i++) {
                    PyObject a = slices.__getitem__(i);
                    QubeDataSetIterator.DimensionIteratorFactory fit;
                    if (a instanceof PySlice) {
                        PySlice slice = (PySlice) a;
                        Number start = (Number) getNumber(slice.start);
                        Number stop = (Number)  getNumber(slice.stop);
                        Number step = (Number)  getNumber(slice.step);
                        fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                    } else if (a.isNumberType()) {
                        int idx = (Integer) a.__tojava__(Integer.class);
                        fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                        reform[i] = true;

                    } else {
                        Object o2 = a.__tojava__(QDataSet.class);
                        QDataSet that = (QDataSet) o2;
                        fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                    }

                    iter.setIndexIteratorFactory(i, fit);
                }

                ArrayList<Integer> qqube = new ArrayList<Integer>();
                for (int i = 0; i < iter.rank(); i++) {
                    if (!reform[i]) {
                        qqube.add(iter.length(i));
                    }
                }
                int qube[] = new int[qqube.size()];
                for (int i = 0; i < qqube.size(); i++) {
                    qube[i] = qqube.get(i);
                }
                DDataSet result = DDataSet.create(qube);

                QubeDataSetIterator resultIter = new QubeDataSetIterator(result);
                while (iter.hasNext()) {
                    iter.next();
                    double d = iter.getValue(rods);
                    resultIter.next();
                    resultIter.putValue(result, d);
                }

                return new PyQDataSet(result);
            } else {
                throw Py.TypeError("invalid index type: "+arg0);
            }
        } else {
            QDataSet that = (QDataSet) o;
            return new PyQDataSet(DataSetOps.applyIndex(rods, 0, that, true));
        }
    }

    @Override
    public void __setitem__(PyObject arg0, PyObject arg1) {
        if ( ds==null ) {
            throw new RuntimeException("__setitem__ on dataset that could not be made into mutable.");
        }
        DataSetIterator iter = new QubeDataSetIterator(ds);

        if (!arg0.isSequenceType()) {
            PyObject a = arg0;
            QubeDataSetIterator.DimensionIteratorFactory fit;
            if (a instanceof PySlice) {
                PySlice slice = (PySlice) a;
                Integer start = (Integer) slice.start.__tojava__(Integer.class);
                Integer stop = (Integer) slice.stop.__tojava__(Integer.class);
                Integer step = (Integer) slice.step.__tojava__(Integer.class);
                fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

            } else if (a.isNumberType()) {
                int idx = (Integer) a.__tojava__(Integer.class);
                fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
            } else {
                Object o = a.__tojava__(QDataSet.class);
                QDataSet that = (QDataSet) o;
                fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
            }

            ((QubeDataSetIterator) iter).setIndexIteratorFactory(0, fit);
        } else if (arg0 instanceof PyQDataSet) {
            Object o = arg0.__tojava__(QDataSet.class);
            QDataSet that = (QDataSet) o;

            if (ds.rank() > 1) {
                iter = new IndexListDataSetIterator(that);
            } else {
                QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                ((QubeDataSetIterator) iter).setIndexIteratorFactory(0, fit);
            }

        } else {
            PySequence slices = (PySequence) arg0;
            for (int i = 0; i < slices.__len__(); i++) {
                PyObject a = slices.__getitem__(i);
                QubeDataSetIterator.DimensionIteratorFactory fit;
                if (a instanceof PySlice) {
                    PySlice slice = (PySlice) a;
                    Integer start = (Integer) slice.start.__tojava__(Integer.class);
                    Integer stop = (Integer) slice.stop.__tojava__(Integer.class);
                    Integer step = (Integer) slice.step.__tojava__(Integer.class);
                    fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                } else if (a.isNumberType()) {
                    int idx = (Integer) a.__tojava__(Integer.class);
                    fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                } else {
                    Object o = a.__tojava__(QDataSet.class);
                    QDataSet that = (QDataSet) o;
                    fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                }

                ((QubeDataSetIterator) iter).setIndexIteratorFactory(i, fit);
            }

        }

        int qube[] = new int[iter.rank()];
        for (int i = 0; i < iter.rank(); i++) {
            qube[i] = iter.length(i);
        }
        QDataSet val = coerce_ds(qube, arg1);

        QubeDataSetIterator it = new QubeDataSetIterator(val);
        while (it.hasNext()) {
            it.next();
            double d = it.getValue(val);
            iter.next();
            iter.putValue(ds, d);
        }

    }

    /**
     * convert the Python number, sequence, or Rank0DataSet to a
     * dataset with a compatible geometry.
     * @param qube
     * @param arg0
     * @return
     */
    private QDataSet coerce_ds(int qube[], PyObject arg0) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                double d = (Double) arg0.__tojava__(Double.class);
                if (qube.length == 0) {
                    return DataSetUtil.asDataSet(d);
                } else {
                    DDataSet that = DDataSet.create(qube);
                    QubeDataSetIterator it = new QubeDataSetIterator(that);
                    while (it.hasNext()) {
                        it.next();
                        it.putValue(that, d);
                    }
                    for (int i = 0; i < 4; i++) {
                        Object op = ds.property("DEPEND_" + i);
                        if (op != null) {
                            that.putProperty("DEPEND_" + i, op);
                        }
                    }
                    return that;
                }
            } else if (arg0.isSequenceType()) {
                return PyQDataSetAdapter.adaptList((PyList) arg0);
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            QDataSet ds = (QDataSet) o;
            if (ds.rank() == 0) {
                RankZeroDataSet r0ds = (RankZeroDataSet) ds;
                MutablePropertyDataSet mpds;
                if (qube.length == 1) {
                    mpds = Ops.replicate(r0ds.value(), qube[0]);
                } else if (qube.length == 2) {
                    mpds = Ops.replicate(r0ds.value(), qube[0], qube[1]);
                } else if (qube.length == 3) {
                    mpds = Ops.replicate(r0ds.value(), qube[0], qube[1], qube[2]);
                } else {
                    throw new IllegalArgumentException("rank limit");
                }
                DataSetUtil.putProperties(DataSetUtil.getProperties(ds), mpds);
                return mpds;
            } else {
                return ds;
            }
        }

    }

    public QDataSet gt( Object o ) {
        System.err.println(o);
        return null;
    }

// coerce logic doesn't seem to kick in, so I do it!
    private QDataSet coerce_ds(PyObject arg0) {
        return coerce_ds(DataSetUtil.qubeDims(rods), arg0);
    }

    @Override
    public Object __coerce_ex__(PyObject arg0) {
        System.err.println("coerce");
        return coerce_ds(DataSetUtil.qubeDims(rods), arg0);
    }

    @Override
    public PyObject __iter__() {
        return new PyIterator() {

            int i = 0;

            @Override
            public PyObject __iternext__() {
                if (i < ds.length()) {
                    PyObject result;
                    if (ds.rank() == 1) {
                        result = new PyFloat(ds.value(i));
                    } else {
                        result = new PyQDataSet(DataSetOps.slice0(ds, i));
                    }
                    i++;
                    return result;
                } else {
                    return null;
                }
            }
        };
    }
}
