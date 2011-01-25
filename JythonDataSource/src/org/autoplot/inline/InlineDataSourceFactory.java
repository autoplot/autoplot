/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.inline;

import java.net.URI;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class InlineDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new InlineDataSource( uri );
    }

}
