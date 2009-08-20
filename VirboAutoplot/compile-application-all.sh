#!/bin/bash

# this copies all the sources into the temp directory, then compiles a few key sources, so
# that unreferenced routines are not used.  This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#
# CDF Support will be awkward because of the binaries.  Support this for the hudson platform.

# set JAVA5_HOME and JAVA6_HOME
if [ "" = "$JAVA5_HOME" ]; then
    JAVA5_HOME=/usr/local/jdk1.5.0_17/
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jre1.6.0_14/
fi

rm -r -f temp-src/
mkdir temp-src/
rm -r -f temp-classes/
mkdir temp-classes

echo "copy jar file classes..."
cd temp-classes
for i in ../../APLibs/lib/*.jar; do
   echo jar xf $i
   jar xf $i
done

# use hacked version of cdf library
rm -rf gsfc/
jar xf ../../APLibs/lib/cdfjava-hack.jar

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo jar xf $i
   jar xf $i
done
cd ..
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
    echo rsync -a --exclude .svn ../${i}/src/ temp-src/
    rsync -a --exclude .svn ../${i}/src/ temp-src/
done
echo "done copy sources"

# special handling of the META-INF stuff.

echo "special handling of META-INF stuff..."

file=org.virbo.datasource.DataSourceFactory.extensions
touch temp-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
touch temp-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFormat.extensions
touch temp-classes/META-INF/$file
for i in `ls ../*/src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done

echo "Main-Class: org.virbo.autoplot.AutoPlotUI" > temp-src/MANIFEST.MF

# remove signatures
rm temp-classes/META-INF/*.RSA
rm temp-classes/META-INF/*.SF

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

echo "copy resources..."
cd temp-src
mkdir -p ../temp-classes/./images/toolbox/
mkdir -p ../temp-classes/./images/icons/
mkdir -p ../temp-classes/./images/toolbar/
mkdir -p ../temp-classes/./com/cottagesystems/jdiskhog/resources/
mkdir -p ../temp-classes/./org/virbo/autoplot/resources/
mkdir -p ../temp-classes/./org/virbo/datasource/
mkdir -p ../temp-classes/./org/netbeans/modules/editor/completion/resources/
for i in `find . -name '*.png'`; do
   cp $i ../temp-classes/$i
done
for i in `find . -name '*.gif'`; do
   cp $i ../temp-classes/$i
done

cd ..
echo "done copy resources."

hasErrors=0

# compile key java classes.
echo "compile sources..."
cd temp-src
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/AutoPlotUI.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/JythonMain.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/pngwalk/DemoPngWalk.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/util/awt/*.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 test/endtoend/*.java; then hasErrors=1; fi
cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFactory.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java
   if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1
for i in `cat ../temp-classes/META-INF/org.virbo.datasource.DataSourceFormat.extensions | cut -d' ' -f1 | sed 's/\./\//g'`; do
   echo $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java
   if ! $JAVA5_HOME/bin/javac -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 $i.java; then hasErrors=1; fi
done
cd ..
echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "make jumbo jar file..."
cd temp-classes

mkdir -p ../dist/
$JAVA5_HOME/bin/jar cmf ../temp-src/MANIFEST.MF ../dist/AutoplotAll.jar *
cd ..
echo "done make jumbo jar file..."

echo "proguard/pack200 stuff..."
#proguard is compiled for Java 6.  This needs to be fixed.
$JAVA6_HOME/bin/java -jar ../APLibs/lib/proguard.jar @apApplicationAll.proguard
$JAVA6_HOME/bin/pack200 dist/AutoplotAll.pro.jar.pack.gz dist/AutoplotAll.pro.jar
echo "done proguard/pack200 stuff."
