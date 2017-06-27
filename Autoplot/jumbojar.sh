#!/bin/bash

./compile-application-all.sh
cp jumbojar_header.txt dist/Autoplot
cat dist/AutoplotAll.jar >>  dist/Autoplot
mv dist/Autoplot dist/autoplot.jar   
chmod 755 dist/autoplot.jar
