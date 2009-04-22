#!/bin/sh

rm -r -f temp
mkdir temp

for i in \
  dist/lib/QDataSet.jar dist/lib/QStream.jar dist/lib/dasCore.jar dist/lib/DataSource.jar \
  dist/lib/BinaryDataSource.jar dist/lib/DataSourcePack.jar dist/lib/TsdsDataSource.jar \
  dist/lib/beansbinding-1.2.1.jar \
  dist/lib/VirboAutoplot.jar \
  dist/AutoplotApplet.jar; do
    name=${i}
    name=`basename $name`
    mkdir temp/$name; unzip -q -d temp/$name $i
done
echo "done unzip libs"



rm -r appletjar
mkdir appletjar

# assert pwd=temp
# add all the classes, assuming no conflicts
echo "copy classes"
for i in temp/*; do
   cp -r $i/* appletjar
done


echo "remove extra stuff from das core"
#rm -r appletjar/org/das2/dasml
rm -r appletjar/org/das2/math
#rm -r appletjar/org/das2/dataset/test
#rm -r appletjar/org/das2/beans/*BeanInfo*
#rm -r appletjar/org/das2/util/awt
#rm -r appletjar/org/das2/util/stream
#rm -r appletjar/org/das2/util/StreamTool*
rm -r appletjar/test
rm -r appletjar/scripts
rm -r appletjar/images
rm -r appletjar/external
#rm -r appletjar/zipfs
rm -r appletjar/org/virbo/autoplot/AutoPlotUI*
rm -r appletjar/org/virbo/autoplot/AddPanelDia*
rm -r appletjar/org/virbo/autoplot/scriptconsole*
#rm -r appletjar/org/virbo/autoplot/resources/
#rm -r appletjar/org/virbo/autoplot/bookmarks/
#rm -r appletjar/org/virbo/ascii/




# special handling of the META-INF stuff.

file=org.virbo.datasource.DataSourceFactory
rm appletjar/META-INF/$file
touch appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.extensions
rm appletjar/META-INF/$file
touch /appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
rm appletjar/META-INF/$file
touch appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> appletjar/META-INF/$file
done
# end, special handling of the META-INF stuff.

rm -r temp/*

# remove all the extraneous junk from Autoplot application
mv appletjar/LICENSE.txt temp/
rm -f appletjar/*   # not the directories
mv temp/LICENSE.txt appletjar/

rm -r appletjar/META-INF/MANIFEST.MF
rm -f appletjar/META-INF/*.RSA
rm -f appletjar/META-INF/*.SF
rm -f appletjar/META-INF/build.txt
rm -f appletjar/META-INF/INDEX.LIST

cd appletjar

# Preferences Object needs to be installed
mkdir META-INF/services
echo "org.das2.system.NullPreferencesFactory" > META-INF/services/java.util.prefs.PreferencesFactory 

pwd
/usr/local/jdk1.5.0_17/bin/jar cmf ../MANIFEST.MF ../temp/AutoplotAppletAll.jar *

cd ..

cp temp/AutoplotAppletAll.jar dist

