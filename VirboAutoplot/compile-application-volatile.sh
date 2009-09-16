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

# set JAVA5_HOME and JAVA6_HOME
if [ "" = "$JAVA5_HOME" ]; then
    JAVA5_HOME=/usr/local/jdk1.5.0_17/
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jre1.6.0_14/
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
  QDataSet QStream dasCore DataSource \
  JythonSupport \
  IdlMatlabSupport \
  AudioSystemDataSource \
  BinaryDataSource DataSourcePack JythonDataSource \
  Das2ServerDataSource TsdsDataSource  \
  NetCdfDataSource CdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
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

echo "Main-Class: org.virbo.autoplot.AutoPlotUI" > temp-volatile-src/MANIFEST.MF

# remove signatures
rm temp-volatile-classes/META-INF/*.RSA
rm temp-volatile-classes/META-INF/*.DSA
rm temp-volatile-classes/META-INF/*.SF

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

for i in $(find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' ); do
   mkdir -p $(dirname ../temp-volatile-classes/$i)
   cp $i ../temp-volatile-classes/$i
done
#for i in `find * -name '*.gif'`; do
#   cp $i ../temp-volatile-classes/$i
#done
#for i in `find * -name '*.html'`; do
#   cp $i ../temp-volatile-classes/$i
#done
#for i in `find * -name '*.py'`; do
#   cp $i ../temp-volatile-classes/$i
#done
#for i in `find * -name '*.jy'`; do
#   cp $i ../temp-volatile-classes/$i
#done
cd ..
echo "done copy resources."

echo "copy help files..."
for i in \
  QDataSet QStream dasCore DataSource \
  JythonSupport \
  IdlMatlabSupport \
  AudioSystemDataSource \
  BinaryDataSource DataSourcePack JythonDataSource \
  Das2ServerDataSource TsdsDataSource  \
  NetCdfDataSource CdfDataSource CefDataSource \
  WavDataSource ImageDataSource ExcelDataSource \
  FitsDataSource OpenDapDataSource \
  VirboAutoplot; do
    echo rsync -a --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
    rsync -a --exclude .svn ../${i}/javahelp/ temp-volatile-classes/
done

echo "done copy help files."

hasErrors=0

# compile key java classes.
echo "compile sources..."
cd temp-volatile-src
echo `pwd`
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/AutoPlotUI.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/virbo/autoplot/JythonMain.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/autoplot/pngwalk/DemoPngWalk.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 org/das2/util/awt/*.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 test/endtoend/*.java; then hasErrors=1; fi
cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-volatile-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java
   if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-volatile-classes:../AutoplotStable.jar:. -d ../temp-volatile-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "make jumbo jar files..."
mkdir -p dist/
cd temp-volatile-classes
$JAVA5_HOME/bin/jar cmf ../temp-volatile-src/MANIFEST.MF ../dist/AutoplotVolatile.jar *
cd ..

echo "done make jumbo jar files..."

echo "sign the jar files..."
$JAVA5_HOME/bin/jarsigner -keypass $KEYPASS -storepass $STOREPASS  dist/AutoplotVolatile.jar virbo

echo "create jnlp file for build..."
cp src/autoplot_two_jar.jnlp dist

cd temp-volatile-src
$JAVA5_HOME/bin/javac -d ../temp-volatile-classes external/FileSearchReplace.java
cd ..
echo $JAVA5_HOME/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE
$JAVA5_HOME/bin/java -cp temp-volatile-classes external.FileSearchReplace dist/autoplot_two_jar.jnlp '#{tag}' $TAG '#{codebase}' $CODEBASE

echo "proguard/pack200 stuff..."
$JAVA6_HOME/bin/pack200 dist/AutoplotVolatile.jar.pack.gz dist/AutoplotVolatile.jar
echo "done proguard/pack200 stuff."

mv AutoplotStable.jar.pack.gz dist/

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pack.gz."
cp src/htaccess.txt dist/