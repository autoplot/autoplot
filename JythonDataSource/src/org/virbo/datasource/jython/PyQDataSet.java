/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import org.python.core.Py;
import org.python.core.PyJavaInstance;
import org.python.core.PyObject;
import org.python.core.PySequence;
import org.python.core.PySlice;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;

/**
 *
 * @author jbf
 */
public class PyQDataSet extends PyJavaInstance {

    WritableDataSet ds;

    PyQDataSet(QDataSet ds) {
        super(ds);
        if (ds instanceof WritableDataSet) {
            this.ds = (WritableDataSet) ds;
        } else {
            this.ds = DDataSet.copy(ds);
        }
    }

    /* plus, minus, multiply, divide */
    @Override
    public PyQDataSet __add__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.add(ds, that));
    }

    @Override
    public PyObject __radd__(PyObject arg0) {
        return __add__(arg0);
    }

    @Override
    public PyObject __sub__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.add(ds, Ops.negate(that)));
    }

    @Override
    public PyObject __rsub__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.add(that, Ops.negate(ds)));
    }

    @Override
    public PyObject __mul__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(ds, that));
    }

    @Override
    public PyObject __rmul__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(that, ds));
    }

    @Override
    public PyObject __div__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(ds, Ops.pow(that, -1)));
    }

    @Override
    public PyObject __rdiv__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.multiply(Ops.pow(ds, -1), that));
    }

    /* unary negate and plus operator */
    @Override
    public PyObject __pos__() {
        return this;
    }

    @Override
    public PyObject __neg__() {
        return new PyQDataSet(Ops.negate(ds));
    }

    @Override
    public PyObject __abs__() {
        return new PyQDataSet(Ops.abs(ds));
    }

    /* pow operator (**) */
    @Override
    public PyObject __pow__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.pow(ds, that));
    }

    @Override
    public PyObject __rpow__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.pow(that, ds));
    }

    /* compare operators */
    @Override
    public PyObject __eq__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.eq(ds, that));
    }

    @Override
    public PyObject __gt__(
            PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.gt(ds, that));
    }

    @Override
    public PyObject __ge__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.ge(ds, that));
    }

    @Override
    public PyObject __le__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.le(ds, that));
    }

    @Override
    public PyObject __lt__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.lt(ds, that));
    }

    @Override
    public PyObject __ne__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.ne(ds, that));
    }

    /* logic operators
     * There's a problem here, that you can't override "and" "or" and "not." 
     * We use the symbols ~ for not, | for or, and & for and.
     * These use the object's __nonzero__ method.
     * TODO: I don't like these any more than C-style booleans.  This should
     * require nominal units that convert the values to boolean.
     */
    @Override
    public PyObject __and__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.and(ds, that));
    }

    @Override
    public PyObject __or__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.or(ds, that));
    }

    @Override
    public PyObject __rand__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.and(that, ds));
    }

    @Override
    public PyObject __ror__(PyObject arg0) {
        QDataSet that = coerce_ds(arg0);
        return new PyQDataSet(Ops.or(that, ds));
    }

    @Override
    public PyObject __invert__() {
        return new PyQDataSet(Ops.not(ds));
    }

    /* accessor and mutator */
    @Override
    public PyObject __getitem__(PyObject arg0) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0 instanceof PySlice) {
                PySlice slice = (PySlice) arg0;
                int start = (Integer) slice.start.__tojava__(Integer.class);
                int stop = (Integer) slice.stop.__tojava__(Integer.class);
                QDataSet result = DataSetOps.trim(ds, start, stop - start);
                return new PyQDataSet(result);
            } else if (arg0.isNumberType()) {
                int idx = (Integer) arg0.__tojava__(Integer.class);
                return Py.java2py(ds.value(idx));
            } else if (arg0.isSequenceType()) {
                QubeDataSetIterator iter = new QubeDataSetIterator(ds);
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
                        Object o2 = a.__tojava__(QDataSet.class);
                        QDataSet that = (QDataSet) o2;
                        fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                    }

                    iter.setIndexIteratorFactory(0, fit);
                }

                int qube[] = new int[iter.rank()];
                for (int i = 0; i < iter.rank(); i++) {
                    qube[i] = iter.length(i);
                }
                DDataSet result = DDataSet.create(qube);
                while (iter.hasNext()) {
                    iter.next();
                    double d = getValue(ds, iter);
                    iter.next();
                    putValue(result, iter, d);
                }
                return new PyQDataSet(result);
            } else {
                return null;
            }
        } else {
            QDataSet that = (QDataSet) o;
            return new PyQDataSet(DataSetOps.applyIndex(ds, 0, that, true));
        }
    }

    @Override
    public void __setitem__(PyObject arg0, PyObject arg1) {
        QubeDataSetIterator iter = new QubeDataSetIterator(ds);

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

            iter.setIndexIteratorFactory(0, fit);

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

                iter.setIndexIteratorFactory(i, fit);
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
            double d = getValue(val, it);
            iter.next();
            putValue(ds, iter, d);
        }

    }

    /* utility methods */
    private double getValue(QDataSet wds, QubeDataSetIterator iter) {
        switch (wds.rank()) {
            case 1:
                return wds.value(iter.index(0));
            case 2:
                return wds.value(iter.index(0), iter.index(1));
            case 3:
                return wds.value(iter.index(0), iter.index(1), iter.index(2));
            default:
                throw new IllegalArgumentException("rank");
        }
    }

    private void putValue(WritableDataSet wds, QubeDataSetIterator iter, double val) {
        switch (wds.rank()) {
            case 1:
                wds.putValue(iter.index(0), val);
                return;
            case 2:
                wds.putValue(iter.index(0), iter.index(1), val);
                return;
            case 3:
                wds.putValue(iter.index(0), iter.index(1), iter.index(2), val);
                return;
            default:
                throw new IllegalArgumentException("rank");
        }
    }

    private QDataSet coerce_ds(int qube[], PyObject arg0) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                double d = (Double) arg0.__tojava__(Double.class);
                DDataSet that = DDataSet.create(qube);
                QubeDataSetIterator it = new QubeDataSetIterator(that);
                while (it.hasNext()) {
                    it.next();
                    putValue( that, it, d );
                }
                return that;
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            return (QDataSet) o;
        }

    }

// coerce logic doesn't seem to kick in, so I do it!
    private QDataSet coerce_ds(PyObject arg0) {
        return coerce_ds(DataSetUtil.qubeDims(ds), arg0);
    }

    @Override
    public Object __coerce_ex__(PyObject arg0) {
        System.err.println("coerce");
        return coerce_ds(DataSetUtil.qubeDims(ds), arg0);
    }
}
