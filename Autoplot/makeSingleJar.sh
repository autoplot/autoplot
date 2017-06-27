#!/bin/bash

#make the single jar ("jumbojar") on the server side so the release need only be transferred once.  Before we would copy both multi-jar and single-jar versions at once.  This should be run in the directory with AutoplotStable.jar in it.
# This should be run immediately after the autoplot-release script is run, and from within the dist folder.

echo 'pwd='`pwd`
if [ ! -e AutoplotVolatile.jar ]; then
  echo "folder should contain AutoplotVolatile.jar"
  exit -1
fi

mkdir tjar
cd tjar
unzip ../AutoplotStable.jar | grep -v "inflating:" | grep -v "creating:"
unzip -o ../AutoplotVolatile.jar | grep -v "inflating:" | grep -v "creating:"
rm -f META-INF/MANIFEST.MF   # remove leftover signatures.
rm -f META-INF/*.DSA
rm -f META-INF/*.SF

echo -e "Main-Class: org.autoplot.AutoplotUI\nApplication-Name: Autoplot\nCodebase: http://autoplot.org/" > META-INF/MANIFEST.MF
zip -r ../autoplot.jar.1 * | grep -v "adding:"

cd ..
cat ../starterScript.sh > autoplot.jar
cat autoplot.jar.1 >> autoplot.jar

chmod 755 autoplot.jar

# clean up temporary files
rm autoplot.jar.1
rm -r tjar
