= What is Autoplot =
Autoplot is an interactive browser for data on the web. Give it a URL or a name of a file on your computer and it tries to create a sensible plot. It was developed to allow quick and interactive browsing of data and metadata.  Autoplot works with more abstract data sources as well, like the CDAWeb at NASA/Goddard.  In this case a compact URI is provided, that refers to data.  These URIs (and URLs) can be pasted into emails for easy collaboration with collegues.

See http://autoplot.org

= Building Autoplot =

The svn for Autoplot is https://svn.code.sf.net/p/autoplot/code/autoplot/trunk.  (Tagged versions are in https://svn.code.sf.net/p/autoplot/code/autoplot/trunk, but these are not done often and the head is typically what people are using, see http://autoplot.org/jnlp/ for release dates and perform checkouts based on these times.)

Check out the sources with Netbeans or some other svn client.

There are a number Netbeans projects that can be built with ant.  They are:
* VirboAutoplot -- Autoplot gui and application model
* CDFDataSource -- support for CDF.
* CEFDataSource -- support for CEF.
* JythonDataSource -- dataset mashups using python code.
* WavDataSource -- the result of the wav tutorial
* DataSourcePack -- a number of data sources, including NetCDF, excel, and ascii tables.
* DataSource -- data source plugin interface and utilities
* QDataSet -- the data model
* APLibs -- an empty project with no sources, but all the libraries needed are in libs.

The "src" directory of each of these folders contains the java source code.  Together these should show nicely what's going on under the hood.  

To build using ant, change into the VirboAutoplot project, and run: "ant jar".  The default target will compile all sub projects, and the resulting jar files will be in dist.
  cd VirboAutoplot
  ant jar

To use the projects in Netbeans (6.1 or later), do the check out from within Netbeans and it should discover the projects after check out.  Then build VirboAutoplot.

The das2 sources are not included, because they are available via svn at [https://saturn.physics.uiowa.edu/svn/das2/dasCore/trunk/]
To compile it, you will need 3 Batik libraries and iText.  These jars are found in the APLibs folder.

= Building Autoplot Servlet =
A simple servlet is found in the repository at https://vxoware.svn.sourceforge.net/svnroot/vxoware/autoplot/trunk/AutoplotServlet.  This shows how Autoplot can be used to create graphics on the server-side, when used with a J2EE container like Apache Tomcat.

# Check out Autoplot sources as described above.
# The location of a server's classpath must be specified.  Locate a J2EE instance, such as Tomcat.
# change directory to the location of the ant build script, build.xml.
# Use ant to compile the servlet, using (where the classpath is set to your server location):
  ant -Dj2ee.platform.classpath=/usr/local/tomcat/apache-tomcat-6.0.16/lib/servlet-api.jar dist
# The war file will be found in the "dist" folder.
