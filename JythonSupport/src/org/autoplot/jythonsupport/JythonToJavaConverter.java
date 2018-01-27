
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import org.autoplot.datasource.DataSetURI;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.parser.SimpleNode;
import org.python.parser.ast.*;

/**
 * experiment with code which converts the Jython AST (syntax tree) into Java
 * code.
 *
 * @author jbf
 */
public class JythonToJavaConverter {

    private static class MyVisitorBase<R> extends VisitorBase {

        boolean looksOkay = true;
        boolean visitNameFail = false;

        StringBuilder builder;
        int lineNumber = 1;
        boolean includeLineNumbers = false;

        MyVisitorBase(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Object visitName(Name node) throws Exception {
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
            traverse("", sn, false);
        }

        private static final Map<Integer,String> ops= new HashMap<>();
        static {
            ops.put( 1, "+" );
            ops.put( 2, "-" );
            ops.put( 3, "*" );
            ops.put( 4, "/" );
            ops.put( 6, "^" );
            ops.put( 12, "/floordiv/" );
        };
                
        public void traverse(String indent, SimpleNode sn, boolean inline) throws Exception {
            if (includeLineNumbers && (this.builder.length() == 0 || builder.charAt(this.builder.length() - 1) == '\n')) {
                this.builder.append(String.format("%04d: ", lineNumber));
            }
            while (sn.beginLine > lineNumber) {
                this.builder.append("\n");
                lineNumber++;
                if (includeLineNumbers) {
                    this.builder.append(String.format("%04d: ", lineNumber));
                }
            }
            //if ( lineNumber==4 ) {
            //    System.err.println("here line number breakpoint at line "+lineNumber );
            //}
            if (sn instanceof org.python.parser.ast.FunctionDef) {
                FunctionDef fd = (FunctionDef) sn;
                this.builder.append("private void ").append(fd.name).append("(");
                for (int i = 0; i < fd.args.args.length; i++) {
                    if (i > 0) {
                        this.builder.append(",");
                    }
                    this.builder.append("").append(fd.args.args[i].getImage().toString());
                }
                this.builder.append(") {\n");
                lineNumber++;
                for (int i = 0; i < fd.body.length; i++) {
                    traverse("\t", fd.body[i], false);
                    if (!inline) {
                        this.builder.append(";\n");
                        lineNumber++;
                    }
                }
                this.builder.append("}\n");
                lineNumber++;
            } else if (sn instanceof Expr) {
                Expr ex = (Expr) sn;
                traverse("", ex.value, true);
                if (!inline) {
                    this.builder.append(";\n");
                    lineNumber++;
                }
            } else if (sn instanceof Print) {
                Print pr = ((Print) sn);
                this.builder.append(indent);
                this.builder.append("System.err.println(");
                for (int i = 0; i < pr.values.length; i++) {
                    if (i > 0) {
                        this.builder.append(",");
                    }
                    traverse("", pr.values[i], false);
                }
                this.builder.append(");");
            } else if (sn instanceof ImportFrom) {
                ImportFrom ff = ((ImportFrom) sn);
                for (int i = 0; i < ff.names.length; i++) {
                    this.builder.append("import ").append(ff.module).append('.').append(ff.names[i].name).append(";\n");
                    lineNumber++;
                }
            } else if (sn instanceof Str) {
                Str ss = (Str) sn;
                this.builder.append("\"");
                this.builder.append(ss.s);
                this.builder.append("\"");
            } else if (sn instanceof Num) {
                Num ex = (Num) sn;
                this.builder.append(ex.n);
            } else if (sn instanceof BinOp) {
                BinOp as = ((BinOp) sn);
                this.builder.append(indent);
                if (as.left instanceof Str && as.op == 5) {
                    this.builder.append("String.format(");
                    traverse("", as.left, true);
                    this.builder.append(",");
                    traverse("", as.right, true);
                    this.builder.append(")");
                } else {
                    traverse("", as.left, true);
                    String sop= ops.get(as.op);
                    if ( sop==null ) sop= " ?? ";
                    this.builder.append( sop );
                    traverse("", as.right, true);
                }
            } else if (sn instanceof Assign) {
                Assign as = ((Assign) sn);
                this.builder.append(indent);
                for (int i = 0; i < as.targets.length; i++) {
                    if (i > 0) {
                        this.builder.append(",");
                    }
                    traverse("", as.targets[i], false);
                }
                this.builder.append("=");
                traverse("", as.value, false);

            } else if (sn instanceof Name) {
                this.builder.append(((Name) sn).id);
            } else if (sn instanceof Call) {
                Call cc = (Call) sn;
                if (cc.func instanceof Name) {
                    if (Character.isUpperCase(((Name) cc.func).id.charAt(0))) {
                        this.builder.append("new").append(" ");
                    }
                }
                traverse("", cc.func, true);
                this.builder.append("(");
                for (int i = 0; i < cc.args.length; i++) {
                    if (i > 0) {
                        this.builder.append(",");
                    }
                    traverse("", cc.args[i], true);
                }
                this.builder.append(")");
                if (!inline) {
                    this.builder.append(";\n");
                    lineNumber++;
                }

            } else if (sn instanceof For) {
                For ff = (For) sn;
                this.builder.append(indent).append("for ( Object ");
                traverse("", ff.target, false);
                this.builder.append(" : ");
                traverse("", ff.iter, false);
                this.builder.append(" ) {\n");
                lineNumber++;
                for (int i = 0; i < ff.body.length; i++) {
                    this.builder.append(indent).append(indent);
                    traverse(indent + indent, ff.body[i], false);
                }
                this.builder.append(indent).append("}\n");
                lineNumber++;
            } else if (sn instanceof If) {
                If ff = (If) sn;
                this.builder.append(indent).append("if ( ");
                traverse("", ff.test, false);
                this.builder.append(" ) {\n");
                lineNumber++;
                for (int i = 0; i < ff.body.length; i++) {
                    this.builder.append(indent).append(indent);
                    traverse(indent + indent, ff.body[i], false);
                    this.builder.append(";");
                }
                this.builder.append(indent).append("}\n");
                lineNumber++;
            } else if (sn instanceof Compare) {
                Compare cp = (Compare) sn;
                traverse("", cp.left, false);
                this.builder.append("?in?");
                for (exprType t : cp.comparators) {
                    traverse("", t, false);
                }
            } else if (sn instanceof Continue) {
                this.builder.append("continue");
            } else if (sn instanceof Attribute) {
                Attribute at = ((Attribute) sn);
                traverse("", at.value, false);
                this.builder.append(".");
                this.builder.append(at.attr);
            } else {
                this.builder.append(sn.toString()).append("\n");
                lineNumber++;
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

    public static String convert(String script) throws Exception {
        Module n = (Module) org.python.core.parser.parse(script, "exec");
        StringBuilder b = new StringBuilder();
        convert(b, n);
        return b.toString();
    }

    private static void convert(StringBuilder sb, Module n) throws Exception {
        VisitorBase vb = new MyVisitorBase(sb);
        n.traverse(vb);

    }

    public static void main(String[] args) throws Exception {
        String code;
        //String code= "def foo():\n  print 'hello'\nfoo()";
        //System.err.println( convert(code) );

        //String furi= "/home/jbf/project/autoplot/script/lookAtUserComments.jy";
        //String furi= "/home/jbf/project/autoplot/script/curveFitting.jy";
        String furi = "/home/jbf/project/autoplot/script/addLabelToPng.jy";

        File src = DataSetURI.getFile(furi, new NullProgressMonitor());

        try (FileReader reader = new FileReader(src)) {
            code = JythonUtil.readScript(new BufferedReader(reader));
            System.err.println(convert(code));
        }
    }

}
