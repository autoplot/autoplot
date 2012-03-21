#!/bin/sh

#make the single jar ("jumbojar") on the server side so the release need only be transferred once.  Before we would copy both multi-jar and single-jar versions at once.  This should be run in the directory with AutoplotStable.jar in it.
# This should be run immediately after the autoplot-release script is run, and from within the dist folder.

if [ ! -e AutoplotVolatile.jar ]; then
  echo "folder should contain AutoplotVolatile.jar"
  exit -1
fi

mkdir tjar
cd tjar
unzip ../AutoplotStable.jar
unzip -o ../AutoplotVolatile.jar
zip -r ../autoplot.jar.1 *

cd ..
cat ../starterScript.sh > autoplot.jar
cat autoplot.jar.1 >> autoplot.jar
rm autoplot.jar.1
rm -r tjar
