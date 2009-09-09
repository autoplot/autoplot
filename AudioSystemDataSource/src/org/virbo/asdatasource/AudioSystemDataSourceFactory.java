/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.asdatasource;

import java.net.URI;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class AudioSystemDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new AudioSystemDataSource(uri);
    }

}
