#!/bin/bash
#
# Purpose: create the volatile jar that goes with the stable jar that is .pack.gz.
# Stable is code we don't expect to change often,
# such as third-party libraries.  The other is code we do expect to change often.

# this copies all the sources into the temp directory, then compiles a few key sources, so
# that unreferenced routines are not used.  This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#
# CDF Support will be awkward because of the binaries.  Support this for the hudson platform.
#
# This should be run from the folder "VirboAutoplot"

# set JAVA_HOME and JAVA6_HOME
if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=$JAVA_HOME
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jdk1.6.0_35/
fi

if [ "" = "$TAG" ]; then
    if [ "" = "$AP_VERSION" ]; then
       TAG=untagged
    else
       TAG=$AP_VERSION
    fi
fi

if [ "" = "$KEYPASS" ]; then
    echo "KEYPASS NEEDED!"
    KEYPASS=virbo1
fi

if [ "" = "$STOREPASS" ]; then
    echo "STOREPASS NEEDED!"
    STOREPASS=dolphin
fi

if [ "" = "$CODEBASE" ]; then
    CODEBASE=NEED_CODEBASE_TO_BE_DEFINED_IN_COMPILE_SCRIPT
fi

if [ "" = "$HUDSON_URL" ]; then
    HUDSON_URL="http://papco.org:8080/hudson"
fi

rm -r -f temp-volatile-src/
mkdir temp-volatile-src/
rm -r -f temp-volatile-classes/
mkdir temp-volatile-classes

echo "copy jar file classes..."
wget -O AutoplotStable.jar ${HUDSON_URL}/job/autoplot-jar-stable/lastSuccessfulBuild/artifact/autoplot/VirboAutoplot/dist/AutoplotStable.jar
wget -O AutoplotStable.jar.pack.gz ${HUDSON_URL}/job/autoplot-jar-stable/lastSuccessfulBuild/artifact/autoplot/VirboAutoplot/dist/AutoplotStable.jar.pack.gz
echo "done copy jar file classes."

echo "copy sources..."
for i in \
  dasCore dasCoreUtil dasCoreDatum \
  QDataSet QStream DataSource \
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
    echo rsync -a --exclude .svn ../${i}/src/ temp-volatile-src/
    rsync -a --exclude .svn ../${i}/src/ temp-volatile-src/
done
echo "done copy sources"

# special handling of the META-INF stuff.

echo "=== special handling of META-INF stuff..."
mkdir temp-volatile-classes/META-INF

file=org.virbo.datasource.DataSourceFactory.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.virbo.datasource.DataSourceFactory.mimeTypes
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.virbo.datasource.DataSourceFormat.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.virbo.datasource.DataSourceEditorPanel.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=helpsets.txt
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

echo "Main-Class: org.virbo.autoplot.AutoplotUI" > temp-volatile-src/MANIFEST.MF

# remove signatures
rm -f temp-volatile-classes/META-INF/*.RSA
rm -f temp-volatile-classes/META-INF/*.DSA
rm -f temp-volatile-classes/META-INF/*.SF

export TIMESTAMP=`date +%Y%m%d_%H%M%S`
cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-volatile-classes/META-INF/build.txt
cat temp-volatile-classes/META-INF/build.txt | sed "s/build.timestamp\:/build.timestamp\: $TIMESTAMP/" > temp-volatile-classes/META-INF/build.txt.1
mv  temp-volatile-classes/META-INF/build.txt.1  temp-volatile-classes/META-INF/build.txt

export TIMESTAMP=`date --utc +%Y-%m-%dT%H:%MZ`
echo $TIMESTAMP > temp-volatile-classes/buildTime.txt

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "=== copy resources..."
cd temp-volatile-src
for i in $( find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.xsl' -o -name '*.xsd' -o -name '*.CSV' ); do
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
for i in $( find * -name 'filenames_alt*.txt' ); do   # kludge support for CDAWeb, where *.txt is too inclusive
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
for i in $( find * -name 'CDFLeapSeconds.txt' ); do   # support for CDF TT2000
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
for i in $( find * -name 'pylisting.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done

cd ..
echo "done copy resources."

echo "=== copy help files..."
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
  CdfJavaDataSource \
  VirboAutoplot; do
    if [ -d ../${i}/javahelp/ ]; then
        echo rsync -av --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
        rsync -av --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
    fi
done

echo "done copy help files."

hasErrors=0

# compile key java classes.
echo "=== compile sources..."
cd temp-volatile-src
echo `pwd`
echo ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotUI.java
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotUI.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/JythonMain.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/help/AutoplotHelpViewer.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotServer.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotDataServer.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/PngWalkTool1.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/ImageResize.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/QualityControlPanel.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/util/awt/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 test/endtoend/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/idlsupport/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/system/NullPreferencesFactory.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/tca/UriTcaSource.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/qstream/filter/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/event/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/dataset/NoDataInIntervalException.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/qstream/filter/*.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/ScreenshotsTool.java; then hasErrors=1; fi

cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done

# NetCDF IOServiceProvider allows Autoplot URIs to be used in ncml files.
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:.. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/netCDF/AbstractIOSP.java; then hasErrors=1; fi
if ! ${JAVA_HOME}bin/javac -target 1.6 -cp ../temp-volatile-classes:../AutoplotStable.jar:.. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/netCDF/APIOServiceProvider.java; then hasErrors=1; fi

cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "=== make jumbo jar files..."
mkdir -p dist/
cd temp-volatile-classes
${JAVA_HOME}bin/jar cmf ../temp-volatile-src/MANIFEST.MF ../dist/AutoplotVolatile.jar *
cd ..

echo "done make jumbo jar files..."

echo "=== normalize jar file before signing..."
${JAVA_HOME}bin/pack200 --repack dist/AutoplotVolatile.jar
echo "sign and pack the jar file..."
echo ${JAVA_HOME}bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS  dist/AutoplotVolatile.jar virbo
${JAVA_HOME}bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS  dist/AutoplotVolatile.jar virbo
${JAVA_HOME}bin/pack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile.jar

echo "=== create jnlp file for build..."
cp src/autoplot_two_jar.jnlp dist
cp src/autoplot_two_jar_pack200.jnlp dist
cp src/pngwalk_two_jar.jnlp dist

echo "=== copy branding for release, such as png icon images"
cp src/*.png dist

echo "=== modify jar files for this particular release"
cd temp-volatile-src
${JAVA_HOME}bin/javac -target 1.6 -d ../temp-volatile-classes external/FileSearchReplace.java
cd ..
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar_pack200.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/pngwalk_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL

#echo "proguard/pack200 stuff..."
#${JAVA_HOME}bin/pack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile.jar
#echo "done proguard/pack200 stuff."

mv AutoplotStable.jar.pack.gz dist/
mv AutoplotStable.jar dist/

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pack.gz."
cp src/htaccess.txt dist/
