/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirror WalkImageSequence, storing the global properties of the quality control.
 * @author jbf
 */
public class QualityControlSequence {

    private int qcOK, qcProb, qcIgn, qcUnknown;     //Quality control counters
    WalkImageSequence walkImageSequence;
    URI qcFolder;

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
    
    QualityControlSequence( WalkImageSequence wis, URI qcFolder ) throws IOException {
        this.walkImageSequence= wis;
        this.qcFolder= qcFolder;
        try {
            if ( walkImageSequence.size()>0 ) { // sequence might be empty
                QualityControlRecord.getRecord(walkImageSequence.imageAt(0).getUri(), qcFolder);
            }
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        refreshQCTotals();
    }

    
    protected final int[] refreshQCTotals() {
        qcOK = qcProb = qcIgn = qcUnknown = 0;
        for(int i=0; i < walkImageSequence.size(); i++) {
            if ( getQualityControlRecord(i)==null ) continue;
            switch (getQualityControlRecord(i).getStatus()) {
                case OK:
                    qcOK++;
                    break;
                case PROBLEM:
                    qcProb++;
                    break;
                case IGNORE:
                    qcIgn++;
                    break;
                case UNKNOWN:
                    qcUnknown++;
                    break;
            }
        }
        walkImageSequence.fireBadgeChanged();
        return new int[] {qcOK, qcProb, qcIgn, qcUnknown};
    }


    public int[] getQCTotals() {
        int r[] = {qcOK, qcProb, qcIgn, qcUnknown};
        return r;
    }

    /**
     * return the QC record 
     * @param forIndex
     * @return 
     */
    public QualityControlRecord getQualityControlRecord(int forIndex) {
        if (!PngWalkTool.isQualityControlEnabled()) {
            throw new IllegalStateException();
        }
        QualityControlRecord rec;
        try {
            URI imageURI= walkImageSequence.imageAt(forIndex).getUri();
            if ( imageURI.toString().length()==0 ) { // bug 3055130 okay
                rec=null;
            } else {
                rec = QualityControlRecord.getRecord( imageURI, qcFolder);
            }
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            rec = null;
        } catch(IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            rec = null;
        }
        return rec;

    }
    
    /**
     * return the QC record where index is for all images.
     * @param forIndex
     * @return 
     */
    public QualityControlRecord getQualityControlRecordNoSubRange(int forIndex) {
        if (!PngWalkTool.isQualityControlEnabled()) {
            throw new IllegalStateException();
        }
        QualityControlRecord rec;
        try {
            
            URI imageURI= walkImageSequence.imageAtNoSubRange(forIndex).getUri();
            if ( imageURI.toString().length()==0 ) { // bug 3055130 okay
                rec=null;
            } else {
                rec = QualityControlRecord.getRecord( imageURI, qcFolder);
            }
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            rec = null;
        } catch(IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            rec = null;
        }
        return rec;

    }
}
