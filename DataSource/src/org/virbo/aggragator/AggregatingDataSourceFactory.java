/*
 * AggregatingDataSourceFactory.java
 *
 * Created on October 25, 2007, 11:02 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.aggragator;

import org.das2.datum.DatumRangeUtil;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URLSplit;

/**
 * ftp://cdaweb.gsfc.nasa.gov/pub/istp/noaa/noaa14/%Y/noaa14_meped1min_sem_%Y%m%d_v01.cdf?timerange=2000-01-01
 * @author jbf
 */
public class AggregatingDataSourceFactory implements DataSourceFactory {

    /** Creates a new instance of AggregatingDataSourceFactory */
    public AggregatingDataSourceFactory() {
    }

    public DataSource getDataSource(URL url) throws Exception {
        String surl = url.toString();
        AggregatingDataSource ads = new AggregatingDataSource(url);
        FileStorageModel fsm = getFileStorageModel(surl);
        ads.setFsm(fsm);
        URLSplit split = DataSetURL.parse(surl);
        Map parms = DataSetURL.parseParams(split.params);
        String stimeRange= (String) parms.get("timerange");
        stimeRange= stimeRange.replaceAll("\\+", " ");
        ads.setViewRange(DatumRangeUtil.parseTimeRange(stimeRange));
        parms.remove("timerange");
        if (parms.size() > 0) {
            ads.setParams(DataSetURL.formatParams(parms));
        }

        return ads;
    }

    private static int splitIndex(String surl) {
        int i = surl.indexOf('%');
        i = surl.lastIndexOf('/', i);
        return i;
    }

    public static FileStorageModel getFileStorageModel(String surl) throws IOException {
        int i = surl.indexOf('?');

        String sansArgs = i == -1 ? surl : surl.substring(0, i);

        i = splitIndex(sansArgs);
        FileSystem fs = FileSystem.create( new URL(sansArgs.substring(0, i)) );
        FileStorageModel fsm = FileStorageModel.create(fs, sansArgs.substring(i));

        return fsm;
    }

    public static CompletionContext getDelegateDataSourceCompletionContext(CompletionContext cc) throws IOException {

        String surl = cc.surl;
        int carotPos = cc.surlpos;
        int urlLen = 0; //this is the position as we parse and process surl.

        FileStorageModel fsm = getFileStorageModel(surl);

        String delegateFile = fsm.getRepresentativeFile(new NullProgressMonitor());

        if (delegateFile == null) {
            throw new IllegalArgumentException("unable to find any files");
        }

        URLSplit split = DataSetURL.parse(surl);

        String delegateFfile = new URL(fsm.getFileSystem().getRootURL(), delegateFile).toString();
        urlLen += delegateFfile.length();
        carotPos -= urlLen - delegateFfile.length();
        split.file = delegateFfile;

        int i = surl.lastIndexOf("timerange=", cc.surlpos);

        if (i != -1) {
            int i1 = surl.indexOf("&", i);
            carotPos -= (i1 - i);
        }

        Map parms = DataSetURL.parseParams(split.params);

        Object value = parms.remove("timerange");

        split.params = DataSetURL.formatParams(parms);

        String delegateUrl = DataSetURL.format(split);

        CompletionContext delegatecc = new CompletionContext();
        delegatecc.surl = delegateUrl;
        delegatecc.surlpos = carotPos;
        delegatecc.context = cc.context;
        delegatecc.resource= new URL( delegateFfile ); 

        return delegatecc;
    }

    /**
     * @throws IllegalArgumentException if it is not able to find any data files.
     */
    public static String getDelegateDataSourceFactoryUrl(String surl) throws IOException, IllegalArgumentException {
        FileStorageModel fsm = getFileStorageModel(surl);

        String file = fsm.getRepresentativeFile(new NullProgressMonitor());

        if (file == null) {
            throw new IllegalArgumentException("unable to find any files");
        }

        URLSplit split = DataSetURL.parse(surl);

        Map parms = DataSetURL.parseParams(split.params);
        parms.remove("timerange");
        split.params = DataSetURL.formatParams(parms);

        try {
            String scompUrl = fsm.getFileSystem().getRootURL() + file;
            if (split.params.length() > 0) {
                scompUrl += "?" + split.params;
            }
            URL compUrl = new URL(scompUrl);
            return compUrl.toString();
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static DataSourceFactory getDelegateDataSourceFactory(String surl) throws IOException, IllegalArgumentException {
        String delegateSurl = getDelegateDataSourceFactoryUrl(surl);
        try {
            return DataSetURL.getDataSourceFactory( DataSetURL.getURI(delegateSurl), new NullProgressMonitor());
        } catch (URISyntaxException ex) {
            Logger.getLogger(AggregatingDataSourceFactory.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public String editPanel(String surl) throws Exception {
        return surl;
    }


    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        DataSourceFactory f = getDelegateDataSourceFactory(cc.surl);
        List<CompletionContext> result = new ArrayList<CompletionContext>();
        String afile = getDelegateDataSourceFactoryUrl(cc.surl);
        CompletionContext delegatecc = getDelegateDataSourceCompletionContext(cc);

        List<CompletionContext> delegateCompletions = f.getCompletions(delegatecc,mon);
        result.addAll(delegateCompletions);

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            result.add(new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "timerange=" ));

        } else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            if (paramName.equals("timerange")) {
                result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<timerange>"));
            }
        } else {
        }
        return result;
    }

    public boolean reject( String surl, ProgressMonitor mon) {
        URLSplit split = DataSetURL.parse(surl);
        Map map = DataSetURL.parseParams(split.params);

        try {
            if (!map.containsKey("timerange")) {
                return true;
            }
            String timeRange = (String) map.get("timerange");
            if (timeRange.length() < 4) {
                return true;
            }
            String delegateSurl = getDelegateDataSourceFactoryUrl(surl);
            return getDelegateDataSourceFactory(surl).reject( delegateSurl, mon );
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return true;
        }
    }

}
