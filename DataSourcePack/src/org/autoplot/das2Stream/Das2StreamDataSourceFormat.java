/* This Java package, org.autoplot.das2Stream is part of the Autoplot application
 *
 * Copyright (C) 2018 Chris Piker <chris-piker@uiowa.edu>
 * 
 * Autoplot is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 as published by the Free
 * Software Foundation, with the additional Classpath exception below.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
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
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;
import org.das2.qstream.QdsToD2sStream;
import org.das2.qstream.StreamException;

/** Format the data into das2 streams.
 * @author jbf,cwp
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {
	
	
	/** Get the das2 stream version value from an Autoplot URI
	 * 
	 * @param uri an Autoplot URI string
	 * @return a string representing the version value.  Only the strings
	 *         QdsToD2sStream.FORMAT_2_2 or QdsToD2sStream.FORMAT_2_3_BASIC are returned
	 */
	public static String getVersion(String uri){
		
		String sVer = URISplit.getParam(uri, "version", QdsToD2sStream.FORMAT_2_2);
		if(sVer.equalsIgnoreCase(QdsToD2sStream.FORMAT_2_3_BASIC))
			return QdsToD2sStream.FORMAT_2_3_BASIC;
		
		// i.e. For now ignore all version strings that are not 2.2 or 2.3-basic
		return QdsToD2sStream.FORMAT_2_2;
	}
	
	
	/** Get the fractional seconds value for das2 text streams from an Autoplot URI
	 * 
	 * @param uri An Autoplot URI string
	 * @return an integer from 0 to 12
	 */
	public static int getFracSeconds(String uri){
		String sVal = Integer.toString(QdsToD2sStream.DEFAUT_FRAC_SEC);
		String sFracSec = URISplit.getParam(uri, "fracsec", sVal);
		int nFracSec;
		try{
			nFracSec = Integer.parseInt(sFracSec);
		} catch (NumberFormatException e) {
			nFracSec = QdsToD2sStream.DEFAUT_FRAC_SEC; // Ignore error
		}
		return nFracSec;
	}
	
	/** Get the number of general significant digits for das2 text streams from an 
	 * Autoplot URI
	 * 
	 * @param uri An Autoplot URI string
	 * @return an integer from 2 to 14
	 */
	public static int getSigDigits(String uri){
		// In general URIs can't be trusted.  There is noting in the URI string that denotes
		// whether it has been encoded or not, and decoded stuff can look (at first glance)
		// like something that has still encoded.  Case in point format=%.3e.  Thus we will
		// not be using any values that have % in them after decoding, so format = "printf
		// style string" is out.  Switching to precision=N where N is some number of digits
		// greater than 1 and less than 15.
		String sVal = URISplit.getParam(uri, "precision", null);
		int nSigDigit = QdsToD2sStream.DEFAUT_SIG_DIGIT;
		if(sVal != null){
			try{
				nSigDigit = Integer.parseInt(sVal);
			} catch (NumberFormatException e) {
				// Should log the error here.
			}
		}
		return nSigDigit;
	}
	
	public static boolean getBinary(String uri){
		// Binary has to be set explicitly, default is text
		String type = URISplit.getParam(uri, "type", "ascii");
		return type.equalsIgnoreCase("binary");
	}
	
	/** Add Das2 export options into a split autoplot URI
	 * 
	 * @param lSplit
	 * @param version A version string, one of QdsToD2sStream.FORMAT_2_2, 
	 *                QdsToD2sStream.FORMAT_2_3_BASIC, etc
	 * @param binary True if a binary stream should be generated
	 * @param sigdigit The number of significant digits to use for general text data output
	 * @param fracsec The number of fractional seconds to use for general text time output
	 * @return 
	 */
	public static URISplit setOptions(
		URISplit lSplit, String version, boolean binary, int sigdigit, int fracsec
	){
		//you would think that lSplit would always be no-null, but than's often not the
		//case, do nothing when getting a null lSplit.
		if(lSplit == null) return null;
		
		Map<String, String> args = URISplit.parseParams(lSplit.params);
		
		if(binary) args.put("type", "binary");  // Default to getBinary
		else args.remove("type");
		
		if(sigdigit == QdsToD2sStream.DEFAUT_SIG_DIGIT )
			args.remove("precision");
		else
			args.put("precision",  Integer.toString(sigdigit));
		
		if(fracsec == QdsToD2sStream.DEFAUT_FRAC_SEC) args.remove("fracsec");
		else args.put("fracsec", Integer.toString(fracsec));
		
		if(version.equalsIgnoreCase(QdsToD2sStream.FORMAT_2_2)) args.remove("version");
		else args.put("version", version);
		
		lSplit.params = URISplit.formatParams(args);
		return lSplit;
	}

	@Override
	public void formatData(String url, QDataSet data, ProgressMonitor mon) 
		throws Exception{

		URISplit split = URISplit.parse(url);

		boolean binary = getBinary(url);
		int nSigDigit = getSigDigits(url);
		int nFracSec = getFracSeconds(url);
		String sVersion = getVersion(url);

		try(FileOutputStream fo = new FileOutputStream(new File(split.resourceUri))) {
			QdsToD2sStream writer;
			if(binary){
				writer = new QdsToD2sStream(sVersion);
			}
			else{
				writer = new QdsToD2sStream(sVersion, nSigDigit, nFracSec);
			}

			if(!writer.write(data, fo)){
				throw new StreamException("Dataset is rank 3 or otherwise incompatible "
					+ "with the das2 stream format");
			}
		}
	}

	@Override
	public boolean canFormat(QDataSet ds){
		QdsToD2sStream writer = new QdsToD2sStream(QdsToD2sStream.FORMAT_2_2);
		return writer.canWrite(ds); 
	}

	@Override
	public String getDescription(){
		return "Das2 Stream data transfer format";
	}

}
