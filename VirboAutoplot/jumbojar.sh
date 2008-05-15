#!/bin/sh

rm -r temp
mkdir temp; cd temp

for i in  ../dist/lib/*.jar ; do
   echo $i
   name=${i}
   name=`basename $name`
   mkdir $name; unzip -q -d $name $i
done
echo "done unzip libs"

rm -r ../jumbojar
mkdir ../jumbojar


# add all the classes, assuming no conflicts
echo "copy classes"
for i in *; do
   echo $i
   cp -r $i/* ../jumbojar
done


# special handling of the META-INF stuff.

file=org.virbo.datasource.DataSourceFactory
rm ../jumbojar/META-INF/$file
touch ../jumbojar/META-INF/$file
for i in `find . -name $file | xargs` ; do
   cat $i >> ../jumbojar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.extensions
rm ../jumbojar/META-INF/$file
touch ../jumbojar/META-INF/$file
for i in `find . -name $file | xargs` ; do
   cat $i >> ../jumbojar/META-INF/$file
done

file=org.virbo.datasource.DataSourceFactory.mimeTypes
rm ../jumbojar/META-INF/$file
touch ../jumbojar/META-INF/$file
for i in `find . -name $file | xargs` ; do
   cat $i >> ../jumbojar/META-INF/$file
done

# VirboAutoplot is the application, copy it last so it's META-INF stuff is used.
unzip -o -d ../jumbojar ../dist/*.jar

cd ../jumbojar
mv META-INF/MANIFEST.MF ..     # get it out of the way
jar c -f ../jumbojar.jar -m ../MANIFEST.MF *

