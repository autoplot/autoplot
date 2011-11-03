#!/bin/bash

# this copies all the sources into the temp directory, then compiles a few key sources, so
# that unreferenced routines are not used.

echo "compile-applet-all v.20111103"

# set JAVA5_HOME and JAVA6_HOME
if [ "" = "$JAVA5_HOME" ]; then
    JAVA5_HOME=/usr/local/jdk1.5.0_15__32/
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jre1.6.0_16__32/
fi

rm -r -f temp-src/
mkdir temp-src/
rm -r -f temp-classes/
mkdir temp-classes

for i in \
  dasCore dasCoreUtil dasCoreDatum \
  QDataSet QStream DataSource \
  AutoplotHelp \
  BinaryDataSource DataSourcePack TsdsDataSource  \
  VirboAutoplot \
  AutoplotApplet; do
    rsync -a ../${i}/src/ temp-src/
done
echo "done copy sources"

cd temp-classes
jar xvf ../../APLibs/lib/beansbinding-1.2.1.jar
jar xvf ../../APLibs/lib/commons/commons-vfs-1.0.jar  # experiment with support for applet
jar xvf ../../APLibs/lib/json-2011-01-27-gitrelease.jar
jar xvf ../../APLibs/lib/javacsv.jar
#jar xvf ../../APLibs/lib/swing-layout-1.0.3.jar

echo "Remove codes that are not used by the applet and cause problems, such as AutoplotUI.java and *EditorPanel.java"
cd ../temp-src
# set traps for things that ought not to be needed by the applet.
rm org/virbo/autoplot/AutoplotUI.java
rm org/virbo/autoplot/AutoplotUI.form
rm org/virbo/datasource/DataSetSelector.java
rm org/virbo/datasource/DataSetSelector.form
rm org/virbo/autoplot/scriptconsole/*
find . -name '*EditorPanel.java' -exec rm {} \;
rm org/virbo/autoplot/TcaElementDialog.java
#rm -rf org/das2/stream/*
rm -rf org/das2/dasml/*

# compile key java classes.
echo "compile sources..."
pwd
hasErrors=0
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/autoplot/AutoplotApplet.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 test/endtoend/TestApplet*.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/tsds/datasource/TsdsDataSourceFactory.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/das2Stream/Das2StreamDataSourceFactory.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/virbo/binarydatasource/BinaryDataSourceFactory.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/autoplot/csv/CsvDataSourceFactory.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/components/propertyeditor/PropertyEditor.java; then hasErrors=1; fi
if ! $JAVA5_HOME/bin/javac -target 1.5 -cp ../temp-classes:. -d ../temp-classes -Xmaxerrs 10 org/das2/beans/*.java; then hasErrors=1; fi

echo "done compile sources."

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1
fi

# special handling of the META-INF stuff.

cd ..

file=org.virbo.datasource.DataSourceFactory
touch temp-classes/META-INF/$file
for i in `ls {../TsdsDataSource/,../BinaryDataSource/,../DataSourcePack/}src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.extensions
touch temp-classes/META-INF/$file
for i in `ls {../TsdsDataSource/,../BinaryDataSource/,../DataSourcePack/}src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
touch temp-classes/META-INF/$file
for i in `ls {../TsdsDataSource/,../BinaryDataSource/,../DataSourcePack/}src/META-INF/$file` ; do
   cat $i >> temp-classes/META-INF/$file
done
# end, special handling of the META-INF stuff.


# copy over the vap promotion XSL files
for i in `ls ../VirboAutoplot/src/org/virbo/autoplot/state/*.xsl` ; do
   echo 'cp $i temp-classes/org/virbo/autoplot/state'
   cp $i temp-classes/org/virbo/autoplot/state
done

mkdir temp-classes/images/
cp -r temp-src/images/cancel*.png temp-classes/images/

cd temp-classes

rm -r org/jdesktop/swingbinding/
rm -r org/das2/components/propertyeditor/*Editor*
rm -r org/das2/components/propertyeditor/*Node*
rm -r org/das2/components/propertyeditor/*Renderer*
rm -r org/das2/components/treetable/
#rm -r org/das2/client/
rm -r org/das2/math/
rm org/das2/util/JCrypt.class
#rm org/das2/util/StreamTool.class  #das2Stream support
#rm org/das2/fsm/FileStorageModel.class
#rm org/das2/graph/XAxisDataLoader.class

mkdir -p ../dist/
$JAVA5_HOME/bin/jar cf ../dist/AutoplotAppletAll.jar *
cd ..

$JAVA5_HOME/bin/pack200 dist/AutoplotAppletAll.jar.pack.gz dist/AutoplotAppletAll.jar

echo "copy example html."
cp src/AutoplotApplet.html dist/
cp src/AutoplotAppletAurora.html dist/

if [ "" != "$EXAMPLE_URI" ]; then
   cd temp-src
   echo $JAVA5_HOME/bin/javac -target 1.5 -d ../temp-classes external/FileSearchReplace.java
   $JAVA5_HOME/bin/javac -target 1.5 -d ../temp-classes external/FileSearchReplace.java
   cd ..
   echo $JAVA5_HOME/bin/java -cp temp-classes external.FileSearchReplace dist/AutoplotApplet.html 'http://www.sarahandjeremy.net/jeremy/data/0B000800408DD710.$Y$m$d.d2s?timerange=2009-03-14' $EXAMPLE_URI
   $JAVA5_HOME/bin/java -cp temp-classes external.FileSearchReplace dist/AutoplotApplet.html 'http://www.sarahandjeremy.net/jeremy/data/0B000800408DD710.$Y$m$d.d2s?timerange=2009-03-14' $EXAMPLE_URI
fi

echo "copy htaccess.  htaccess must be moved to .htaccess to provide support for .pro.jar.pack.gz."
cp src/htaccess.txt dist/

echo "done"
