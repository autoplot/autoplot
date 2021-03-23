
package org.autoplot.pds;

import gov.nasa.pds.label.Label;
import gov.nasa.pds.label.object.ArrayObject;
import gov.nasa.pds.label.object.FieldDescription;
import gov.nasa.pds.label.object.FieldType;
import gov.nasa.pds.label.object.TableObject;
import gov.nasa.pds.label.object.TableRecord;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class PdsDataSource extends AbstractDataSource {

    public PdsDataSource(URI uri) {
        super(uri);
    }

    /**
     * bootstrap routine for getting data from fields of a TableObject.  TODO: rewrite so that
     * multiple fields are read at once.
     * @param t
     * @param columnName
     * @return
     * @throws IOException 
     */
    private QDataSet getFromTable( TableObject t, String[] columnNames ) throws IOException {
        
        int ncols= columnNames.length;
        int[] icols= new int[ncols];
        
        DataSetBuilder dsb= new DataSetBuilder(2,100,ncols);
        
        for ( int i=0; i<ncols; i++ ) {
            int icol= -1;
            FieldDescription[] fields= t.getFields();
            for ( int j=0; j<fields.length; j++ ) {
                if ( fields[j].getName().equals(columnNames[i]) ) {
                    icol= j;
                    break;
                }
            }
            //TODO: what is returned when column isn't found?
            icols[i]= icol;
            FieldDescription fieldDescription= t.getFields()[icol];
            dsb.setName( i, fieldDescription.getName() );
            dsb.setLabel( i, fieldDescription.getName() );
            //TODO: Larry has nice descriptions.  How to get at those? https://space.physics.uiowa.edu/pds/cassini-rpws-electron_density/data/2006/rpws_fpe_2006-141_v1.xml
            switch (fieldDescription.getType()) {
                case ASCII_DATE:
                case ASCII_DATE_DOY:
                case ASCII_DATE_TIME_DOY_UTC:
                case ASCII_DATE_TIME_UTC:
                case ASCII_DATE_TIME_DOY:
                case ASCII_DATE_TIME_YMD:
                case ASCII_DATE_TIME_YMD_UTC:
                    dsb.setUnits(i, Units.us2000);
                    break;
                    //TODO: create timeparser 
                default:
                    dsb.setUnits(i, Units.dimensionless ); // TODO: how to get "unit" from label
            }
        }
        
        TableRecord r;
        while ( ( r= t.readNext())!=null ) {
            for ( int i=0; i<ncols; i++ ) {
                try {
                    dsb.putValue( -1, i, r.getString(icols[i]+1) );
                } catch (ParseException ex) {
                    dsb.putValue( -1, i, dsb.getUnits(i).getFillDatum() );
                }
            }
            dsb.nextRecord();
        }
        
        return dsb.getDataSet();
    }
    
    private double[] flatten( double[][] dd ) {
        double[] rank1= new double[dd.length*dd[0].length];
        int nj= dd[0].length;
        int kk= 0;
        for ( int i=0; i<dd.length; i++ ) {
            double[] d= dd[i];
            for ( int j=0; j<nj; j++ ) {
                rank1[kk++]= d[j];
            }
        }
        return rank1;
    }
    
    
     /**
     *
     * @param monitor the value of monitor
     * @throws IOException
     * @throws SAXException
     */
    private Document readXML( File f ) throws IOException, SAXException {
        DocumentBuilder builder= null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

        Document document;
        
        try (InputStream in = new FileInputStream(f)) {
            InputSource source = new InputSource( in );
            document = builder.parse(source);
        }     
        
        return document;
    }
    
    @Override
    public org.das2.qds.QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String name= getParam("arg_0","");
        
        URISplit split= URISplit.parse( getURI() );
            
        URL fileUrl= PdsDataSourceFactory.getFileResource( split.resourceUri.toURL(), mon );
        File xmlfile = DataSetURI.getFile( split.resourceUri.toURL() ,new NullProgressMonitor());
        DataSetURI.getFile(fileUrl,mon );
                    
        Label label = Label.open( xmlfile.toURI().toURL() ); 
        
        Document doc= readXML(xmlfile);
                
        List<String> names= new ArrayList<>();
        String X= getParam("X","");
        if ( !X.equals("") ) {
            names.add(X);
        }
        String Y= getParam("Y","");
        if ( !Y.equals("") ) {
            names.add(Y);
        }
        
        String Z= getParam("Z","");
        if ( !Z.equals("") ) {
            names.add(Z);
        }

        if ( !name.equals("") ) {
            names.add(name);
        }
            
        QDataSet result=null;
        QDataSet[] results= new QDataSet[names.size()];
        
        // see which parameters will come from tables.
        for ( TableObject t : label.getObjects( TableObject.class) ) {
            List<String> tableColumnNames= new ArrayList<>();
            List<Integer> datasetColumnIndexes= new ArrayList<>();
            
            for ( int i=0; i<names.size(); i++ ) {
                name= names.get(i);
                for ( FieldDescription fd: t.getFields() ) {
                    if ( name.equals( fd.getName() ) ) { 
                        tableColumnNames.add( fd.getName() );
                        datasetColumnIndexes.add(i);
                    }
                }
            }
            if ( tableColumnNames.size()>0 ) {
                QDataSet bresults= getFromTable( t, tableColumnNames.toArray(new String[tableColumnNames.size()]) );
                int iresults= 0;
                for ( int i:datasetColumnIndexes ) {
                    results[i]= Ops.unbundle( bresults, iresults );
                    iresults++;                    
                }
            }
        }
        
        for ( int i=0; i<names.size(); i++ ) {
            if ( results[i]!=null ) continue;
            name= names.get(i);
            for ( ArrayObject a: label.getObjects(ArrayObject.class) ) {
                Units units=null;
                if ( a.getName().equals(name) ) {
                    if ( a.getAxes()==2 ) {
                        double[][] dd= a.getElements2D();
                        double[] rank1= flatten(dd);
                        int[] qube= new int[] { dd.length, dd[0].length };
                        results[i]= DDataSet.wrap( rank1, qube );
                    } else if ( a.getAxes()==1 ) {
                        double[] dd= a.getElements1D();
                        int[] qube= new int[] { dd.length };
                        DDataSet ddresult= DDataSet.wrap( dd, qube );
                        if ( name.equals("Epoch") ) {
                            logger.info("Epoch kludge results in CDF_TT2000 units");
                            units= Units.cdfTT2000;
                            ddresult.putProperty( QDataSet.UNITS, units );
                        }
                        results[i]= ddresult;
                    }
                    
                    XPathFactory factory= XPathFactory.newInstance();
                    XPath xpath= factory.newXPath();

                    if ( doc!=null ) {
                        String sunits= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/Element_Array/unit/text()", doc );
                        sunits= sunits.trim();
                        if ( sunits.length()>0 ) {
                            ((MutablePropertyDataSet)results[i]).putProperty( QDataSet.UNITS, Units.lookupUnits(sunits) );
                        }
                        if ( units==null || !UnitsUtil.isTimeLocation(units) ) {
                            String labl=      (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/name/text()", doc );
                            if ( labl.length()==0 ) labl= name;
                            ((MutablePropertyDataSet)results[i]).putProperty( QDataSet.LABEL, labl );
                            String title= (String) xpath.evaluate( "//Product_Observational/File_Area_Observational/Array[name='"+name+"']/description/text()", doc );
                            if ( title.length()>0 ) {
                                ((MutablePropertyDataSet)results[i]).putProperty( QDataSet.TITLE, title.trim() );
                            }
                        }
                        
                    }
                }
            }
        }
                
        if ( result==null ) {
            for ( int i=0; i<names.size(); i++ ) {
                name= names.get(i);
            }
            switch (results.length) {
                case 1:
                    return results[0];
                case 2:
                    return Ops.link( results[0], results[1] );
                case 3:
                    return Ops.link( results[0], results[1], results[2] );
                default:
                    break;
            }
        }
        return null;
    }
    
}
