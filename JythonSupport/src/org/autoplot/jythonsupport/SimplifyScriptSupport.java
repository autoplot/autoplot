
package org.autoplot.jythonsupport;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.python.core.PySyntaxError;
import org.python.parser.ast.If;
import org.python.parser.ast.Module;
import org.python.parser.ast.stmtType;
import org.das2.util.LoggerManager;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.Call;
import org.python.parser.ast.Name;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.exprType;

/**
 * AST support for Jython completions.
 * @author jbf
 */
public class SimplifyScriptSupport {
    
    private static final Logger logger= LoggerManager.getLogger("jython");
        
    private static final String GETDATASET_CODE= 
        "def getDataSet( uri, timerange='', monitor='' ):\n" +
        "    'return a dataset for the given URI'\n" +
        "    return dataset(0)\n\n";
    
    public static String removeSideEffects( String script ) {
         Module n= (Module)org.python.core.parser.parse( script, "exec" );
         String[] ss= script.split("\n");
         HashSet variableNames= new HashSet();
         int lastLine= ss.length;
         return simplifyScriptToGetCompletions( ss, n.body, variableNames, 1, lastLine, 0 );
    }
    
    /**
     * useful for debugging
     * @param result
     * @param line
     * @return 
     */
     private static StringBuilder appendToResult( StringBuilder result, String line ) {
        //if ( line.contains("sTimeBinSeconds") ) {
        //    System.err.println("heresTimeBinSeconds");
        //}
        result.append(line);
        return result;
    }
     
    /**
     * extracts the parts of the program that are quickly executed, generating a
     * code which can be run and then queried for completions.
     *
     * @param script the entire python program
     * @return the python program with lengthy calls removed.
     */
     public static String simplifyScriptToCompletions( String script ) throws PySyntaxError {
         String[] ss= script.split("\n");
         
         int lastLine= ss.length;
         
         // check for continuation in last getParam call.
         while ( ss.length>lastLine+1 && ss[lastLine].trim().length()>0 && Character.isWhitespace( ss[lastLine].charAt(0) ) ) {
             lastLine++;
         }
         // Chris showed that a closing bracket or paren doesn't need to be indented.  See test038/jydsCommentBug.jyds
         if ( lastLine<ss.length ) {
             String closeParenCheck= ss[lastLine].trim();
             if ( closeParenCheck.equals(")") || closeParenCheck.equals("]") ) {
                 lastLine++;
             }
         }
         
         HashSet variableNames= new HashSet();
         variableNames.add("getParam");  // this is what allows the getParam calls to be included.
         variableNames.add("getDataSet"); // this will be replaced with a trivial call for completions.
         variableNames.add("str");  // include casts.
         variableNames.add("int");
         variableNames.add("long");
         variableNames.add("float");
         variableNames.add("datum");
//         variableNames.add("datumRange");
//         variableNames.add("URI");
//         variableNames.add("URL");
         
         try {
             Module n= (Module)org.python.core.parser.parse( script, "exec" );
             String s= simplifyScriptToGetCompletions( ss, n.body, variableNames, 1, lastLine, 0 );
             s= GETDATASET_CODE + s;
             s= "PWD='file:/tmp/'\n"+s;
             return s;
             
         } catch ( PySyntaxError ex ) {
             throw ex;
         }
     }
     
     /**
      * Extracts the parts of the program that get parameters or take a trivial amount of time to execute.  
      * This may call itself recursively when if blocks are encountered.
      * See test038.
      * @param ss the entire script.
      * @param stmts statements being processed.
      * @param variableNames variable names that have been resolved.
      * @param beginLine first line of the script being processed.
      * @param lastLine INCLUSIVE last line of the script being processed.
      * @param depth recursion depth, for debugging.
      * @return 
      */
     public static String simplifyScriptToGetCompletions( String[] ss, stmtType[] stmts, HashSet variableNames, int beginLine, int lastLine, int depth  ) {
         int acceptLine= -1;  // first line to accept
         int currentLine= 0; // current line we are writing (0 is first line).
         StringBuilder result= new StringBuilder();
         for ( int istatement=0; istatement<stmts.length; istatement++ ) {
             stmtType o= stmts[istatement];
             logger.log( Level.FINER, "line {0}: {1}", new Object[] { o.beginLine, o.beginLine>0 ? ss[o.beginLine-1] : "(bad line number)" } );
             if ( o.beginLine>0 ) {
                 beginLine= o.beginLine;
             } else {
                 acceptLine= beginLine; // elif clause in autoplot-test038/lastSuccessfulBuild/artifact/test038_demoParms1.jy
             }
             if ( beginLine>lastLine ) {
                 continue;
             }
             if ( o instanceof org.python.parser.ast.If ) {
                 if ( acceptLine>-1 ) {
                    for ( int i=acceptLine; i<beginLine; i++ ) {
                        appendToResult( result,ss[i-1]).append("\n");
                    }
                 }
                 If iff= (If)o;
                 boolean includeBlock;
                 if ( simplifyScriptToGetCompletionsCanResolve( iff.test, variableNames ) ) {
                     for ( int i=beginLine; i<iff.body[0].beginLine; i++ ) {
                         result.append(ss[i-1]).append("\n");
                     } // write out the 'if' part
                     includeBlock= true;
                 } else {
                     includeBlock= false;
                 }
                 int lastLine1;  //lastLine1 is the last line of the "if" clause.
                 if ( iff.orelse!=null && iff.orelse.length>0 ) {
                     if ( iff.orelse[0].beginLine>0 ) {
                         lastLine1= iff.orelse[0].beginLine-2;  // -2 is for the "else:" part.
                     } else {
                         if ( iff.orelse[0] instanceof If ) {
                             lastLine1= ((If)iff.orelse[0]).test.beginLine-1;
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
                     String ss1= simplifyScriptToGetCompletions( ss, iff.body, variableNames, -1, lastLine1, depth+1 );
                     if ( ss1.length()==0 ) {
                         String line;
                         if ( iff.beginLine==0 && beginLine>0 && iff.body[0].beginLine>0 ) {
                            line= ss[iff.body[0].beginLine-1]; 
                         } else {
                            line= ss[iff.beginLine];
                         }
                         String[] ss2= line.split("\\S",-2);
                         String indent= ss2[0];
                         result.append(indent).append("pass\n");  
                         logger.fine("things have probably gone wrong...");
                     } else {
                         appendToResult( result,ss1);
                     }
                     if ( iff.orelse!=null ) {
                         appendToResult( result,ss[lastLine1] );
                         int lastLine2;
                         if ( (istatement+1)<stmts.length ) {
                            lastLine2= stmts[istatement+1].beginLine-1;
                         } else {
                            lastLine2= lastLine;
                         }
                         String ss2= simplifyScriptToGetCompletions( ss, iff.orelse, variableNames, lastLine1+2, lastLine2, depth+1 );
                         if ( ss2.length()>0 ) {
                             result.append("\n");
                         }
                         appendToResult( result,ss2);
                         if ( ss2.length()==0  ) { // we didn't add anything...
                             String line;
                             line= ss[iff.orelse[0].beginLine-1];
                             String[] ss3= line.split("\\S",-2);
                             String indent= ss3[0];
                             result.append("\n").append(indent).append("pass\n");  
                         } else {
                             result.append("\n");  // write of the else or elif line
                         }
                     }
                 }
                 acceptLine= -1;
                } else {
                 if ( simplifyScriptToGetCompletionsOkay( o, variableNames ) ) {
                     if ( acceptLine<0 ) {
                         acceptLine= (o).beginLine;
                         for ( int i=currentLine+1; i<acceptLine; i++ ) {
                             result.append("\n");
                             currentLine= acceptLine;
                         }
                     }
                 } else {
                     if ( acceptLine>-1 ) {
                         int thisLine= (o).beginLine;
                         for ( int i=acceptLine; i<thisLine; i++ ) {
                             appendToResult(result,ss[i-1]).append("\n");
                         }
                         appendToResult(result,"\n");
                         currentLine= thisLine;
                         acceptLine= -1;
                     }
                }
             }
         }
         if ( acceptLine>-1 ) {
             int thisLine= lastLine;
             for ( int i=acceptLine; i<=thisLine; i++ ) {
                 appendToResult( result,ss[i-1]).append("\n");
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
      * return true if we can include this in the script without a huge performance penalty.
      * @param o
      * @return 
      */
     private static boolean simplifyScriptToGetCompletionsOkay( stmtType o, HashSet<String> variableNames ) {
         //if ( o.beginLine==607 ) {  // leave this commented code as a reference for debugging
         //    System.err.println("here at line "+o.beginLine);
         //}
         if ( ( o instanceof org.python.parser.ast.ImportFrom ) ) return true;
         if ( ( o instanceof org.python.parser.ast.Import ) ) return true;
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
                     }
                 }
                 return true;
             } else {
                 return false;
             }
         }
         if ( ( o instanceof org.python.parser.ast.If ) )  return simplifyScriptToGetCompletionsOkayNoCalls(o,variableNames);
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

     //there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, such as the string.upper() function.
     //The commas are to guard against the id being a subset of another id ("lower," does not match "lowercase").
     //TODO: update this after Python upgrade.  //TODO: this should be a map and a long list
     private static final String[] okay= new String[] { "range,", "xrange,", "getParam,", "getDataSet,", "lower,", "upper,", "URI,", "URL,", "DatumRangeUtil,", "TimeParser",
        "str,", "int,", "long,", "float,", "datum,", "findgen,", "dindgen,", "ones,", "zeros,", "linspace,", "dblarr,", "fltarr,", "color,", "colorFromString,"  };
     
     /**
      * return true if the function call is trivial to execute and can be evaluated within a few milliseconds.
      * @param sn
      * @return 
      */
     private static boolean trivialFunctionCall( SimpleNode sn ) {
         if ( sn instanceof Call ) {
             Call c= (Call)sn;
             boolean klugdyOkay= false;
             String ss= c.func.toString();
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
     
     private static class MyVisitorBase<R> extends VisitorBase {
         boolean looksOkay= true; 
         boolean visitNameFail= false;
         
         HashSet names= new HashSet();
         MyVisitorBase( HashSet names ) {
             this.names= names;
         }

        @Override
        public Object visitName(Name node) throws Exception {
            if ( !names.contains(node.id) ) {
                visitNameFail= true;
            }
            return super.visitName(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Object visitCall(Call node) throws Exception {
            return super.visitCall(node); //To change body of generated methods, choose Tools | Templates.
        }
        
         @Override
         protected Object unhandled_node(SimpleNode sn) throws Exception {
             return sn;
         }
         @Override
         public void traverse(SimpleNode sn) throws Exception {
             if ( sn instanceof Call ) {
                 looksOkay= trivialFunctionCall(sn);
             } else if ( sn instanceof Assign ) { // TODO: I have to admit I don't understand what traverse means.  I would have thought it was all nodes...
                 Assign a= ((Assign)sn);
                 exprType et= a.value;
                 if ( et instanceof Call ) {
                     looksOkay= trivialFunctionCall(et);
                 }
             } else if ( sn instanceof Name ) {
                 //visitName((Name)sn).id
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
