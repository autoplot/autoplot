/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.util;

import java.util.ResourceBundle;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *
 * @author jbf
 */
public class TransparentLogger extends Logger {
    public static Logger getLogger( String name ) {
        return new TransparentLogger( Logger.getLogger(name) );
    }

    Logger logger;

    private TransparentLogger( Logger logger ) {
        super("transparent"+logger, null );
        this.logger= logger;
    }

    public String toString() {
        Logger ll= logger;
        Level l= ll.getLevel();
        while ( l==null ) {
            ll= ll.getParent();
            l= ll.getLevel();
        }
        Logger hl= logger;
        Handler[] h= hl.getHandlers();
        while ( h.length==0 ) {
            hl= hl.getParent();
            h= hl.getHandlers();
        }
        String slevel;
        if ( ll!=logger ) {
            slevel= "" + l + "(from "+ll+")";
        } else {
            slevel= "" + l;
        }
        return logger.getName() + "  " + slevel + "  " + h[0] + "@" + h[0].getLevel();
    }

    public void warning(String msg) {
        logger.warning(msg);
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        logger.throwing(sourceClass, sourceMethod, thrown);
    }

    public void severe(String msg) {
        logger.severe(msg);
    }

    public synchronized void setUseParentHandlers(boolean useParentHandlers) {
        logger.setUseParentHandlers(useParentHandlers);
    }

    public void setParent(Logger parent) {
        logger.setParent(parent);
    }

    public void setLevel(Level newLevel) throws SecurityException {
        logger.setLevel(newLevel);
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        logger.setFilter(newFilter);
    }

    public synchronized void removeHandler(Handler handler) throws SecurityException {
        logger.removeHandler(handler);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Throwable thrown) {
        logger.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object[] params) {
        logger.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        logger.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
    }

    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        logger.logrb(level, sourceClass, sourceMethod, bundleName, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        logger.logp(level, sourceClass, sourceMethod, msg, thrown);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        logger.logp(level, sourceClass, sourceMethod, msg, params);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        logger.logp(level, sourceClass, sourceMethod, msg, param1);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        logger.logp(level, sourceClass, sourceMethod, msg);
    }

    public void log(Level level, String msg, Throwable thrown) {
        logger.log(level, msg, thrown);
    }

    public void log(Level level, String msg, Object[] params) {
        logger.log(level, msg, params);
    }

    public void log(Level level, String msg, Object param1) {
        logger.log(level, msg, param1);
    }

    public void log(Level level, String msg) {
        logger.log(level, msg);
    }

    public void log(LogRecord record) {
        logger.log(record);
    }

    public boolean isLoggable(Level level) {
        return logger.isLoggable(level);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public synchronized boolean getUseParentHandlers() {
        return logger.getUseParentHandlers();
    }

    public String getResourceBundleName() {
        return logger.getResourceBundleName();
    }

    public ResourceBundle getResourceBundle() {
        return logger.getResourceBundle();
    }

    public Logger getParent() {
        return logger.getParent();
    }

    public String getName() {
        return logger.getName();
    }

    public Level getLevel() {
        return logger.getLevel();
    }

    public synchronized Handler[] getHandlers() {
        return logger.getHandlers();
    }

    public Filter getFilter() {
        return logger.getFilter();
    }

    public void finest(String msg) {
        logger.finest(msg);
    }

    public void finer(String msg) {
        logger.finer(msg);
    }

    public void fine(String msg) {
        logger.fine(msg);
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
        logger.exiting(sourceClass, sourceMethod, result);
    }

    public void exiting(String sourceClass, String sourceMethod) {
        logger.exiting(sourceClass, sourceMethod);
    }

    public void entering(String sourceClass, String sourceMethod, Object[] params) {
        logger.entering(sourceClass, sourceMethod, params);
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
        logger.entering(sourceClass, sourceMethod, param1);
    }

    public void entering(String sourceClass, String sourceMethod) {
        logger.entering(sourceClass, sourceMethod);
    }

    public void config(String msg) {
        logger.config(msg);
    }

    public synchronized void addHandler(Handler handler) throws SecurityException {
        logger.addHandler(handler);
    }


}
