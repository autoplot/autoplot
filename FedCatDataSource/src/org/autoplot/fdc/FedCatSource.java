/* Copyright (C) 2019 Chris Piker, Jeremy Faden
 * 
 * This package, org.autoplot.dfc, is part Autoplot <autoplot.org>.  It provides an
 * interface to the Das2 Federated Catalog (DFC) system.
 *
 * Autoplot is open source
 */
package org.autoplot.fdc;

import java.net.URI;
import java.text.ParseException;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.catalog.DasNode;
import org.das2.util.monitor.ProgressMonitor;
/**
 *
 * @author cwp
 */
public class FedCatSource extends AbstractDataSource
{
	DasNode node;
	
	public FedCatSource(URI uri, DasNode _node) throws ParseException
	{
		super(uri);
		node = _node;
	}

	@Override
	public QDataSet getDataSet(ProgressMonitor mon) throws Exception
	{
		return DataSetUtil.replicateDataSet( 30, 1.0 );
	}
	
}
