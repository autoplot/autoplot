/* Copyright (C) 2019 Chris Piker, Jeremy Faden
 * 
 * This package, org.autoplot.dfc, is part Autoplot <autoplot.org>.  It provides an
 * interface to the Das2 Federated Catalog (DFC) system.
 *
 * Autoplot is open source
 *
 * DcfNodeSource.java
 */
package org.autoplot.dfc;

import java.net.URI;
import java.text.ParseException;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.util.catalog.DasNode;
import org.das2.util.catalog.DasNodeFactory;
import org.das2.util.monitor.ProgressMonitor;
/**
 *
 * @author cwp
 */
public class DfcSource extends AbstractDataSource
{
	DasNode node;
	
	public DfcSource(URI uri, DasNode _node) throws ParseException
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
