/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.jythonsupport;

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
             if ( sn instanceof org.python.parser.ast.FunctionDef ) {
                 FunctionDef fd = (FunctionDef)sn;
                 this.builder.append("void ").append(fd.name).append("(");
                 for ( int i=0; i<fd.args.args.length; i++ ) {
                     if (i>0 ) this.builder.append(",");
                     this.builder.append("").append(fd.args.args[i].getImage().toString());
                 }
                 this.builder.append( ") {\n");
                 for ( int i=0; i<fd.body.length; i++ ) {
                     traverse( "\t", fd.body[i] );
                     this.builder.append( "\n");
                 }
                 this.builder.append( "}\n");
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
                     this.builder.append(as.targets[i]);
                 }
                 this.builder.append("=");
                 traverse( "",as.value );
                 
             } else if ( sn instanceof Name ) {
                 this.builder.append(((Name)sn).id);
             } else {
                 this.builder.append(sn.toString());
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
        String code= "def foo():\n  print 'hello'\nfoo()";
        System.err.println( convert(code) );
    }
    
}
