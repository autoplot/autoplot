/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.beans.ExceptionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyException;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.capability.Caching;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class JythonDataSource extends AbstractDataSource implements Caching {

    ExceptionListener listener;

    public JythonDataSource(URL url, JythonDataSourceFactory factory) {
        super(url);
        addCability(Caching.class, this);
        this.listener = factory.listener;

    }

    @Override
    public synchronized QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        mon.started();

        PyException causedBy = null;
        try {
            if (interp == null) {
                interp = JythonUtil.createInterpreter(false);
                interp.set("monitor", mon);

                // interp.exec("def getParam( x, def ):\n  return params.has_key(x) ? params[x] : def\n\n");

                interp.exec("params=dict()");
                for (String s : params.keySet()) {
                    if (!s.equals("arg_0")) {
                        interp.exec("params['" + s + "']=" + params.get(s));
                    }
                }

                try {
                    boolean debug = false;  //TODO: exceptions will have the wrong line number in this mode.
                    if (debug) {
                        int i = 0;
                        BufferedReader reader = new BufferedReader(new FileReader(super.getFile(new NullProgressMonitor())));
                        String s = reader.readLine();
                        i++;
                        while (s != null) {
                            Logger.getLogger("virbo.jythondatasource").fine("" + i + ": " + s);
                            interp.exec(s);
                            s = reader.readLine();
                            i++;
                        }
                    } else {
                        interp.execfile(new FileInputStream(super.getFile(new NullProgressMonitor())));
                    }
                } catch (PyException ex) {
                    causedBy = ex;
                    ex.printStackTrace();
                    if (listener != null) {
                        listener.exceptionThrown(ex);
                    }
                } catch (Exception ex) {
                    throw ex;
                }

                if (causedBy == null) {
                    cacheDate = resourceDate(this.url);
                    cacheUrl = cacheUrl(this.url);
                }
            }

            String expr = params.get("arg_0");

            if (expr == null) {
                expr = "result";
            }


            PyObject result = interp.eval(expr);

            QDataSet res;
            if (result instanceof PyList) {
                res = JythonOps.coerce((PyList) result);
            } else {
                res = (QDataSet) result.__tojava__(QDataSet.class);
            }

            if (causedBy != null) {
                interp = null;
                cacheUrl = null;
                cacheDate = null;
                Logger.getLogger("virbo.jythonDataSouce").warning("exception in processing: " + causedBy);
            }

            return res;

        } catch (PyException ex) {

            String msg = "PyException: " + ex;
            if (causedBy != null) {
                msg += "\ncaused by:\n" + causedBy;
            }
            interp = null;
            cacheUrl = null;
            cacheDate = null;

            throw new RuntimeException(msg);
        } finally {
            mon.finished();
        }
    }
    PythonInterpreter interp = null;

    private String cacheUrl(URL url) {
        DataSetURL.URLSplit split = DataSetURL.parse(url.toString());
        Map<String, String> params = DataSetURL.parseParams(split.params);
        params.remove("arg_0");
        split.params = DataSetURL.formatParams(params);
        return DataSetURL.format(split);
    }

    private Date resourceDate(URL url) throws IOException {
        File src = DataSetURL.getFile(url, new NullProgressMonitor());
        return new Date(src.lastModified());
    }
    Date cacheDate = null;
    String cacheUrl = null;

    private boolean useCache(URL url) {
        try {
            if ((cacheDate != null && !resourceDate(url).after(cacheDate)) && (cacheUrl != null && cacheUrl.equals(cacheUrl(url)))) {
                return true;
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    public boolean satisfies(String surl) {
        try {
            return useCache(new URL(surl));
        } catch (IOException ex) {
            return false;
        }
    }

    public void resetURL(String surl) {
        try {
            this.url = new URL(surl);
            DataSetURL.URLSplit split = DataSetURL.parse(url.toString());
            params = DataSetURL.parseParams(split.params);
            resourceURL = new URL(split.file);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }


    }
}
