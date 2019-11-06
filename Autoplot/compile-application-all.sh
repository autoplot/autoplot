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
# Used by: autoplot-jar-all on http://jfaden.net/jenkins.  This is used for testing.
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
  Das2CatDataSource \
  NetCdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
  CdfJavaDataSource CDAWebDataSource PDSPPIDataSource HapiDataSource \
  Autoplot; do
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
  Autoplot; do
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
compilef 'org/virbo/autoplot/*.java'  # support old launchers.
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
compilef 'org/das2/graph/CurveRenderer.java'  
compilef 'org/das2/graph/RangeLabel.java'  
compilef 'org/das2/graph/LookupAxis.java'  
compilef 'org/das2/graph/CollapseSpectrogramRenderer.java'  
compilef 'org/das2/datum/Ratio.java'
compilef 'org/das2/datum/RationalNumber.java'
compilef 'org/das2/datum/SIUnits.java'
compilef 'org/das2/qds/RepeatIndexDataSet.java'
compilef 'org/autoplot/jythonsupport/ui/DataMashUp.java'  
compilef 'org/das2/util/*Formatter.java'
compilef 'org/autoplot/util/jemmy/*.java'
compilef 'org/das2/qds/filters/*.java'
compilef 'org/das2/qds/demos/PlasmaModel.java'
compilef 'org/virbo/autoplot/*.java' # why is this repeated?
compilef 'test/Unicode.java'
compilef 'org/das2/util/Expect.java'

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

echo "setting version to \${AP_VERSION}=${AP_VERSION} in build.tag"
cat temp-classes/META-INF/build.txt | sed "s/build.tag:/build.tag: ${AP_VERSION}/g" > temp-classes/META-INF/build.txt.1
mv temp-classes/META-INF/build.txt.1 temp-classes/META-INF/build.txt

echo "make jumbo jar file..."
cd temp-classes
mkdir -p ../dist/
rm -f ../temp-src/MANIFEST.MF   # remove leftover signatures.
echo "Main-Class: org.autoplot.AutoplotUI" > ../temp-src/MANIFEST.MF
$JAR cmf ../temp-src/MANIFEST.MF ../dist/AutoplotAll.jar *
cd ..

echo "done make jumbo jar file..."

