#!/bin/bash
#
# Purpose: create the volatile jar that goes with the stable jar that is .pack.gz.
# Stable is code we don't expect to change often,
# such as third-party libraries.  The other is code we do expect to change often.
#
# This copies all the sources into the temp directory, then compiles a few key sources, so
# that unreferenced routines are not used.  This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#
# CDF Support will be awkward because of the binaries.  Support this for the hudson platform.
#
# This should be run from the folder "Autoplot"
#
# Used by: ???
#

echo "Is this used anywhere?  Contact autoplot@groups.google.com."
exit(1)

# set JAVA_HOME 
if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.7.0_80/
fi

if [[ "$JAVA_HOME" != */ ]]; then    # check for no trailing slash
    JAVA_HOME=${JAVA_HOME}/
fi

if [ "" = "$TAG" ]; then
    if [ "" = "$AP_VERSION" ]; then
       TAG=untagged
    else
       TAG=$AP_VERSION
    fi
fi
echo "TAG=${TAG}"

JAVAC=${JAVA_HOME}bin/javac
JAR=${JAVA_HOME}bin/jar

if [ ! -f $JAVAC ]; then 
    echo "Java executable does not exist.  Set, for example, \"export JAVA_HOME=/usr/local/jdk1.7.0_80/\""
    exit -1
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
    set +x
    STOREPASS=dolphin
    set -x
fi

if [ "" = "$CODEBASE" ]; then
    CODEBASE=NEED_CODEBASE_TO_BE_DEFINED_IN_COMPILE_SCRIPT
fi

if [ "" = "$HUDSON_URL" ]; then
    HUDSON_URL="http://apps-pw.physics.uiowa.edu/hudson"
fi

if [ "" = "$WGET" ]; then
    WGET=wget
fi

if [ "" = "$RSYNC" ]; then
    RSYNC=rsync
fi

if [ "" = "$AWK" ]; then
    AWK=awk
fi

rm -r -f temp-volatile-src/
mkdir temp-volatile-src/
rm -r -f temp-volatile-classes/
mkdir temp-volatile-classes

echo "copy jar file classes..."
wget -O AutoplotStable.jar ${HUDSON_URL}/job/autoplot-jar-stable/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar
wget -O AutoplotStable.jar.pack.gz ${HUDSON_URL}/job/autoplot-jar-stable/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar.pack.gz
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
  CdfJavaDataSource CDAWebDataSource PDSPPIDataSource HapiDataSource \
  Autoplot; do
    echo ${RSYNC} -a --exclude .svn ../${i}/src/ temp-volatile-src/
    ${RSYNC} -a --exclude .svn ../${i}/src/ temp-volatile-src/
done
echo "done copy sources"

# special handling of the META-INF stuff.

echo "=== special handling of META-INF stuff..."
mkdir temp-volatile-classes/META-INF

file=org.autoplot.datasource.DataSourceFactory.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceFactory.mimeTypes
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceFormat.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceEditorPanel.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=helpsets.txt
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

echo -e "Main-Class: org.virbo.autoplot.AutoplotUI\nApplication-Name: Autoplot\nCodebase: http://autoplot.org/\n" > temp-volatile-src/MANIFEST.MF

# remove signatures
rm -f temp-volatile-classes/META-INF/*.RSA
rm -f temp-volatile-classes/META-INF/*.DSA
rm -f temp-volatile-classes/META-INF/*.SF

export TIMESTAMP=`date +%Y%m%d_%H%M%S`
cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-volatile-classes/META-INF/build.txt
cat temp-volatile-classes/META-INF/build.txt | sed "s/build.timestamp\:/build.timestamp\: $TIMESTAMP/" > temp-volatile-classes/META-INF/build.txt.1
mv  temp-volatile-classes/META-INF/build.txt.1  temp-volatile-classes/META-INF/build.txt

export TIMESTAMP=`date -u +%Y-%m-%dT%H:%MZ`
echo $TIMESTAMP > temp-volatile-classes/buildTime.txt

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "=== copy resources..."
cd temp-volatile-src
for i in $( find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.jyds' -o -name '*.xml' -o -name '*.xsl' -o -name '*.xsd' -o -name '*.CSV' -o -name '*.properties' -o -name '*.ttf' -o -name '*.otf' ); do
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
for i in $( find * -name 'pylisting*.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
for i in $( find * -name 'pylistingapp*.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done
for i in $( find * -name 'packagelist*.txt' ); do  
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done

mkdir -p ../temp-volatile-classes/orbits
for i in $( find orbits -type f ); do               # copy in orbits files
   cp $i ../temp-volatile-classes/$i
done

if [ -f  /home/jbf/project/autoplot/fonts/scheme_bk.otf ]; then
   cp /home/jbf/project/autoplot/fonts/scheme_bk.otf ../temp-volatile-classes/resources
   echo "scheme_bk.otf is a proprietary font which is not licensed for use outside of Autoplot." > ../temp-volatile-classes/resources/fonts.license.txt
fi

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
  NetCdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
  CdfJavaDataSource \
  Autoplot; do
    if [ -d ../${i}/javahelp/ ]; then
        echo rsync -av --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
        rsync -av --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
    fi
done

echo "done copy help files."

hasErrors=0

#JAVAARGS="-g -target 1.7 -source 1.7 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10"
JAVAARGS="-g -target 1.7 -source 1.7 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10"

# compile key java classes.
echo "=== compile sources..."
cd temp-volatile-src
echo $JAVAC $JAVAARGS org/autoplot/AutoplotUI.java
if ! $JAVAC $JAVAARGS org/autoplot/AutoplotUI.java; then echo "****"; hasErrors=1; fi
if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi
if ! $JAVAC $JAVAARGS org/autoplot/state/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/scriptconsole/DumpRteExceptionHandler.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/JythonMain.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/help/AutoplotHelpViewer.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/AutoplotServer.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/AutoplotDataServer.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/qds/util/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/pngwalk/PngWalkTool1.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/pngwalk/ImageResize.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/pngwalk/QualityControlPanel.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/beans/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/util/awt/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS test/endtoend/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/idlsupport/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/virbo/idlsupport/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/system/NullPreferencesFactory.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/tca/UriTcaSource.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/qds/NearestNeighborTcaFunction.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/ScreenshotsTool.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/wgetfs/WGetFileSystemFactory.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/fsm/FileStorageModelNew.java; then echo "*****"; hasErrors=1; fi  # some scripts use this old name.
if ! $JAVAC $JAVAARGS org/das2/math/filter/*.java; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/components/DataPointRecorderNew.java; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/components/AngleSpectrogramSlicer.java; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/graph/Auralizor.java; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/graph/CurveRenderer.java' ; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/qstream/*.java; then echo "*****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/qstream/filter/*.java; then echo "*****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/datum/SIUnits.java; then echo "****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/qds/RepeatIndexDataSet.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/jythonsupport/ui/DataMashUp.java; then echo "****"; hasErrors=1; fi  
if ! $JAVAC $JAVAARGS org/das2/util/*Formatter.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/util/jemmy/*.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/das2/qds/filters/*.java; then echo "****"; hasErrors=1; fi

cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC $JAVAARGS $i.java
   if ! $JAVAC $JAVAARGS $i.java; then echo "****"; hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC $JAVAARGS $i.java
   if ! $JAVAC $JAVAARGS $i.java; then echo "****"; hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVAC $JAVAARGS $i.java
   if ! $JAVAC $JAVAARGS $i.java; then echo "****"; hasErrors=1; fi
done

# NetCDF IOServiceProvider allows Autoplot URIs to be used in ncml files.
echo "compile AbstractIOSP and APIOServiceProvider"
if ! $JAVAC $JAVAARGS org/autoplot/netCDF/AbstractIOSP.java; then echo "****"; hasErrors=1; fi
if ! $JAVAC $JAVAARGS org/autoplot/netCDF/APIOServiceProvider.java; then echo "****"; hasErrors=1; fi

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
${JAVA_HOME}bin/javac -target 1.7 -source 1.7 -d ../temp-volatile-classes external/FileSearchReplace.java
cd ..
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar_pack200.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL
${JAVA_HOME}bin/java -cp temp-volatile-classes external.FileSearchReplace dist/pngwalk_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url}' $HUDSON_URL

mv AutoplotStable.jar.pack.gz dist/
mv AutoplotStable.jar dist/

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pack.gz."
cp src/htaccess.txt dist/
