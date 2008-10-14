package org.virbo.datasource;

public class URLSplit {

    public String scheme;
    public String path;
    public String file;
    public String ext;
    public String params;

    public String toString() {
        return path + "\n" + file + "\n" + ext + "\n" + params;
    }
}
