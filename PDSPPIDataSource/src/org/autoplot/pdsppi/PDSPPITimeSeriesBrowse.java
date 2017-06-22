/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.text.ParseException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public final class PDSPPITimeSeriesBrowse implements TimeSeriesBrowse {

    DatumRange tr;
    String baseUri=null;
    String templateUri=null;
    
    public PDSPPITimeSeriesBrowse() {
        //note this must only be called from DataSourceFactory
    }
    
    public PDSPPITimeSeriesBrowse( String uri ) {
        try {
            this.setURI( uri );
        } catch ( ParseException ex ) {
            this.templateUri= null;
        }
    }
    
    @Override
    public void setTimeRange(DatumRange dr) {
        this.tr= dr;
    }

    @Override
    public DatumRange getTimeRange() {
        return this.tr;
    }

    @Override
    public void setTimeResolution(Datum d) {
        // ignore
    }

    @Override
    public Datum getTimeResolution() {
        return null;
    }

    @Override
    public String getURI() {
        if ( this.templateUri!=null ) {
            TimeParser tp= TimeParser.create(templateUri);
            return tp.format(tr);
        } else {
            return baseUri;
        }
    }

    @Override
    public String blurURI() {
        return getURI();
    }

    @Override
    public void setURI(String suri) throws ParseException {
        if ( this.baseUri==null ) {
            this.baseUri= suri;
            this.templateUri= PDSPPIDB.getInstance().checkTimeSeriesBrowse(suri);
        }
        if ( this.templateUri!=null ) {
            TimeParser tp= TimeParser.create(templateUri);
            DatumRange dr= tp.parse(suri).getTimeRange();
            this.tr= dr;
        } else {
            // do nothing.
        }
    }
    
}
