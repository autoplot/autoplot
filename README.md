# Introduction to Autoplot

Autoplot is an interactive browser for data on the web. Give it a URL or a
name of a file on your computer and it tries to create a sensible plot. It was
developed to allow quick and interactive browsing of data and metadata.
Autoplot works with more abstract data sources as well, like the CDAWeb at 
NASA/Goddard.  In this case a compact URI is provided, that refers to data.
These URIs (and URLs) can be pasted into emails for easy collaboration with 
collegues.

See https://autoplot.org

# Building Autoplot 

Autoplot is a git repo at github.com, and uses another repo for the Das2 library
which has developed along with Autoplot.  First clone Autoplot, and then Autoplot's
compile scripts are expecting the Das2 library codes to be "under" the Autoplot
code:

```
git clone git@github.com:autoplot/autoplot.git
cd autoplot
git clone git@github.com:das-developers/das2java.git
cd Autoplot
ant jar
ant run
```

For distribution of the executables, Install4J is used to build .dmg, .exe, .deb, and .rpm files.  A script is
used to create a "single-jar" (sometimes called a Jumbo Jar) release, compile-application.sh.  Install4J cannot
use this single jar because of the header launch script it contains, and the header script must be removed.
This is done with an Autoplot script, but other methods could be used.

There are a number Netbeans projects that can be built with ant.  They are:
* Autoplot -- Autoplot gui and application model
* CDFDataSource -- support for CDF.
* JythonDataSource -- dataset mashups using python code.
* WavDataSource -- the result of the wav tutorial
* DataSourcePack -- a number of data sources, including NetCDF, excel, and ascii tables.
* DataSource -- data source plugin interface and utilities
* QDataSet -- the data model
* dasCore -- graphics and GUI
* APLibs -- an empty project with no sources, but all the libraries needed are in libs.

The "src" directory of each of these folders contains the Java source code.  Together these should show nicely what's going on under the hood.  

To use the projects in Netbeans (8.1 or later), do the check out from within Netbeans and it should discover the projects after check out.  Then build Autoplot.

= Building Autoplot Servlet =
A simple servlet is found in the repository at https://svn.code.sf.net/p/autoplot/code/autoplot/trunk/AutoplotServlet.  This shows how Autoplot can be used to create graphics on the server-side, when used with a J2EE container like Apache Tomcat.

* Check out Autoplot sources as described above.
* The location of a server's classpath must be specified.  Locate a J2EE instance, such as Tomcat.
* change directory to the location of the ant build script, build.xml.
* Use ant to compile the servlet, using (where the classpath is set to your server location):
~~~~~
  ant -Dj2ee.platform.classpath=/usr/local/tomcat/apache-tomcat-6.0.16/lib/servlet-api.jar dist
~~~~~
* The war file will be found in the "dist" folder.

