
package org.autoplot.jythonsupport;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.qds.ops.Ops;
import org.das2.qds.DataSetIterator;
import org.das2.qds.IndexListDataSetIterator;
import org.das2.qds.QubeDataSetIterator;
import org.python.core.Py;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyIterator;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyLong;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.core.PySequence;
import org.python.core.PySlice;
import org.python.core.PyString;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.TrimStrideWrapper;
import org.das2.qds.WritableDataSet;
import org.das2.qds.ops.CoerceUtil;

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

    private static final Logger logger= Logger.getLogger("jython");

    WritableDataSet ds;
    MutablePropertyDataSet mpds;
    QDataSet rods; // read-only dataset
    Units units; // indicates if the units have been set.
    int serialNumber;
    
    private static final AtomicInteger _seq= new AtomicInteger(1000);

    public PyQDataSet( ) {
        throw new IllegalArgumentException("no-arg constructor is not supported");
    }
    
    /**
     * Note getDataSet will always provide a writable dataset.
     * @param ds 
     */
    public PyQDataSet(QDataSet ds) {
        super(ds);
        this.serialNumber= _seq.incrementAndGet();
        
        if (ds instanceof WritableDataSet && !((WritableDataSet)ds).isImmutable() ) {
            this.ds = (WritableDataSet) ds;
            this.mpds= (MutablePropertyDataSet) ds;
            this.rods= ds;
        } else if ( ds instanceof MutablePropertyDataSet && !((MutablePropertyDataSet)ds).isImmutable() ) {
            this.ds = null;
            this.mpds= (MutablePropertyDataSet) ds;
            this.rods= ds;
        } else if (ds.rank() == 0) {
            this.ds = null;
            this.rods = ds;
        } else {
            logger.fine("read-only dataset will not support writing.");
            this.ds= null;
            this.rods= ds;
        }
        this.units= (Units)ds.property(QDataSet.UNITS);
    }

    public QDataSet getQDataSet() {
        return this.rods;
    }
    
    /**
     * return the serial number.
     * @return 
     */
    public int getSerialNumber() {
        return serialNumber;
    }
    
    /* plus, minus, multiply, divide */
    @Override
    public PyQDataSet __add__(PyObject arg0) {
        if ( arg0 instanceof PyInteger && !arg0.__nonzero__() ) { // special check to support "sum" which starts with zero.
            return this;
        }
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
    
    @Override
    public boolean __nonzero__() {
        if ( this.rods.rank()>0 ) {
            throw new IllegalArgumentException("data must be rank 0");
        }
        Units u= (Units)this.rods.property(QDataSet.UNITS);
        if ( u!=null && u.getOffsetUnits()!=u ) {
            throw new IllegalArgumentException("data must be dimensionless or a ratiometric datum.");
        } else {
            return this.rods.value()!=0;
        }
    }

    @Override
    public PyObject __int__() {
        if ( rods.rank()>0 ) {
            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
        }
        return Py.newInteger((int)rods.value());
    }

    @Override
    public PyFloat __float__() {
        if ( rods.rank()>0 ) {
            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
        }
        return Py.newFloat(rods.value());
    }

    @Override
    public PyLong __long__() {
        if ( rods.rank()>0 ) {
            throw Py.TypeError("PyQDataSet with rank="+rods.rank()+" found where rank 0 was expected");
        }
        return Py.newLong((int)rods.value());
    }

    
    private final static Map<String,PyReflectedFunction> binaryInfixMethods;
    static {
        binaryInfixMethods= new HashMap<>(); //TODO: what is this?
        binaryInfixMethods.put( "gt", new PyReflectedFunction("gt") );
        
        for ( Method m: BinaryInfixOps.class.getMethods() ) {
            PyReflectedFunction func= binaryInfixMethods.get(m.getName());
            if ( func==null ) {
                func= new PyReflectedFunction(m.getName());
                binaryInfixMethods.put( m.getName(), func );
            }
            func.addMethod(m);
        }
    }

    @Override
    public PyObject __ge__(PyObject o) {
        PyObject r= BinaryInfixOps.ge( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .ge operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __gt__(PyObject o) {
        PyObject r= BinaryInfixOps.gt( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .gt operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __le__(PyObject o) {
        PyObject r= BinaryInfixOps.le( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .le operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __lt__(PyObject o) {
        PyObject r= BinaryInfixOps.lt( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .lt operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __eq__(PyObject o) {
        PyObject r= BinaryInfixOps.eq( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .eq operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __ne__(PyObject o) {
        PyObject r= BinaryInfixOps.ne( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .ne operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __and__(PyObject o) {
        PyObject r= BinaryInfixOps.and( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .and operator");
        } else {
            return r;
        }
    }

    @Override
    public PyObject __or__(PyObject o) {
        PyObject r= BinaryInfixOps.or( this, o );
        if ( r instanceof PyQDataSet ) {
            throw new IllegalArgumentException("use .or operator");
        } else {
            return r;
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
        } else if ( name.equals("property") ) {
            if ( arg1 instanceof PyString ) {
                return Py.java2py(this.rods.property(arg1.toString()));
            } else {
                return super.invoke(name,arg1);
            }
        } else {
            return super.invoke(name,arg1);
        }
    }
    
    /**
     * experiment with making the dataset mutable, even after it has been 
     * made immutable.
     */
    private void makeMutable() {
        logger.log(Level.FINE, "makeMutable called using: {0}", rods);
        if ( ds==null ) {
            this.ds= Ops.copy(rods);
            this.mpds= ds;
            this.rods= ds;
        } else {
            this.ds= Ops.copy(ds);
            this.mpds= ds;
            this.rods= ds;            
        }

    }

    @Override
    public PyObject invoke(String name, PyObject arg1, PyObject arg2) {
        PyReflectedFunction func= binaryInfixMethods.get(name);
        if ( func!=null ) {
            return func.__call__(this,arg1,arg2);
        } else {
            switch (name) {
                case "putProperty":
                    if ( mpds==null || this.mpds.isImmutable() ) {
                        makeMutable();
                    }
                    this.putProperty( (PyString)arg1, arg2 );
                    return Py.None;
                case "property":
                    if ( arg1 instanceof PyString && arg2 instanceof PyInteger ) {
                        return Py.java2py(this.rods.property(arg1.toString(),((PyInteger)arg2).getValue()));
                    } else {
                        return super.invoke(name,arg1,arg2);
                    }
                default:
                    return super.invoke(name,arg1,arg2);
            }
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
            return super.invoke(name,args);
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

    /**
     * bug 1623, where we might want to interpret the PyList as 
     * a rank 1 dataset.
     * @param arg0 any python object
     * @return the same object, or rank 1 PyQDataSet when PyList can be adapted.
     */
    private PyObject maybeAdaptList( PyObject arg0 ) {
        if ( arg0 instanceof PyList ) {
            PyList list= ((PyList)arg0);
            if ( list.size()>0 ) {
                Object o= list.get(0);
                if ( o instanceof Number ) {
                    arg0= new PyQDataSet( PyQDataSetAdapter.adaptList(list) );
                } else if ( o instanceof QDataSet || o instanceof Datum ) {
                    arg0= new PyQDataSet( PyQDataSetAdapter.adaptList(list) );
                } else if ( o instanceof PyInteger || o instanceof PyFloat || o instanceof PyLong ) {
                    arg0= new PyQDataSet( PyQDataSetAdapter.adaptList(list) );
                }
            }
        }
        return arg0;
    }
    
    /* accessor and mutator */
    /**
     * This implements the Python indexing, such as data[4,:,3:5].  Note this
     * includes many QDataSet operations: a single index represents a slice, a 
     * range like 3:5 is a trim, an array is a sort, and a colon leaves a dimension
     * alone.  See http://autoplot.org/developer.python.indexing
     *
     * TODO: preserve metadata
	 * TODO: verify all index types.
     * @param arg0 various python types http://autoplot.org/developer.python.indexing
     * @return element or subset of data.
     */
    @Override
    public PyObject __getitem__(PyObject arg0) {
        if ( arg0 instanceof PyList ) {
            arg0= maybeAdaptList((PyList)arg0);
        }
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

                QDataSet sds= rods.slice(idx); //TODO: why is this not the writable dataset, or a copy of it?
                //TODO: properties and context.
                return new PyQDataSet( sds );
                
            } else if (arg0.isSequenceType()) {
                PySequence slices = (PySequence) arg0;
                if ( slices.__len__()==2 && slices.__getitem__(1) instanceof PyInteger ) { // sf 3473406: optimize for ds[:,0] to use unbundle
                    if ( slices.__getitem__(0) instanceof PySlice ) {
                        int index= ((Number)slices.__getitem__(1).__tojava__( Number.class )).intValue();
                        if ( index<0 ) index= rods.length(0) + index;
                        QDataSet unb1= DataSetOps.unbundle( rods, index, false );
                        PySlice slice = (PySlice) slices.__getitem__(0);
                        if ( slice.start instanceof PyNone && slice.stop instanceof PyNone && slice.step instanceof PyNone ) {
                            return new PyQDataSet( unb1 );
                        } else if ( slice.step instanceof PyNone || ((Number)slice.step.__tojava__(Number.class)).intValue()==1 ) { // use native trim if possible.
                            int start= slice.start.isNumberType() ? ((Number)slice.start.__tojava__( Number.class )).intValue() : 0;
                            int stop=  slice.stop.isNumberType() ? ((Number)slice.stop.__tojava__( Number.class )).intValue() : unb1.length();
                            if ( start<0 ) start= unb1.length()+start;
                            if ( stop<0 ) stop= unb1.length()+stop;
                            return new PyQDataSet( unb1.trim(start,stop) );
                        }
                    }
                }
                if ( slices.__len__()>rods.rank() ) {
                    throw new IllegalArgumentException("rank "+slices.__len__()+" access on a rank "+rods.rank()+" dataset" );
                }
                
                Map<String,Object> bundleProps= new HashMap();
                
                QDataSet[] lists= new QDataSet[slices.__len__()];
                boolean allLists= true;
                boolean betterBeAllLists= false;
                QubeDataSetIterator iter = new QubeDataSetIterator(rods);
                for (int i = 0; i < slices.__len__(); i++) {
                    PyObject a = slices.__getitem__(i);
                    if ( ! ( a instanceof PyQDataSet ) && !( a instanceof PyInteger || a instanceof PyFloat ) ) {
                        allLists= false;
                    }
                    QubeDataSetIterator.DimensionIteratorFactory fit= null;
                    if (a instanceof PySlice) {
                        PySlice slice = (PySlice) a;
                        Number start = (Number) getNumber(slice.start); // TODO: for the 0th index and step=1, native trim can be used.
                        Number stop = (Number)  getNumber(slice.stop);
                        Number step = (Number)  getNumber(slice.step);
                        fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                    } else if ( a instanceof PyQDataSet ) {
                        Object o2 = a.__tojava__(QDataSet.class);
                        QDataSet that = (QDataSet) o2;
                        switch (that.rank()) {
                            case 0:
                                int idx = (int)(that.value());
                                fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                                break;
                            case 1:
                                fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                                break;
                            default:
                                betterBeAllLists= true;
                                break;
                        }
                        lists[i]= ((PyQDataSet)a).rods;
                    } else if (a.isNumberType()) {
                        int idx = ((Number) getNumber( a )).intValue();
                        fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                        if ( i==rods.rank()-1 ) {
                            QDataSet bds= (QDataSet) rods.property( "BUNDLE_"+i );
                            if ( bds!=null && rods.property( "DEPEND_"+i )==null ) { // https://sourceforge.net/p/autoplot/bugs/1478/
                                DataSetUtil.sliceProperties( bds, idx, bundleProps );
                            }
                        }
                        lists[i]= DataSetUtil.asDataSet( idx );
                    } else {
                        QDataSet that = coerce_ds(a);
                        fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                        lists[i]= DataSetUtil.asDataSet( that );
                    }

                    if ( fit!=null ) iter.setIndexIteratorFactory(i, fit);
                }
                
                if ( betterBeAllLists && !allLists ) {
                    throw new IllegalArgumentException("index error, because all indeces must be lists.");
                }
                    
                ArrayDataSet result;
                if ( allLists && lists.length == rods.rank() ) {
                    lists = checkIndexBundle( lists );
                    result= getItemAllLists( lists );
                    
                } else {

                    result= iter.createEmptyDs();

                    QubeDataSetIterator resultIter = new QubeDataSetIterator(result);
                    while (iter.hasNext()) {
                        iter.next();
                        double d = iter.getValue(rods);
                        resultIter.next();
                        resultIter.putValue(result, d);
                    }
                }
                
                DataSetUtil.copyDimensionProperties( rods, result );

                if ( !bundleProps.isEmpty() ) { 
                    DataSetUtil.putProperties( bundleProps, result );
                }
                
                return new PyQDataSet(result);
            } else {
                throw Py.TypeError("invalid index type: "+arg0);
            }
        } else {
            QDataSet that = (QDataSet) o;
            
            DataSetIterator iter = new QubeDataSetIterator(rods);

            QDataSet dep0= null;
            
            if ( that.rank()>1 && ( SemanticOps.isBundle(that) || SemanticOps.isLegacyBundle(that) ) ) {
                for ( int j=0; j<that.length(0); j++ ) {
                    QDataSet that1= DataSetOps.unbundle(that,j);
                    QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory( that1 );
                    try {
                        ((QubeDataSetIterator)iter).setIndexIteratorFactory(j, fit);
                        if ( j==0 ) {
                            dep0= (QDataSet) rods.property(QDataSet.DEPEND_0);
                            if ( dep0!=null ) {
                                dep0= DataSetOps.applyIndex( dep0, 0, that1, false );
                            }
                        }
                    } catch ( ArrayIndexOutOfBoundsException ex ) {
                        ArrayIndexOutOfBoundsException ex1= new ArrayIndexOutOfBoundsException("array index is out of bounds because of expression like accumS[r] where r is rank 2 list of indeces.");
                        throw ex1;
                    }
                }
            } else if ( that.rank()==1 && SemanticOps.isRank1Bundle(that) ) {
                logger.fine( "getitem with rank 1 bundle of indices" );
                QDataSet sds= rods;
                for ( int i=0; i<that.length(); i++ ) {
                    sds= sds.slice( (int)that.value(i) );
                }
                return new PyQDataSet( sds );
                
            } else if ( that.rank()==0 ) {
                QDataSet sds= rods.slice( (int)that.value() ); //TODO: why is this not the writable dataset, or a copy of it?
                return new PyQDataSet( sds );
                
            } else if ( that.rank()>1 ) {
                WritableDataSet result= getItemAllLists( new QDataSet[] { that } );
                DataSetUtil.copyDimensionProperties( rods, result );
                return new PyQDataSet(result);
                
            } else {
                QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                ((QubeDataSetIterator)iter).setIndexIteratorFactory(0, fit);        
                dep0= (QDataSet) rods.property(QDataSet.DEPEND_0);
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
            DataSetUtil.copyDimensionProperties( rods, result );
            
            return new PyQDataSet(result);
        }
    }

    /**
     * Assign the values to the indeces specified.  
     * Note, if the value has units and the PyQDataSet does not yet have units, 
     * then the units are assigned.
     * See http://autoplot.org/developer.python.indexing
     * @param arg0 the indeces
     * @param arg1 the values to assign
     */
    @Override
    public void __setitem__(PyObject arg0, PyObject arg1) {
        if ( ds==null || ds.isImmutable() ) {
            makeMutable();
        }
        DataSetIterator iter = new QubeDataSetIterator(ds);
        if ( arg0 instanceof PyList ) {
            arg0= maybeAdaptList((PyList)arg0);
        }
        if (!arg0.isSequenceType()) {
            PyObject a = arg0;
            QubeDataSetIterator.DimensionIteratorFactory fit;
            if (a instanceof PySlice) {
                PySlice slice = (PySlice) a;
                Integer start = ( slice.start==Py.None ) ? null : getInteger( slice.start );
                Integer stop = ( slice.stop==Py.None ) ? null : getInteger( slice.stop );
                Integer step = ( slice.step==Py.None ) ? null : getInteger( slice.step );
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
                if ( SemanticOps.isRank1Bundle(that) ) {
                    for ( int k=0; k<that.length(); k++ ) {
                        QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory(that.slice(k));
                        ((QubeDataSetIterator) iter).setIndexIteratorFactory(k, fit);
                    }
                } else {
                    iter = new IndexListDataSetIterator(that);
                }
            } else {
                QubeDataSetIterator.DimensionIteratorFactory fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                ((QubeDataSetIterator) iter).setIndexIteratorFactory(0, fit);
            }

        } else { // arg0.isSequenceType
            PySequence slices = (PySequence) arg0;
            QDataSet[] lists= new QDataSet[slices.__len__()];
            boolean allLists= true;
            int[] qubedims= DataSetUtil.qubeDims(ds);
            for (int i = 0; i < slices.__len__(); i++) {
                PyObject a = slices.__getitem__(i);
                if (!(a instanceof PyQDataSet) && !(a instanceof PyInteger || a instanceof PyFloat)) {
                    allLists= false;
                } else if (a instanceof PyInteger || a instanceof PyFloat) {
                    int idx;
                    if (a instanceof PyInteger) {
                        idx = ((PyInteger) a).getValue();
                    } else {
                        idx = (int) (((PyFloat) a).getValue());
                    }
                    if (idx < 0) {
                        if (i == 0 || qubedims != null) {
                            idx = (i == 0) ? (ds.length() + idx) : (qubedims[i] + idx);
                        }
                    }
                    lists[i] = DataSetUtil.asDataSet(idx);
                } else {
                    lists[i] = ((PyQDataSet) a).rods;
                }
            }
            if (allLists) {
                QDataSet val = coerceDsInternal(arg1);
                lists = checkIndexBundle(lists);
                setItemAllLists(lists, val);
                if (units == null) { // see repeat code below.  Return requires repetition.
                    logger.fine("resetting units based on values assigned");
                    Units u = SemanticOps.getUnits(val);
                    if (u != Units.dimensionless) {
                        this.ds.putProperty(QDataSet.UNITS, u);
                    }
                    units = u;
                }

                return;

            } else {
                int[] qubeDims = DataSetUtil.qubeDims(ds);
                for (int i = 0; i < slices.__len__(); i++) {
                    PyObject a = slices.__getitem__(i);
                    QubeDataSetIterator.DimensionIteratorFactory fit;
                    if (a instanceof PySlice) {
                        PySlice slice = (PySlice) a; // TODO: why not the same as 75 lines prior?
                        Integer start = (Integer) slice.start.__tojava__(Integer.class);
                        Integer stop = (Integer) slice.stop.__tojava__(Integer.class);
                        Integer step = (Integer) slice.step.__tojava__(Integer.class);
                        fit = new QubeDataSetIterator.StartStopStepIteratorFactory(start, stop, step);

                    } else if (a.isNumberType() && !(a instanceof PyQDataSet)) {
                        if (a instanceof PyFloat) {
                            throw new IllegalArgumentException("float used to index array");
                        }
                        int idx = (Integer) a.__tojava__(Integer.class);
                        if (idx < 0) {
                            if (i == 0 || qubeDims != null) {
                                idx = i == 0 ? (ds.length() + idx) : (qubeDims[i] + idx);
                            } else {
                                throw new IllegalArgumentException("negative index not supported for non-qube.");
                            }
                        }
                        fit = new QubeDataSetIterator.SingletonIteratorFactory(idx);
                    } else {
                        QDataSet that = coerce_ds(a);
                        if (that.rank() == 0) {
                            fit = new QubeDataSetIterator.SingletonIteratorFactory((int) that.value());
                        } else {
                            fit = new QubeDataSetIterator.IndexListIteratorFactory(that);
                        }
                    }

                    ((QubeDataSetIterator) iter).setIndexIteratorFactory(i, fit);
                }
            }
        }

        QDataSet val = coerceDsInternal(arg1);
        if ( units==null ) { // see repeat code above.
            logger.fine("resetting units based on values assigned");
            Units u= SemanticOps.getUnits(val);
            if ( u!=Units.dimensionless ) this.ds.putProperty(QDataSet.UNITS,u);
            units= u;
        }
        
        // figure out what the fill value will be.
        Number fill= (Number)val.property(QDataSet.FILL_VALUE);
        if ( fill!=null ) {
            if ( this.ds.property(QDataSet.FILL_VALUE)!=null ) {
                fill= (Number)this.ds.property(QDataSet.FILL_VALUE);
            }
        } else {
            fill= (Number)this.ds.property(QDataSet.FILL_VALUE);
        }
        
        boolean resultHasFill= false;
        if ( fill==null ) {
            fill= -1e38;
        }
        double dfill= fill.doubleValue();

        UnitsConverter uc;
        try {
            uc= SemanticOps.getUnits(val).getConverter(units);
        } catch ( InconvertibleUnitsException ex ) {
            uc= UnitsConverter.IDENTITY;
        }
                
        // see org.das2.qds.ops.CoerceUtil, make version that makes iterators.
        if ( val.rank()==0 ) {
            if ( Ops.valid(val).value()==0 ) {
                while (iter.hasNext()) {
                    iter.next();
                    iter.putValue(ds, dfill );
                    resultHasFill= true;
                }
            } else {
                double d = uc.convert(val.value());
                while (iter.hasNext()) {
                    iter.next();
                    iter.putValue(ds, d);
                }
            }
        } else if ( val.rank()!=iter.rank() ) {
            throw new IllegalArgumentException("not supported, couldn't reconcile ranks in set[" + val + "]=" + iter );
        } else {
            QDataSet wds= DataSetUtil.weightsDataSet(val);
            QubeDataSetIterator it = new QubeDataSetIterator(val);
            while (it.hasNext()) {
                it.next();
                if ( !iter.hasNext() ) throw new IllegalArgumentException("assigned dataset has too many elements");
                iter.next();
                double w = it.getValue(wds);
                if ( w==0 ) {
                    double d = dfill;
                    iter.putValue(ds, d);
                    resultHasFill= true;
                } else {
                    double d = uc.convert(it.getValue(val));
                    iter.putValue(ds, d);
                }
            }
            if ( iter.hasNext() ) {
                iter.next();
                if ( iter.hasNext() ) {
                    throw new IllegalArgumentException("assigned dataset has too few elements");
                } else {
                    logger.log(Level.INFO, "dataset assignment looks suspect, where there is an extra element which was not assigned: {0}", iter);
                }
                
            }
        }
        
        if ( resultHasFill ) {
            Number tfill= (Number)this.ds.property(QDataSet.FILL_VALUE);
            if ( tfill==null ) {
                logger.fine("add FILL_VALUE to dataset");
                this.ds.putProperty( QDataSet.FILL_VALUE, fill );
            }
        }
                
    }

    /**
     * the output of the where command of a rank N dataset is a rank 2 dataset
     * idx[M,N] where M cases where the condition was true.  This means that
     * the first dataset might be a bundle of indeces, and here we break them
     * out to N datasets.
     * @see https://github.com/autoplot/dev/blob/master/bugs/2134/indexWithRank2_case5.jy
     * @param lists
     * @return 
     */
    private QDataSet[] checkIndexBundle(QDataSet[] lists) {
        if ( lists.length<rods.rank() ) {
            if ( lists[0].rank()==2 && SemanticOps.isLegacyBundle(lists[0]) ) { // output of "where" command
                logger.log(Level.FINER, "bundle of indices found: {0}", lists[0]);
                QDataSet[] newLists= new QDataSet[lists[0].length(0)+lists.length-1];
                for ( int j=0; j<lists[0].length(0); j++ ) {
                    newLists[j]= Ops.unbundle(lists[0],j);
                }
                int ick=1;
                for ( int j=lists[0].length(0); j<newLists.length; j++ ) {
                    newLists[j]= lists[ ick ];
                    ick++;
                }
                lists= newLists;
            }
        }
        return lists;
    }
    
    /**
     * encapsulate the logic where we can interpret a int, float or rank 0 dataset as an int.
     * @param o rank 0 dataset, int or float.
     * @return integer interpretation of the value.
     */
    private static int getInteger( PyObject obj ) {
        if ( obj instanceof PyQDataSet ) {
            PyQDataSet pds= (PyQDataSet)obj;
            if ( pds.rods.rank()!=0 ) {
                throw new IllegalArgumentException("QDataSet cannot be interpreted as integer, because its rank is greater than 0");
            } else {
                return (int)pds.rods.value();
            }
        } else {
            return ( (Number) obj.__tojava__(Number.class) ).intValue();
        }
    }

    /**
     * convert the object into the type needed for the property.
     * @param context the dataset to which we are assigning the value.
     * @param name the property name
     * @param value the value
     * @return the correct value.
     * @see org.das2.qds.ops.Ops#convertPropertyValue
     */
    private static Object convertPropertyValue( QDataSet context, String name, Object value ) {
        
        if ( value==null ) return value;
        
        if ( value instanceof PyObject ) {
            PyObject pyvalue= (PyObject)value;
            if ( value instanceof PyQDataSet ) {
                value= pyvalue.__tojava__(QDataSet.class);
            } else if ( value instanceof PyDatum ) {
                value= pyvalue.__tojava__(Datum.class);
            } else if ( value instanceof PyInteger ) {
                value= pyvalue.__tojava__(Integer.class);
            } else if ( value instanceof PyFloat ) {
                value= pyvalue.__tojava__(Float.class);
            } else if ( value instanceof PyLong ) {
                value= pyvalue.__tojava__(Long.class);
            } else if ( value instanceof PyString ) {
                value= pyvalue.__tojava__(String.class);
            } else {
                value= pyvalue.__tojava__(Object.class);
            }
        }

        value= Ops.convertPropertyValue( context, name, value );
        return value;
        
    }

    /* we need to wrap put methods as well... */
    public void putProperty( PyString prop, Object value ) {
        if ( mpds==null || mpds.isImmutable() ) {
            throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        }
        
        String sprop= prop.toString();
        
        if ( value.equals(Py.None) ) {
            value= null;
        } 
        
        value= convertPropertyValue( rods, prop.toString(), value );

        mpds.putProperty(sprop, value);
        
    }
    
    public void putProperty( PyString prop, int index, Object value ) {
        if ( mpds==null || mpds.isImmutable() ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        String sprop= prop.toString();
        
        if ( value.equals(Py.None) ) {
            value= null;
        } else if ( value instanceof PyObject ) {
            Class clas= DataSetUtil.getPropertyClass(prop.toString() );
            PyObject po = (PyObject)value;
            value= po.__tojava__(clas);
        }
        mpds.putProperty(sprop,index,value);
        
    }
    
    public void putValue( double value ) {
        if ( ds==null ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        ds.putValue(value);
    }
    public void putValue( int i0, double value ) {
        if ( ds==null ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        ds.putValue(i0,value);
    }
    public void putValue( int i0, int i1, double value ) {
        if ( ds==null ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        ds.putValue(i0,i1,value);
    }
    public void putValue( int i0, int i1, int i2, double value ) {
        if ( ds==null ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        ds.putValue(i0,i1,i2,value);
    }
    public void putValue( int i0, int i1, int i2, int i3, double value ) {
        if ( ds==null ) throw new RuntimeException("putProperty on dataset that could not be made into mutable, use copy.");
        ds.putValue(i0,i1,i2,i3,value);
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
                Object o2= arg0.__tojava__( Object.class );
                if ( o2 instanceof Number ) {
                    double d = ((Number)o2).doubleValue();
                    return DataSetUtil.asDataSet(d);
                } else {
                    QDataSet do2;
                    if ( o2 instanceof org.das2.datum.TimeUtil.TimeStruct ) {
                        do2= DataSetUtil.asDataSet( org.das2.datum.TimeUtil.toDatum( (org.das2.datum.TimeUtil.TimeStruct)o2) );
                    } else if ( o2 instanceof org.das2.datum.Datum ) {
                        do2= DataSetUtil.asDataSet( (org.das2.datum.Datum)o2 );
                    } else if ( o2 instanceof org.das2.datum.DatumRange ) {
                        do2= DataSetUtil.asDataSet( (org.das2.datum.DatumRange)o2 );
                    } else {
                        throw new ClassCastException("unable to convert: "+arg0);
                    }
                    return do2;
                }
                
            } else if (arg0 instanceof PyString ) {
                try {
                    if ( units!=null ) {
                        if ( units instanceof EnumerationUnits ) {
                            return DataSetUtil.asDataSet(((EnumerationUnits)units).createDatum(arg0.toString()));
                        } else {
                            // do not attempt to interpret this with is dataset's units because it might be intentionally dimensionless.
                            return DataSetUtil.asDataSet(DatumUtil.parse(arg0.toString()));
                        }
                    } else {
                        return DataSetUtil.asDataSet(DatumUtil.parse(arg0.toString()));
                    }
                } catch (ParseException ex) {
                    throw new IllegalArgumentException(ex);
                }
            } else if (arg0.isSequenceType()) {
                return PyQDataSetAdapter.adaptList((PyList) arg0);
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            QDataSet lds = (QDataSet) o;
            if (lds.rank() == 0) {
                // QDataSet library handles coerce logic.
                return lds;
            } else {
                return lds;
            }
        }

    }

    public PyQDataSet append( PyObject arg0 ) {
        Object o = arg0.__tojava__(QDataSet.class);
        DDataSet result;
        if (o == null || o == Py.NoConversion) {
            if (arg0.isNumberType()) {
                double d = (Double) arg0.__tojava__(Double.class);
                result= (DDataSet) ArrayDataSet.copy( double.class, rods);
                result= (DDataSet) ArrayDataSet.append( result, DDataSet.wrap( new double[] { d } ) );
            } else if (arg0 instanceof PyList) {
                result= (DDataSet) ArrayDataSet.copy( double.class, rods );
                result= (DDataSet) ArrayDataSet.append( result, DDataSet.copy( PyQDataSetAdapter.adaptList((PyList) arg0) ) );
            } else if (arg0.isSequenceType()) {
                throw Py.TypeError("unable to coerce sequence: " + arg0);
            } else {
                throw Py.TypeError("unable to coerce: " + arg0);
            }
        } else {
            QDataSet lds = (QDataSet) o;
            result= (DDataSet) ArrayDataSet.copy( double.class, rods);
            result= (DDataSet) ArrayDataSet.append( result, DDataSet.copy( lds ) );
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
                    result = new PyQDataSet( rods.slice(i) );
                    i++;
                    return result;
                } else {
                    return null;
                }
            }
        };
    }

    /**
     * This is what does the magical coersion, see https://sourceforge.net/p/autoplot/bugs/1861/
     * @param c the class needed.
     * @return instance of the class if available.
     */
    @Override
    public Object __tojava__(Class c) {
        if ( c.isArray() && c.getComponentType()==double.class && rods.rank()==1 ) {
            double[] result= new double[rods.length()];
            for ( int i=0; i< rods.length(); i++  ) {
                result[i]= rods.value(i);
            }
            return result;
        } else if ( c.isAssignableFrom(QDataSet.class) ) {
            return rods;
        } else if ( c.isAssignableFrom(MutablePropertyDataSet.class) ) {
            return mpds;
        } else if ( c.isAssignableFrom(WritableDataSet.class) ) {
            return ds;
        } else if ( rods.rank()==0 ) {
            logger.fine("this is not supported because the double version would be used where a dataset would work.");
            // See sftp://nudnik.physics.uiowa.edu/home/jbf/project/autoplot/tests/1861/demo1861UsesDoubleNotQDataSet.jy
            //Datum datum= DataSetUtil.asDatum(rods);
//            if ( false && datum.getUnits()==Units.dimensionless ) {
//                if ( c==double.class ) {
//                    return datum.value();
//                } else if ( c==Double.class ) {
//                    return datum.value();
//                } else if ( c==float.class ) {
//                    return (float)datum.value();
//                } else if ( c==Float.class ) {
//                    return (float)datum.value();
//                } else if ( c==Number.class ) {
//                    return datum.value();
//                }
//            } else {
                logger.fine("data must be dimensionless to be used as a double.");
            //}
        }
        return super.__tojava__(c);
    }


    @Override
    public String toString() {
        return "" + rods.toString() + " (pyqds)";
    }

    @Override
    public boolean isNumberType() {
        return this.rods.rank()==0;
    }

    /**
     * handle special case where rank 1 datasets are used to index a rank N array.
     * @param lists datasets of rank 0 or rank 1
     * @param val the value (or values) to assign.
     * @return the array extracted.
     */
    private void setItemAllLists( QDataSet[] lists, QDataSet val ) {
                
        QDataSet[] ll= new QDataSet[2];
        ll[0]= lists[0];
        for ( int i=1; i<lists.length; i++) {
            ll[1]= lists[i];
            CoerceUtil.coerce( ll[0], ll[1], false, ll );
            lists[0]= ll[0];
            lists[i]= ll[1];
        }
        for ( int i=1; i<lists.length; i++) {
            ll[1]= lists[i];
            CoerceUtil.coerce( ll[0], ll[1], false, ll );
            lists[0]= ll[0];
            lists[i]= ll[1];
        }
        CoerceUtil.coerce( ll[0], val, false, ll );
        val= ll[1];
        QubeDataSetIterator it = new QubeDataSetIterator( val );

        switch (lists[0].rank()) {  // all datasets in lists[] will have the same rank.
            case 0:
                switch (ds.rank()) {
                    case 1:
                        it.next();
                        ds.putValue( (int)lists[0].value(), it.getValue(val));
                        break;
                    case 2:
                        it.next();
                        ds.putValue( (int)lists[0].value(), (int)lists[1].value(), it.getValue(val));
                        break;
                    case 3: 
                        it.next();
                        ds.putValue(
                            (int)lists[0].value(),
                            (int)lists[1].value(),
                            (int)lists[2].value(), it.getValue(val));
                        break;
                    case 4:
                        it.next();
                        ds.putValue( 
                            (int)lists[0].value(),
                            (int)lists[1].value(),
                            (int)lists[2].value(),
                            (int)lists[3].value(), it.getValue(val));
                        break;
                    default:
                        break;
                }   break;
            case 1:
                int n= lists[0].length();
                switch (ds.rank()) {
                    case 1:
                        for ( int i=0;i<n;i++ ) {
                            it.next();
                            ds.putValue( (int)lists[0].value(i), it.getValue(val));
                        }
                        break;
                    case 2:
                        for ( int i=0;i<n;i++ ) {
                            it.next();
                            ds.putValue( (int)lists[0].value(i), (int)lists[1].value(i), it.getValue(val));
                        }
                        break;
                    case 3:
                        for ( int i=0;i<n;i++ ) {
                            it.next();
                            ds.putValue( 
                                (int)lists[0].value(i),
                                (int)lists[1].value(i),
                                (int)lists[2].value(i), it.getValue(val));
                        }
                        break;
                    case 4:
                        for ( int i=0;i<n;i++ ) {
                            it.next();
                            ds.putValue( 
                                (int)lists[0].value(i),
                                (int)lists[1].value(i),
                                (int)lists[2].value(i),
                                (int)lists[3].value(i), it.getValue(val));
                        }
                        break;
                    default:
                        break;
                }   break;
            default:
                QubeDataSetIterator iter= new QubeDataSetIterator(lists[0]);
                switch ( rods.rank() ) {
                    case 1:
                        while ( it.hasNext() ) {
                            it.next();
                            iter.next();
                            double d= it.getValue(val);
                            ds.putValue( (int)iter.getValue( lists[0] ), d );
                        }
                        break;
                    case 2:
                        while ( it.hasNext() ) {
                            it.next();
                            iter.next();
                            double d= it.getValue(val);
                            ds.putValue( (int)iter.getValue( lists[0] ), (int)iter.getValue( lists[1] ), d );
                        }
                        break;
                    case 3:
                        while ( it.hasNext() ) {
                            it.next();
                            iter.next();
                            double d= it.getValue(val);
                            ds.putValue(
                                (int)iter.getValue( lists[0] ),
                                (int)iter.getValue( lists[1] ), 
                                (int)iter.getValue( lists[2] ), d );
                        }
                        break;
                    case 4:
                        while ( it.hasNext() ) {
                            it.next();
                            iter.next();
                            double d= it.getValue(val);
                            ds.putValue(
                                (int)iter.getValue(lists[0]), 
                                (int)iter.getValue(lists[1]),
                                (int)iter.getValue(lists[2]), 
                                (int)iter.getValue(lists[3]), d );
                        }
                        break;
                }   break;
        }
        
    }
    
    /**
     * handle special case where rank 1 datasets are used to index a rank N array.
     * @param lists datasets of rank 0 or rank 1
     * @return the array extracted.
     */
    private ArrayDataSet getItemAllLists( QDataSet[] lists ) {
                
        QDataSet[] ll= new QDataSet[2];
        ll[0]= lists[0];
        for ( int i=1; i<lists.length; i++) {
            ll[1]= lists[i];
            CoerceUtil.coerce( ll[0], ll[1], false, ll );
            lists[0]= ll[0];
            lists[i]= ll[1];
        }
        
        ArrayDataSet result;
        switch (lists[0].rank()) {
            case 0:
                result= ArrayDataSet.createRank0( ArrayDataSet.guessBackingStore(rods) );
                break;
            case 1:
                result= ArrayDataSet.createRank1( ArrayDataSet.guessBackingStore(rods), lists[0].length() );
                break;
            default:
                result= ArrayDataSet.create( ArrayDataSet.guessBackingStore(rods), DataSetUtil.qubeDims( lists[0] ) );
                break;
        }

        switch (lists[0].rank()) { // all datasets in lists[] will have the same rank.
            case 0:
                switch (rods.rank()) {
                    case 1:
                        result.putValue( rods.value( (int)lists[0].value() ) );
                        break;
                    case 2:
                        result.putValue( rods.value( (int)lists[0].value(), (int)lists[1].value() ) );
                        break;
                    case 3:
                        result.putValue( rods.value(
                            (int)lists[0].value(),
                            (int)lists[1].value(),
                            (int)lists[2].value() ) );
                        break;
                    case 4:
                        result.putValue( rods.value(
                            (int)lists[0].value(),
                            (int)lists[1].value(),
                            (int)lists[2].value(),
                            (int)lists[3].value() ) );
                        break;
                    default:
                        break;
                }   break;
            case 1:
                int n= lists[0].length();
                switch (rods.rank()) {
                    case 1:
                        for ( int i=0;i<n;i++ ) {
                            result.putValue( i, rods.value( (int)lists[0].value(i) ) );
                        }
                        break;
                    case 2:
                        for ( int i=0;i<n;i++ ) {
                            result.putValue( i, rods.value( (int)lists[0].value(i), (int)lists[1].value(i) ) );
                        }
                        break;
                    case 3:
                        for ( int i=0;i<n;i++ ) {
                            result.putValue( i,
                                rods.value(
                                    (int)lists[0].value(i),
                                    (int)lists[1].value(i),
                                    (int)lists[2].value(i) ) );
                        }
                        break;
                    case 4:
                        for ( int i=0;i<n;i++ ) {
                            result.putValue( i,
                                rods.value(
                                    (int)lists[0].value(i),
                                    (int)lists[1].value(i),
                                    (int)lists[2].value(i),
                                    (int)lists[3].value(i) ) );
                        }
                        break;
                    default:
                        break;
                }   break;
            default:
                QubeDataSetIterator iter= new QubeDataSetIterator( result );
                switch ( rods.rank() ) {
                    case 1:
                        while ( iter.hasNext() ) {
                            iter.next();
                            double d= rods.value( (int)iter.getValue(lists[0]) );
                            iter.putValue( result, d );
                        }
                        break;
                    case 2:
                        while ( iter.hasNext() ) {
                            iter.next(); 
                            double d= rods.value(
                                (int)iter.getValue(lists[0]),
                                (int)iter.getValue(lists[1]) );
                            iter.putValue( result, d );
                        }
                        break;
                    case 3:
                        while ( iter.hasNext() ) {
                            iter.next();
                            double d= rods.value(
                                (int)iter.getValue(lists[0]), 
                                (int)iter.getValue(lists[1]),
                                (int)iter.getValue(lists[2]) );
                            iter.putValue( result, d );
                        }
                        break;
                    case 4:
                        while ( iter.hasNext() ) {
                            iter.next();
                            double d= rods.value(
                                (int)iter.getValue(lists[0]), 
                                (int)iter.getValue(lists[1]),
                                (int)iter.getValue(lists[2]), 
                                (int)iter.getValue(lists[3]) );
                            iter.putValue( result, d );
                        }
                        break;
                }   break;
        }
        return result;
        
    }

}
