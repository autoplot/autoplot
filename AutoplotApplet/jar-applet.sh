#!/bin/sh

rm -r -f temp
mkdir temp
cd temp

# BinaryDataSource is to support TSDS.  DataSourcePack is to provide das2stream.
for i in \
  ../../VirboAutoplot/dist/lib/QDataSet.jar \
  ../../VirboAutoplot/dist/lib/QStream.jar \
  ../../VirboAutoplot/dist/lib/dasCore.jar \
  ../../VirboAutoplot/dist/lib/DataSource.jar \
  ../../VirboAutoplot/dist/lib/BinaryDataSource.jar \
  ../../VirboAutoplot/dist/lib/DataSourcePack.jar \
  ../../VirboAutoplot/dist/lib/TsdsDataSource.jar \
  ../../VirboAutoplot/dist/lib/swing-layout-1.0.3.jar \
  ../../VirboAutoplot/dist/VirboAutoplot.jar \
  ../dist/AutoplotApplet.jar   ; do
   echo $i
   name=${i}
   name=`basename $name`
   mkdir $name; unzip -q -d $name $i
done
echo "done unzip libs"

rm -r ../appletjar
mkdir ../appletjar

# assert pwd=temp
# add all the classes, assuming no conflicts
echo "copy classes"
for i in *; do
   echo $i
   cp -r $i/* ../appletjar
done


# special handling of the META-INF stuff.

file=org.virbo.datasource.DataSourceFactory
rm ../appletjar/META-INF/$file
touch ../appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> ../appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.extensions
rm ../appletjar/META-INF/$file
touch ../appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> ../appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
rm ../appletjar/META-INF/$file
touch ../appletjar/META-INF/$file
for i in `find . -name $file ` ; do
   cat $i >> ../appletjar/META-INF/$file
done
# end, special handling of the META-INF stuff.

cd ..
rm -r temp/*

# VirboAutoplot is the application, copy it last so its META-INF stuff is used.
unzip -o -d appletjar dist/AutoplotApplet.jar

# remove all the extraneous junk from Autoplot application
mv appletjar/LICENSE.txt temp/
rm -f appletjar/*   # not the directories
mv temp/LICENSE.txt appletjar/

mv appletjar/META-INF/MANIFEST.MF temp/     # get it out of the way
rm -f appletjar/META-INF/*.RSA
rm -f appletjar/META-INF/*.SF
rm -f appletjar/META-INF/build.txt
rm -f appletjar/META-INF/INDEX.LIST

cd appletjar

# Preferences Object needs to be installed
mkdir META-INF/services
echo "org.das2.system.NullPreferencesFactory" > META-INF/services/java.util.prefs.PreferencesFactory 

pwd
jar cmf ../temp/MANIFEST.MF ../temp/AutoplotAppletAll.jar  *

cd ..
mv temp/AutoplotAppletAll.jar dist

cp src/AutoplotApplet.html dist
cp src/TestApplet.html dist
