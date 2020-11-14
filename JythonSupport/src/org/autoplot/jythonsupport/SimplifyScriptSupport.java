
package org.autoplot.jythonsupport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.python.core.PySyntaxError;
import org.python.parser.ast.If;
import org.python.parser.ast.Module;
import org.python.parser.ast.stmtType;
import org.das2.util.LoggerManager;
import org.python.core.PyJavaClass;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assert;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.BinOp;
import org.python.parser.ast.Call;
import org.python.parser.ast.Expr;
import org.python.parser.ast.Index;
import org.python.parser.ast.Name;
import org.python.parser.ast.Num;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.aliasType;
import org.python.parser.ast.exprType;

/**
 * AST support for Jython completions.  This is not meant to be thorough, but
 * instead should be helpful when working with scripts.  
 * @author jbf
 * @see JythonUtil#simplifyScriptToGetParams(java.lang.String, boolean) 
 */
public class SimplifyScriptSupport {
    
    private static final Logger logger= LoggerManager.getLogger("jython.simplify");
        
    private static final String GETDATASET_CODE= 
        "def getDataSet( uri, timerange='', monitor='' ):\n" +
        "    'return a dataset for the given URI'\n" +
        "    return dataset(0)\n\n";
    
    /**
     * Remove parts of the script which are expensive so that the script can
     * be run and completions offered.
     * TODO: What is the difference between this and simplifyScriptToCompletions?
     * @param script Jython script
     * @return simplified version of the script.
     * @see #simplifyScriptToCompletions(java.lang.String) 
     */
    public static String removeSideEffects( String script ) {
         String[] ss= script.split("\n");
         String lastLine= ss[ss.length-1].trim();
         if ( lastLine.endsWith(":") ) {
             ss= Arrays.copyOf(ss,ss.length-1);
             script= JythonUtil.join( ss, "\n" );
         }
         Module n= (Module)org.python.core.parser.parse( script, "exec" );
         HashSet variableNames= new HashSet();
         int ilastLine= ss.length;
         return simplifyScriptToGetCompletions( ss, n.body, variableNames, 1, ilastLine, 0 );
    }
    
    /**
     * useful for debugging
     * @param result
     * @param line
     * @return 
     */
     private static StringBuilder appendToResult( StringBuilder result, String line ) {
        result.append(line);
        return result;
    }
     
    /**
     * extracts the parts of the program that are quickly executed, generating a
     * code which can be run and then queried for completions.
     *
     * @param script the entire python program
     * @return the python program with lengthy calls removed.
     * @see #removeSideEffects(java.lang.String) 
     * @see JythonUtil#simplifyScriptToGetParams(java.lang.String, boolean) 
     */
     public static String simplifyScriptToCompletions( String script ) throws PySyntaxError {
         
         if ( script.trim().length()==0 ) return script;
         
         String[] ss1= (script).split("\n");
         String[] ss= new String[ss1.length+1];
         ss[0]= "# simplifyScriptToGetCompletions";
         System.arraycopy( ss1, 0, ss, 1, ss1.length );
         
         int lastLine= ss.length-1;
         
         HashSet variableNames= new HashSet();
         variableNames.add("getParam");  // this is what allows the getParam calls to be included.
         variableNames.add("getDataSet"); // this will be replaced with a trivial call for completions.
         variableNames.add("str");  // include casts.
         variableNames.add("int");
         variableNames.add("long");
         variableNames.add("float");
         variableNames.add("datum");
         variableNames.add("datumRange");
         variableNames.add("dataset");
         variableNames.add("URI");
         variableNames.add("URL");
         variableNames.add("PWD");
         
         try {
             Module n=null;
             
             int count=4;
             PySyntaxError ex0=null;
             while ( lastLine>0 && count>0 ) {
                try {
                    n = (Module)org.python.core.parser.parse( script, "exec" );
                    break;
                } catch ( PySyntaxError ex ) { // pop off the last line and try again.
                    if ( ex0==null ) ex0= ex;
                    lastLine--;
                    script= JythonUtil.join( Arrays.copyOf(ss,lastLine), "\n" );
                    count--;
                }
             }
             
             if ( n==null ) throw ex0;

             String s= simplifyScriptToGetCompletions( ss, n.body, variableNames, 1, lastLine, 0 );
             s= GETDATASET_CODE + s;
             s= "PWD='file:/tmp/'\n"+s;
             return s;
             
         } catch ( PySyntaxError ex ) {
             throw ex;
         }
     }
     
     private static String getIfBlock( String[] ss, stmtType[] body, HashSet variableNames, 
             int firstLine, int lastLine1, int depth) {
        StringBuilder result= new StringBuilder();
        String ss1= simplifyScriptToGetCompletions(ss, body, variableNames, firstLine, lastLine1, depth+1 );
        if ( ss1.length()==0 ) {
//            String line;
//            if ( firstLine==0 && iff.beginLine>0 ) {
//                line= ss[body[0].beginLine-1]; 
//            } else {
//                line= ss[iff.beginLine];
//            }
            Pattern p= Pattern.compile("(\\s*)(\\S*).*");
            Matcher m= p.matcher(ss[firstLine]);
            String indent;
            if ( m.matches() ) {
                indent= m.group(1);
            } else {
                indent= "";
            }
            result.append(indent).append("pass  ## SimplifyScriptSupport.getIfBlock \n");  
            logger.fine("things have probably gone wrong...");
        } else {
            appendToResult( result,ss1);
        }
        return result.toString();
     }
     
     /**
      * Using the stmtType get the line, or lines.  If the last line contains
      * a single triple-quote, we need to kludge a little and look for the 
      * preceding triple-quote in previous lines.
      * @param ss
      * @param o
      * @return 
      */
     public static String getSourceForStatement( String[] ss, stmtType o ) {
         if ( o.beginLine==0 ) {
             return "(bad line number";
         }
         String theLine= ss[o.beginLine];
         
         String tripleQuotes="'''";
         int i1= theLine.indexOf(tripleQuotes);
         if ( i1>-1 ) {
             int i0= theLine.lastIndexOf(tripleQuotes,i1-3);
             if ( i0==-1 ) {
                 int lastLine= o.beginLine;
                 int firstLine= lastLine;
                 while ( firstLine>=0 ) {
                     theLine= ss[firstLine]+"\n"+theLine;
                     if ( ss[firstLine].contains(tripleQuotes) ) {
                         break;
                     } else {
                         firstLine= firstLine-1;
                     }
                 }
             }
         }
         return theLine;
     }
     
     /**
      * Extracts the parts of the program that get parameters or take a trivial amount of time to execute.  
      * This may call itself recursively when if blocks are encountered.
      * See test038.
      * @param ss the entire script, with a null at index 0.
      * @param stmts statements being processed.
      * @param variableNames variable names that have been resolved.
      * @param beginLine first line of the script being processed, or -1 to use stmts[0].beginLine
      * @param lastLine INCLUSIVE last line of the script being processed.
      * @param depth recursion depth, for debugging.
      * @return the simplified script
      * @see JythonUtil#simplifyScriptToGetParams(java.lang.String[], org.python.parser.ast.stmtType[], java.util.HashSet, int, int, int) 
      */
public static String simplifyScriptToGetCompletions( String[] ss, stmtType[] stmts, 
             HashSet variableNames, int beginLine, int lastLine, int depth  ) {
        //String spaces= "                              "
        //        + "                              "
        //        + "                              ";
         if ( lastLine>=ss.length ) {
             throw new IllegalArgumentException("lastLine is >= number of lines");
         }
        if ( !ss[0].equals("# simplifyScriptToGetCompletions") ) {
            throw new IllegalArgumentException("first line must be '# simplifyScriptToGetCompletions'");
        }
        int acceptLine= -1; // first line to accept
        int currentLine= beginLine; // current line we are writing (0 is first line).
        StringBuilder result= new StringBuilder();
        for (int istatement=0; istatement<stmts.length; istatement++) {
             stmtType o= stmts[istatement];
             String theLine= getSourceForStatement( ss, o );
             int lineCount= theLine.split("\n",-2).length;
            
             if ( depth==0 ) {
                logger.finest(theLine); //breakpoint here.
                //System.err.println(theLine);
             }
             logger.log( Level.FINER, "line {0}: {1}", new Object[] { o.beginLine, theLine } );
             if ( o.beginLine>0 ) {
                 if ( beginLine<0 && istatement==0 ) acceptLine= o.beginLine;
                 if ( lineCount>1 ) {
                    beginLine= o.beginLine - (lineCount-1) ;
                 } else {
                    beginLine= o.beginLine;
                 }
             } else {
                 acceptLine= beginLine; // elif clause in autoplot-test038/lastSuccessfulBuild/artifact/test038_demoParms1.jy
             }
             if ( beginLine>lastLine ) {
                 continue;
             }
             if ( o instanceof Assign && !simplifyScriptToGetCompletionsOkay( o, variableNames ) ) {
                 Assign a= (Assign)o;
                 String cl= maybeIdentifyType( a );
                 if ( cl!=null ) {
                     result.append(cl);
                     continue;
                 }
             }
               
            if (o instanceof org.python.parser.ast.If) {
                if (acceptLine > -1) {
                    for (int i = acceptLine; i < beginLine; i++) {
                        appendToResult(result, ss[i]).append("\n");
                    }
                 }
                If iff = (If) o;
                 boolean includeBlock;
                 if (simplifyScriptToGetCompletionsCanResolve( iff.test, variableNames )) {
                     for ( int i=beginLine; i<iff.body[0].beginLine; i++) {
                         result.append(ss[i]).append("\n");
                     } // write out the 'if' part
                     includeBlock= true;
                 } else {
                     includeBlock= false;
                 }
                 int lastLine1;  //lastLine1 is the last line of the "if" clause.
                 int elseLine=-1;                 
                 if ( iff.orelse!=null && iff.orelse.length>0 ) {
                     if ( iff.orelse[0].beginLine>0 ) {
                         lastLine1= iff.orelse[0].beginLine-1;  // -1 is for the "else:" part.
                         if ( ss[lastLine1].trim().startsWith("else") ) {
                             elseLine= lastLine1;
                             lastLine1=lastLine1-1;
                        } else if ( ss[lastLine1].trim().startsWith("elif") ) {
                            elseLine= lastLine1;
                            lastLine1=lastLine1-1;
                         }
                     } else {
                         if ( iff.orelse[0] instanceof If ) {
                            elseLine = ((If) iff.orelse[0]).test.beginLine;
                            lastLine1= elseLine-1;
                         } else {
                             logger.warning("failure to deal with another day...");
                             throw new RuntimeException("this case needs to be dealt with...");
                         }
                     }
                 } else if ( (istatement+1)<stmts.length ) {
                     lastLine1= stmts[istatement+1].beginLine-1;
                 } else {
                     lastLine1= lastLine;
                 }
                 if ( includeBlock ) {
                     String ss1= getIfBlock(ss, iff.body, variableNames, beginLine+1, lastLine1, depth+1 );
                     appendToResult( result,ss1);
                     if ( iff.orelse!=null ) {
                         if ( (istatement+1)>=stmts.length ) {
                            lastLine1= lastLine;
                         } else {
                            lastLine1= stmts[istatement+1].beginLine-1;
                         }
                         if ( iff.orelse[0].beginLine==0 ) {
                             result.append("\n");
                         } else {
                             if ( iff.orelse[0].beginLine>0 && ss[iff.orelse[0].beginLine-1].trim().startsWith("else:") ) {
                                 result.append(ss[iff.orelse[0].beginLine-1]).append("\n");
                                 ss1= getIfBlock(ss, iff.orelse, variableNames, iff.orelse[0].beginLine, lastLine1, depth+1 );
                                 appendToResult( result,ss1);
                             } else {
                                 result.append(ss[iff.orelse[0].beginLine]).append("\n");
                             }
                         }
                         
                     }
                 }
                 currentLine= lastLine1;
                 acceptLine= -1;
             } else if ( o instanceof Assert ) {
                 String m= maybeModelAssert((Assert)o,variableNames);
                 if ( m!=null ) {
                     result.append(m).append("\n");
                     currentLine= acceptLine;
                 }
    
             } else {
                 if ( simplifyScriptToGetCompletionsOkay( o, variableNames ) ) {
                     if ( acceptLine<0 ) {
                         acceptLine= beginLine;
                         for ( int i=currentLine+1; i<acceptLine; i++ ) {
                             result.append("\n");
                             currentLine= acceptLine;
                         }
                     }
                 } else {
                     if ( acceptLine>-1 ) {
                         int thisLine= beginLine;
                         for ( int i=acceptLine; i<=thisLine; i++ ) {
                             if ( i<thisLine ) {
                                 appendToResult(result,ss[i]).append("\n");
                             } else {
                                 if ( ss[i].length()>0 && Character.isWhitespace(ss[i].charAt(0) ) ) {
                                     appendToResult(result,ss[i]).append("\n");
                                 }
                             }
                         }
                         appendToResult(result,"\n");
                         currentLine= thisLine;
                         acceptLine= -1;
                     }
                }
             }
         }
         if ( acceptLine>-1 ) {
             lastLine= JythonUtil.handleContinue( ss, lastLine );
             int thisLine = lastLine;
             for (int i = acceptLine; i <= thisLine; i++) {
                appendToResult(result, ss[i]).append("\n");
             }
         }
         return result.toString();         
     }
     
     /**
      * can we resolve this node given the variable names we know?
      * @param o
      * @param variableNames
      * @return true if the node can be resolved.
      */
     private static boolean simplifyScriptToGetCompletionsCanResolve( SimpleNode o, HashSet<String> variableNames ) {
        //if ( o.beginLine>=617 && o.beginLine<619 ) {
        //    System.err.println( "here at 617-ish");
        //}
        if ( o instanceof Name ) { 
            Name c= (Name)o;
            if ( !variableNames.contains( c.id ) ) {
                logger.finest( String.format( "%04d canResolve->false: %s", o.beginLine, o.toString() ) );
                return false;
            }
        }
        if ( o instanceof Attribute ) {
            Attribute at= (Attribute)o;
            while ( at.value instanceof Attribute || at.value instanceof Subscript ) {
                if ( at.value instanceof Attribute ) {
                    at= (Attribute)at.value;
                } else {
                    Subscript s= (Subscript)at.value;
                    if ( s.value instanceof Attribute ) {
                        at= (Attribute)s.value;
                    } else {
                        return false; // oh just give up...
                    }
                }
            }
            if ( at.value instanceof Name ) {
                Name n= (Name)at.value;
                if ( !variableNames.contains( n.id ) ) return false;
            }
        }
        MyVisitorBase vb= new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest( String.format( " %04d canResolve->%s: %s", o.beginLine,  vb.visitNameFail, o ) );
            return vb.looksOkay || !vb.visitNameFail;
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest( String.format( "!! %04d canResolve->false: %s", o.beginLine, o ) );
         return false;
     }     
     
     /**
      * dumb kludge where no-arg constructor is called to get an instance for
      * completions.  This is really an experiment...
      * @param a
      * @return 
      */
     private static String maybeModelAssert( Assert a, HashSet<String> variableNames ) {
        if ( a.test instanceof Call ) {
            org.python.parser.ast.Call cc= ( org.python.parser.ast.Call)a.test;
            exprType f= cc.func;
            if ( f instanceof Name ) {
                if ( ((Name)f).id.equals("isinstance") ) {
                    if ( cc.args.length==2 ) {
                        exprType a1= cc.args[0];
                        if ( a1 instanceof Name ) {
                            exprType a2= cc.args[1];
                            if ( a2 instanceof Name && variableNames.contains(((Name)a2).id)) {
                                return String.format( "%s__class=%s # inserted by maybeModelAssert", ((Name)a1).id, ((Name)a2).id );
                            }
                        }
                    }
                }
            }
            return null;
        } else {
            return null;
        }
     }
     
     /**
      * return true if we can include this in the script without a huge performance penalty.
      * @param o the statement, for example an import or an assignment
      * @return true if we can include this in the script without a huge performance penalty.
      */
     private static boolean simplifyScriptToGetCompletionsOkay( stmtType o, HashSet<String> variableNames ) {
         logger.log(Level.FINEST, "simplify script line: {0}", o.beginLine);
         if ( ( o instanceof org.python.parser.ast.ImportFrom ) ) {
             org.python.parser.ast.ImportFrom importFrom= (org.python.parser.ast.ImportFrom)o;
             for ( aliasType a: importFrom.names ) {
                 if ( a.asname!=null ) {
                     variableNames.add( a.asname );
                 } else {
                     variableNames.add( a.name );
                 }
             }
             return true;
         }
         if ( ( o instanceof org.python.parser.ast.Import ) ) {
             org.python.parser.ast.Import imporrt= (org.python.parser.ast.Import)o;
             for ( aliasType a: imporrt.names ) {
                 if ( a.asname!=null ) {
                     variableNames.add( a.asname );
                 } else {
                     variableNames.add( a.name );
                 }
             }
             return true;
         }
         if ( ( o instanceof org.python.parser.ast.Expr ) ) {
             Expr e= (Expr)o;
             if ( ( e.value instanceof Call ) && trivialFunctionCall( (Call)e.value ) ) {
                 return true;
             }
         }
         if ( ( o instanceof org.python.parser.ast.ClassDef ) ) return true;
         if ( ( o instanceof org.python.parser.ast.FunctionDef ) ) return true;
         if ( ( o instanceof org.python.parser.ast.Assign ) ) {
             Assign a= (Assign)o;
             if ( simplifyScriptToGetCompletionsOkayNoCalls( a.value, variableNames ) ) {
                 if ( !simplifyScriptToGetCompletionsCanResolve(a.value, variableNames ) ) {
                     return false;
                 }
                 for (exprType target : a.targets) {
                     exprType et = (exprType) target;
                     if (et instanceof Name) {
                         String id = ((Name) target).id;
                         variableNames.add(id);
                         logger.log(Level.FINEST, "assign to variable {0}", id);
                         //TODO: can we identify type?  Insert <id>__type=... for completions.
                     } else if ( et instanceof Attribute ) {
                         Attribute at= (Attribute)et;
                         while ( at.value instanceof Attribute || at.value instanceof Subscript ) {
                             if ( at.value instanceof Attribute ) {
                                 at= (Attribute)at.value;
                             } else {
                                 Subscript s= (Subscript)at.value;
                                 if ( s.value instanceof Attribute ) {
                                     at= (Attribute)s.value;
                                 } else {
                                     return false; // oh just give up...
                                 }
                             }
                         }
                         if ( at.value instanceof Name ) {
                             Name n= (Name)at.value;
                             if ( !variableNames.contains( n.id ) ) return false;
                         }
                     } else if ( et instanceof Subscript ) {
                         Subscript subscript= (Subscript)et;
                         exprType et2= subscript.value;
                         if ( et2 instanceof Name ) {
                             Name n= (Name)et2;
                             if ( variableNames.contains( n.id ) ) return true;
                         }
                         return false;
                     } else {
                         return false;
                     }
                 }
                 return true;
             } else {
                 return false;
             }
         }
         if ( ( o instanceof org.python.parser.ast.If ) )  {
             return simplifyScriptToGetCompletionsOkayNoCalls(o,variableNames);
         }
         if ( ( o instanceof org.python.parser.ast.Print ) ) return false;
         logger.log( Level.FINEST, "not okay to simplify: {0}", o);
         return false;
     }
     
     
     /**
      * inspect the node to look for function calls that are not to the function "getParam".  This is awful code that 
      * will be rewritten when we upgrade Python to 2.7.
      * @param o
      * @param variableNames
      * @return 
      */
     private static boolean simplifyScriptToGetCompletionsOkayNoCalls( SimpleNode o, HashSet<String> variableNames ) {
        
        if ( o instanceof Call ) { 
            Call c= (Call)o;

            if ( !trivialFunctionCall(c) ) {
                if ( !trivialConstructorCall(c) ) {
                    logger.finest( String.format( "%04d simplify->false: %s", o.beginLine, o.toString() ) );
                    return false;                    
                }
            }
        }
        MyVisitorBase vb= new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest( String.format( " %04d simplify->%s: %s", o.beginLine, vb.looksOkay(), o ) );
            return vb.looksOkay();
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest( String.format( "!! %04d simplify->false: %s", o.beginLine, o ) );
         return false;
     }

     /**
      * there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, 
      * such as the string.upper() function. The commas are to guard against the id being a subset of another 
      * id ("lower," does not match "lowercase").
      * TODO: update this after Python upgrade.  
      */
     private static final String[] okay= new String[] { "range,", "xrange,", "irange,", 
         "getParam,", "getDataSet,", "lower,", "upper,", "URI,", "URL,", 
         "setScriptDescription", "setScriptTitle", "setScriptLabel", "setScriptIcon",
         "DatumRangeUtil,", "TimeParser,",
         "str,", "int,", "long,", "float,", "datum,", "datumRange,", "dataset,",
         "indgen,","findgen,", "dindgen,", 
         "ones,", "zeros,", 
         "linspace,", "logspace,",
         "dblarr,", "fltarr,", "strarr,", "intarr,", "bytarr,",
         "ripples,", "split,", 
         "color,", "colorFromString,", "isinstance,"  };
     private static final Set<String> okaySet= new HashSet<>();
     static {
         for ( String o: okay ) okaySet.add(o.substring(0,o.length()-1));
     }
     
     private static String getFunctionName( exprType t ) {
         if ( t instanceof Name ) {
             return ((Name)t).id;
         } else if ( t instanceof Attribute ) {
             Attribute a= (Attribute)t;
             return getFunctionName(a.value)+"."+a.attr;
         } else {
             return t.toString();
         }
     } 
     
     /**
      * return true if the function call is trivial to execute and can be evaluated within a few milliseconds.  For example,
      * findgen can be called because no calculations are made in the call, but fft cannot.  Typically these are Order 1 (a.k.a.
      * constant time) operations, but also many Order N operations are so fast they are allowed.
      * @param sn an AST node pointed at a Call.
      * @return true if the function call is trivial to execute 
      */
     private static boolean trivialFunctionCall( SimpleNode sn ) {
         if ( sn instanceof Call ) {
             Call c= (Call)sn;
             boolean klugdyOkay= false;
             String ss= c.func.toString(); // we just want "DatumRangeUtil" of the Attribute
             //String ss= getFunctionName(c.func);
             for ( String s: okay ) {
                if ( ss.contains(s) ) klugdyOkay= true;
             }
             if ( klugdyOkay==false ) {
                 if ( ss.contains("TimeUtil") && ss.contains("now")  ) {
                     klugdyOkay= true;
                 }
             }
             logger.log(Level.FINER, "trivialFunctionCall={0} for {1}", new Object[]{klugdyOkay, c.func.toString()});
             return klugdyOkay;
         } else {
             return false;
         }
     }
             
     
     /**
      * return true if the function call is trivial to execute because it's a constructor,
      * which presumably takes little time to create.
      * @param sn
      * @return true if it is a constructor call
      */
     private static boolean trivialConstructorCall( SimpleNode sn ) {
         if ( sn instanceof Call ) {
             Call c= (Call)sn;
             if ( c.func instanceof Name ) {
                 String funcName= ((Name)c.func).id;
                 return Character.isUpperCase(funcName.charAt(0));
             } else if ( c.func instanceof Attribute ) {  // Rectangle.Double
                 String funcName= ((Attribute)c.func).attr;
                 return Character.isUpperCase(funcName.charAt(0));
             } else {
                 return false;
             }
         } else {
             return false;
         }
     }

     /**
      * if we recognize the function that is called, then go ahead and keep track
      * of the type.  This is a quick and cheesy implementation that just looks
      * for a few names.
      * @param a
      * @return 
      */
     private static String maybeIdentifyType(Assign a) {
        if ( a.targets.length==1 ) {
            exprType target= a.targets[0];
            exprType et = (exprType) target;
            if (et instanceof Name) {
                String id = ((Name) target).id;
                if ( a.value instanceof Call ) {
                    Call c= (Call)a.value;
                    if ( c.func instanceof Name ) {
                        String funcName= ((Name)c.func).id;
                        if ( funcName.equals("getApplication") ) {
                            return "from org.autoplot import AutoplotUI\n" + id + "__class=AutoplotUI\n";
                        } else if ( funcName.equals("getApplicationModel") ) {
                            return "from org.autoplot import ApplicationModel\n" + id + "__class=ApplicationModel\n";
                        } else if ( funcName.equals("getDataSource") ) {
                            return "from org.autoplot.datasource import DataSource\n" + id + "__class=DataSource\n";
                        }
                    }
                }
            }
        }
        return null;
    }
     
     private static class MyVisitorBase<R> extends VisitorBase {
         boolean looksOkay= true; 
         boolean visitNameFail= false;
         
         HashSet names;
         MyVisitorBase( HashSet names ) {
             this.names= names;
         }

        @Override
        public Object visitName(Name node) throws Exception {
            logger.log(Level.FINER, "visitName({0})", node);
            if ( !names.contains(node.id) ) {
                visitNameFail= true;
            }
            return super.visitName(node); 
        }

        @Override
        public Object visitCall(Call node) throws Exception {
            logger.log(Level.FINER, "visitCall({0})", node);
            return super.visitCall(node); 
        }
        
         @Override
         protected Object unhandled_node(SimpleNode sn) throws Exception {
             return sn;
         }
         @Override
         public void traverse(SimpleNode sn) throws Exception {
             logger.log(Level.FINER, "traverse({0})", sn);
             if ( sn instanceof Call ) {
                 looksOkay= trivialFunctionCall(sn) || trivialConstructorCall(sn);
                 logger.log(Level.FINER, "looksOkay={0}", looksOkay);
             } else if ( sn instanceof Assign ) { 
                 Assign a= ((Assign)sn);
                 exprType et= a.value;
                 if ( et instanceof Call ) {
                     looksOkay= trivialFunctionCall(et) || trivialConstructorCall(sn);
                     logger.log(Level.FINER, "looksOkay={0}", looksOkay);
                 }
             } else if ( sn instanceof Name ) {
                 String t= ((Name)sn).id;
                 if ( t.length()>1 && Character.isUpperCase( t.charAt(0) ) ) {
                     logger.log(Level.FINER, "name is assumed to be a constructor call name: {0}", t);
                     return;
                 }
                 if ( !names.contains(t)
                         && !okaySet.contains(t)) {
                    looksOkay= false; // TODO: why are there both looksOkay and visitNameFail?
                    logger.log(Level.FINER, "looksOkay={0}", looksOkay);
                 }
             } else if ( sn instanceof Attribute ) {
                 traverse( ((Attribute)sn).value );  // DatumRangeUtil

             } else if ( sn instanceof Subscript ) {
                 Subscript ss= (Subscript)sn;
                 exprType et= ss.value;
                 if ( et instanceof Name ) {
                     traverse((Name)(et));
                 }
                 //ss.value;
                 //visitName((Name))
             } else if ( sn instanceof BinOp ) {
                 BinOp bo= (BinOp)sn;
                 traverse( bo.left );
                 traverse( bo.right );
             } else if ( sn instanceof Num ) {
                 
             } else if ( sn instanceof Index ) {
                 Index index= (Index)sn;
                 traverse( index.value );
                 
             } else {
                 logger.log(Level.FINE, "unchecked: {0}", sn);
             }
         }
         public boolean looksOkay() {
             return looksOkay;
         }
         /**
          * this contains a node whose name we can't resolve.
          * @return 
          */
         public boolean visitNameFail() {
             return visitNameFail;
         }
     }
     
    
}
