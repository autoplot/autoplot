package org.autoplot.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 * This allows a legacy preference to be used alongside a new preference,
 * keeping the users preferences.
 * @author jbf
 */
public class MigratePreference extends Preferences {

    private final Preferences p1;
    private final Preferences p2;

    /**
     * create a new where preferences are first read from p1 and if not found 
     * there the data is read from from p2 (and if not found there then the
     * default is used.  When writing a preference, a copy is made to both
     * p1 and p2.
     * @param p1
     * @param p2 
     */
    public MigratePreference( Preferences p1, Preferences p2 ) {
        this.p1= p1;
        this.p2= p2;
    }
    
    @Override
    public void put(String key, String value) {
        this.p1.put( key, value );
        this.p2.put( key, value );
    }

    @Override
    public String get(String key, String def) {
        return this.p1.get( key, this.p2.get(key, def) );
    }

    @Override
    public void remove(String key) {
        this.p1.remove(key);
        this.p2.remove(key);
    }

    @Override
    public void clear() throws BackingStoreException {
        this.p1.clear();
        this.p2.clear();
    }

    @Override
    public void putInt(String key, int value) {
        this.p1.putInt(key, value);
        this.p2.putInt(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        return this.p1.getInt(key, this.p2.getInt( key, def) );
    }

    @Override
    public void putLong(String key, long value) {
        this.p1.putLong(key, value);
        this.p2.putLong(key, value);
    }

    @Override
    public long getLong(String key, long def) {
        return this.p1.getLong(key, this.p2.getLong( key, def) );
    }

    @Override
    public void putBoolean(String key, boolean value) {
        this.p1.putBoolean(key, value);
        this.p2.putBoolean(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return this.p1.getBoolean(key, this.p2.getBoolean( key, def) );
    }

    @Override
    public void putFloat(String key, float value) {
        this.p1.putFloat(key, value);
        this.p2.putFloat(key, value);
    }

    @Override
    public float getFloat(String key, float def) {
        return this.p1.getFloat(key, this.p2.getFloat( key, def ) );
    }

    @Override
    public void putDouble(String key, double value) {
        this.p1.putDouble(key, value);
        this.p2.putDouble(key, value);
    }

    @Override
    public double getDouble(String key, double def) {
        return this.p1.getDouble(key, this.p2.getDouble( key, def) );
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        this.p1.putByteArray(key, value);
        this.p2.putByteArray(key, value);
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        return this.p1.getByteArray(key, this.getByteArray( key, def) );
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return this.p1.keys();
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        return this.p1.childrenNames();
    }

    @Override
    public Preferences parent() {
        return this.p1.parent();
    }

    @Override
    public Preferences node(String pathName) {
        return new MigratePreference( this.p1.node(pathName), this.p2.node(pathName) );
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        return this.p1.nodeExists(pathName);
    }

    @Override
    public void removeNode() throws BackingStoreException {
        this.p1.removeNode();
        this.p2.removeNode();
    }

    @Override
    public String name() {
        return this.p1.name();
    }

    @Override
    public String absolutePath() {
        return this.p1.name();
    }

    @Override
    public boolean isUserNode() {
        return this.p1.isUserNode();
    }

    @Override
    public String toString() {
        return "MigratePreference";
    }

    @Override
    public void flush() throws BackingStoreException {
        this.p1.flush();
        this.p2.flush();
    }

    @Override
    public void sync() throws BackingStoreException {
        this.p1.sync();
        this.p2.sync();
    }

    @Override
    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
        this.p1.addPreferenceChangeListener(pcl);
    }

    @Override
    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
        this.p1.removePreferenceChangeListener(pcl);
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener ncl) {
        this.p1.addNodeChangeListener(ncl);
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener ncl) {
        this.p1.removeNodeChangeListener(ncl);
    }

    @Override
    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
        this.p1.exportNode(os);
    }

    @Override
    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
        this.p1.exportSubtree(os);
    }
    
}
