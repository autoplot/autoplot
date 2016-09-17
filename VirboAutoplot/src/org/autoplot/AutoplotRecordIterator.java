/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class AutoplotRecordIterator implements Iterator<QDataSet>  {

    int index;
    int lastIndex;
    QDataSet src;
    
    public AutoplotRecordIterator( String uri, DatumRange dr ) {
        try {
            QDataSet ds= org.virbo.jythonsupport.Util.getDataSet( uri, dr.toString() );
            QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
            if ( dep0!=null ) {
                this.src= Ops.bundle( dep0, ds );
            } else {
                this.src= ds;
            }
            constrainDepend0(dr);
        } catch (Exception ex) {
            Logger.getLogger(AutoplotRecordIterator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    /**
     * limit the data returned such that only data within the datum range
     * provided are returned.
     * @param dr 
     */
    public void constrainDepend0( DatumRange dr ) {
        index= 0;
        lastIndex= src.length();
        QDataSet dep0= Ops.slice1( this.src, 0 );
        QDataSet findeces= Ops.findex( dep0, dr );
        this.index= (int)Math.ceil( findeces.value(0) );
        this.lastIndex= (int)Math.ceil( findeces.value(1) );
    }
    
    @Override
    public boolean hasNext() {
        return this.index < this.lastIndex;
    }

    @Override
    public QDataSet next() {
        return src.slice(index++);
    }
    
}
