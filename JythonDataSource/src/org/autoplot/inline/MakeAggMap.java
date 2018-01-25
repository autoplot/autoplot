/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.inline;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.Util;
import org.das2.util.LoggerManager;

/**
 * Given a vap+inline URI, can its getDataSet calls be converted so that
 * the vap+inline URI becomes an aggregation?
 * @author jbf
 */
public class MakeAggMap implements DataSourceUtil.URIMap {

    private static Logger logger= LoggerManager.getLogger("jython.inline");
    
    @Override
    public String map(String uri) {
        String vapScheme= uri.substring(0,11);
        String inline= uri.substring(11);
        String[] code = Util.guardedSplit( inline, '&', '\'', '\"' );
        String[] newCode= new String[code.length];
        Pattern p= Pattern.compile("([_a-zA-Z0-9]+\\s*\\=\\s*getDataSet\\(\\s*[\\'\\\"])(.+)([\\'\\\"]\\s*\\)\\s*)");
        int iline=0;
        String timerange = null;
        boolean changed= false;
        for ( String c: code ) {
            Matcher m= p.matcher(c);
            if ( m.matches() ) {
                String uri1= m.group(2);
                String nuri1= DataSourceUtil.makeAggregation(uri1);
                if ( nuri1==null || nuri1.equals(uri1) ) {
                    logger.log(Level.FINE, "unable to aggregate: {0}", uri1);
                    return uri;
                } else {
                    newCode[iline]= m.group(1)+nuri1+m.group(3);
                    URISplit split= URISplit.parse(nuri1);
                    Map<String,String> params= URISplit.parseParams(split.params);
                    changed= true;
                    if ( timerange==null ) timerange= params.get(URISplit.PARAM_TIME_RANGE);
                }
            } else {
                newCode[iline]= c;
            }
            iline++;
        }
        if ( changed ) {
            StringBuilder b= new StringBuilder(vapScheme);
            for ( String s: newCode ) {
                b.append(s).append("&");
            }
            b.append("timerange=").append(timerange);
            return b.toString();
        } else {
            return uri;
        }
    }
    
}
