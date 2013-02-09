#!/bin/bash

# this copies all the sources into the temp directory, then compiles a few key sources, so
# that unreferenced routines are not used.  This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#
# CDF Support will be awkward because of the binaries.  Support this for the hudson platform.

# You should defined JAVA_HOME before running this script.
# If its not defined, there is a small attempt made to use something.
#
# On a PC or Linux, JAVA_HOME will probably be set, and no changes will be needed.
# On a Mac, you might have to set JAVA_HOME manually.
# On OS 10.5.8, this worked:
#  JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home

if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.5.0_15__32/
fi

echo "\${AP_VERSION}=${AP_VERSION}"

JAVAC=$JAVA_HOME/bin/javac
JAR=$JAVA_HOME/bin/jar

if [ \! -f $JAVAC ]; then
   echo ""
   echo "Can't find javac in JAVA_HOME=$JAVA_HOME"
   echo ""
   exit -1
fi

rm -r -f temp-src/
mkdir temp-src/
rm -r -f temp-classes/
mkdir temp-classes

echo "copy jar file classes..."
cd temp-classes
ls ../../APLibs/lib/*.jar
for i in ../../APLibs/lib/*.jar; do
   echo jar xf $i
   jar xf $i
done

# use beta version of cdf library that supports tt2000.
echo "using tt2000 support"
rm -rf gsfc/
jar xf ../../APLibs/lib/cdfjava.3.3.2.tt2000.jar

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo jar xf $i
   jar xf $i
done

for i in ../../APLibs/lib/commons/*.jar; do
   echo jar xf $i
   jar xf $i
done

cd ..
echo "done copy jar file classes."

echo "copy sources..."
for i in \
  dasCore dasCoreUtil dasCoreDatum \
  QDataSet QStream  DataSource \
  JythonSupport \
  AutoplotHelp \
  IdlMatlabSupport \
  AudioSystemDataSource \
  BinaryDataSource DataSourcePack JythonDataSource \
  Das2ServerDataSource TsdsDataSource  \
  NetCdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
  CdfDataSource CdfJavaDataSource CDAWebDataSource \
  VirboAutoplot; do
    echo rsync -a --exclude .svn ../${i}/src/ temp-src/
    rsync -a --exclude .svn ../${i}/src/ temp-src/
done
echo "done copy sources"

# special handling of the META-INF stuff.

echo "special handling of META-INF stuff..."

#TODO: check for end-of-lines on each entry.

file=org.virbo.datasource.DataSourceFactory.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.virbo.datasource.DataSourceFactory.mimeTypes
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.virbo.datasource.DataSourceFormat.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.virbo.datasource.DataSourceEditorPanel.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=helpsets.txt
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

echo "Main-Class: org.virbo.autoplot.AutoplotUI" > temp-src/MANIFEST.MF

# remove signatures
rm temp-classes/META-INF/*.RSA
rm temp-classes/META-INF/*.DSA
rm temp-classes/META-INF/*.SF

cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-classes/META-INF/build.txt

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "copy resources..."
cd temp-src
for i in $(find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.xsl' -o -name '*.xsd' -o -name '*.CSV' ); do
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done
for i in $( find * -name 'filenames_alt*.txt' ); do   # kludge support for CDAWeb, where *.txt is too inclusive
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done
for i in $( find * -name 'CDFLeapSeconds.txt' ); do   # support for CDF TT2000
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done

cd ..
echo "done copy resources."

echo "copy help files..."
for i in \
  dasCore dasCoreUtil dasCoreDatum \
  QDataSet QStream DataSource \
  JythonSupport \
  AutoplotHelp \
  IdlMatlabSupport \
  AudioSystemDataSource \
  BinaryDataSource DataSourcePack JythonDataSource \
  Das2ServerDataSource TsdsDataSource  \
  NetCdfDataSource CdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
  VirboAutoplot; do
    if [ -d ../${i}/javahelp/ ]; then
        echo rsync -av --exclude .svn ../${i}/javahelp/ temp-classes/
        rsync -av --exclude .svn ../${i}/javahelp/ temp-classes/
    fi
done

echo "done copy help files."

hasErrors=0

# compile key java classes.
echo "compile sources..."
cd temp-src
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotUI.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/JythonMain.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/help/AutoplotHelpViewer.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotServer.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotDataServer.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/dsutil/*.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/pngwalk/PngWalkTool1.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/pngwalk/ImageResize.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/pngwalk/QualityControlPanel.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/util/awt/*.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 test/endtoend/*.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/idlsupport/*.java; then hasErrors=1; fi
#if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 test/util/filesystem/*.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/system/NullPreferencesFactory.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/ScreenshotsTool; then hasErrors=1; fi

cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java
   if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java
   if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java
   if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done

# NetCDF IOServiceProvider allows Autoplot URIs to be used in ncml files.
echo "compile AbstractIOSP and APIOServiceProvider"
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/netCDF/AbstractIOSP.java; then hasErrors=1; fi
if ! $JAVAC -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/netCDF/APIOServiceProvider.java; then hasErrors=1; fi

cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "setting version to \${AP_VERSION}=${AP_VERSION} in build.tag"
cat temp-classes/META-INF/build.txt | sed "s/build.tag:/build.tag: ${AP_VERSION}/g" > temp-classes/META-INF/build.txt.1
mv temp-classes/META-INF/build.txt.1 temp-classes/META-INF/build.txt

echo "make jumbo jar file..."
cd temp-classes
mkdir -p ../dist/
$JAR cmf ../temp-src/MANIFEST.MF ../dist/AutoplotAll.jar *
cd ..

echo "done make jumbo jar file..."

