/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.das2.datum.DatumUtil;
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
import org.python.core.PyString;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.TrimStrideWrapper;
import org.virbo.dataset.WritableDataSet;

/**
 * PyQDataSet wraps a QDataSet to provide Python operator overloading and
 * indexing.  For example, the Python plus "+" operator is implemented in the
 * method "__add__", and ds[0,:] is implemented in __getitem__ and __setitem__.
 * The class PyQDataSetAdapter is responsible for creating the PyQDataSet when
 * a QDataSet is brought into the Python interpreter.
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

    public QDataSet getQDataSet() {
        return this.rods;
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

//    @Override
//    public PyObject __int__() {
//        if ( rods.rank()>0 ) {
//            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
//        }
//        return Py.newInteger((int)rods.value());
//    }
//
//    @Override
//    public PyFloat __float__() {
//        if ( rods.rank()>0 ) {
//            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
//        }
//        return Py.newFloat(rods.value());
//    }
//
//    @Override
//    public PyLong __long__() {
//        if ( rods.rank()>0 ) {
//            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
//        }
//        return Py.newLong((int)rods.value());
//    }
//
//
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

    protected static Number getNumber( Object po ) {
        if ( po instanceof QDataSet ) {
            QDataSet qpo= (QDataSet)po;
            if ( qpo.rank()==0 ) {
                return qpo.value();
            } else {
                throw Py.TypeError("QDataSet with rank>0 found where number was expected");
            }
        } else if ( po instanceof PyQDataSet ) {
            PyQDataSet pqd=  ((PyQDataSet)po);
            QDataSet qpo= pqd.rods;
            if ( qpo.rank()==0 ) {
                return qpo.value();
            } else {
                throw Py.TypeError("PyQDataSet with rank>0 found where number was expected");
            }
        } else if ( po instanceof PyObject ) {
            Object result=  ((PyObject)po).__tojava__( Number.class );
            if ( result==Py.NoConversion ) {
                throw Py.TypeError("can't convert to number: "+((PyObject)po).__repr__() );
            }
            return (Number) result;
        } else {
            if ( po instanceof Number ) {
                return (Number) po;
            } else {
                throw Py.TypeError("can't convert to number: "+po );
            }
        }
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
     * alone.  See http://autoplot.org/developer.python.indexing
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
                Number start = (Number) getNumber( slice.start  );
                Number stop = (Number) getNumber(slice.stop  );
                Number step = (Number) getNumber(slice.step  );

                if ( step==null || step.equals(1) ) {
                    if ( start==null ) start= 0;
                    if ( stop==null ) stop= rods.length();
                    if ( start.intValue()<0 ) start= rods.length() + start.intValue();
                    if ( stop.intValue()<0 ) stop= rods.length() + stop.intValue();
                    return new PyQDataSet( rods.trim( start.intValue(), stop.intValue() ) );
                } else {
                    TrimStrideWrapper wds= new TrimStrideWrapper(rods);
                    wds.setTrim( 0, start, stop, step );
                    return new PyQDataSet(wds);
                }

            } else if (arg0.isNumberType()) {
                int idx = ((Number) arg0.__tojava__(Number.class)).intValue();
                if ( idx<0 ) {
                    idx= rods.length()+idx;
                }
                if ( rods.rank()>1 ) {
                    QDataSet sds= rods.slice(idx);
                    //TODO: properties and context.
                    return new PyQDataSet( sds );
                } else {
                    return Py.java2py(rods.value(idx));
                }
                
            } else if (arg0.isSequenceType()) {
                QubeDataSetIterator iter = new QubeDataSetIterator(rods);
                PySequence slices = (PySequence) arg0;
                for (int i = 0; i < slices.__len__(); i++) {
                    PyObject a = slices.__getitem__(i);
                    QubeDataSetIterator.DimensionIteratorFactory fit;
                    if (a instanceof PySlice) {
                        PySlice slice = (PySlice) a;
                        Number start = (Number) getNumber(slice.start);
                        Number stop = (Number)  getNumber(slice.stop);
                        Number step = (Number)  getNumber(slice.step);
                        fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                    } else if ( a instanceof PyQDataSet ) {
                        Object o2 = a.__tojava__(QDataSet.class);
                        QDataSet that = (QDataSet) o2;
                        fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                        
                    } else if (a.isNumberType()) {
                        int idx = ((Number) getNumber( a )).intValue();
                        fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                        // TODO: ds[:,0] where ds is a bundle is effectively an unbundle operation.  See https://sourceforge.net/tracker/?func=detail&aid=3473406&group_id=199733&atid=970682
                    } else {
                        QDataSet that = coerce_ds(a);
                        fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                    }

                    iter.setIndexIteratorFactory(i, fit);
                }

                DDataSet result= iter.createEmptyDs();

                QubeDataSetIterator resultIter = new QubeDataSetIterator(result);
                while (iter.hasNext()) {
                    iter.next();
                    double d = iter.getValue(rods);
                    resultIter.next();
                    resultIter.putValue(result, d);
                }
                DataSetUtil.copyDimensionProperties( ds, result );

                return new PyQDataSet(result);
            } else {
                throw Py.TypeError("invalid index type: "+arg0);
            }
        } else {
            QDataSet that = (QDataSet) o;
            
            DataSetIterator iter = new QubeDataSetIterator(ds);

            QDataSet dep0= null;
            if ( ds.rank()>1 ) {
                iter= new IndexListDataSetIterator( that );
                //don't do anything with dep0 for now...
            } else {
                QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                ((QubeDataSetIterator)iter).setIndexIteratorFactory(0, fit);
                dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
                if ( dep0!=null ) {
                    dep0= DataSetOps.applyIndex( dep0, 0, that, false );
                }
            }

            DDataSet result= iter.createEmptyDs();
            //TODO: look at DataSetOps.applyIndex sometime
            QubeDataSetIterator resultIter = new QubeDataSetIterator(result);
            while (iter.hasNext()) {
                iter.next();
                double d = iter.getValue(rods);
                resultIter.next();
                resultIter.putValue(result, d);
            }

            if ( dep0!=null && dep0.length()==result.length() ) {
                result.putProperty( QDataSet.DEPEND_0, dep0 ); // yeah, we did it right!
            }
            DataSetUtil.copyDimensionProperties( ds, result );
            
            return new PyQDataSet(result);
        }
    }

    /**
     * See http://autoplot.org/developer.python.indexing
     * @param arg0
     * @param arg1
     */
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
                Integer start = ( slice.start==Py.None ) ? null : ( (Number) slice.start.__tojava__(Number.class) ).intValue();
                Integer stop = ( slice.stop==Py.None ) ? null : ( (Number) slice.stop.__tojava__(Number.class) ).intValue();
                Integer step = ( slice.step==Py.None ) ? null : ( (Number) slice.step.__tojava__(Number.class) ).intValue();
                fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

            } else if (a.isNumberType()) {
                int idx = ( (Number) a.__tojava__(Number.class) ).intValue();
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
            QDataSet[] lists= new QDataSet[slices.__len__()];
            boolean allLists= true;
            for (int i = 0; i < slices.__len__(); i++) {
                PyObject a = slices.__getitem__(i);
                if ( ! ( a instanceof PyQDataSet ) ) {
                    allLists= false;
                } else {
                    lists[i]= ((PyQDataSet)a).rods;
                }
            }
            if ( allLists ) {
                int n= lists[0].length();
                QDataSet val= coerceDsInternal(  arg1 );
                QubeDataSetIterator it = new QubeDataSetIterator( val );
                if ( ds.rank()==1 ) {
                    for ( int i=0;i<n;i++ ) {
                        it.next();
                        ds.putValue( (int)lists[0].value(i), it.getValue(val));
                    }
                } else if ( ds.rank()==2 ) {
                    for ( int i=0;i<n;i++ ) {
                        it.next();
                        ds.putValue( (int)lists[0].value(i),(int)lists[1].value(i), it.getValue(val));
                    }

                } else if ( ds.rank()==3 ) {
                    for ( int i=0;i<n;i++ ) {
                        it.next();
                        ds.putValue( (int)lists[0].value(i),
                                (int)lists[1].value(i),
                                (int)lists[2].value(i), it.getValue(val));
                    }

                } else if ( ds.rank()==4 ) {
                    for ( int i=0;i<n;i++ ) {
                        it.next();
                        ds.putValue( (int)lists[0].value(i),
                                (int)lists[1].value(i),
                                (int)lists[2].value(i),
                                (int)lists[2].value(i), it.getValue(val));
                    }
                }
                return;
            }
            for (int i = 0; i < slices.__len__(); i++) {
                PyObject a = slices.__getitem__(i);
                QubeDataSetIterator.DimensionIteratorFactory fit;
                if (a instanceof PySlice) {
                    PySlice slice = (PySlice) a;
                    Integer start = (Integer) slice.start.__tojava__(Integer.class);
                    Integer stop = (Integer) slice.stop.__tojava__(Integer.class);
                    Integer step = (Integer) slice.step.__tojava__(Integer.class);
                    fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                } else if ( a.isNumberType() && ! ( a instanceof PyQDataSet ) ) {
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
        QDataSet val = coerceDsInternal(arg1);

        // see org.virbo.dsops.CoerceUtil, make version that makes iterators.
        if ( val.rank()==0 ) {
            double d = val.value();
            while (iter.hasNext()) {
                iter.next();
                iter.putValue(ds, d);
            }
        } else if ( val.rank()!=iter.rank() ) {
            throw new IllegalArgumentException("not supported, couldn't reconcile ranks: arg=" + val.rank() + " and ds=" + iter.rank());
        } else {
            QubeDataSetIterator it = new QubeDataSetIterator(val);
            while (it.hasNext()) {
                it.next();
                double d = it.getValue(val);
                iter.next();
                iter.putValue(ds, d);
            }
        }
    }

    /**
     * convert the Python number, sequence, or Rank0DataSet to a
     * dataset with a compatible geometry.
     * @param qube
     * @param arg0
     * @return
     */
    private QDataSet coerceDsInternal( PyObject arg0) {
        Object o = arg0.__tojava__(QDataSet.class);
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                try {
                    double d = (Double) arg0.__tojava__(Double.class);
                    return DataSetUtil.asDataSet(d);
                } catch ( RuntimeException ex ) {
                    Object o2= arg0.__tojava__( Object.class );
                    QDataSet do2;
                    if ( o2 instanceof org.das2.datum.TimeUtil.TimeStruct ) {
                        do2= DataSetUtil.asDataSet( org.das2.datum.TimeUtil.toDatum( (org.das2.datum.TimeUtil.TimeStruct)o2) );
                    } else if ( o2 instanceof org.das2.datum.Datum ) {
                        do2= DataSetUtil.asDataSet( (org.das2.datum.Datum)o2 );
                    } else {
                        throw ex;
                    }
                    return do2;
                }
                
            } else if (arg0 instanceof PyString ) {
                try {
                    return DataSetUtil.asDataSet(DatumUtil.parse(arg0.toString()));
                } catch (ParseException ex) {
                    throw new IllegalArgumentException(ex);
                }
            } else if (arg0.isSequenceType()) {
                return PyQDataSetAdapter.adaptList((PyList) arg0);
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            QDataSet ds = (QDataSet) o;
            if (ds.rank() == 0) {
                // QDataSet library handles coerce logic.
                return ds;
            } else {
                return ds;
            }
        }

    }

    public QDataSet gt( Object o ) {
        System.err.println(o);
        return null;
    }

    public PyQDataSet append( PyObject arg0 ) {
        Object o = arg0.__tojava__(QDataSet.class);
        DDataSet result;
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                double d = (Double) arg0.__tojava__(Double.class);
                result= (DDataSet) ArrayDataSet.copy( double.class, rods);
                result= (DDataSet) ArrayDataSet.append( result, DDataSet.wrap( new double[] { d } ) );
            } else if (arg0.isSequenceType()) {
                result= (DDataSet) ArrayDataSet.copy( double.class, rods );
                result= (DDataSet) ArrayDataSet.append( result, DDataSet.copy( PyQDataSetAdapter.adaptList((PyList) arg0) ) );
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            QDataSet ds = (QDataSet) o;
            result= (DDataSet) ArrayDataSet.copy( double.class, rods);
            result= (DDataSet) ArrayDataSet.append( result, DDataSet.copy( ds ) );
        }

        return new PyQDataSet(result);
    }

// coerce logic doesn't seem to kick in, so I do it!
    private QDataSet coerce_ds(PyObject arg0) {
        return coerceDsInternal( arg0);
    }

    @Override
    public Object __coerce_ex__(PyObject arg0) {
        return coerceDsInternal( arg0);
    }

    @Override
    public PyObject __iter__() {
        return new PyIterator() {

            int i = 0;

            @Override
            public PyObject __iternext__() {
                if (i < rods.length()) {
                    PyObject result;
                    if (rods.rank() == 1) {
                        result = new PyFloat(rods.value(i));
                    } else {
                        result = new PyQDataSet( rods.slice(i) );
                    }
                    i++;
                    return result;
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public Object __tojava__(Class c) {
        if ( c.isArray() && c.getComponentType()==double.class && rods.rank()==1 ) {
            double[] result= new double[rods.length()];
            for ( int i=0; i< rods.length(); i++  ) {
                result[i]= rods.value(i);
            }
            return result;
        }
        return super.__tojava__(c);
    }


    public String toString() {
        return "PyQDataSet wrapping " + rods.toString();
    }
}
