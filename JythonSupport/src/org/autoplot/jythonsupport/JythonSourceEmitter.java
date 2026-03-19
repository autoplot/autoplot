package org.autoplot.jythonsupport;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import static org.autoplot.jythonsupport.JythonToJavaConverter.TYPE_MAP;
import static org.autoplot.jythonsupport.JythonToJavaConverter.TYPE_STRING;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Index;
import org.python.parser.ast.Name;
import org.python.parser.ast.Slice;
import org.python.parser.ast.Str;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.exprType;
import org.python.parser.ast.sliceType;

/**
 * Best-effort source emitter for Jython 2.2 org.python.parser.ast trees.
 *
 * This class is intentionally self-contained and reflection-based so that it can tolerate minor differences between historical
 * Jython 2.2 builds.
 *
 * Usage:
 *
 * Object mod = parser.parse(...); // returns org.python.parser.ast.modType String code = new Jython22Emitter().toSource(mod);
 *
 * Notes: - This emits structurally-correct Python/Jython source, not original formatting. - Comments are not preserved. - Some rare
 * node forms may need small adjustments for your exact jar.
 */
public class JythonSourceEmitter {

    private final StringBuilder out = new StringBuilder();
    private int indent = 0;
    private boolean startOfLine = true;

    public String toSource(Object node) {
        out.setLength(0);
        indent = 0;
        startOfLine = true;
        emit(node, 0);
        return out.toString();
    }

    // ----------------------------------------------------------------------
    // Core writing helpers
    // ----------------------------------------------------------------------
    private void write(String s) {
        if (s == null || s.length() == 0) {
            return;
        }
        if (startOfLine) {
            for (int i = 0; i < indent; i++) {
                out.append("    ");
            }
            startOfLine = false;
        }
        out.append(s);
    }

    private void newline() {
        out.append('\n');
        startOfLine = true;
    }

    private void ensureNewline() {
        if (!startOfLine) {
            newline();
        }
    }

    private void emitSuite(Object body) {
        indent++;
        newline();
        if (body == null) {
            write("pass");
        } else {
            emit(body, 0);
        }
        indent--;
    }

    // ----------------------------------------------------------------------
    // Reflection helpers
    // ----------------------------------------------------------------------
    private static String simpleName(Object node) {
        return node == null ? "null" : node.getClass().getSimpleName();
    }

    private static Object field(Object obj, String name) {
        if (obj == null) {
            return null;
        }
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException("Unable to read field " + name + " from " + obj.getClass(), e);
            }
        }
        return null;
    }

    private static String sfield(Object obj, String name) {
        Object v = field(obj, name);
        return v == null ? null : String.valueOf(v);
    }

    private static int ifield(Object obj, String name, int dflt) {
        Object v = field(obj, name);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return dflt;
    }

    private static List<Object> listField(Object obj, String name) {
        Object v = field(obj, name);
        return asList(v);
    }

    private static List<Object> asList(Object v) {
        List<Object> r = new ArrayList<Object>();
        if (v == null) {
            return r;
        }

        if (v instanceof java.util.List) {
            r.addAll((java.util.List<?>) v);
            return r;
        }

        Class<?> c = v.getClass();
        if (c.isArray()) {
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) {
                r.add(Array.get(v, i));
            }
            return r;
        }

        r.add(v);
        return r;
    }

    private static boolean boolField(Object obj, String name, boolean dflt) {
        Object v = field(obj, name);
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        return dflt;
    }

    // ----------------------------------------------------------------------
    // Precedence
    // ----------------------------------------------------------------------
    private static final int PREC_TUPLE = 1;
    private static final int PREC_TEST = 2;
    private static final int PREC_OR = 3;
    private static final int PREC_AND = 4;
    private static final int PREC_NOT = 5;
    private static final int PREC_CMP = 6;
    private static final int PREC_BITOR = 7;
    private static final int PREC_BITXOR = 8;
    private static final int PREC_BITAND = 9;
    private static final int PREC_SHIFT = 10;
    private static final int PREC_ADD = 11;
    private static final int PREC_MUL = 12;
    private static final int PREC_UNARY = 13;
    private static final int PREC_POWER = 14;
    private static final int PREC_ATOM = 15;

    private void emitExpr(Object node, int parentPrec) {
        emit(node, parentPrec);
    }

    private void maybeParen(int myPrec, int parentPrec, Runnable body) {
        boolean paren = myPrec < parentPrec;
        if (paren) {
            write("(");
        }
        body.run();
        if (paren) {
            write(")");
        }
    }

    private void emitArray(Object arr) {
        int n = java.lang.reflect.Array.getLength(arr);
        if (n == 0) {
            write("pass");
            return;
        }

        Class<?> component = arr.getClass().getComponentType();
        boolean looksLikeStmtArray
                = component != null
                && component.getName().endsWith(".stmtType");

        for (int i = 0; i < n; i++) {
            Object item = java.lang.reflect.Array.get(arr, i);

            if (i > 0) {
                newline();
            }

            emit(item, looksLikeStmtArray ? 0 : PREC_TUPLE);
        }
    }

    // ----------------------------------------------------------------------
    // Generic dispatch
    // ----------------------------------------------------------------------
    private void emit(Object node, int parentPrec) {
        if (node == null) {
            write("None");
            return;
        }

        // Handle raw Java arrays such as stmtType[]
        Class<?> c = node.getClass();
        if (c.isArray()) {
            emitArray(node);
            return;
        }

        String n = simpleName(node);

        // Module-ish
        if ("Module".equals(n) || "Interactive".equals(n) || "Expression".equals(n)) {
            emitModuleish(node);
            return;
        }

        // Statement container
        if ("Stmt".equals(n) || "Suite".equals(n)) {
            emitStmtList(listField(node, "nodes"));
            return;
        }

        // Statements
        if ("FunctionDef".equals(n) || "Function".equals(n)) {
            emitFunction(node);
            return;
        }
        if ("ClassDef".equals(n) || "Class".equals(n)) {
            emitClass(node);
            return;
        }
        if ("Assign".equals(n)) {
            emitAssign(node);
            return;
        }
        if ("AugAssign".equals(n)) {
            emitAugAssign(node);
            return;
        }
        if ("Subscript".equals(n) ) {
            emitSubscript(node, parentPrec);
            return;
        }
        if ("AssName".equals(n) || "AssAttr".equals(n) || "Slice".equals(n)) {
            emitAssignmentTarget(node, parentPrec);
            return;
        }
        if ("Return".equals(n)) {
            emitReturn(node);
            return;
        }
        if ("Yield".equals(n)) {
            emitYield(node, parentPrec);
            return;
        }
        if ("Print".equals(n)) {
            emitPrint(node);
            return;
        }
        if ("Printnl".equals(n)) {
            emitPrintnl(node);
            return;
        }
        if ("Discard".equals(n) || "Expr".equals(n)) {
            emitExprStmt(node);
            return;
        }
        if ("Pass".equals(n)) {
            write("pass");
            return;
        }
        if ("Break".equals(n)) {
            write("break");
            return;
        }
        if ("Continue".equals(n)) {
            write("continue");
            return;
        }
        if ("If".equals(n)) {
            emitIf(node);
            return;
        }
        if ("While".equals(n)) {
            emitWhile(node);
            return;
        }
        if ("For".equals(n)) {
            emitFor(node);
            return;
        }
        if ("TryExcept".equals(n)) {
            emitTryExcept(node);
            return;
        }
        if ("TryFinally".equals(n)) {
            emitTryFinally(node);
            return;
        }
        if ("Raise".equals(n)) {
            emitRaise(node);
            return;
        }
        if ("Import".equals(n)) {
            emitImport(node);
            return;
        }
        if ("ImportFrom".equals(n) || "From".equals(n)) {
            emitFromImport(node);
            return;
        }
        if ("Global".equals(n)) {
            emitGlobal(node);
            return;
        }
        if ("Exec".equals(n)) {
            emitExec(node);
            return;
        }
        if ("Assert".equals(n)) {
            emitAssert(node);
            return;
        }
        if ("Del".equals(n)) {
            emitDel(node);
            return;
        }

        // Expressions
        if ("Name".equals(n)) {
            write(nameText(node));
            return;
        }
        if ("Const".equals(n)) {
            emitConst(node);
            return;
        }
        if ("Tuple".equals(n)) {
            emitTuple(node, parentPrec);
            return;
        }
        if ("List".equals(n)) {
            emitList(node);
            return;
        }
        if ("Dict".equals(n)) {
            emitDict(node);
            return;
        }
        if ("Backquote".equals(n)) {
            emitBackquote(node, parentPrec);
            return;
        }
        if ("CallFunc".equals(n) || "Call".equals(n)) {
            emitCall(node, parentPrec);
            return;
        }
        if ("Getattr".equals(n) || "Attribute".equals(n)) {
            emitGetattr(node, parentPrec);
            return;
        }
        if ("Lambda".equals(n)) {
            emitLambda(node, parentPrec);
            return;
        }
        if ("Compare".equals(n)) {
            emitCompare(node, parentPrec);
            return;
        }
        if ("And".equals(n)) {
            emitNAry(node, " and ", PREC_AND, parentPrec);
            return;
        }
        if ("Or".equals(n)) {
            emitNAry(node, " or ", PREC_OR, parentPrec);
            return;
        }
        if ("Not".equals(n)) {
            emitUnaryKeyword(node, "not ", PREC_NOT, parentPrec);
            return;
        }

        if ("Add".equals(n)) {
            emitBinary(node, " + ", PREC_ADD, parentPrec);
            return;
        }
        if ("Sub".equals(n)) {
            emitBinary(node, " - ", PREC_ADD, parentPrec);
            return;
        }
        if ("Mul".equals(n)) {
            emitBinary(node, " * ", PREC_MUL, parentPrec);
            return;
        }
        if ("Div".equals(n)) {
            emitBinary(node, " / ", PREC_MUL, parentPrec);
            return;
        }
        if ("FloorDiv".equals(n)) {
            emitBinary(node, " // ", PREC_MUL, parentPrec);
            return;
        }
        if ("Mod".equals(n)) {
            emitBinary(node, " % ", PREC_MUL, parentPrec);
            return;
        }
        if ("Power".equals(n)) {
            emitBinaryRight(node, " ** ", PREC_POWER, parentPrec);
            return;
        }
        if ("LeftShift".equals(n)) {
            emitBinary(node, " << ", PREC_SHIFT, parentPrec);
            return;
        }
        if ("RightShift".equals(n)) {
            emitBinary(node, " >> ", PREC_SHIFT, parentPrec);
            return;
        }
        if ("Bitand".equals(n)) {
            emitNAry(node, " & ", PREC_BITAND, parentPrec);
            return;
        }
        if ("Bitor".equals(n)) {
            emitNAry(node, " | ", PREC_BITOR, parentPrec);
            return;
        }
        if ("Bitxor".equals(n)) {
            emitNAry(node, " ^ ", PREC_BITXOR, parentPrec);
            return;
        }
        if ("UnaryAdd".equals(n)) {
            emitUnaryPrefix(node, "+", PREC_UNARY, parentPrec);
            return;
        }
        if ("UnarySub".equals(n)) {
            emitUnaryPrefix(node, "-", PREC_UNARY, parentPrec);
            return;
        }
        if ("Invert".equals(n)) {
            emitUnaryPrefix(node, "~", PREC_UNARY, parentPrec);
            return;
        }

        if ("Subscript".equals(n)) {
            emitSubscript(node, parentPrec);
            return;
        }
        if ("Slice".equals(n)) {
            emitSlice(node, parentPrec);
            return;
        }
        if ("Sliceobj".equals(n)) {
            emitSliceobj(node, parentPrec);
            return;
        }

        if ("ListComp".equals(n)) {
            emitListComp(node);
            return;
        }
        if ("ListCompFor".equals(n) || "ListCompIf".equals(n) || "GenExpr".equals(n)
                || "GenExprFor".equals(n) || "GenExprIf".equals(n) || "Keyword".equals(n)
                || "Ellipsis".equals(n)) {
            emitFallback(node);
            return;
        }
        if ("Str".equals(n)) {
            emitStr(node);
            return;
        }
        if ("Int".equals(n)) {
            emitInt(node);
            return;
        }
        if ("Num".equals(n)) {
            emitNum(node);
            return;
        }
        if ("BinOp".equals(n)) {
            emitBinOp(node, parentPrec);
            return;
        }
        emitFallback(node);
    }

    // ----------------------------------------------------------------------
    // Module / statement helpers
    // ----------------------------------------------------------------------
    private void emitModuleish(Object node) {
        Object body = field(node, "body");
        if (body == null) {
            body = field(node, "node");
        }
        if (body == null) {
            body = field(node, "code");
        }
        emit(body, 0);
        ensureNewline();
    }

    private void emitStmtList(List<Object> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            write("pass");
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                newline();
            }
            emit(nodes.get(i), 0);
        }
    }

    private void emitExprStmt(Object node) {
        Object value = field(node, "expr");
        if (value == null) {
            value = field(node, "value");
        }
        emitExpr(value, 0);
    }

    private void emitFunction(Object node) {
        String name = sfield(node, "name");
        if (name == null) {
            name = "anonymous";
        }

        List<Object> argnames = listField(node, "argnames");
        Object defaults = field(node, "defaults");
        Object code = field(node, "code");
        Object decorators = field(node, "decorators");
        int flags = ifield(node, "flags", 0);

        List<Object> defs = asList(defaults);

        if (decorators != null) {
            List<Object> decs = asList(decorators);
            for (int i = 0; i < decs.size(); i++) {
                write("@");
                emitExpr(decs.get(i), PREC_ATOM);
                newline();
            }
        }

        write("def ");
        write(name);
        write("(");
        emitArguments(argnames, defs, flags);
        write("):");
        emitSuite(code);
    }

    private void emitClass(Object node) {
        String name = sfield(node, "name");
        List<Object> bases = listField(node, "bases");
        Object code = field(node, "code");

        write("class ");
        write(name == null ? "Anonymous" : name);
        if (bases != null && !bases.isEmpty()) {
            write("(");
            for (int i = 0; i < bases.size(); i++) {
                if (i > 0) {
                    write(", ");
                }
                emitExpr(bases.get(i), PREC_TUPLE);
            }
            write(")");
        }
        write(":");
        emitSuite(code);
    }

    private void emitArguments(List<Object> argnames, List<Object> defaults, int flags) {
        if (argnames == null) {
            argnames = new ArrayList<Object>();
        }
        if (defaults == null) {
            defaults = new ArrayList<Object>();
        }

        int n = argnames.size();
        int d = defaults.size();
        int firstDefault = n - d;

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                write(", ");
            }
            emitArgName(argnames.get(i));

            if (i >= firstDefault && i - firstDefault < d) {
                write("=");
                emitExpr(defaults.get(i - firstDefault), PREC_TUPLE);
            }
        }

        // Historical Jython flags may encode *args/**kwargs.
        // If your build exposes names separately, adjust here.
        String vararg = sfield(field(nodeOrNull(argnames), "__owner__"), "vararg");
        String kwarg = sfield(field(nodeOrNull(argnames), "__owner__"), "kwarg");
        if (vararg != null && vararg.length() > 0) {
            if (n > 0) {
                write(", ");
            }
            write("*" + vararg);
            if (kwarg != null && kwarg.length() > 0) {
                write(", **" + kwarg);
            }
        } else if (kwarg != null && kwarg.length() > 0) {
            if (n > 0) {
                write(", ");
            }
            write("**" + kwarg);
        }

        // If your exact Jython 2.2 build stores star args differently,
        // edit this method only.
        if ((flags & 0x04) != 0 || (flags & 0x08) != 0) {
            // Unknown exact names in this historical build.
            // Left intentionally silent to avoid emitting incorrect names.
        }
    }

    private Object nodeOrNull(List<Object> list) {
        return null;
    }

    private void emitArgName(Object arg) {
        if (arg == null) {
            write("arg");
            return;
        }
        if (arg instanceof String) {
            write((String) arg);
            return;
        }
        String n = simpleName(arg);
        if ("AssName".equals(n) || "Name".equals(n)) {
            write(nameText(arg));
            return;
        }
        if ("Tuple".equals(n)) {
            write("(");
            List<Object> nodes = listField(arg, "nodes");
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) {
                    write(", ");
                }
                emitArgName(nodes.get(i));
            }
            write(")");
            return;
        }
        write(String.valueOf(arg));
    }

    private void emitExprTypeArray( exprType[] e ) {
        if ( e.length==0 ) {
            return;
        }
        emitExpr(e[0],PREC_TUPLE);
        
    }
    private void emitAssign(Object node) {
        Assign a= (Assign)node;
        
        emitExprTypeArray(a.targets);
        write(" = ");
        
        emitExpr(a.value,PREC_TUPLE);
    }

    private void emitAugAssign(Object node) {
        Object left = field(node, "node");
        if (left == null) {
            left = field(node, "target");
        }
        String op = sfield(node, "op");
        Object right = field(node, "expr");
        if (right == null) {
            right = field(node, "value");
        }

        emitAssignmentTarget(left, PREC_TUPLE);
        write(" " + (op == null ? "+=" : op + "=") + " ");
        emitExpr(right, PREC_TUPLE);
    }

    private void emitReturn(Object node) {
        write("return");
        Object value = field(node, "value");
        if (value != null) {
            write(" ");
            emitExpr(value, PREC_TUPLE);
        }
    }

    private void emitYield(Object node, int parentPrec) {
        maybeParen(PREC_TEST, parentPrec, new Runnable() {
            public void run() {
                write("yield");
                Object value = field(node, "value");
                if (value != null) {
                    write(" ");
                    emitExpr(value, PREC_TUPLE);
                }
            }
        });
    }

    private void emitPrint(Object node) {
        write("print ");
        Object dest = field(node, "dest");
        if (dest != null) {
            write(">> ");
            emitExpr(dest, PREC_TUPLE);
            List<Object> values = listField(node, "nodes");
            if (!values.isEmpty()) {
                write(", ");
            }
            emitCommaList(values);
            return;
        }
        emitCommaList(listField(node, "nodes"));
        if (!boolField(node, "nl", false)) {
            write(",");
        }
    }

    private void emitPrintnl(Object node) {
        write("print");
        Object dest = field(node, "dest");
        List<Object> values = listField(node, "nodes");
        if (dest != null || !values.isEmpty()) {
            write(" ");
        }
        if (dest != null) {
            write(">> ");
            emitExpr(dest, PREC_TUPLE);
            if (!values.isEmpty()) {
                write(", ");
            }
        }
        emitCommaList(values);
    }

    private void emitIf(Object node) {
        List<Object> tests = listField(node, "tests");
        Object else_ = field(node, "else_");
        if (else_ == null) {
            else_ = field(node, "orelse");
        }

        for (int i = 0; i < tests.size(); i++) {
            Object pair = tests.get(i);
            List<Object> items = asList(pair);
            Object test = items.size() > 0 ? items.get(0) : field(pair, "test");
            Object body = items.size() > 1 ? items.get(1) : field(pair, "body");

            if (i == 0) {
                write("if ");
            } else {
                write("elif ");
            }

            emitExpr(test, PREC_TUPLE);
            write(":");
            emitSuite(body);

            if (i + 1 < tests.size()) {
                newline();
            }
        }

        if (else_ != null) {
            newline();
            write("else:");
            emitSuite(else_);
        }
    }

    private void emitWhile(Object node) {
        write("while ");
        emitExpr(field(node, "test"), PREC_TUPLE);
        write(":");
        emitSuite(field(node, "body"));

        Object else_ = field(node, "else_");
        if (else_ == null) {
            else_ = field(node, "orelse");
        }
        if (else_ != null) {
            newline();
            write("else:");
            emitSuite(else_);
        }
    }

    private void emitFor(Object node) {
        write("for ");
        emitAssignmentTarget(field(node, "assign"), PREC_TUPLE);
        write(" in ");
        emitExpr(field(node, "list"), PREC_TUPLE);
        write(":");
        emitSuite(field(node, "body"));

        Object else_ = field(node, "else_");
        if (else_ == null) {
            else_ = field(node, "orelse");
        }
        if (else_ != null) {
            newline();
            write("else:");
            emitSuite(else_);
        }
    }

    private void emitTryExcept(Object node) {
        write("try:");
        emitSuite(field(node, "body"));

        List<Object> handlers = listField(node, "handlers");
        for (int i = 0; i < handlers.size(); i++) {
            newline();
            Object h = handlers.get(i);
            Object type = field(h, "type");
            Object name = field(h, "name");
            Object body = field(h, "body");

            write("except");
            if (type != null) {
                write(" ");
                emitExpr(type, PREC_TUPLE);
                if (name != null) {
                    write(", ");
                    emitAssignmentTarget(name, PREC_TUPLE);
                }
            }
            write(":");
            emitSuite(body);
        }

        Object else_ = field(node, "else_");
        if (else_ == null) {
            else_ = field(node, "orelse");
        }
        if (else_ != null) {
            newline();
            write("else:");
            emitSuite(else_);
        }
    }

    private void emitTryFinally(Object node) {
        write("try:");
        emitSuite(field(node, "body"));
        newline();
        write("finally:");
        emitSuite(field(node, "finalbody"));
    }

    private void emitRaise(Object node) {
        write("raise");
        Object e1 = field(node, "expr1");
        Object e2 = field(node, "expr2");
        Object e3 = field(node, "expr3");

        if (e1 != null) {
            write(" ");
            emitExpr(e1, PREC_TUPLE);
            if (e2 != null) {
                write(", ");
                emitExpr(e2, PREC_TUPLE);
                if (e3 != null) {
                    write(", ");
                    emitExpr(e3, PREC_TUPLE);
                }
            }
        }
    }

    private void emitImport(Object node) {
        write("import ");
        emitNameAliasList(listField(node, "names"));
    }

    private void emitFromImport(Object node) {
        String modname = sfield(node, "modname");
        if (modname == null) {
            modname = sfield(node, "module");
        }
        write("from ");
        write(modname == null ? "?" : modname);
        write(" import ");
        emitNameAliasList(listField(node, "names"));
    }

    private void emitGlobal(Object node) {
        write("global ");
        List<Object> names = listField(node, "names");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                write(", ");
            }
            write(String.valueOf(names.get(i)));
        }
    }

    private void emitExec(Object node) {
        write("exec ");
        emitExpr(field(node, "expr"), PREC_TUPLE);
        Object locals = field(node, "locals");
        Object globals = field(node, "globals");
        if (globals != null) {
            write(" in ");
            emitExpr(globals, PREC_TUPLE);
            if (locals != null) {
                write(", ");
                emitExpr(locals, PREC_TUPLE);
            }
        }
    }

    private void emitAssert(Object node) {
        write("assert ");
        emitExpr(field(node, "test"), PREC_TUPLE);
        Object fail = field(node, "fail");
        if (fail != null) {
            write(", ");
            emitExpr(fail, PREC_TUPLE);
        }
    }

    private void emitDel(Object node) {
        write("del ");
        emitExpr(field(node, "expr"), PREC_TUPLE);
    }

    // ----------------------------------------------------------------------
    // Expression helpers
    // ----------------------------------------------------------------------
    private String nameText(Object node) {
        String id = sfield(node, "id");
        if (id == null) {
            id = sfield(node, "name");
        }
        if (id == null) {
            id = sfield(node, "attrname");
        }
        return id == null ? "?" : id;
    }

    private void emitConst(Object node) {
        Object value = field(node, "value");
        if (value == null) {
            write("None");
        } else if (value instanceof String) {
            write(quote((String) value));
        } else if (value instanceof Character) {
            write(quote(String.valueOf(value)));
        } else {
            write(String.valueOf(value));
        }
    }

    private String quote(String s) {
        StringBuilder b = new StringBuilder();
        b.append('\'');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '\'') {
                b.append('\\').append(ch);
            } else if (ch == '\n') {
                b.append("\\n");
            } else if (ch == '\r') {
                b.append("\\r");
            } else if (ch == '\t') {
                b.append("\\t");
            } else {
                b.append(ch);
            }
        }
        b.append('\'');
        return b.toString();
    }

    private void emitTuple(Object node, int parentPrec) {
        final List<Object> nodes = listField(node, "nodes");
        maybeParen(PREC_TUPLE, parentPrec, new Runnable() {
            public void run() {
                if (nodes.size() == 1) {
                    emitExpr(nodes.get(0), PREC_TUPLE);
                    write(",");
                } else {
                    for (int i = 0; i < nodes.size(); i++) {
                        if (i > 0) {
                            write(", ");
                        }
                        emitExpr(nodes.get(i), PREC_TUPLE);
                    }
                }
            }
        });
    }

    private void emitList(Object node) {
        List<Object> nodes = listField(node, "nodes");
        write("[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                write(", ");
            }
            emitExpr(nodes.get(i), PREC_TUPLE);
        }
        write("]");
    }

    private void emitDict(Object node) {
        List<Object> items = listField(node, "items");
        write("{");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                write(", ");
            }
            Object pair = items.get(i);
            List<Object> p = asList(pair);
            Object k = p.size() > 0 ? p.get(0) : field(pair, "key");
            Object v = p.size() > 1 ? p.get(1) : field(pair, "value");
            emitExpr(k, PREC_TUPLE);
            write(": ");
            emitExpr(v, PREC_TUPLE);
        }
        write("}");
    }

    private void emitBackquote(final Object node, int parentPrec) {
        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                write("`");
                emitExpr(field(node, "expr"), PREC_TUPLE);
                write("`");
            }
        });
    }

    private void emitCall(final Object node, int parentPrec) {
        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                Object fn = field(node, "node");
                if (fn == null) {
                    fn = field(node, "func");
                }
                emitExpr(fn, PREC_ATOM);
                write("(");

                boolean first = true;

                List<Object> args = listField(node, "args");
                for (int i = 0; i < args.size(); i++) {
                    if (!first) {
                        write(", ");
                    }
                    emitExpr(args.get(i), PREC_TUPLE);
                    first = false;
                }

                Object star = field(node, "star_args");
                if (star == null) {
                    star = field(node, "starargs");
                }
                if (star != null) {
                    if (!first) {
                        write(", ");
                    }
                    write("*");
                    emitExpr(star, PREC_TUPLE);
                    first = false;
                }

                Object dstar = field(node, "dstar_args");
                if (dstar == null) {
                    dstar = field(node, "kwargs");
                }
                if (dstar != null) {
                    if (!first) {
                        write(", ");
                    }
                    write("**");
                    emitExpr(dstar, PREC_TUPLE);
                }

                write(")");
            }
        });
    }

    private void emitGetattr(final Object node, int parentPrec) {
        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                Object base = field(node, "expr");
                if (base == null) {
                    base = field(node, "value");
                }
                emitExpr(base, PREC_ATOM);
                write(".");
                write(nameText(node));
            }
        });
    }

    private void emitLambda(final Object node, int parentPrec) {
        maybeParen(PREC_TEST, parentPrec, new Runnable() {
            public void run() {
                write("lambda ");
                List<Object> argnames = listField(node, "argnames");
                List<Object> defaults = asList(field(node, "defaults"));
                int flags = ifield(node, "flags", 0);
                emitArguments(argnames, defaults, flags);
                write(": ");
                emitExpr(field(node, "code"), PREC_TUPLE);
            }
        });
    }

    private void emitCompare(final Object node, int parentPrec) {
        maybeParen(PREC_CMP, parentPrec, new Runnable() {
            public void run() {
                emitExpr(field(node, "expr"), PREC_CMP);

                List<Object> ops = listField(node, "ops");
                for (int i = 0; i < ops.size(); i++) {
                    Object opPair = ops.get(i);
                    List<Object> pair = asList(opPair);
                    Object op = pair.size() > 0 ? pair.get(0) : field(opPair, "op");
                    Object rhs = pair.size() > 1 ? pair.get(1) : field(opPair, "expr");

                    write(" ");
                    write(String.valueOf(op));
                    write(" ");
                    emitExpr(rhs, PREC_CMP);
                }
            }
        });
    }

    private void emitBinary(final Object node, final String op, final int myPrec, int parentPrec) {
        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                emitExpr(field(node, "left"), myPrec);
                write(op);
                emitExpr(field(node, "right"), myPrec + 1);
            }
        });
    }

    private void emitBinaryRight(final Object node, final String op, final int myPrec, int parentPrec) {
        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                emitExpr(field(node, "left"), myPrec);
                write(op);
                emitExpr(field(node, "right"), myPrec);
            }
        });
    }

    private void emitNAry(final Object node, final String op, final int myPrec, int parentPrec) {
        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                List<Object> nodes = listField(node, "nodes");
                for (int i = 0; i < nodes.size(); i++) {
                    if (i > 0) {
                        write(op);
                    }
                    emitExpr(nodes.get(i), myPrec);
                }
            }
        });
    }

    private void emitUnaryPrefix(final Object node, final String op, final int myPrec, int parentPrec) {
        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                write(op);
                Object expr = field(node, "expr");
                if (expr == null) {
                    expr = field(node, "operand");
                }
                emitExpr(expr, myPrec);
            }
        });
    }

    private void emitUnaryKeyword(final Object node, final String op, final int myPrec, int parentPrec) {
        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                write(op);
                Object expr = field(node, "expr");
                if (expr == null) {
                    expr = field(node, "operand");
                }
                emitExpr(expr, myPrec);
            }
        });
    }

    private void emitSubscript(final Object node, int parentPrec) {
        final Object base = field(node, "expr");
        final List<Object> subs = listField(node, "subs");
        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                emitExpr(base, PREC_ATOM);
                write("[");
                for (int i = 0; i < subs.size(); i++) {
                    if (i > 0) {
                        write(", ");
                    }
                    emitExpr(subs.get(i), PREC_TUPLE);
                }
                write("]");
            }
        });
    }

    private void emitSlice(final Object node, int parentPrec) {
        final Object base = field(node, "expr");
        final Object lower = field(node, "lower");
        final Object upper = field(node, "upper");

        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                if (base != null) {
                    emitExpr(base, PREC_ATOM);
                    write("[");
                }
                if (lower != null) {
                    emitExpr(lower, PREC_TUPLE);
                }
                write(":");
                if (upper != null) {
                    emitExpr(upper, PREC_TUPLE);
                }
                if (base != null) {
                    write("]");
                }
            }
        });
    }

    private void emitSliceobj(Object node, int parentPrec) {
        List<Object> nodes = listField(node, "nodes");
        maybeParen(PREC_ATOM, parentPrec, new Runnable() {
            public void run() {
                for (int i = 0; i < nodes.size(); i++) {
                    if (i > 0) {
                        write(":");
                    }
                    if (nodes.get(i) != null) {
                        emitExpr(nodes.get(i), PREC_TUPLE);
                    }
                }
            }
        });
    }

    private void emitListComp(Object node) {
        write("[");
        emitExpr(field(node, "expr"), PREC_TUPLE);
        List<Object> quals = listField(node, "quals");
        for (int i = 0; i < quals.size(); i++) {
            Object q = quals.get(i);
            String qn = simpleName(q);
            if ("ListCompFor".equals(qn)) {
                write(" for ");
                emitAssignmentTarget(field(q, "assign"), PREC_TUPLE);
                write(" in ");
                emitExpr(field(q, "list"), PREC_TUPLE);
                List<Object> ifs = listField(q, "ifs");
                for (int j = 0; j < ifs.size(); j++) {
                    write(" if ");
                    emitExpr(ifs.get(j), PREC_TUPLE);
                }
            } else if ("ListCompIf".equals(qn)) {
                write(" if ");
                emitExpr(field(q, "test"), PREC_TUPLE);
            } else {
                write(" /* ");
                write(qn);
                write(" */ ");
            }
        }
        write("]");
    }
//
//    private void emitSubscript( Object node, int parentPrec ) {
//        Subscript s= (Subscript)node;
//        sliceType st = s.slice;
//        if (st instanceof Slice) {
//            Slice slice = (Slice) st;
//            emit( st, parentPrec );
//        } else {
//            write("[");
//            emit( st, parentPrec );
//            write("]");
//        } 
//    }

    private void emitAssignmentTarget(Object node, int parentPrec) {
        emit(node, parentPrec);
    }

    private void emitCommaList(List<Object> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                write(", ");
            }
            emitExpr(nodes.get(i), PREC_TUPLE);
        }
    }

    private void emitNameAliasList(List<Object> names) {
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                write(", ");
            }
            Object n = names.get(i);

            if (n instanceof String) {
                write((String) n);
                continue;
            }

            List<Object> pair = asList(n);
            if (pair.size() == 2) {
                write(String.valueOf(pair.get(0)));
                Object alias = pair.get(1);
                if (alias != null && !"null".equals(String.valueOf(alias))) {
                    write(" as ");
                    write(String.valueOf(alias));
                }
                continue;
            }

            String name = sfield(n, "name");
            if (name == null) {
                name = sfield(n, "id");
            }
            String asname = sfield(n, "asname");

            if (name != null) {
                write(name);
                if (asname != null && asname.length() > 0) {
                    write(" as ");
                    write(asname);
                }
            } else {
                write(String.valueOf(n));
            }
        }
    }

    private void emitFallback(Object node) {
        String n = simpleName(node);
        write("/* UNHANDLED:" + n + " */");

        // Safer than throwing when traversing large trees.
        // Replace with a RuntimeException if you prefer fail-fast.
    }

    private void emitStr(Object node) {
        Object v = field(node, "s");
        if (v == null) {
            v = field(node, "value");
        }
        if (v == null) {
            write("''");
        } else {
            write(quote(String.valueOf(v)));
        }
    }

    private void emitInt(Object node) {
        Object v = field(node, "n");
        if (v == null) {
            v = field(node, "value");
        }
        if (v == null) {
            write("0");
        } else {
            write(String.valueOf(v));
        }
    }

    private void emitNum(Object node) {
        Object v = field(node, "n");
        if (v == null) {
            v = field(node, "value");
        }
        if (v == null) {
            write("0");
        } else {
            write(String.valueOf(v));
        }
    }

    private void emitBinOp(final Object node, int parentPrec) {
        final Object left = field(node, "left");
        final Object op = field(node, "op");
        final Object right = field(node, "right");

        final String opText = binOpText(op);
        final int myPrec = precedenceForBinOp(opText);

        maybeParen(myPrec, parentPrec, new Runnable() {
            public void run() {
                emitExpr(left, myPrec);
                write(" ");
                write(opText);
                write(" ");
                emitExpr(right, myPrec + 1);
            }
        });
    }

    private String binOpText(Object op) {
        if (op == null) {
            return "?";
        }

        if ( op instanceof Integer ) {
            int iop= ((Integer)op).intValue();
            switch ( iop ) {
                case 1: return "+";
                case 2: return "-";
                case 3: return "*";
                case 4: return "/";
            }
        }
        
        String n = simpleName(op);

        if ("Add".equals(n)) {
            return "+";
        }
        if ("Sub".equals(n)) {
            return "-";
        }
        if ("Mult".equals(n) || "Mul".equals(n)) {
            return "*";
        }
        if ("Div".equals(n)) {
            return "/";
        }
        if ("FloorDiv".equals(n)) {
            return "//";
        }
        if ("Mod".equals(n)) {
            return "%";
        }
        if ("Pow".equals(n) || "Power".equals(n)) {
            return "**";
        }
        if ("LShift".equals(n) || "LeftShift".equals(n)) {
            return "<<";
        }
        if ("RShift".equals(n) || "RightShift".equals(n)) {
            return ">>";
        }
        if ("BitOr".equals(n) || "Bitor".equals(n)) {
            return "|";
        }
        if ("BitXor".equals(n) || "Bitxor".equals(n)) {
            return "^";
        }
        if ("BitAnd".equals(n) || "Bitand".equals(n)) {
            return "&";
        }

        return n;
    }

    private int precedenceForBinOp(String op) {
        if ("**".equals(op)) {
            return PREC_POWER;
        }
        if ("*".equals(op) || "/".equals(op) || "//".equals(op) || "%".equals(op)) {
            return PREC_MUL;
        }
        if ("+".equals(op) || "-".equals(op)) {
            return PREC_ADD;
        }
        if ("<<".equals(op) || ">>".equals(op)) {
            return PREC_SHIFT;
        }
        if ("&".equals(op)) {
            return PREC_BITAND;
        }
        if ("^".equals(op)) {
            return PREC_BITXOR;
        }
        if ("|".equals(op)) {
            return PREC_BITOR;
        }
        return PREC_ADD;
    }
//TODO: ChatGPT mentions something about exponentiation is right-associative, see
//https://chatgpt.com/share/69bc2ff1-38a4-800c-a3d6-64f157dbec90
}
