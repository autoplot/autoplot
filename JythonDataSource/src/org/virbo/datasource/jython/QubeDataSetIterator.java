/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class QubeDataSetIterator {

    public interface DimensionIterator {

        boolean hasNext();

        int nextIndex();

        int index();

        int length();
    }

    public interface DimensionIteratorFactory {

        DimensionIterator newIterator(int len);
    }

    public static class StartStopStepIterator implements DimensionIterator {

        int start;
        int stop;
        int step;
        int index;

        public StartStopStepIterator(int start, int stop, int step) {
            this.start = start;
            this.stop = stop;
            this.step = step;
            this.index= start-step;
        }

        public boolean hasNext() {
            return index + step < stop;
        }

        public int nextIndex() {
            index += step;
            return index;
        }

        public int index() {
            return index;
        }

        public int length() {
            return (stop - start) / step;
        }
    }

    public static class StartStopStepIteratorFactory implements DimensionIteratorFactory {

        Integer start;
        Integer stop;
        Integer step;

        public StartStopStepIteratorFactory(Integer start, Integer stop, Integer step) {
            this.start = start;
            this.stop = stop;
            this.step = step;
        }

        public DimensionIterator newIterator(int length) {
            int start1 = this.start = start == null ? 0 : start;
            int stop1 = stop == null ? length : stop;
            int step1 = step == null ? 1 : step;
            if ( start1 < 0) start1 = length - start1;
            if ( stop1 < 0) stop1 = length - stop1;
            
            return new StartStopStepIterator(start1, stop1, step1);
        }
    }

    public static class IndexListIterator implements DimensionIterator {

        QDataSet ds;
        int index;

        public IndexListIterator(QDataSet ds) {
            this.ds = ds;
            this.index = -1;
        }

        public boolean hasNext() {
            return index < ds.length();
        }

        public int nextIndex() {
            index++;
            return (int) ds.value(index);
        }

        public int index() {
            return (int) ds.value(index);
        }

        public int length() {
            return ds.length();
        }
    }

    public static class IndexListIteratorFactory implements DimensionIteratorFactory {

        QDataSet ds;

        public IndexListIteratorFactory(QDataSet ds) {
            this.ds = ds;
        }

        public DimensionIterator newIterator(int length) {
            return new IndexListIterator(ds);
        }
    }

    public static class SingletonIterator implements DimensionIterator {

        int index;
        boolean hasNext = true;

        public SingletonIterator(int index) {
            this.index = index;
        }

        public boolean hasNext() {
            return hasNext;
        }

        public int nextIndex() {
            hasNext = false;
            return index;
        }

        public int index() {
            return index;
        }

        public int length() {
            return 1;
        }
    }

    public static class SingletonIteratorFactory implements DimensionIteratorFactory {

        int index;

        public SingletonIteratorFactory(int index) {
            this.index = index;
        }

        public DimensionIterator newIterator(int length) {
            return new SingletonIterator(index);
        }
    }
    DimensionIterator[] it = new DimensionIterator[3];
    DimensionIteratorFactory[] fit = new DimensionIteratorFactory[3];
    int rank;
    int[] qube;
    
    /**
     * dataset iterator to help in implementing the complex indexing
     * types of python.  
     * 
     * create a new iterator, set the index iterator factories, iterate.
     * @param ds
     */
    QubeDataSetIterator(QDataSet ds) {
        this.qube = DataSetUtil.qubeDims(ds);
        this.rank= ds.rank();
        for (int i = 0; i < ds.rank(); i++) {
            fit[i] = new StartStopStepIteratorFactory(0, null, 1);
        }
        initialize();
    }

    /**
     * reinitializes the iterator.
     * @param dim
     * @param fit
     */
    void setIndexIteratorFactory(int dim, DimensionIteratorFactory fit) {
        this.fit[dim] = fit;
        initialize();
    }

    /**
     * now that the factories are configured, initialize the iterator to
     * begin iterating.
     */
    private void initialize() {
        for ( int i=0; i<rank; i++ ) {
            it[i] = fit[i].newIterator( dimLength(i) );
        }        
    }
    
    /**
     * return the length of the dimension.  This is introduced with the intention
     * that this can support non-qube datasets as well.
     * @param idim
     * @return
     */
    private final int dimLength( int idim ) {
        return qube[idim];
    }
    
    public boolean hasNext() {
        int i = rank - 1;
        if (it[i].hasNext()) {
            return true;
        } else {
            if (i > 0) {
                for (int j = i - 1; j >= 0; j--) {
                    if ( it[j].hasNext() ) {
                        return true;
                    }
                }
            }            
        }
        return false;
    }

    public void next() {
        // implement borrow logic
        int i = rank - 1;
        if (it[i].hasNext()) {
            it[i].nextIndex();
        } else {
            if (i > 0) {
                for (int j = i - 1; j >= 0; j--) {
                    if ( it[j].hasNext() ) {
                        it[j].nextIndex();
                        for ( int k=j+1; k<=i; k++ ) {
                            it[k]= fit[k].newIterator( dimLength(k) );
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("no more elements");
            }
        }
    }

    public int index(int dim) {
        return it[dim].index();
    }

    public int length(int dim) {
        return it[dim].length();
    }

    public int rank() {
        return rank;
    }
}
