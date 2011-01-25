To be able to develop on and run Autoplot using Eclipse:

1. collect all filename extensions into one place

In the Netbeans project, the contents of these files
get concatenated automatically:
org.virbo.datasource.DataSourceFactory.extensions
These are spread throughout the META-INF directories of various source folders.
To find them, use something like this:

cd <path/to/workspaces>/autoplot
cat `find . -name "*DataSourceFactory.extensions" | grep -v '/classes'`

Take all of the lines (class names and filename extensions and put them in the
first source path that Elipse identifies (currently this is the one in the
AudioSystemDataSource/src/META-INF directory.


2. To launch the main Autoplot program run the main in this class:

org.virbo.autoplot.AutoplotUI

(You can easily find this using Shift-ALT-T, or Shift-Apple-T on a Mac)



