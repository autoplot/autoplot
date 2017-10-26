/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.autoplot.datasource.DataSetURI;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.parser.SimpleNode;
import org.python.parser.ast.*;

/**
 * experiment with code which converts the Jython AST (syntax tree) into Java 
 * code.
 * @author jbf
 */
public class JythonToJavaConverter {
    
    private static class MyVisitorBase<R> extends VisitorBase {
         boolean looksOkay= true; 
         boolean visitNameFail= false;
         
         StringBuilder builder;
         int lineNumber=1;
         
         MyVisitorBase( StringBuilder builder ) {
             this.builder= builder;
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
             traverse( "", sn );
         }
         
         public void traverse(String indent, SimpleNode sn) throws Exception {
             while ( sn.beginLine>lineNumber ) {
                 this.builder.append("\n");
                 lineNumber++;
             }
             if ( sn instanceof org.python.parser.ast.FunctionDef ) {
                 FunctionDef fd = (FunctionDef)sn;
                 this.builder.append("private void ").append(fd.name).append("(");
                 for ( int i=0; i<fd.args.args.length; i++ ) {
                     if (i>0 ) this.builder.append(",");
                     this.builder.append("").append(fd.args.args[i].getImage().toString());
                 }
                 this.builder.append( ") {\n"); lineNumber++;
                 for ( int i=0; i<fd.body.length; i++ ) {
                     traverse( "\t", fd.body[i] );
                     this.builder.append( "\n"); lineNumber++;
                 }
                 this.builder.append( "}\n"); lineNumber++;
             } else if ( sn instanceof Expr ) {
                 ((Expr)sn).getImage();
             } else if ( sn instanceof Print ) {
                 Print pr= ((Print)sn);
                 this.builder.append(indent);
                 this.builder.append("System.err.println(");
                 for ( int i=0; i<pr.values.length; i++ ) {
                     if ( i>0 ) this.builder.append(",");
                     traverse( "", pr.values[i] );
                 }
                 this.builder.append(");");
             } else if ( sn instanceof ImportFrom ) {
                 ImportFrom ff= ((ImportFrom)sn);
                 for ( int i=0; i<ff.names.length; i++ ) {
                     this.builder.append("import ").append(ff.module).append('.').append(ff.names[i].name).append("\n"); lineNumber++;
                 }
             } else if ( sn instanceof Str ) {
                 Str ss= (Str)sn;
                 this.builder.append("\"");
                 this.builder.append(ss.s);
                 this.builder.append("\"");
             } else if ( sn instanceof Assign ) {
                 Assign as= ((Assign)sn);
                 this.builder.append(indent);
                 for ( int i=0; i<as.targets.length; i++ ) {
                     if ( i>0 ) this.builder.append(",");
                     traverse("",as.targets[i]);
                 }
                 this.builder.append("=");
                 traverse( "",as.value );
                 
             } else if ( sn instanceof Name ) {
                 this.builder.append(((Name)sn).id);
             } else if ( sn instanceof Call ) {
                 Call cc= (Call)sn;
                 if ( cc.func instanceof Name ) {
                     this.builder.append(((Name)cc.func).id);                 
                     this.builder.append("(");
                     for ( int i=0; i<cc.args.length; i++ ) {
                         if ( i>0 ) this.builder.append(",");
                         traverse( "", cc.args[i] );
                     }
                     this.builder.append(")\n"); lineNumber++;
                 } else {
                     this.builder.append(cc.func.toString()).append("\n");  lineNumber++;
                 }
             } else if ( sn instanceof For ) {
                 For ff= (For)sn;
                 this.builder.append(indent).append("for ( Object ");
                 traverse("",ff.target);
                 this.builder.append(" : ");
                 traverse("",ff.iter);
                 this.builder.append(" ) {\n"); lineNumber++;
                 for ( int i=0; i<ff.body.length; i++ ) {
                     this.builder.append(indent).append(indent);
                     traverse( indent+indent, ff.body[i] );
                 }
                 this.builder.append(indent).append("}\n");  lineNumber++;
             } else if ( sn instanceof If ) {
                 If ff= (If)sn;
                 this.builder.append(indent).append("if ( ");
                 traverse("",ff.test );
                 this.builder.append(" ) {\n"); lineNumber++;
                 for ( int i=0; i<ff.body.length; i++ ) {
                     this.builder.append(indent).append(indent);
                     traverse( indent+indent, ff.body[i] );
                 }
                 this.builder.append(indent).append("}\n");  lineNumber++;
             } else if ( sn instanceof Compare ) {
                 Compare cp= (Compare)sn;
                 traverse( "", cp.left );
                 this.builder.append( "?in?" );
                 for ( exprType t: cp.comparators) {
                    traverse( "", t );
                 }
             } else if ( sn instanceof Continue ) {
                 this.builder.append("continue");
             } else {
                 this.builder.append(sn.toString()).append("\n"); lineNumber++;
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
    
    public static String convert( String script ) throws Exception {
        Module n= (Module)org.python.core.parser.parse( script, "exec" );
        StringBuilder b= new StringBuilder();
        convert( b, n );
        return b.toString();
    }
    
    private static void convert( StringBuilder sb, Module n ) throws Exception {
        VisitorBase vb= new MyVisitorBase(sb);
        n.traverse(vb);

    }
    
    public static void main( String[] args ) throws Exception {
        String code;
        //String code= "def foo():\n  print 'hello'\nfoo()";
        //System.err.println( convert(code) );
        
        String furi= "/home/jbf/project/autoplot/script/lookAtUserComments.jy";
        
        File src = DataSetURI.getFile(furi, new NullProgressMonitor() );

        try (FileReader reader = new FileReader(src)) {
            code= JythonUtil.readScript( new BufferedReader( reader ) );
            System.err.println( convert(code) );
        }
    }
    
}
