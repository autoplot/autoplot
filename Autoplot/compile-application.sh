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
# This should be run from the folder "Autoplot"
#
# Used by: autoplot-release on Jenkins.
#

# set JAVA_HOME
if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.8/
fi

if [[ $JAVA_HOME != */ ]]; then
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

if [ "" = "$DO_HIDE" ]; then
    DO_HIDE="true"
fi

echo "JAVA_HOME=${JAVA_HOME}"
echo "DO_HIDE=${DO_HIDE}  # if false then show passwords etc for debugging."

JAVAC=${JAVA_HOME}/bin/javac
JAR=${JAVA_HOME}/bin/jar

if [ ! -f $JAVAC ]; then 
    echo "Java executable does not exist.  Set, for example, \"export JAVA_HOME=/usr/local/jdk1.7.0_80/\""
    exit -1
fi

# we rsync over stable jars to compile against.  Setting AP_KEEP_STABLE=T means keep the Jar files.
if [ "" = "$AP_KEEP_STABLE" ]; then
    AP_KEEP_STABLE=F
fi

echo "AP_KEEP_STABLE=${AP_KEEP_STABLE}  # if T then keep the stable jars for release"

if [ "" = "$ALIAS" ]; then
    ALIAS=virbo
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

hasErrors=0

function raiseerror {
   echo '*****'
   echo '*****'
   echo '*****'
   hasErrors=1
}

#JAVAARGS="-g -target 1.7 -source 1.7 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10"
#JAVAARGS="-g -target 1.7 -source 1.7 -Xlint:unchecked -bootclasspath $JAVA_HOME/jre/lib/rt.jar -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10"
JAVAARGS="-g -target 1.8 -source 1.8 -bootclasspath $JAVA_HOME/jre/lib/rt.jar -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10"

export timer=`date +%s`

allcodes='org/autoplot/AutoplotUI.java'
function compilef {
   #timer1=`date +%s`
   #dt=`expr $(( timer1 - timer ))`
   #echo "DATE: $timer1 $1"
   #echo "ELAPSED TIME (SEC): $dt"
   #echo $JAVAC $JAVAARGS $1
   #if ! $JAVAC $JAVAARGS $1; then raiseerror; fi
   allcodes="$allcodes $1"
}

function compilef-go {
   echo "# compilef-go in $PWD"
   echo $JAVAC $JAVAARGS $allcodes
   if ! $JAVAC $JAVAARGS $allcodes; then raiseerror; fi
}

if [ "" = "$CODEBASE" ]; then
    CODEBASE=NEED_CODEBASE_TO_BE_DEFINED_IN_COMPILE_SCRIPT
fi

if [ "" = "$HUDSON_URL" ]; then
    HUDSON_URL="https://cottagesystems.com/jenkins/"
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

echo "CODEBASE="
echo $CODEBASE

rm -r -f temp-volatile-src/
mkdir temp-volatile-src/
rm -r -f temp-volatile-classes/
mkdir temp-volatile-classes

echo "pwd=" `pwd`
if [ "" = "$AUTOPLOT_STABLE_DIR" ]; then 
   echo "copy jar file classes using wget -q..."
   echo ${WGET} --no-check-certificate -q -O AutoplotStable.jar ${HUDSON_URL}/job/autoplot-jar-stable-2017/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar 
   ${WGET} --no-check-certificate -q -O AutoplotStable.jar ${HUDSON_URL}/job/autoplot-jar-stable-2017/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar # 2>&1 | head -100
   echo ${WGET} --no-check-certificate -q -O AutoplotStable.jar.pack.gz ${HUDSON_URL}/job/autoplot-jar-stable-2017/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar.pack.gz 
   ${WGET} --no-check-certificate -q -O AutoplotStable.jar.pack.gz ${HUDSON_URL}/job/autoplot-jar-stable-2017/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar.pack.gz # 2>&1 | head -100
   if [ $? -ne 0 ]; then
      echo "wget fails: $WGET wget --no-check-certificate -q -O AutoplotStable.jar ${HUDSON_URL}/job/autoplot-jar-stable-2017/lastSuccessfulBuild/artifact/autoplot/Autoplot/dist/AutoplotStable.jar"
      exit -1
   fi
else
   echo "copy jar file classes using cp..."
   echo cp ${AUTOPLOT_STABLE_DIR}/AutoplotStable.jar .
   cp ${AUTOPLOT_STABLE_DIR}/AutoplotStable.jar .
   echo cp ${AUTOPLOT_STABLE_DIR}/AutoplotStable.jar.pack.gz  .
   cp ${AUTOPLOT_STABLE_DIR}/AutoplotStable.jar.pack.gz  .
fi

echo "done copy jar file classes."

echo "=== look for plugins, META-INF/org.autoplot.datasource.DataSourceFactory.extensions etc =="
#echo 'ls -1 ../*/src/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | awk  \'BEGIN { FS = "/" } ; { print $2 }\' | sort | uniq | xargs'
plugins=`ls -1 ../*/src/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | $AWK  'BEGIN { FS = "/" } ; { print $2 }' | sort | uniq | xargs`
echo $plugins

echo "copy sources..."
for i in \
  das2java/dasCore das2java/dasCoreUtil das2java/dasCoreDatum \
  das2java/QDataSet das2java/QStream \
  DataSource \
  JythonSupport \
  AutoplotHelp \
  IdlMatlabSupport \
  $plugins \
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

file=org.autoplot.datasource.DataSourceFormatEditorPanel.extensions
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

file=helpsets.txt
sed -n p ../*/src/META-INF/$file > temp-volatile-classes/META-INF/$file

if [ "" == "$AUTOPLOT_NO_JNLP_TEMPLATE" ]; then   
   cp -r temp-volatile-src/JNLP-INF temp-volatile-classes/JNLP-INF/
else 
   echo "Not copying JNLP-INF because AUTOPLOT_NO_JNLP_TEMPLATE is set"
   rm -rf temp-volatile-classes/JNLP-INF/
fi

printf "Main-Class: org.autoplot.AutoplotUI\nPermissions: all-permissions\nCodebase: autoplot.org *.physics.uiowa.edu jfaden.net\nApplication-Name: Autoplot\n" > temp-volatile-src/MANIFEST.MF

# remove signatures
rm -f temp-volatile-classes/META-INF/*.RSA
rm -f temp-volatile-classes/META-INF/*.DSA
rm -f temp-volatile-classes/META-INF/*.SF

export TIMESTAMP=`date +%Y%m%d_%H%M%S`
cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-volatile-classes/META-INF/build.txt
echo "build.jenkinsURL: $BUILD_URL" >> temp-volatile-classes/META-INF/build.txt

export TIMESTAMP=`date -u +%Y-%m-%dT%H:%MZ`
echo $TIMESTAMP > temp-volatile-classes/buildTime.txt

cat temp-volatile-classes/META-INF/build.txt | sed "s/build.timestamp\:/build.timestamp\: $TIMESTAMP/" > temp-volatile-classes/META-INF/build.txt.1
mv  temp-volatile-classes/META-INF/build.txt.1  temp-volatile-classes/META-INF/build.txt

cat temp-volatile-classes/META-INF/build.txt | sed "s/build.user.name\:/build.user.name\: $USER/" > temp-volatile-classes/META-INF/build.txt.1
mv  temp-volatile-classes/META-INF/build.txt.1  temp-volatile-classes/META-INF/build.txt

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "=== copy resources..."
cd temp-volatile-src
for i in $( find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.jyds' -o -name '*.xml' -o -name '*.xsl' -o -name '*.xsd' -o -name '*.CSV' -o -name '*.properties' -o -name '*.ttf' -o -name '*.otf' -o -name '*.json' ); do
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
for i in $( find * -name 'pylistingapp.txt' ); do   # support for python on LANL where listing of autoplot.org cannot be done.
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
for i in $( find * -name 'packagelist.txt' ); do   # TODO: this is madness...  Need to figure out how to get any .txt...  build.txt is the problem...
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done

mkdir -p ../temp-volatile-classes/orbits
for i in $( find orbits -type f ); do               # copy in orbits files
   cp $i ../temp-volatile-classes/$i
done

echo "copy scheme_bk font from /home/jbf/project/autoplot..."
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
  DataSourcePack \
  $plugins \
  Autoplot; do
    if [ -d ../${i}/javahelp/ ]; then
        echo ${RSYNC} --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
        ${RSYNC} --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
    fi
done

echo "done copy help files."

hasErrors=0

# compile key java classes.  These contain references that cause all needed Java classes to be compiled in.
echo "=== compile sources..."
cd temp-volatile-src
echo "pwd=" `pwd`
t0=`date +%s`

echo $JAVAC $JAVAARGS org/autoplot/AutoplotUI.java
if ! $JAVAC $JAVAARGS org/autoplot/AutoplotUI.java; then echo "\n\n****\n\n"; hasErrors=1; fi
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
compilef 'org/das2/fsm/FileStorageModelNew.java'  # some scripts use this old name.
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
compilef 'org/virbo/autoplot/*.java'
compilef 'test/Unicode.java'
compilef 'org/das2/util/Expect.java'
compilef 'external/AuralizationHandler.java'
compilef 'org/das2/util/filesystem/GitCommand.java'
compilef 'org/autoplot/scriptconsole/ScriptPanelSupport.java'

cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done
cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done
cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done
cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFormatEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.autoplot.datasource.DataSourceFormatEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   compilef $i.java
done

## NetCDF IOServiceProvider allows Autoplot URIs to be used in ncml files.
#echo "compile AbstractIOSP and APIOServiceProvider"
#compilef org/autoplot/netCDF/AbstractIOSP.java
#compilef org/autoplot/netCDF/APIOServiceProvider.java

compilef-go

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

## See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5078608 "Digital signatures are invalid after unpacking"
## See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6575373 "Error verifying signatures of pack200 files in some cases"
## See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6351684 "pack200 doesn't work on/corrupts obfuscated files"
#echo "=== normalize jar file before signing..."
#${JAVA_HOME}/bin/pack200 --repack dist/AutoplotVolatile1.jar dist/AutoplotVolatile.jar
#${JAVA_HOME}/bin/pack200 --repack dist/AutoplotVolatile2.jar dist/AutoplotVolatile1.jar # http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6575373  Note this doesn't appear to have an effect.
#mv dist/AutoplotVolatile2.jar dist/AutoplotVolatile.jar
#rm dist/AutoplotVolatile1.jar
#
#echo "=== sign and pack the jar file..."
#
#if [ "$DO_HIDE" = "true" ]; then 
#   echo "  use set +x to hide private info"
#   #echo  ${JAVA_HOME}/bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS $JARSIGNER_OPTS dist/AutoplotVolatile.jar "$ALIAS"
#   set +x
#fi
#
#if ! ${JAVA_HOME}/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS" $JARSIGNER_OPTS dist/AutoplotVolatile.jar "$ALIAS"; then
#   echo "Fail to sign resources!"
#   exit 1
#fi
#
#if [ "$DO_HIDE" = "true" ]; then 
#   set -x
#fi
#
#echo "=== verify the jar file..."
#${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile.jar | head -10

#echo "=== sign and pack the jar file..."
#${JAVA_HOME}/bin/pack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile.jar
#${JAVA_HOME}/bin/unpack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile_pack_gz.jar
#
#if ! ${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile.jar | head -10; then
#   echo "jarsigner verify failed on file dist/AutoplotVolatile.jar!"
#   exit 1
#fi
#
#echo "=== verify signed and unpacked jar file..."
#if ! ${JAVA_HOME}/bin/jarsigner -verify -verbose dist/AutoplotVolatile_pack_gz.jar | head -10; then
#   echo "jarsigner verify  failed on pack_gz file dist/AutoplotVolatile_pack_gz.jar!"
#   exit 1
#fi
#rm dist/AutoplotVolatile_pack_gz.jar
#
#echo "=== create jnlp file for build..."
#cp src/autoplot.jnlp dist
#cp src/autoplot_4GB.jnlp dist
#cp src/autoplot_1GB.jnlp dist
#cp src/autoplot_prod_4GB.jnlp dist
#cp src/autoplot_prod_1GB.jnlp dist

echo "=== copy branding for release, such as png icon images"
cp src/*.png dist
cp src/*.gif dist  # mac Java7 has a bug where it can't use pngs for icons, use .gif instead.
cp src/index.html dist  #TODO: why?

echo "=== modify jar files for this particular release"
cd temp-volatile-src
$JAVAC  -target 1.7 -source 1.7 -d ../temp-volatile-classes external/FileSearchReplace.java
cd ..
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_1GB.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_4GB.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_prod_1GB.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
${JAVA_HOME}/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_prod_4GB.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
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

