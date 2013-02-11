#!/bin/bash
#
# Purpose: create two jars files that are .pack.gz.  One is code we don't expect to change often,
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
    JAVA_HOME=/usr/local/jdk1.6.0_35/
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jdk1.6.0_35/
fi

if [ "" = "$TAG" ]; then
    TAG=untagged
fi

if [ "" = "$CODEBASE" ]; then
    CODEBASE=NEED_CODEBASE_TO_BE_DEFINED_IN_COMPILE_SCRIPT
fi

if [ "" = "$HUDSON_URL" ]; then
    HUDSON_URL="http://papco.org:8080/hudson"
fi

rm -r -f temp-stable-src/
mkdir temp-stable-src/
rm -r -f temp-volatile-src/
mkdir temp-volatile-src/
rm -r -f temp-stable-classes/
mkdir temp-stable-classes
rm -r -f temp-volatile-classes/
mkdir temp-volatile-classes

echo "copy jar file classes..."
cd temp-stable-classes
for i in ../../APLibs/lib/*.jar; do
   echo jar xf $i
   jar xf $i
done

# use hacked version of cdf library
rm -rf gsfc/
jar xf ../../APLibs/lib/cdfjava.3.3.2.tt2000.jar

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo jar xf $i
   jar xf $i
done
cd ..
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

echo "special handling of META-INF stuff..."
mkdir temp-volatile-classes/META-INF

file=org.virbo.datasource.DataSourceFactory.extensions
touch temp-volatile-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-volatile-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
touch temp-volatile-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-volatile-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFormat.extensions
touch temp-volatile-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-volatile-classes/META-INF/$file
done


file=org.virbo.datasource.DataSourceEditorPanel.extensions
touch temp-volatile-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-volatile-classes/META-INF/$file
done

echo "Main-Class: org.virbo.autoplot.AutoplotUI" > temp-volatile-src/MANIFEST.MF

# remove signatures
rm temp-volatile-classes/META-INF/*.RSA
rm temp-volatile-classes/META-INF/*.DSA
rm temp-volatile-classes/META-INF/*.SF
rm temp-stable-classes/META-INF/*.RSA
rm temp-stable-classes/META-INF/*.SF
rm temp-stable-classes/META-INF/*.DSA

cat src/META-INF/build.txt | sed "s/build.tag\:/build.tag\: $TAG/" > temp-volatile-classes/META-INF/build.txt
# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "copy resources..."
cd temp-volatile-src
#mkdir -p ../temp-volatile-classes/images/toolbox/
#mkdir -p ../temp-volatile-classes/images/icons/
#mkdir -p ../temp-volatile-classes/images/toolbar/
#mkdir -p ../temp-volatile-classes/com/cottagesystems/jdiskhog/resources/
#mkdir -p ../temp-volatile-classes/org/virbo/autoplot/resources/
#mkdir -p ../temp-volatile-classes/org/virbo/datasource/
#mkdir -p ../temp-volatile-classes/org/netbeans/modules/editor/completion/resources/

for i in $(find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' -o -name '*.xsl' -o -name '*.xsd' ); do
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
cd ..
echo "done copy resources."

hasErrors=0

# compile key java classes.
echo "compile sources..."
cd temp-volatile-src
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotUI.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/JythonMain.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/help/AutoplotHelpViewer.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotServer.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/PngWalkTool1.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/DemoPngWalk.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/DemoPngWalk1.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/ImageResize.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/QualityControlPanel.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/util/awt/*.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 test/endtoend/*.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/idlsupport/*.java; then hasErrors=1; fi
if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/ScreenshotsTool.java; then hasErrors=1; fi

cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceEditorPanel.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! $JAVA_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../temp-stable-classes:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done

cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "make jumbo jar files..."
mkdir -p dist/
cd temp-stable-classes
$JAVA_HOME/bin/jar cf ../dist/AutoplotAllStable.jar *
cd ..
cd temp-volatile-classes
$JAVA_HOME/bin/jar cmf ../temp-volatile-src/MANIFEST.MF ../dist/AutoplotAllVolatile.jar *
cd ..

echo "done make jumbo jar files..."

echo "sign the jar files..."
$JAVA_HOME/bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS  dist/AutoplotAllVolatile.jar virbo
$JAVA_HOME/bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS  dist/AutoplotAllStable.jar virbo

echo "create jnlp file for build..."
cp src/autoplot_two_jar.jnlp dist

cd temp-volatile-src
$JAVA_HOME/bin/javac -target 1.5 -d ../temp-volatile-classes external/FileSearchReplace.java
cd ..
echo "replace codebase, etc"
$JAVA_HOME/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url} $HUDSON_URL
$JAVA_HOME/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar_pack200.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE '#{hudson_url} $HUDSON_URL

echo "proguard/pack200 stuff..."
#proguard is compiled for Java 6.  This needs to be fixed.
#$JAVA6_HOME/bin/java -jar ../APLibs/lib/proguard.jar @apApplicationAll.proguard
#$JAVA6_HOME/bin/pack200 dist/AutoplotAll.pro.jar.pack.gz dist/AutoplotAll.pro.jar
$JAVA6_HOME/bin/pack200 dist/AutoplotAllVolatile.jar.pack.gz dist/AutoplotAllVolatile.jar
$JAVA6_HOME/bin/pack200 dist/AutoplotAllStable.jar.pack.gz dist/AutoplotAllStable.jar
echo "done proguard/pack200 stuff."

echo "copy branding for release, such as png icon images"
cp src/*.png dist

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pack.gz."
cp src/htaccess.txt dist/
