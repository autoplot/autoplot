
package org.autoplot.jythonsupport;

import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.python.core.Py;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PyLong;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

/**
 * Wrap a das2 Datum, implementing operators.
 * @author jbf
 */
public class PyDatum extends PyJavaInstance {
    
    private static final Logger logger= Logger.getLogger("jython");

    Datum datum;
    
    public PyDatum( Datum d ) {
        super(d);
        this.datum= d;
    }
    
    /**
     * create a PyDatum by parsing to Datum with Ops.datum.
     * @param s 
     */
    public PyDatum( String s ) {
        this(Ops.datum(s));
    }
    
    /* plus, minus, multiply, divide */
    @Override
    public PyObject __add__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( datum.add( ((PyDatum)arg0).datum ) );
        } else if ( arg0 instanceof PyQDataSet ) {
            return arg0.__radd__(this);
        } else if ( arg0.isNumberType() ) {
            return new PyDatum( datum.add( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );            
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__add__(arg0);
        }        
    }

    @Override
    public PyObject __radd__(PyObject arg0) {
        return __add__(arg0);
    }

    @Override
    public PyObject __sub__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( datum.subtract(((PyDatum)arg0).datum ) );
        } else if ( arg0 instanceof PyQDataSet ) {
            return arg0.__rsub__(this);         
        } else if ( arg0.isNumberType() ) {
            return new PyDatum( datum.subtract( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );                        
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__sub__(arg0);
        }
    }

    @Override
    public PyObject __rsub__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( (((PyDatum)arg0).datum ).subtract(datum) );
        } else if ( arg0.isNumberType() ) {
            return new PyDatum( datum.getUnits().createDatum( arg0.__float__().getValue() ).subtract( datum ) );
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__rsub__(arg0);
        }
    }

    @Override
    public PyObject __mul__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( datum.multiply( ((PyDatum)arg0).datum ) );
        } else if ( arg0 instanceof PyQDataSet  ) {
            return arg0.__rmul__( this );
	    } else if ( arg0.isNumberType() ) {
            return new PyDatum( datum.multiply( Units.dimensionless.createDatum( arg0.__float__().getValue() ) ) );               
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__mul__(arg0);
        } 
    }

    @Override
    public PyObject __rmul__(PyObject arg0) {
        return __mul__(arg0);
    }

    @Override
    public PyObject __div__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( datum.divide( ((PyDatum)arg0).datum ) );
        } else if ( arg0 instanceof PyQDataSet ) {
            return arg0.__rdiv__( this );
        } else if ( arg0.isNumberType() ) {
            return new PyDatum( datum.divide( Units.dimensionless.createDatum( arg0.__float__().getValue() ) ) );               
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__div__(arg0);
        } 
    }

    @Override
    public PyObject __rdiv__( PyObject arg0 ) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( ((PyDatum)arg0).datum.divide(datum) );
        } else if ( arg0.isNumberType() ) {
            return new PyDatum( Units.dimensionless.createDatum( arg0.__float__().getValue() ).divide( datum ) );        
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__rdiv__(arg0);
        }
    }

    @Override
    public PyObject __floordiv__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            double d= this.datum.doubleValue( datum.getUnits() );
            double divisor= ((PyDatum)arg0).datum.doubleValue(datum.getUnits());
            d= Math.floor( d/divisor );
            return new PyDatum( Units.dimensionless.createDatum(d) );
        } else if ( arg0.isNumberType() ) {
            double d= this.datum.doubleValue( datum.getUnits() );
            double divisor= arg0.__float__().getValue();
            d= Math.floor( d/divisor );
            return new PyDatum( this.datum.getUnits().createDatum(d) );
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__floordiv__(arg0);
        }
    }

    @Override
    public PyObject __rfloordiv__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            double divisor= this.datum.doubleValue( datum.getUnits() );
            double d= ((PyDatum)arg0).datum.doubleValue(datum.getUnits());
            d= Math.floor( d/divisor );
            return new PyDatum( Units.dimensionless.createDatum(d) );
        } else if ( arg0.isNumberType() ) {
            double divisor= this.datum.doubleValue( datum.getUnits() );
            double d= arg0.__float__().getValue();
            d= Math.floor( d/divisor );
            return new PyDatum( this.datum.getUnits().createDatum(d) );            
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__rfloordiv__(arg0);
        }
    }

    @Override
    public PyObject __mod__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            Datum d1= this.datum;
            Units u1= d1.getUnits().getOffsetUnits();
            Datum d2= ((PyDatum)arg0).datum;
            Units u2= d2.getUnits();
            final UnitsConverter uc= u1.getConverter(u2);
            final double base= 0;
            double d= uc.convert( d1.value()-base ) % (d2.value());
            return new PyDatum( u2.createDatum(d) );
        } else if ( arg0.isNumberType() ) {
            Datum d1= this.datum;
            final double base= 0;
            double d= ( d1.value()-base ) % (arg0.__float__().getValue() );
            return new PyDatum( Units.dimensionless.createDatum(d) );
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__mod__(arg0);
        }
    }

    @Override
    public PyObject __rmod__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            Datum d2= this.datum;
            Units u2= d2.getUnits().getOffsetUnits();
            Datum d1= ((PyDatum)arg0).datum;
            Units u1= d1.getUnits();
            final UnitsConverter uc= u1.getConverter(u2);
            final double base= 0;
            double d= uc.convert( d1.value()-base ) % (d2.value());
            return new PyDatum( u2.createDatum(d) );
        } else if ( arg0.isNumberType() ) {
            Datum d1= this.datum;
            double divisor= d1.value();
            double d= ( arg0.__float__().getValue() ) % ( divisor );
            return new PyDatum( Units.dimensionless.createDatum(d) );
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__rmod__(arg0);
        }
    }

    /* unary negate and plus operator */
    @Override
    public PyObject __pos__() {
        return this;
    }

    @Override
    public PyObject __neg__() {
        return new PyDatum( this.datum.negative() );
    }

    @Override
    public PyObject __abs__() {
        return new PyDatum( this.datum.abs() );
    }

    /* pow operator (**) */
    @Override
    public PyObject __pow__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( datum.power( ((PyDatum)arg0).datum ) );
        } else if ( arg0.isNumberType() ) {
            double darg= ( arg0.__float__().getValue() );
            return new PyDatum( datum.power( darg ) );
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__pow__(arg0);
        }
    }

    @Override
    public PyObject __rpow__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return new PyDatum( ((PyDatum)arg0).datum.power( this.datum ) );
        } else if ( arg0.isNumberType() ) {
            double darg= ( arg0.__float__().getValue() );
            return new PyDatum( Units.dimensionless.createDatum( darg ).power( datum ) );            
        } else {
            return new PyQDataSet( DataSetUtil.asDataSet(datum) ).__rpow__(arg0);
        }
    }

    @Override
    public boolean __nonzero__() {
        Units u= datum.getUnits();
        if ( u!=null && u.getOffsetUnits()!=u ) {
            throw new IllegalArgumentException("data must be dimensionless or a ratiometric datum.");
        } else {
            return datum.value()!=0;
        }
    }

    @Override
    public PyObject __int__() {
        return Py.newInteger((int)datum.value());
    }

    @Override
    public PyFloat __float__() {
        return Py.newFloat((double)datum.value());
    }

    @Override
    public PyLong __long__() {
        //TODO: check on this.
        return Py.newLong( ((int)datum.value()) );
    }
    
    private static PyObject toPyBoolean( boolean r ) {
        return new PyInteger( r ? 1 : 0 );
    }
    
    @Override
    public PyObject __ge__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( datum.ge(((PyDatum)arg0).datum) );
        } else if ( arg0.isNumberType() ) {
            return toPyBoolean( datum.ge( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );            
        } else {
            return toPyBoolean( datum.ge(JythonOps.datum(arg0)) );
        }
    }
    

    @Override
    public PyObject __gt__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( datum.gt(((PyDatum)arg0).datum) );
        } else if ( arg0.isNumberType() ) {
            return toPyBoolean( datum.gt( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );            
        } else {
            return toPyBoolean( datum.gt(JythonOps.datum(arg0)) );
        }
    }

    @Override
    public PyObject __le__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( datum.le(((PyDatum)arg0).datum) );
        } else if ( arg0.isNumberType() ) {
            return toPyBoolean( datum.le( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );            
        } else {
            return toPyBoolean( datum.le(JythonOps.datum(arg0)) );
        }
    }

    @Override
    public PyObject __lt__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( datum.lt(((PyDatum)arg0).datum) );
        } else if ( arg0.isNumberType() ) {
            return toPyBoolean( datum.lt( datum.getUnits().createDatum( arg0.__float__().getValue() ) ) );
        } else {
            return toPyBoolean( datum.lt(JythonOps.datum(arg0)) );
        }
    }

    @Override
    public PyObject __eq__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( datum.equals(((PyDatum)arg0).datum) );
        } else {
            return toPyBoolean( datum.equals(JythonOps.datum(arg0)) );
        }
    }

    @Override
    public PyObject __ne__(PyObject arg0) {
        if ( arg0 instanceof PyDatum ) {
            return toPyBoolean( !datum.equals(((PyDatum)arg0).datum) );
        } else {
            return toPyBoolean( !datum.equals(JythonOps.datum(arg0)) );
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

    /**
     * This is what does the magical coersion, see https://sourceforge.net/p/autoplot/bugs/1861/
     * @param c the class needed.
     * @return instance of the class if available.
     */
    @Override
    public Object __tojava__(Class c) {
        logger.fine("this is not supported because the double version would be used where a dataset would work.");
        // See sftp://nudnik.physics.uiowa.edu/home/jbf/project/autoplot/tests/1861/demo1861UsesDoubleNotQDataSet.jy        
        if ( false && datum.getUnits()==Units.dimensionless ) {
            if ( c==double.class ) {
                return datum.value();
            } else if ( c==Double.class ) {
                return datum.value();
            } else if ( c==float.class ) {
                return (float)datum.value();
            } else if ( c==Float.class ) {
                return (float)datum.value();
            } else if ( c==Number.class ) {
                return datum.value();
            }    
        }
        return super.__tojava__(c);        
    }
    
    @Override
    public PyString __str__( ) {
        return new PyString( datum.toString() );
    }
}
