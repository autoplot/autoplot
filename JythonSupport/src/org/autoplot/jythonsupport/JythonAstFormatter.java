/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.autoplot.jythonsupport;

import org.python.parser.Node;
import org.python.parser.ast.*;

/**
 * Convert a Jython AST back into reasonably formatted Jython source.
 *
 * Intended for the AST used by Jython 2.2.
 * 
 * @see JythonSourceEmitter
 */
public class JythonAstFormatter {

    private static class LineCountingStringBuilder {

        private final StringBuilder builder = new StringBuilder();
        private int lineCount = 1;

        public LineCountingStringBuilder append(String s) {
            builder.append(s);

            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\n') {
                    lineCount++;
                }
            }
            return this;
        }

        public LineCountingStringBuilder append(char c) {
            builder.append(c);

            if (c == '\n') {
                lineCount++;
            }
            return this;
        }

        public LineCountingStringBuilder append(Object o) {
            return append(String.valueOf(o));
        }

        public int getLineCount() {
            return lineCount;
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }

    private static final String INDENT = "    ";

    public static String format(Node node) {
        LineCountingStringBuilder out = new LineCountingStringBuilder();
        format(node, out, 0);
        return out.toString();
    }

    private static void format(Node node, LineCountingStringBuilder out, int indent) {
        if (node == null) {
            return;
        }

        /*
         * Expressions
         */

        if (node instanceof Name) {
            Name n = (Name) node;
            out.append(n.id);
            return;
        }

        if (node instanceof org.python.parser.ast.Num) {
            Num n = (Num) node;
            out.append(n.n);
            return;
        }

        if (node instanceof Str) {
            Str n = (Str) node;
            appendString(out, n.s);
            return;
        }

        if (node instanceof BinOp) {
            BinOp n = (BinOp) node;

            out.append("(");
            format(n.left, out, indent);
            out.append(" ");
            out.append(binaryOperator(n.op));
            out.append(" ");
            format(n.right, out, indent);
            out.append(")");
            return;
        }

        if (node instanceof UnaryOp) {
            UnaryOp n = (UnaryOp) node;

            out.append(unaryOperator(n.op));
            format(n.operand, out, indent);
            return;
        }

        if (node instanceof Attribute) {
            Attribute n = (Attribute) node;

            format(n.value, out, indent);
            out.append(".");
            out.append(n.attr);
            return;
        }

        if (node instanceof List) {
            List n = (List) node;

            out.append("[");
            for (int i = 0; i < n.elts.length; i++) {
                if (i > 0) out.append(", ");
                format(n.elts[i], out, indent);
            }
            out.append("]");
            return;
        }

        if (node instanceof Tuple) {
            Tuple n = (Tuple) node;

            out.append("(");
            for (int i = 0; i < n.elts.length; i++) {
                if (i > 0) out.append(", ");
                format(n.elts[i], out, indent);
            }

            if (n.elts.length == 1) {
                out.append(",");
            }

            out.append(")");
            return;
        }

        if (node instanceof Call) {
            Call n = (Call) node;

            format(n.func, out, indent);
            out.append("(");

            boolean comma = false;

            for (int i = 0; i < n.args.length; i++) {
                if (comma) out.append(", ");
                format(n.args[i], out, indent);
                comma = true;
            }

            for (int i = 0; i < n.keywords.length; i++) {
                if (comma) out.append(", ");

                keywordType k = n.keywords[i];

                out.append(k.arg);
                out.append("=");
                format(k.value, out, indent);

                comma = true;
            }

            if (n.starargs != null) {
                if (comma) out.append(", ");
                out.append("*");
                format(n.starargs, out, indent);
                comma = true;
            }

            if (n.kwargs != null) {
                if (comma) out.append(", ");
                out.append("**");
                format(n.kwargs, out, indent);
            }

            out.append(")");
            return;
        }

        /*
         * Statements
         */

        if (node instanceof Expr) {
            Expr n = (Expr) node;

            indent(out, indent);
            format(n.value, out, indent);
            return;
        }

        if (node instanceof Assign) {
            Assign n = (Assign) node;

            indent(out, indent);

            for (int i = 0; i < n.targets.length; i++) {
                if (i > 0) {
                    out.append(" = ");
                }
                format(n.targets[i], out, indent);
            }

            out.append(" = ");
            format(n.value, out, indent);
            return;
        }

        if (node instanceof Return) {
            Return n = (Return) node;

            indent(out, indent);
            out.append("return");

            if (n.value != null) {
                out.append(" ");
                format(n.value, out, indent);
            }
            return;
        }

        if (node instanceof Pass) {
            indent(out, indent);
            out.append("pass");
            return;
        }

        if (node instanceof Break) {
            indent(out, indent);
            out.append("break");
            return;
        }

        if (node instanceof Continue) {
            indent(out, indent);
            out.append("continue");
            return;
        }

        /*
         * Compound statements
         */

        if (node instanceof If) {
            If n = (If) node;

            indent(out, indent);
            out.append("if ");
            format(n.test, out, indent);
            out.append(":\n");

            formatStatements(n.body, out, indent + 1);

            if (n.orelse != null && n.orelse.length > 0) {
                out.append("\n");
                indent(out, indent);
                out.append("else:\n");
                formatStatements(n.orelse, out, indent + 1);
            }
            return;
        }

        if (node instanceof While) {
            While n = (While) node;

            indent(out, indent);
            out.append("while ");
            format(n.test, out, indent);
            out.append(":\n");

            formatStatements(n.body, out, indent + 1);
            return;
        }

        if (node instanceof For) {
            For n = (For) node;

            indent(out, indent);
            out.append("for ");
            format(n.target, out, indent);
            out.append(" in ");
            format(n.iter, out, indent);
            out.append(":\n");

            formatStatements(n.body, out, indent + 1);
            return;
        }

        if (node instanceof org.python.parser.ast.Module) {
            org.python.parser.ast.Module n = (org.python.parser.ast.Module) node;
            for ( stmtType n1 : n.body ) {
                while ( n1.beginLine>out.getLineCount() ) {
                    out.append("\n");
                }
                format( n1, out, indent);
                out.append("\n");
            }
            return;
        }
        
        if (node instanceof Compare) {
            Compare n = (Compare) node;

            format(n.left, out, indent);

            for (int i = 0; i < n.ops.length; i++) {
                out.append(" ");
                out.append(compareOperator(n.ops[i]));
                out.append(" ");
                format(n.comparators[i], out, indent);
            }
            return;
        }
        
        if (node instanceof Import) {
            Import n = (Import) node;

            indent(out, indent);
            out.append("import ");

            for (int i = 0; i < n.names.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }

                aliasType a = n.names[i];
                out.append(a.name);

                if (a.asname != null) {
                    out.append(" as ");
                    out.append(a.asname);
                }
            }
            return;
        }

        if (node instanceof ImportFrom) {
            ImportFrom n = (ImportFrom) node;

            indent(out, indent);
            out.append("from ");
            out.append(n.module);
            out.append(" import ");

            for (int i = 0; i < n.names.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }

                aliasType a = n.names[i];
                out.append(a.name);

                if (a.asname != null) {
                    out.append(" as ");
                    out.append(a.asname);
                }
            }
            return;
        }
        
        if (node instanceof Dict) {
            Dict n = (Dict) node;

            out.append("{");

            for (int i = 0; i < n.keys.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }

                format(n.keys[i], out, indent);
                out.append(": ");
                format(n.values[i], out, indent);
            }

            out.append("}");
            return;
        }
        
        if (node instanceof Subscript) {
           Subscript n = (Subscript) node;

            format(n.value, out, indent);
            out.append("[");
            format(n.slice, out, indent);
            out.append("]");
            return;
        }
        
        if (node instanceof Index) {
            Index n = (Index) node;
            format(n.value, out, indent);
            return;
        }
        
        if (node instanceof ListComp) {
            ListComp n = (ListComp) node;

            out.append("[");
            format(n.elt, out, indent);

            for (int i = 0; i < n.generators.length; i++) {
                org.python.parser.ast.listcompType c= n.generators[i];

                out.append(" for ");
                format(c.target, out, indent);

                out.append(" in ");
                format(c.iter, out, indent);

                for (int j = 0; j < c.ifs.length; j++) {
                    out.append(" if ");
                    format(c.ifs[j], out, indent);
                }
            }

            out.append("]");
            return;
        }
        
        if (node instanceof BoolOp) {
            BoolOp n = (BoolOp) node;

            String op = boolOperator(n.op);

            out.append("(");

            for (int i = 0; i < n.values.length; i++) {
                if (i > 0) {
                    out.append(" ");
                    out.append(op);
                    out.append(" ");
                }

                format(n.values[i], out, indent);
            }

            out.append(")");
            return;
        }
        
        if (node instanceof Slice) {
            Slice n = (Slice) node;

            if (n.lower != null) {
                format(n.lower, out, indent);
            }

            out.append(":");

            if (n.upper != null) {
                format(n.upper, out, indent);
            }

            if (n.step != null) {
                out.append(":");
                format(n.step, out, indent);
            }

            return;
        }
        
        if (node instanceof Print) {
            Print n = (Print) node;

            indent(out, indent);
            out.append("print");

            if (n.dest != null) {
                out.append(" >>");
                format(n.dest, out, indent);

                if (n.values.length > 0) {
                    out.append(", ");
                }
            } else if (n.values.length > 0) {
                out.append(" ");
            }

            for (int i = 0; i < n.values.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }
                format(n.values[i], out, indent);
            }

            // nl == false means the Python statement ended with a comma.
            if (!n.nl) {
                out.append(",");
            }

            return;
        }
        
        if (node instanceof FunctionDef) {
            FunctionDef n = (FunctionDef) node;

            indent(out, indent);
            out.append("def ");
            out.append(n.name);
            out.append("(");

            formatArguments(n.args, out, indent);

            out.append("):\n");

            formatStatements(n.body, out, indent + 1);
            return;
        }

        if (node instanceof Raise) {
            Raise n = (Raise) node;

            indent(out, indent);
            out.append("raise");

            if (n.type != null) {
                out.append(" ");
                format(n.type, out, indent);

                if (n.inst != null) {
                    out.append(", ");
                    format(n.inst, out, indent);

                    if (n.tback != null) {
                        out.append(", ");
                        format(n.tback, out, indent);
                    }
                }
            }

            return;
        }
        
        /*
         * Unknown node -- make this conspicuous.
         */

        out.append("<UNSUPPORTED:");
        out.append(node.getClass().getSimpleName());
        out.append(">");
    }


    private static void formatStatements(
            stmtType[] statements,
            LineCountingStringBuilder out,
            int indent) {

        if (statements == null) {
            return;
        }

        for (int i = 0; i < statements.length; i++) {
            if (i > 0) {
                out.append("\n");
            }
            while ( statements[i].beginLine>out.getLineCount() ) {
                out.append("\n");
            }            
            format(statements[i], out, indent);
        }
    }


    private static void indent(LineCountingStringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append(INDENT);
        }
    }


    private static void appendString(LineCountingStringBuilder out, String s) {
        out.append("'");

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\'':
                    out.append("\\'");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }

        out.append("'");
    }

    private static String binaryOperator(int op) {
        if (op == operatorType.Add)      return "+";
        if (op == operatorType.Sub)      return "-";
        if (op == operatorType.Mult)     return "*";
        if (op == operatorType.Div)      return "/";
        if (op == operatorType.FloorDiv) return "//";
        if (op == operatorType.Mod)      return "%";
        if (op == operatorType.Pow)      return "**";
        if (op == operatorType.LShift)   return "<<";
        if (op == operatorType.RShift)   return ">>";
        if (op == operatorType.BitOr)    return "|";
        if (op == operatorType.BitXor)   return "^";
        if (op == operatorType.BitAnd)   return "&";

        return "???";
    }

    private static String unaryOperator(int op) {
        if (op == unaryopType.Invert) return "~";
        if (op == unaryopType.Not)    return "not ";
        if (op == unaryopType.UAdd)   return "+";
        if (op == unaryopType.USub)   return "-";

        return "???";
    }
    
    private static String compareOperator(int op) {
        switch (op) {
            case cmpopType.Eq:
                return "==";
            case cmpopType.NotEq:
                return "!=";
            case cmpopType.Lt:
                return "<";
            case cmpopType.LtE:
                return "<=";
            case cmpopType.Gt:
                return ">";
            case cmpopType.GtE:
                return ">=";
            case cmpopType.Is:
                return "is";
            case cmpopType.IsNot:
                return "is not";
            case cmpopType.In:
                return "in";
            case cmpopType.NotIn:
                return "not in";
            default:
                return "<cmpop:" + op + ">";
        }
    }
    
    private static String boolOperator(int op) {
        switch (op) {
            case boolopType.And:
                return "and";
            case boolopType.Or:
                return "or";
            default:
                return "<boolop:" + op + ">";
        }
    }
    
    private static void formatArguments(
            argumentsType args,
            LineCountingStringBuilder out,
            int indent) {

        int nargs = args.args.length;
        int ndefaults = args.defaults.length;
        int firstDefault = nargs - ndefaults;

        boolean needComma = false;

        for (int i = 0; i < nargs; i++) {
            if (needComma) {
                out.append(", ");
            }

            format(args.args[i], out, indent);

            if (i >= firstDefault) {
                out.append("=");
                format(args.defaults[i - firstDefault], out, indent);
            }

            needComma = true;
        }

        if (args.vararg != null) {
            if (needComma) {
                out.append(", ");
            }

            out.append("*");
            out.append(args.vararg);
            needComma = true;
        }

        if (args.kwarg != null) {
            if (needComma) {
                out.append(", ");
            }

            out.append("**");
            out.append(args.kwarg);
        }
    }    
    
}