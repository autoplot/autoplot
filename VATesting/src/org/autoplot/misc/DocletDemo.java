/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import com.sun.javadoc.*;

/**
 * Demonstrate use of doclets to create better documentation.
 * @author jbf
 */
public class DocletDemo {
    
    private static String getSignature( MethodDoc md ) {
        StringBuilder build= new StringBuilder();
        build.append(md.name());
        build.append("(");
        int ip=0;
        for ( Parameter p: md.parameters() ) {
            if ( ip>0 ) build.append(",");
            build.append( p.type() ).append( " " ).append( p.name() );
            ip++;
        }
        build.append(") &rarr; " );
        build.append(md.returnType());
        return build.toString();
    }
    
    private static String getParameterList( MethodDoc md ) {
        StringBuilder build= new StringBuilder();
        for ( ParamTag p: md.paramTags() ) {
            build.append( p.parameterName() ).append(" - ").append(p.parameterComment()).append("\n");
            
        }
        return build.toString();
    }
    
    public static boolean start(RootDoc root) {
        ClassDoc[] classes = root.classes();
        for (int i = 0; i < classes.length; ++i) {
            for ( MethodDoc md: classes[i].methods() ) {
                //if ( md.name().equals("where") ) {
                    System.out.println( "=" + getSignature(md) + "=" );
                    System.out.println( md.commentText() );
                    System.out.println( "== Parameters ==" );
                    System.out.println(getParameterList(md));
                    System.out.println( "== Returns ==" ) ;
                    System.out.println( md.returnType() );
                    System.out.println( "\n\n" );
                //}
            }
        }
        return true;
    }
}
