/* This Java package, org.autoplot.das2Stream is part of the Autoplot application
 *
 * Copyright (C) 2018 Jeremy Faden <faden@cottagesystems.com>
 * 
 * Autoplot is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 as published by the Free
 * Software Foundation, with the Classpath exception below.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License version 2
 * containing the Classpath exception clause along with this library; if not, write
 * to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 *
 * Classpath Exception
 * -------------------
 * The copyright holders designate this particular java package as subject to the
 * "Classpath" exception as provided here.
 *
 * Linking this package statically or dynamically with other modules is making a
 * combined work based on this package.  Thus, the terms and conditions of the GNU
 * General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this package give you
 * permission to link this package with independent modules to produce an
 * application, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting application under terms of your choice,
 * provided that you also meet, for each independent module, the terms and
 * conditions of the license of that module.  An independent module is a module
 * which is not derived from or based on this package.  If you modify this package,
 * you may extend this exception to your version of the package, but you are not
 * obligated to do so.  If you do not wish to do so, delete this exception
 * statement from your version.
 */
package org.autoplot.das2Stream;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;

/**
 * Format the data into QStreams.
 * @author jbf
 */
public class QStreamDataSourceFormat implements DataSourceFormat {

    @Override
    public void formatData( String url, QDataSet data, ProgressMonitor mon) throws Exception {

        URISplit split = URISplit.parse(url);
        java.util.Map<String, String> params= URISplit.parseParams(split.params);

        boolean binary= "binary".equals( params.get( "type" ) );
        try (FileOutputStream fo = new FileOutputStream( new File( split.resourceUri ) )) {
            if ( SemanticOps.isBundle(data) ) {
                new org.das2.qstream.BundleStreamFormatter().format( data, fo, !binary );
            } else {
                new org.das2.qstream.SimpleStreamFormatter().format( data, fo, !binary );
            }
        }
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return true; // at least it should, so if it can't it's a bug elsewhere.
    }

    @Override
    public String getDescription() {
        return "QStream data transfer format";
    }

}
