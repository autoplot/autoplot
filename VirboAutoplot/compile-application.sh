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
#
# Used by: autoplot-jar-all on http://jfaden.net:8080/hudson.  This is used for testing.
#

if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.7/
fi

JAVAARGS="-g -target 1.7 -source 1.7 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10"

echo "\${AP_VERSION}=${AP_VERSION}"

if [ "" = "$TAG" ]; then
    if [ "" = "$AP_VERSION" ]; then
       TAG=untagged
    else
       TAG=$AP_VERSION
    fi
fi
echo "TAG=${TAG}"

JAVAC=$JAVA_HOME/bin/javac
JAR=$JAVA_HOME/bin/jar

if [ ! -f $JAVAC ]; then 
    echo "Java executable does not exist.  Set for example \"export JAVA_HOME=/usr/local/jdk1.7.0_80/\""
    exit -1
fi

rm -r -f temp-src/
mkdir temp-src/
rm -r -f temp-classes/
mkdir temp-classes

hasErrors=0

function raiseerror {
   echo '*****'
   echo '*****'
   echo '*****'
   hasErrors=1
}

function compilef {
   echo $JAVAC $JAVAARGS $1
   if ! $JAVAC $JAVAARGS $1; then raiseerror; fi
}

echo "copy jar file classes..."
cd temp-classes
ls ../../APLibs/lib/*.jar
for i in ../../APLibs/lib/*.jar; do
   echo jar xf $i
   jar xf $i
done

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo jar xf $i
   jar xf $i
done

for i in ../../APLibs/lib/commons/*.jar; do
   echo jar xf $i
   jar xf $i
done

if [ "" = "$RSYNC" ]; then
    echo "using default rsync, assuming it is on the path"
    RSYNC=rsync
else
    echo "RSYNC="$RSYNC
fi

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
  CdfJavaDataSource CDAWebDataSource PDSPPIDataSource HapiDataSource \
  VirboAutoplot; do
    echo $RSYNC -a --exclude .svn ../${i}/src/ temp-src/
    $RSYNC -a --exclude .svn ../${i}/src/ temp-src/
done
echo "done copy sources"

# special handling of the META-INF stuff.

echo "special handling of META-INF stuff..."

#TODO: check for end-of-lines on each entry.

file=org.autoplot.datasource.DataSourceFactory.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceFactory.mimeTypes
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceFormat.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=org.autoplot.datasource.DataSourceEditorPanel.extensions
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

file=helpsets.txt
sed -n p ../*/src/META-INF/$file > temp-classes/META-INF/$file

echo "Main-Class: org.autoplot.AutoplotUI" > temp-src/MANIFEST.MF

# remove signatures
rm temp-classes/META-INF/*.RSA
rm temp-classes/META-INF/*.DSA
rm temp-classes/META-INF/*.SF

cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-classes/META-INF/build.txt
echo "build.jenkinsURL: $BUILD_URL" >> temp-classes/META-INF/build.txt

export TIMESTAMP=`date -u +%Y-%m-%dT%H:%MZ`
echo $TIMESTAMP > temp-classes/buildTime.txt

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "copy resources..."
cd temp-src
for i in $(find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.jyds' -o -name '*.xml' -o -name '*.xsl' -o -name '*.xsd' -o -name '*.CSV' -o -name '*.properties' ); do
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
for i in $( find * -name 'pylisting*.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done
for i in $( find * -name 'pylistingapp*.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done
for i in $( find * -name 'packagelist*.txt' ); do  
   mkdir -p $(dirname ../temp-classes/$i)
   cp $i ../temp-classes/$i
done

mkdir -p ../temp-classes/orbits
for i in $( find orbits -type f ); do               # copy in orbits files
   cp $i ../temp-classes/$i
done

if [ -f  /home/jbf/project/autoplot/fonts/scheme_bk.otf ]; then
   cp /home/jbf/project/autoplot/fonts/scheme_bk.otf ../temp-volatile-classes/resources
   echo "scheme_bk.otf is a proprietary font which is not licensed for use outside of Autoplot." > ../temp-volatile-classes/resources/fonts.license.txt
fi

cd ..
echo "pwd=" `pwd`
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
  VirboAutoplot; do
    if [ -d ../${i}/javahelp/ ]; then
        echo ${RSYNC} -av --exclude .svn ../${i}/javahelp/ temp-classes/
        ${RSYNC} -av --exclude .svn ../${i}/javahelp/ temp-classes/
    fi
done

echo "done copy help files."

# compile key java classes.
echo "compile sources..."
cd temp-src
echo $JAVAC $JAVAARGS org/autoplot/AutoplotUI.java
compilef 'org/autoplot/AutoplotUI.java'
if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi
echo "only the first compile is echoed."
compilef 'org/autoplot/state/*.java'
compilef 'org/autoplot/scriptconsole/DumpRteExceptionHandler.java'
compilef 'org/autoplot/JythonMain.java'
compilef 'org/autoplot/help/AutoplotHelpViewer.java'
compilef 'org/autoplot/AutoplotServer.java'
compilef 'org/autoplot/AutoplotDataServer.java'
compilef 'org/das2/qds/util/*.java'
compilef 'org/autoplot/pngwalk/PngWalkTool1.java'
compilef 'org/autoplot/pngwalk/ImageResize.java'
compilef 'org/autoplot/pngwalk/QualityControlPanel.java'
compilef 'org/das2/beans/*.java'
compilef 'org/das2/util/awt/*.java'
compilef 'org/das2/util/ExceptionHandler.java'
compilef 'test/endtoend/*.java'
compilef 'org/autoplot/idlsupport/*.java'
compilef 'org/virbo/idlsupport/*.java'
compilef 'org/das2/system/NullPreferencesFactory.java'
compilef 'org/autoplot/tca/UriTcaSource.java'
compilef 'org/das2/qds/NearestNeighborTcaFunction.java'
compilef 'org/das2/qstream/filter/*.java' 
compilef 'org/das2/event/*.java'
compilef 'org/das2/dataset/NoDataInIntervalException.java'
compilef 'org/autoplot/ScreenshotsTool.java'
compilef 'org/autoplot/wgetfs/WGetFileSystemFactory.java'
compilef 'org/das2/fsm/FileStorageModelNew.java' # some scripts use this old name.
compilef 'org/das2/math/filter/*.java' 
compilef 'org/das2/components/DataPointRecorderNew.java' 
compilef 'org/das2/components/AngleSpectrogramSlicer.java' 
compilef 'org/das2/graph/Auralizor.java' 
compilef 'org/das2/datum/Ratio.java'
compilef 'org/das2/datum/RationalNumber.java'
compilef 'org/das2/datum/SIUnits.java'
compilef 'org/das2/qds/RepeatIndexDataSet.java'
compilef 'org/autoplot/jythonsupport/ui/DataMashUp.java'  
compilef 'org/das2/util/*Formatter.java'
compilef 'org/autoplot/util/jemmy/*.java'
compilef 'org/das2/qds/filters/*.java'

cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done
cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done
cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done

# NetCDF IOServiceProvider allows Autoplot URIs to be used in ncml files.
echo "compile AbstractIOSP and APIOServiceProvider"
compilef org/autoplot/netCDF/AbstractIOSP.java
compilef org/autoplot/netCDF/APIOServiceProvider.java

cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi


if [ "$justCompile" = "1" ]; then
  echo "justCompile set to 1, stopping"
  exit 0
else
  echo "justCompile not set to 1, continue on"
fi


#Don't do this, since we modify the jnlp file to make a release.
#echo "=== make signed jnlp file..."  # http://www.coderanch.com/t/554729/JNLP-Web-Start/java/Signing-JNLP-JNLP-INF-directory
#mkdir temp-volatile-classes/JNLP-INF
#cp dist/autoplot.jnlp temp-volatile-classes/JNLP-INF/APPLICATION.JNLP


echo "=== make jumbo jar files..."
mkdir -p dist/
cd temp-volatile-classes
echo " ==manifest=="
cat ../temp-volatile-src/MANIFEST.MF
echo " ==manifest=="
${JAVA_HOME}/bin/jar cmf ../temp-volatile-src/MANIFEST.MF ../dist/AutoplotVolatile.jar *
cd ..

echo "done make jumbo jar files..."

# See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5078608 "Digital signatures are invalid after unpacking"
# See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6575373 "Error verifying signatures of pack200 files in some cases"
# See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6351684 "pack200 doesn't work on/corrupts obfuscated files"
echo "=== normalize jar file before signing..."
${JAVA_HOME}/bin/pack200 --repack dist/AutoplotVolatile1.jar dist/AutoplotVolatile.jar
${JAVA_HOME}/bin/pack200 --repack dist/AutoplotVolatile2.jar dist/AutoplotVolatile1.jar # http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6575373  Note this doesn't appear to have an effect.
mv dist/AutoplotVolatile2.jar dist/AutoplotVolatile.jar
rm dist/AutoplotVolatile1.jar

echo "=== sign and pack the jar file..."
echo "  use set +x to hide private info"
#echo  ${JAVA_HOME}/bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS $JARSIGNER_OPTS dist/AutoplotVolatile.jar "$ALIAS"
set +x
if ! ${JAVA_HOME}/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS" $JARSIGNER_OPTS dist/AutoplotVolatile.jar "$ALIAS"; then
   echo "Fail to sign resources!"
   exit 1
fi
set -x

echo "=== verify the jar file..."
${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile.jar | head -10

echo "=== sign and pack the jar file..."
${JAVA_HOME}/bin/pack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile.jar
${JAVA_HOME}/bin/unpack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile_pack_gz.jar

if ! ${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile.jar | head -10; then
   echo "jarsigner verify failed on file dist/AutoplotVolatile.jar!"
   exit 1
fi

echo "=== verify signed and unpacked jar file..."
if ! ${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile_pack_gz.jar | head -10; then
   echo "jarsigner verify  failed on pack_gz file dist/AutoplotVolatile_pack_gz.jar!"
   exit 1
fi
rm dist/AutoplotVolatile_pack_gz.jar

echo "=== create jnlp file for build..."
cp src/autoplot.jnlp dist

echo "=== copy branding for release, such as png icon images"
cp src/*.png dist
cp src/*.gif dist  # mac Java7 has a bug where it can't use pngs for icons, use .gif instead.
cp src/index.html dist  #TODO: why?

echo "=== modify jar files for this particular release"
cd temp-volatile-src
compilef ../temp-volatile-classes external/FileSearchReplace.java
cd ..
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/index.html '#{tag}' $TAG '#{codebase}' $CODEBASE

# if these are needed.
# These are needed for the single-jar build.
#if [ $AP_KEEP_STABLE = 'T' ]; then
mv AutoplotStable.jar.pack.gz dist/
mv AutoplotStable.jar dist/
#else
#  rm AutoplotStable.jar.pack.gz
#  rm AutoplotStable.jar
#fi

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pack.gz."
cp src/htaccess.txt dist/

# remove this proprietary font so that it isn't accidentally released.
rm -f temp-volatile-classes/resources/scheme_bk.otf

