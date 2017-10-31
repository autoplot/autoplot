/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.python.core;

import java.lang.reflect.Method;

/**
 *
 * @author jbf
 */
public class PyReflectedFunctionPeeker {

    PyReflectedFunction f;

    public PyReflectedFunctionPeeker(PyReflectedFunction f) {
        this.f = f;
    }

    public int getArgsCount() {
        return this.f.nargs;
    }

    public Class getDeclaringClass(int iarg) {
        return this.f.argslist[iarg].declaringClass;
    }

    public Class[] getArguments(int iarg) {
        return this.f.argslist[iarg].args;
    }

    public Method getMethod(int i) {
        Class[] cs = getArguments(i);
        final Class declaringClass = getDeclaringClass(i);
        try {
            switch (cs.length) {
                case 0:
                    return declaringClass.getMethod(f.__name__);
                case 1:
                    return declaringClass.getMethod(f.__name__, cs[0]);
                case 2:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1]);
                case 3:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2]);
                case 4:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3]);
                case 5:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4]);
                case 6:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5]);
                case 7:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6]);
                case 8:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7]);
                case 9:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8]);
                case 10:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9]);
                case 11:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10]);
                case 12:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10],cs[11]);
                case 13:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10],cs[11],cs[12]);
                case 14:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10],cs[11],cs[12],cs[13]);
                case 15:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10],cs[11],cs[12],cs[13],cs[14]);
                case 16:
                    return declaringClass.getMethod(f.__name__, cs[0],cs[1],cs[2],cs[3],cs[4],cs[5],cs[6],cs[7],cs[8],cs[9],cs[10],cs[11],cs[12],cs[13],cs[14],cs[15],cs[16]);
                default:
                    throw new IllegalArgumentException("too many params ("+cs.length+")");
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getName() {
        return this.f.__name__;
    }
}
