package org.autoplot.jythonsupport;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.python.core.PySyntaxError;
import org.python.parser.ast.If;
import org.python.parser.ast.Module;
import org.python.parser.ast.stmtType;
import org.das2.util.LoggerManager;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assert;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.BinOp;
import org.python.parser.ast.Call;
import org.python.parser.ast.Compare;
import org.python.parser.ast.Dict;
import org.python.parser.ast.Expr;
import org.python.parser.ast.Index;
import org.python.parser.ast.List;
import org.python.parser.ast.Name;
import org.python.parser.ast.Num;
import org.python.parser.ast.Str;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.Tuple;
import org.python.parser.ast.UnaryOp;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.aliasType;
import org.python.parser.ast.exprType;
import org.python.util.PythonInterpreter;

/**
 * AST support for Jython completions. This is not meant to be thorough, but instead should be helpful when working with scripts.
 *
 * @author jbf
 * @see JythonUtil#simplifyScriptToGetParams(java.lang.String, boolean)
 */
public class SimplifyScriptSupport {

    private static final Logger logger = LoggerManager.getLogger("jython.simplify");

    /**
     * eat away at the end of the script until it can be parsed
     *
     * @param script a Jython script.
     * @return the script with lines at the end removed such that the script can compile.
     * @see JythonCompletionTask#trimLinesToMakeValid(java.lang.String)
     */
    public static String alligatorParse(String script) {
        logger.entering("SimplifyScriptSupport", "alligatorParse");
        String[] ss = JythonUtil.splitCodeIntoLines(null, script);
        String scri = script;
        int lastLine = ss.length;
        boolean parseOkay= false;
        while (lastLine > 0) {
            scri = JythonUtil.join(Arrays.copyOfRange(ss, 0, lastLine), "\n");
            try {
                org.python.core.parser.parse(scri, "exec");
                parseOkay= true;
                break;
            } catch (Exception e) {
                logger.finest("fail to parse, no worries.");
            }
            lastLine--;
        }
        if ( parseOkay==false ) {
            scri= "";
        }
        logger.exiting("SimplifyScriptSupport", "alligatorParse");
        return scri;
    }

    /**
     * quick and dirty attempt to resolve tuple for format statement.
     * @param n
     * @param row
     * @param column
     * @param env
     * @return 
     */
    public static Object[] tryResolveTupleNode(  SimpleNode n, int row, int column, Map<String,Object> env ) {
        Tuple t= (Tuple)n;
        Object[] result= new Object[t.elts.length];
        for ( int i=0; i<result.length; i++ ) {
            result[i]= tryResolveStringNode( t.elts[i], row, column, env );
            if ( result[i]==null ) return null;
        }
        return result;
    }
    
    /**
     * given the node n, try to resolve its string value, maybe by implementing some of the addition (concatenation).
     * This was introduced to support URI completions within Jython codes, allowing the filename to be a variable and
     * thus shortening lines.
     * @param n node within an AST.
     * @param row the row of the caret
     * @param column the column of the caret
     * @param env any variables which have been identified as string values.
     * @return the string or null.
     */
    public static String tryResolveStringNode( SimpleNode n, int row, int column, Map<String,Object> env ) {
        if ( n.beginLine==row ) {
            if ( n instanceof Assign ) {
                Assign a= (Assign)n;
                return tryResolveStringNode( a.value, row, column, env );
            } else if ( n instanceof Str ) {
                return ((Str)n).s;
            } else if ( n instanceof Num ) {
                return String.valueOf(((Num)n).n);
            } else if ( n instanceof Expr ) {
                Expr e= (Expr)n;
                return tryResolveStringNode( e.value, row, column, env );
            } else if ( n instanceof Call ) {
                Call e= (Call)n;
                if ( e.func instanceof Name && ((Name)e.func).id.equals("getParam") ) {
                    if ( e.args.length>1 ) {
                        String s= tryResolveStringNode( e.args[1], row, column, env );
                        if ( s!=null ) return s;
                    }
                } else if ( e.func instanceof Name && ((Name)e.func).id.equals("str") ) {
                    if ( e.args.length==1 ) {
                        String s= tryResolveStringNode( e.args[0], row, column, env );
                        if ( s!=null ) return s;
                    }
                }
                return null;
            } else if ( n instanceof Name ) {
                Name na= (Name)n;
                Object o= env.get( na.id );
                if ( o instanceof String ) {
                    return (String)o;
                } else {
                    return null;
                }
            } else if ( n instanceof BinOp ) {
                BinOp e= (BinOp)n;
                String sleft= tryResolveStringNode( e.left, row, column, env );
                String sright= tryResolveStringNode( e.right, row, column, env );
                if ( sleft!=null && sright!=null && e.op==BinOp.Add ) {
                    return sleft + sright;
                } else if ( sleft!=null && e.right instanceof Tuple && e.op==BinOp.Mod ) {
                    //PythonInterpreter interp= new PythonInterpreter(null);
                    Object[] ss= tryResolveTupleNode( e.right, row, column, env );
                    if ( ss!=null ) {
                        sleft= sleft.replaceAll("\\%d", "%s"); // small cheat
                        try {
                            return String.format( sleft,ss );
                        } catch (Exception ex ) {
                            logger.log( Level.INFO, ex.getMessage(), ex );
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * given the node n, try to resolve its string value, maybe by implementing some of the addition (concatenation).
     * This was introduced to support URI completions within Jython codes, allowing the filename to be a variable and
     * thus shortening lines.
     * @param n the AST
     * @param row the row of the caret
     * @param column the column of the caret
     * @param env any variables which have been identified as string values.
     * @return the string or null.
     */    
    public static String tryResolveStringNode( Module n, int row, int column, Map<String,Object> env ) {
        stmtType thet=null;
        for ( stmtType t : n.body ) {
            if ( t.beginLine>=row ) {
                thet= t;
                break;
            }
            if ( t instanceof Assign ) {
                Assign a= (Assign)t;
                if ( a.targets.length==1 && a.targets[0] instanceof Name ) {
                    if ( a.value instanceof Str ) {
                        env.put( ((Name)a.targets[0]).id, ((Str)a.value).s );
                    } else if ( a.value instanceof BinOp ) {
                        String s= tryResolveStringNode( a.value, a.beginLine, a.beginColumn, env );
                        if ( s!=null ) {
                            env.put( ((Name)a.targets[0]).id, s );
                        }
                    } else if ( a.value instanceof Call ) {
                        Call c= (Call)a.value;
                        if ( c.func instanceof Name && ((Name)c.func).id.equals("getParam") ) {
                            String s= tryResolveStringNode( a.value, a.beginLine, a.beginColumn, env );
                            if ( s!=null ) {
                                env.put( ((Name)a.targets[0]).id, s );
                            }
                        }
                    }
                }
            }
        }
        // find the node within thet containing the carot.
        if ( thet instanceof Assign ) {
            exprType t= ((Assign)thet).value;
            if ( t instanceof Call ) {
                Call c= (Call)t;
                if ( c.func instanceof Name && ((Name)c.func).id.equals("getDataSet") ) {
                    return tryResolveStringNode( c.args[0], row, column, env );
                }
            }
        }
        return null;
    }
    
    /**
     * Remove parts of the script which are expensive so that the script can be run and completions offered. TODO: What is the
     * difference between this and simplifyScriptToCompletions?
     *
     * @param script Jython script
     * @return simplified version of the script.
     * @see #simplifyScriptToCompletions(java.lang.String)
     * @see https://github.com/autoplot/dev/tree/master/bugs/sf/1687
     */
    public static String removeSideEffects(String script) {

        script = alligatorParse(script);

        String[] ss = JythonUtil.splitCodeIntoLines("# simplifyScriptToGetCompletions", script);

        Module n;
        try {
            n = (Module) org.python.core.parser.parse(script, "exec");
        } catch (Exception ex) {
            // do it again so we can debug.
            n = (Module) org.python.core.parser.parse(script, "exec");
        }

        if (false && n.body.length > 0 && n.body[0].beginLine > n.beginLine) {
            logger.fine("shifting line numbers!");
            int shift = n.body[0].beginLine - n.beginLine;
            // strange bug here.
            for (stmtType s : n.body) {
                s.beginLine -= shift;
            }
        }
        HashSet variableNames = new HashSet();
        int ilastLine = ss.length - 1;
        return simplifyScriptToGetCompletions(ss, n.body, variableNames, 1, ilastLine, 0);
    }

    /**
     * useful for debugging
     *
     * @param result
     * @param line
     * @return
     */
    private static StringBuilder appendToResult(StringBuilder result, String line) {
        result.append(line);
        return result;
    }

    /**
     * extracts the parts of the program that are quickly executed, generating a code which can be run and then queried for
     * completions. This uses a Jython syntax tree (AST), so the code must be free of syntax errors. This will remove lines from the
     * end of the code until the code compiles, in case the script has a partially defined def or class.
     *
     * @param script the entire python program
     * @return the python program with lengthy calls removed.
     * @see #removeSideEffects(java.lang.String)
     * @see JythonUtil#simplifyScriptToGetParams(java.lang.String, boolean)
     */
    public static String simplifyScriptToCompletions(String script) throws PySyntaxError {

        if (script.trim().length() == 0) {
            return script;
        }
        script = alligatorParse(script);

        String[] ss = JythonUtil.splitCodeIntoLines("# simplifyScriptToGetCompletions", script);

        int lastLine = ss.length - 1;

        HashSet variableNames = new HashSet();
        variableNames.add("getParam");  // this is what allows the getParam calls to be included.
        //variableNames.add("getDataSet"); // new class lookup is cleaner method for this.
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
        variableNames.add("dom"); // TODO: only true for .jy scripts.
        variableNames.add("True");
        variableNames.add("False");

        try {
            Module n = null;

            int count = 4;
            PySyntaxError ex0 = null;
            while (lastLine > 0 && count > 0) {
                try {
                    n = (Module) org.python.core.parser.parse(script, "exec");
                    break;
                } catch (PySyntaxError ex) { // pop off the last line and try again.
                    if (ex0 == null) {
                        ex0 = ex;
                    }
                    lastLine--;
                    script = JythonUtil.join(Arrays.copyOf(ss, lastLine), "\n");
                    count--;
                }
            }

            if (n == null) {
                throw ex0;
            }

            String s = simplifyScriptToGetCompletions(ss, n.body, variableNames, 1, lastLine, 0);
            //s= GETDATASET_CODE + s;
            //s= "PWD='file:/tmp/'\n"+s;
            return s;

        } catch (PySyntaxError ex) {
            throw ex;
        }
    }

    private static String getIfBlock(String[] ss, stmtType[] body, HashSet variableNames,
            int firstLine, int lastLine1, int depth) {
        StringBuilder result = new StringBuilder();
        String ss1 = simplifyScriptToGetCompletions(ss, body, variableNames, firstLine, lastLine1, depth + 1);
        if (ss1.length() == 0) {
//            String line;
//            if ( firstLine==0 && iff.beginLine>0 ) {
//                line= ss[body[0].beginLine-1]; 
//            } else {
//                line= ss[iff.beginLine];
//            }
            Pattern p = Pattern.compile("(\\s*)(\\S*).*");
            Matcher m = p.matcher(ss[firstLine]);
            String indent;
            if (m.matches()) {
                indent = m.group(1);
            } else {
                indent = "";
            }
            result.append(indent).append("pass  ## SimplifyScriptSupport.getIfBlock \n");
            logger.fine("things have probably gone wrong...");
        } else {
            appendToResult(result, ss1);
        }
        return result.toString();
    }

    /**
     * Using the stmtType get the line, or lines. If the last line contains a single triple-quote, we need to kludge a little and
     * look for the preceding triple-quote in previous lines.
     *
     * @param ss
     * @param o
     * @return
     */
    public static String getSourceForStatement(String[] ss, stmtType o) {
        if (o.beginLine == 0) {
            return "(bad line number)";
        }
        int endLine= o.beginLine;
        int beginLine= endLine;
        if ( o instanceof Expr ) {
            Expr e = (Expr)o;
            if ( e.value.beginLine<beginLine ) {
                beginLine= e.value.beginLine;
            }
        }
        StringBuilder s= new StringBuilder();
        for ( int i=beginLine; i<=endLine; i++ ) {
            s.append( ss[i] );
            if ( i<endLine ) s.append( "\n" );
        }
        return s.toString();
    }

    /**
     * return the indent for a line, for example the " " in " continue "
     *
     * @param line
     * @return the indent
     */
    public static String getIndent(String line) {
        String s = line.trim();
        return line.substring(0, line.indexOf(s));
    }

    /**
     * Extracts the parts of the program that get parameters or take a trivial amount of time to execute. This may call itself
     * recursively when if blocks are encountered. See test038.
     *
     * @param ss the entire script, with a null at index 0.
     * @param stmts statements being processed.
     * @param variableNames variable names that have been resolved.
     * @param beginLine first line of the script being processed, or -1 to use stmts[0].beginLine
     * @param lastLine INCLUSIVE last line of the script being processed.
     * @param depth recursion depth, for debugging.
     * @return the simplified script
     * @see JythonUtil#simplifyScriptToGetParams(java.lang.String[], org.python.parser.ast.stmtType[], java.util.HashSet, int, int,
     * int)
     */
    public static String simplifyScriptToGetCompletions(String[] ss, stmtType[] stmts,
            HashSet variableNames, int beginLine, int lastLine, int depth) {
        //String spaces= "                              "
        //        + "                              "
        //        + "                              ";
        if (lastLine >= ss.length) {
            throw new IllegalArgumentException("lastLine is >= number of lines");
        }
        if (!ss[0].equals("# simplifyScriptToGetCompletions")) {
            throw new IllegalArgumentException("first line must be '# simplifyScriptToGetCompletions'");
        }
        Map<String, String> importedNames = new LinkedHashMap<>();
        importedNames.put("Color", "java.awt");
        importedNames.put("DatumRange", "org.das2.datum" );
        importedNames.put("Units", "org.das2.datum" );
        importedNames.put("DatumRangeUtil","org.das2.datum");
        importedNames.put("TimeUtil","org.das2.datum");
        importedNames.put("URL", "java.net");
        importedNames.put("URI", "java.net");
        importedNames.put("TimeParser", "org.das2.datum" );
        int acceptLine = -1; // first line to accept
        int currentLine = beginLine; // current line we are writing.
        StringBuilder result = new StringBuilder();
        for (int istatement = 0; istatement < stmts.length; istatement++) {
            stmtType o = stmts[istatement];
            String theLine = getSourceForStatement(ss, o);
            int lineCount = theLine.split("\n", -2).length;

            if (depth == 0) {
                logger.finest(theLine); //breakpoint here.
                //System.err.println(theLine);
            }
            logger.log(Level.FINER, "line {0}: {1}", new Object[]{o.beginLine, theLine});
            if (o.beginLine > 0) {
                if (beginLine < 0 && istatement == 0) {
                    acceptLine = o.beginLine;
                }
                if (lineCount > 1) {
                    beginLine = o.beginLine - (lineCount - 1);
                } else {
                    beginLine = o.beginLine;
                }
            } else {
                acceptLine = beginLine; // elif clause in autoplot-test038/lastSuccessfulBuild/artifact/test038_demoParms1.jy
            }
            if (beginLine > lastLine) {
                continue;
            }
            if (o instanceof Assign ) {
                if ( !simplifyScriptToGetCompletionsOkay(o, variableNames,importedNames) ) {
                    // check for method calls where we know the type.
                    Assign a = (Assign) o;
                    String cl = maybeIdentifyType(a, importedNames);
                    if (cl != null) {
                        if (acceptLine > -1) {
                            for (int i = acceptLine; i < beginLine; i++) {
                                appendToResult(result, ss[i]).append("\n");
                            }
                        }
                        if ( cl.contains("\n") ) {
                            String[] cls= cl.split("\n");
                            for ( String cl1: cls ) {
                                appendToResult(result, getIndent(theLine) + cl1).append("\n") ;
                            }
                        } else {
                            appendToResult(result, getIndent(theLine) + cl).append("\n") ;
                        }
                        acceptLine = -1;
                        continue;
                    }
                } else {
                    Assign a = (Assign) o;
                    String cl = maybeIdentifyType(a, importedNames);
                    if (cl != null) {
                        if (acceptLine > -1) {
                            for (int i = acceptLine; i < beginLine; i++) {
                                appendToResult(result, ss[i]).append("\n");
                            }
                        }
                        appendToResult(result, getIndent(theLine) + cl).append("\n") ;
                        acceptLine = -1;
                        continue;
                    }
                }
            }

            if (o instanceof org.python.parser.ast.ImportFrom) {
                org.python.parser.ast.ImportFrom i = (org.python.parser.ast.ImportFrom) o;
                for (aliasType n : i.names) {
                    importedNames.put(n.name, i.module);
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
                if (simplifyScriptToGetCompletionsCanResolve(iff.test, variableNames)) {
                    for (int i = beginLine; i < iff.body[0].beginLine; i++) {
                        result.append(ss[i]).append("\n");
                    } // write out the 'if' part
                    includeBlock = true;
                } else {
                    includeBlock = false;
                }
                int lastLine1;  //lastLine1 is the last line of the "if" clause. (inclusive)
                int elseLine = -1;
                if (iff.orelse != null && iff.orelse.length > 0) {
                    if (iff.orelse[0].beginLine > 0) {
                        lastLine1 = iff.orelse[0].beginLine - 1;  // -1 is for the "else:" part.
                        if (ss[lastLine1].trim().startsWith("else")) {
                            elseLine = lastLine1;
                            lastLine1 = lastLine1 - 1;
                        } else if (ss[lastLine1].trim().startsWith("elif")) {
                            elseLine = lastLine1;
                            lastLine1 = lastLine1 - 1;
                        }
                    } else {
                        if (iff.orelse[0] instanceof If) {
                            elseLine = ((If) iff.orelse[0]).test.beginLine;
                            lastLine1 = elseLine - 1;
                        } else {
                            logger.warning("failure to deal with another day...");
                            throw new RuntimeException("this case needs to be dealt with...");
                        }
                    }
                } else if ((istatement + 1) < stmts.length) {
                    lastLine1 = stmts[istatement + 1].beginLine - 1;
                } else {
                    lastLine1 = lastLine;
                }
                if (includeBlock) {
                    HashSet variableNames1= new HashSet(variableNames);
                    String ss1 = getIfBlock(ss, iff.body, variableNames1,
                            Math.min(beginLine + 1, lastLine1), lastLine1, depth + 1);
                    appendToResult(result, ss1);
                    if (iff.orelse != null) {
                        if ((istatement + 1) >= stmts.length) {
                            lastLine1 = lastLine;
                        } else {
                            lastLine1 = stmts[istatement + 1].beginLine - 1;
                        }
                        if (iff.orelse[0].beginLine == 0) {
                            result.append("\n");
                        } else {
                            if (iff.orelse[0].beginLine > 0 && ss[iff.orelse[0].beginLine - 1].trim().startsWith("else:")) {
                                result.append(ss[iff.orelse[0].beginLine - 1]).append("\n");
                                HashSet variableNames2= new HashSet(variableNames);
                                ss1 = getIfBlock(ss, iff.orelse, variableNames2, iff.orelse[0].beginLine, lastLine1, depth + 1);
                                appendToResult(result, ss1);
                                //TODO: if variable is added to variableNames1 and variableNames2 then add it to variableNames.
                            } else {
                                result.append(ss[iff.orelse[0].beginLine]).append("\n");
                            }
                        }

                    }
                }
                currentLine = lastLine1;
                acceptLine = -1;
            } else if (o instanceof Assert) {
                String m = maybeModelAssert((Assert) o, variableNames);
                if (m != null) {
                    result.append(m).append("\n");
                    currentLine = acceptLine;
                }
            } else if ( o instanceof Expr ) {
                if ( ((Expr)o).value instanceof Str ) {
                    result.append(theLine).append("\n");
                    currentLine = o.beginLine;
                    acceptLine= -1;
                } else {
                    if (acceptLine > -1) { // always skip Expr.
                        int thisLine = beginLine;
                        for (int i = acceptLine; i <= thisLine; i++) {
                            if (i < thisLine) {
                                appendToResult(result, ss[i]).append("\n");
                            } else {
                                if (ss[i].length() > 0 && Character.isWhitespace(ss[i].charAt(0))) {
                                    appendToResult(result, ss[i]).append("\n");
                                }
                            }
                        }
                        appendToResult(result, "\n");
                        currentLine = thisLine;
                        acceptLine = -1;
                    }       
                }
            } else { // Assign, etc
                if (simplifyScriptToGetCompletionsOkay(o, variableNames,importedNames)) {
                    if (acceptLine < 0) {
                        acceptLine = beginLine;
                        for (int i = currentLine + 1; i < acceptLine; i++) {
                            result.append("\n");
                            currentLine = acceptLine;
                        }
                    }
                } else {
                    if (acceptLine > -1) {
                        int thisLine = beginLine;
                        for (int i = acceptLine; i <= thisLine; i++) {
                            if (i < thisLine) {
                                appendToResult(result, ss[i]).append("\n");
                            } else {
                                if (ss[i].length() > 0 && Character.isWhitespace(ss[i].charAt(0))) {
                                    appendToResult(result, ss[i]).append("\n");
                                }
                            }
                        }
                        appendToResult(result, "\n");
                        currentLine = thisLine;
                        acceptLine = -1;
                    }
                }
            }
        }
        if (acceptLine > -1) {
            lastLine = JythonUtil.handleContinue(ss, lastLine);
            int thisLine = lastLine;
            for (int i = acceptLine; i <= thisLine; i++) {
                appendToResult(result, ss[i]).append("\n");
            }
            String slastLine = ss[thisLine].trim();
            if (slastLine.endsWith(":")) {
                appendToResult(result, getIndent(slastLine) + "    pass");
            }
        }
        return result.toString();
    }

    /**
     * can we resolve this node given the variable names we know?
     *
     * @param o
     * @param variableNames
     * @return true if the node can be resolved.
     */
    private static boolean simplifyScriptToGetCompletionsCanResolve(SimpleNode o, HashSet<String> variableNames) {
        //if ( o.beginLine>=617 && o.beginLine<619 ) {
        //    System.err.println( "here at 617-ish");
        //}
        if (o instanceof Name) {
            Name c = (Name) o;
            if (!variableNames.contains(c.id)) {
                logger.finest(String.format("%04d canResolve->false: %s", o.beginLine, o.toString()));
                return false;
            }
        } else if (o instanceof Attribute) {
            Attribute at = (Attribute) o;
            while (at.value instanceof Attribute || at.value instanceof Subscript) {
                if (at.value instanceof Attribute) {
                    at = (Attribute) at.value;
                } else {
                    Subscript s = (Subscript) at.value;
                    if (s.value instanceof Attribute) {
                        at = (Attribute) s.value;
                    } else {
                        return false; // oh just give up...
                    }
                }
            }   
            if ( !simplifyScriptToGetCompletionsCanResolve( at.value, variableNames ) ) {
                return false;
            }
        } else if ( o instanceof Compare ) {
            Compare c = (Compare)o;
            if ( simplifyScriptToGetCompletionsCanResolve( c.left, variableNames ) ) {
                for ( exprType e : c.comparators ) {
                    if ( ! simplifyScriptToGetCompletionsCanResolve( e, variableNames ) ) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
                  
        } else if ( o instanceof Call ) {
            Call c = (Call)o;
            if ( !simplifyScriptToGetCompletionsCanResolve( c.func, variableNames ) ) {
                return false;
            }
            for ( exprType e : c.args ) {
                if ( ! simplifyScriptToGetCompletionsCanResolve( e, variableNames ) ) {
                    return false;
                }
            }
        } else if ( o instanceof Subscript ) {
            return false;
              
        } else if ( o instanceof Str ) {
            return true;
        } else if ( o instanceof Num ) {
            return true;
        }
        
        MyVisitorBase vb = new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest(String.format(" %04d canResolve->%s: %s", o.beginLine, vb.visitNameFail, o));
            return vb.looksOkay || !vb.visitNameFail;

        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest(String.format("!! %04d canResolve->false: %s", o.beginLine, o));
        return false;
    }

    /**
     * dumb kludge where no-arg constructor is called to get an instance for completions. This is really an experiment...
     *
     * @param a
     * @return
     */
    private static String maybeModelAssert(Assert a, HashSet<String> variableNames) {
        if (a.test instanceof Call) {
            org.python.parser.ast.Call cc = (org.python.parser.ast.Call) a.test;
            exprType f = cc.func;
            if (f instanceof Name) {
                if (((Name) f).id.equals("isinstance")) {
                    if (cc.args.length == 2) {
                        exprType a1 = cc.args[0];
                        if (a1 instanceof Name) {
                            exprType a2 = cc.args[1];
                            if (a2 instanceof Name && variableNames.contains(((Name) a2).id)) {
                                return String.format("%s" + JythonCompletionTask.__CLASSTYPE + "=%s # inserted by maybeModelAssert", ((Name) a1).id, ((Name) a2).id);
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
     * This should be used for read access, not write access, so:<ul>
     * <li>p= dom.plots[0]  is okay
     * <li>dom.plots[1].rowId= dom.plots[0].rowId  is not okay.
     * </ul>
     * @param o the statement, for example an import or an assignment
     * @param variableNames known symbol names 
     * @param importedNames map of name to path (DatumRange &rarr; org.das2.datum)
     * @return true if we can include this in the script without a huge performance penalty.
     */
    private static boolean simplifyScriptToGetCompletionsOkay(stmtType o, HashSet<String> variableNames,Map<String,String> importedNames) {
        logger.log(Level.FINEST, "simplify script line: {0}", o.beginLine);
        if ((o instanceof org.python.parser.ast.ImportFrom)) {
            org.python.parser.ast.ImportFrom importFrom = (org.python.parser.ast.ImportFrom) o;
            for (aliasType a : importFrom.names) {
                if (a.asname != null) {
                    variableNames.add(a.asname);
                } else {
                    variableNames.add(a.name);
                }
            }
            return true;
        }
        if ((o instanceof org.python.parser.ast.Import)) {
            org.python.parser.ast.Import imporrt = (org.python.parser.ast.Import) o;
            for (aliasType a : imporrt.names) {
                if (a.asname != null) {
                    variableNames.add(a.asname);
                } else {
                    variableNames.add(a.name);
                }
            }
            return true;
        }
        if ((o instanceof org.python.parser.ast.Expr)) {
            Expr e = (Expr) o;
            if ((e.value instanceof Call) ) {
                if ( trivialFunctionCall((Call) e.value) ) {
                    return true;
                } else {
                    Call c= (Call) e.value;
                    if ( c.func instanceof Attribute ) {
                        Attribute aa= (Attribute)c.func;
                        if ( aa.value instanceof Name ) {
                            Name naa= (Name)aa.value;
                            if ( variableNames.contains(naa.id) ) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        if ((o instanceof org.python.parser.ast.ClassDef)) {
            return true;
        }
        if ((o instanceof org.python.parser.ast.FunctionDef)) {
            return true;
        }
        if ((o instanceof org.python.parser.ast.Assign)) {
            Assign a = (Assign) o;
            if (simplifyScriptToGetCompletionsOkayNoCalls(a.value, variableNames)) {
                if (!simplifyScriptToGetCompletionsCanResolve(a.value, variableNames)) {
                    return false;
                }
                for (exprType target : a.targets) {
                    exprType et = (exprType) target;
                    if (et instanceof Name) {
                        String id = ((Name) target).id;
                        variableNames.add(id);
                        logger.log(Level.FINEST, "assign to variable {0}", id);
                        //TODO: can we identify type?  Insert <id>__CLASSTYPE=... for completions.
                        if ( a.value instanceof Call ) {
                            String type= maybeIdentifyReturnType( id, (Call)a.value, importedNames );
                            logger.log(Level.FINE, "id type: {0}__CLASSTYPE= {1}", new Object[]{id, type});
                        }
                    } else if (et instanceof Attribute) {
                        return false;
//                        Attribute at = (Attribute) et;
//                        while (at.value instanceof Attribute || at.value instanceof Subscript) {
//                            if (at.value instanceof Attribute) {
//                                at = (Attribute) at.value;
//                            } else {
//                                Subscript s = (Subscript) at.value;
//                                if (s.value instanceof Attribute) {
//                                    at = (Attribute) s.value;
//                                } else {
//                                    return false; // oh just give up...
//                                }
//                            }
//                        }
//                        if (at.value instanceof Name) {
//                            Name n = (Name) at.value;
//                            if (!variableNames.contains(n.id)) {
//                                return false;
//                            }
//                        }
                    } else if (et instanceof Subscript) {
                        return false;
                        //Subscript subscript = (Subscript) et;
                        //exprType et2 = subscript.value;
                        //if (et2 instanceof Name) {
                        //    Name n = (Name) et2;
                        //    if (variableNames.contains(n.id)) {
                        //        return true;
                        //    }
                        //}
                        //return false;
                    } else {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
        if ((o instanceof org.python.parser.ast.If)) {
            return simplifyScriptToGetCompletionsOkayNoCalls(o, variableNames);
        }
        if ((o instanceof org.python.parser.ast.Print)) {
            return false;
        }
        logger.log(Level.FINEST, "not okay to simplify: {0}", o);
        return false;
    }

    /**
     * inspect the node to look for function calls that are not to the function "getParam". This is awful code that will be
     * rewritten when we upgrade Python to 2.7.
     *
     * @param o
     * @param variableNames
     * @return
     */
    private static boolean simplifyScriptToGetCompletionsOkayNoCalls(SimpleNode o, HashSet<String> variableNames) {

        if (o instanceof Call) {
            Call c = (Call) o;

            if (!trivialFunctionCall(c)) {
                if (!trivialConstructorCall(c)) {
                    logger.finest(String.format("%04d simplify->false: %s", o.beginLine, o.toString()));
                    return false;
                }
            }
        } else if ( o instanceof Str ) {
            return true;
        } else if ( o instanceof Num ) {
            return true;
        }
        
        MyVisitorBase vb = new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest(String.format(" %04d simplify->%s: %s", o.beginLine, vb.looksOkay(), o));
            return vb.looksOkay();

        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest(String.format("!! %04d simplify->false: %s", o.beginLine, o));
        return false;
    }

    /**
     * there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, such as the
     * string.upper() function. The commas are to guard against the id being a subset of another id ("lower," does not match
     * "lowercase"). TODO: update this after Python upgrade.
     * @see JythonUtil#okay
     */
    private static final String[] okay = new String[]{
        "range,", "xrange,", "irange,","map,","join,",
        "getParam,", "lower,", "upper,", "URI,", "URL,",
        "setScriptDescription", "setScriptTitle", "setScriptLabel", "setScriptIcon",
        "DatumRangeUtil,", "TimeParser,",
        "str,", "int,", "long,", "float,", "datum,", "datumRange,", "dataset,",
        "indgen,", "findgen,", "dindgen,",
        "ones,", "zeros,",
        "linspace,", "logspace,",
        "dblarr,", "fltarr,", "strarr,", "intarr,", "bytarr,",
        "ripples,",//"split,", // remove split because it is confused with the URISplit.
        "color,", "colorFromString,", "isinstance,"};
 
    private static final Set<String> okaySet = new HashSet<>();

    static {
        for (String o : okay) {
            okaySet.add(o.substring(0, o.length() - 1));
        }
    }

    private static String getFunctionName(exprType t) {
        if (t instanceof Name) {
            return ((Name) t).id;
        } else if (t instanceof Attribute) {
            Attribute a = (Attribute) t;
            return getFunctionName(a.value) + "." + a.attr;
        } else {
            return t.toString();
        }
    }

    /**
     * return true if the function call is trivial to execute and can be evaluated within a few milliseconds. For example, findgen
     * can be called because no calculations are made in the call, but fft cannot. Typically these are Order 1 (a.k.a. constant
     * time) operations, but also many Order N operations are so fast they are allowed.
     *
     * @param sn an AST node pointed at a Call.
     * @return true if the function call is trivial to execute
     */
    private static boolean trivialFunctionCall(SimpleNode sn) {
        if (sn instanceof Call) {
            Call c = (Call) sn;
            boolean klugdyOkay = false;
            String ss = c.func.toString(); // we just want "DatumRangeUtil" of the Attribute
            //String ss= getFunctionName(c.func);
            for (String s : okay) {
                if (ss.contains(s)) {
                    klugdyOkay = true;
                }
            }
            if (klugdyOkay == false) {
                if (ss.contains("TimeUtil") && ss.contains("now")) {
                    klugdyOkay = true;
                }
            }
            logger.log(Level.FINER, "trivialFunctionCall={0} for {1}", new Object[]{klugdyOkay, c.func.toString()});
            return klugdyOkay;
        } else {
            return false;
        }
    }

    /**
     * return true if the function call is trivial to execute because it's a constructor, which presumably takes little time to
     * create.
     *
     * @param sn
     * @return true if it is a constructor call
     */
    private static boolean trivialConstructorCall(SimpleNode sn) {
        if (sn instanceof Call) {
            Call c = (Call) sn;
            if (c.func instanceof Name) {
                String funcName = ((Name) c.func).id;
                return Character.isUpperCase(funcName.charAt(0));
            } else if (c.func instanceof Attribute) {  // Rectangle.Double
                String funcName = ((Attribute) c.func).attr;
                return Character.isUpperCase(funcName.charAt(0));
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * placeholder for code which identifies constructors. This is fragile and misguided code, which looks for the Java convention
     * of uppercase letter starting.
     *
     * @param name the name, like GrannyTextRenderer
     * @param importedNames map from name to path "GrannyTextRenderer" &rarr; "org.das2.util"
     * @return true if the name is known to be a constructor call.
     */
    private static boolean isConstructor(String name, Map<String, String> importedNames) {
        if (importedNames.containsKey(name)) {
            return name.length() > 2 && Character.isUpperCase(name.charAt(0)) && Character.isLowerCase(name.charAt(1));
        } else {
            return false;
        }

    }

    /**
     * return the class for the name.
     *
     * @param name the name, like GrannyTextRenderer
     * @param importedNames map from name to path "GrannyTextRenderer" &rarr; "org.das2.util"
     * @return
     */
    private static Class getClassFor(String name, Map<String, String> importedNames) {
        String path = importedNames.get(name);
        try {
            return Class.forName(path + "." + name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static String maybeIndentifyValue( exprType t ) {
        if ( t instanceof Num ) {
            return String.valueOf(((Num)t).n);
        } else if ( t instanceof Str ) {
            return "'"+ ((Str)t).toString() + "'";
        } else if ( t instanceof Name ) {
            String n=  ((Name)t).id;
            if ( n.equals("True") || n.equals("False") ) {
                return n;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * return the line of code needed to use the result, or null.
     *
     * @param id the identifier being assigned the value
     * @param c the function being called
     * @param importedNames map of name to path (DatumRange &rarr; org.das2.datum)
     * @return the assignment and possibly an additional import statement
     */
    private static String maybeIdentifyReturnType(String id, Call c, Map<String, String> importedNames) {
        if (c.func instanceof Name) {
            String funcName = ((Name) c.func).id;
            switch (funcName) {
                case "getApplication":
                    return "from org.autoplot import AutoplotUI\n" 
                            + id + JythonCompletionTask.__CLASSTYPE + " = AutoplotUI\n";
                case "getApplicationModel":
                    return "from org.autoplot import ApplicationModel\n" 
                            + id + JythonCompletionTask.__CLASSTYPE + " = ApplicationModel\n";
                case "getDataSource":
                    return "from org.autoplot.datasource import DataSource\n" 
                            + id + JythonCompletionTask.__CLASSTYPE + " = DataSource\n";
                case "addMouseModule":
                    return "import org.das2.event.MouseModule\n" 
                            + id + JythonCompletionTask.__CLASSTYPE + " = org.das2.event.MouseModule    # return type from " + funcName + " (spot line898)\n";
                case "createDataPointRecorder":
                    return "import org.das2.components.DataPointRecorder\n" 
                            + id + JythonCompletionTask.__CLASSTYPE + " = org.das2.components.DataPointRecorder    # return type from " + funcName + " (spot line898)\n";

                case "getParam": // weird--there's a special getParam loaded, but this doesn't work for  getParam( 'allowCache', True, 'allow storage of ephemeris to local file', [ True,False ] )
                    exprType e1= c.args[1];
                    String vv= maybeIndentifyValue(e1);
                    if ( vv!=null ) {
                        return id + " = " + vv;
                    } else {
                        if ( e1.getImage()==null ) {
                            if ( e1 instanceof Subscript ) {
                                Subscript s= (Subscript)e1;
                                if ( s.slice instanceof Index && s.value instanceof Name ) {
                                    Index i= (Index)s.slice;
                                    String ss= maybeIndentifyValue(i.value);
                                    String nn= ((Name)s.value).id;
                                    return id + " = " + nn + "[" + ss +"]";
                                } else {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        } else {
                            return id  + JythonCompletionTask.__CLASSTYPE + " = " + e1.getImage();
                        }
                    }
                case "getDataSet":
                case "xtags":
                case "ytags":
                case "findgen":
                case "linspace":
                case "fftPower":
                case "magnitude":
                    return id + JythonCompletionTask.__CLASSTYPE + " = QDataSet    # return type from " + funcName + " (spot line789)\n";
                case "datumRange":
                    return id + JythonCompletionTask.__CLASSTYPE + " = DatumRange    # return type from " + funcName + " (spot line896)\n";
                case "datum":
                    return id + JythonCompletionTask.__CLASSTYPE + " = Datum    # return type from " + funcName + " (spot line898)\n";
                default:
                    break;
            }
            for ( Method m: Ops.class.getMethods() ) {
                if ( m.getName().equals(funcName) ) {
                    if ( QDataSet.class.isAssignableFrom( m.getReturnType() ) ) {
                        return id + JythonCompletionTask.__CLASSTYPE + " = QDataSet    # ( spot line 898 )";
                    }
                }
            }
            if (isConstructor(funcName, importedNames)) {
                return id + JythonCompletionTask.__CLASSTYPE + " = " + funcName + "  # isConstructor (line794)\n";
            } else {
                return null;
            }
        } else if (c.func instanceof Attribute) {
            // p=PngWalkTool.start(...)
            Attribute at = (Attribute) c.func;
            if (at.value instanceof Name) {
                String attrName = ((Name) at.value).id;
                if (attrName.equals("PngWalkTool") && at.attr.equals("start")) {
                    return "from org.autoplot.pngwalk import PngWalkTool\n" + id + JythonCompletionTask.__CLASSTYPE + "= PngWalkTool #(spot line802)\n";
                } else if (importedNames.containsKey(attrName)) {
                    Class claz = getClassFor(attrName, importedNames);
                    if (claz == null) {
                        return null;
                    }
                    Method[] mm = claz.getMethods();
                    String methodName = at.attr;
                    for (Method m : mm) {
                        if (m.getName().equals(methodName)) { //&& m.getGenericParameterTypes().length ) {
                            Class rclz = m.getReturnType();
                            String rclzn = rclz.getSimpleName();
                            StringBuilder result= new StringBuilder();
                            if ( rclz.getPackage()!=null ) {
                                result.append( String.format( "from %s import %s\n", rclz.getPackage().getName(), rclzn ) );
                            }
                            result.append( String.format( "%s%s = %s # (line895)\n", id, JythonCompletionTask.__CLASSTYPE, rclzn ) );
                            return result.toString();
                        }
                    }
                    return null;
                }
            } else if (at.value instanceof Call) {
                String x = maybeIdentifyReturnType("x", (Call) at.value, importedNames);
                if (x != null) {
                    int i = x.indexOf("=");
                    try {
                        String s= x.substring(i + 1);
                        int i2= s.indexOf('#');
                        if ( i2>-1 ) {
                            s= s.substring(0,i2);
                        }
                        s= s.trim();
                        if ( importedNames.containsKey(s) ) {
                            String packg= importedNames.get(s);
                            Class clz = Class.forName(packg+"."+s);
                            try {
                                Method m = clz.getMethod(at.attr); //TODO: this is only single-argument calls
                                Class rclz = m.getReturnType();
                                String rclzn = rclz.getSimpleName();
                                return "from " + rclz.getPackage().getName() + " import " + rclzn + "\n"
                                        + id + JythonCompletionTask.__CLASSTYPE + " = " + rclzn + "   # (spot line826)";
                            } catch (NoSuchMethodException | SecurityException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }
                    } catch (ClassNotFoundException ex) {
                        logger.log(Level.SEVERE, null, ex);
                        return null; // shouldn't happen
                    }
                    return null;
                } else {
                    return null;
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * if we recognize the function that is called, then go ahead and keep track of the type. This is a quick and cheesy
     * implementation that just looks for a few names. For example,<ul>
     * <li>x= getApplication() -- x is an AutoplotUI
     * <li>x= getApplicationModel() -- x is a ApplicationModel
     * <li>x= getDataSource() -- x is a DataSource
     * <li>x= PngWalkTool.start() -- x is a PngWalkTool
     * </ul>
     * See bug https://sourceforge.net/p/autoplot/bugs/2319/ for some discussion on this.
     *
     * @param a the assignment
     * @return the Jython code to insert.
     */
    private static String maybeIdentifyType(Assign a, Map<String, String> importedNames) {
        if (a.targets.length == 1) {
            exprType target = a.targets[0];
            exprType et = (exprType) target;
            if (et instanceof Name) {
                String id = ((Name) target).id;
                if (a.value instanceof Call) {
                    Call c = (Call) a.value;
                    return maybeIdentifyReturnType(id, c, importedNames);
                } else if ( a.value instanceof Subscript ) {
                    Subscript s= ((Subscript)a.value);
                    if ( s.value instanceof Attribute ) {
                        Attribute att = (Attribute)s.value;
                        if ( att.value instanceof Name ) {
                            if ( ((Name)att.value).id.equals("dom") ) {
                                if ( att.attr.equals("plots") ) {
                                    String rclzn = "org.autoplot.dom.Plot";
                                    return "import org.autoplot.dom.Plot\n"
                                    + id + JythonCompletionTask.__CLASSTYPE + " = " + rclzn + "  # (spot line955 a)\n";
                                } else if ( att.attr.equals("canvases") ) {
                                    String rclzn = "org.autoplot.dom.Canvas";
                                    return "import org.autoplot.dom.Canvas\n"
                                    + id + JythonCompletionTask.__CLASSTYPE + " = " + rclzn + "  # (spot line955 b)\n";
                                } else if ( att.attr.equals("plotElements") ) {
                                    String rclzn = "org.autoplot.dom.PlotElement";
                                    return "import org.autoplot.dom.PlotElement\n"
                                    + id + JythonCompletionTask.__CLASSTYPE + " = " + rclzn + "  # (spot line955 c)\n";
                                }
                            }
                        }
                    } else if ( s.value instanceof Name ) {
                        return id + JythonCompletionTask.__CLASSTYPE + " = QDataSet  # (spot line1014 a)\n";
                    }
                } else if ( a.value instanceof BinOp ) { // just go ahead and assume it's a QDataSet
                    return id + JythonCompletionTask.__CLASSTYPE + " = QDataSet  # (spot line1014 b)\n";
                } else if ( a.value instanceof Num ) {
                    return id + " = " + ((Num)a.value).n;
                } else if ( a.value instanceof Str ) {
                    return id + " = \"\"\"" + ((Str)a.value).s + "\"\"\"";
                }
            } else if ( et instanceof Tuple && a.value instanceof Tuple ) {  // lowCut_toggle, highcut_toggle, filttype_toggle, order_toggle = 0.5, 20, 'Bandpass', 4 # filter toggles LOW FLYER
                Tuple targetTuple= (Tuple)et;
                Tuple valueTuple= (Tuple)a.value;
                if ( targetTuple.elts.length== valueTuple.elts.length ) {
                    StringBuilder result= new StringBuilder();
                    for ( int i=0; i<targetTuple.elts.length; i++ ) {
                        if ( targetTuple.elts[i] instanceof Name ) {
                            Assign a1= new Assign( new exprType[] { targetTuple.elts[i] }, valueTuple.elts[i] );
                            String line= maybeIdentifyType( a1, importedNames );
                            if ( line!=null ) {
                                result.append(line).append("\n");
                            }
                        }
                    }
                    if ( result.length()>0 ) {
                        return result.toString();
                    }
                }
            }
        }
        return null;
    }

    private static Object Name(exprType func) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static class MyVisitorBase<R> extends VisitorBase {

        boolean looksOkay = true;
        boolean visitNameFail = false;

        HashSet names;

        MyVisitorBase(HashSet names) {
            this.names = names;
        }

        @Override
        public Object visitName(Name node) throws Exception {
            logger.log(Level.FINER, "visitName({0})", node);
            if (!names.contains(node.id)) {
                visitNameFail = true;
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
            if (sn instanceof Call) {
                looksOkay = trivialFunctionCall(sn) || trivialConstructorCall(sn);
                Call c= (Call)sn;
                for ( exprType t: c.args ) {
                    traverse(t);
                }
                logger.log(Level.FINER, "looksOkay={0}", looksOkay);
            } else if (sn instanceof Assign) {
                Assign a = ((Assign) sn);
                exprType et = a.value;
                if (et instanceof Call) {
                    looksOkay = trivialFunctionCall(et) || trivialConstructorCall(sn);
                    logger.log(Level.FINER, "looksOkay={0}", looksOkay);
                }
            } else if (sn instanceof Name) {
                String t = ((Name) sn).id;
                if (t.length() > 1 && Character.isUpperCase(t.charAt(0))) {
                    logger.log(Level.FINER, "name is assumed to be a constructor call name: {0}", t);
                    return;
                }
                if (!names.contains(t)
                        && !okaySet.contains(t)) {
                    looksOkay = false; // TODO: why are there both looksOkay and visitNameFail?
                    visitNameFail= true; // TODO: I really don't understand this...
                    logger.log(Level.FINER, "looksOkay={0}", looksOkay);
                }
            } else if (sn instanceof Attribute) {
                traverse(((Attribute) sn).value);  // DatumRangeUtil

            } else if (sn instanceof Subscript) {
                Subscript ss = (Subscript) sn;
                exprType et = ss.value;
                if (et instanceof Name) {
                    traverse((Name) (et));
                }
                //ss.value;
                //visitName((Name))
            } else if (sn instanceof BinOp) {
                BinOp bo = (BinOp) sn;
                traverse(bo.left);
                traverse(bo.right);
            } else if (sn instanceof UnaryOp) {
                UnaryOp bo = (UnaryOp) sn;
                traverse(bo.operand);
            } else if (sn instanceof Num) {

            } else if ( sn instanceof Str ) {
                
            } else if (sn instanceof Index) {
                Index index = (Index) sn;
                traverse(index.value);
                
            } else if (sn instanceof Tuple) {
                Tuple tuple = (Tuple) sn;
                for ( exprType e: tuple.elts ) {
                    traverse(e);
                }
            } else if (sn instanceof List) {
                List ll = (List) sn;
                for ( exprType e: ll.elts ) {
                    traverse(e);
                }   
            } else if (sn instanceof Dict) {
                Dict dict = (Dict) sn;
                for ( exprType e: dict.keys ) {
                    traverse(e);
                }   
                for ( exprType e: dict.values ) {
                    traverse(e);
                }   
            } else {
                logger.log(Level.FINE, "unchecked: {0}", sn);
                looksOkay= false;
            }
        }

        public boolean looksOkay() {
            return looksOkay;
        }

        /**
         * this contains a node whose name we can't resolve.
         *
         * @return
         */
        public boolean visitNameFail() {
            return visitNameFail;
        }
    }

}
