#!/bin/sh

rm -r temp
mkdir temp; cd temp

# BinaryDataSource is to support TSDS.  DataSourcePack is to provide das2stream.
for i in ../dist/lib/QDataSet.jar ../dist/lib/dasCore.jar ../dist/lib/dasCoreUI.jar \
  ../dist/lib/DataSource.jar ../dist/lib/BinaryDataSource.jar \
  ../dist/lib/DataSourcePack.jar \
  ../dist/lib/TsdsDataSource.jar ../dist/lib/swing-layout-1.0.3.jar \
  ../dist/VirboAutoplot.jar ; do
   echo $i
   name=${i}
   name=`basename $name`
   mkdir $name; unzip -q -d $name $i
done
echo "done unzip libs"

rm -r ../appletjar
mkdir ../appletjar


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
for i in `find . -name $file | xargs` ; do
   cat $i >> ../appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.extensions
rm ../appletjar/META-INF/$file
touch ../appletjar/META-INF/$file
for i in `find . -name $file | xargs` ; do
   cat $i >> ../appletjar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
rm ../appletjar/META-INF/$file
touch ../appletjar/META-INF/$file
for i in `find . -name $file | xargs` ; do
   cat $i >> ../appletjar/META-INF/$file
done

# VirboAutoplot is the application, copy it last so it's META-INF stuff is used.
unzip -o -d ../appletjar ../dist/*.jar

cd ../appletjar
mv META-INF/MANIFEST.MF ..     # get it out of the way
rm META-INF/*.RSA
rm META-INF/*.SF
rm META-INF/build.txt
rm META-INF/INDEX.LIST

jar cf ../AutoplotApplet.jar  *
mv ../AutoplotApplet.jar ../dist/
