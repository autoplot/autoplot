#!/bin/bash
#
# This file consists of a Java jar archive prefixed with a starter script.
# It should be executable on any POSIX.1 compliant OS. (Any UNIX, Linux
# or Max OS X)
#
# The version of Java used will be:
#
#  1. $JAVA_HOME/bin/java - if the JAVA_HOME environment variable is defined
#
#    -- or --
#
#  2. java - The first executable named "java" encountered in your path.
#
# This file is still a valid jar file and may be inspected by standard tools
# such as "unzip".
#
#  -h  --headless   run in headless mode
#  -Jxxx            pass argument xxx to JRE (e.g. -J-Xmx1G to limit to 1 Gig of RAM)
#  -s  --script     launch into script
#
#  Set APDEBUG=1 to print debug infomation. (In bash, "export APDEBUG=1")
#
#  For example:
#    wget -O autoplot.jar http://autoplot.org/jnlp/latest/autoplot.jar
#    chmod 755 autoplot.jar
#    wget -O sayHello.jy http://autoplot.org/data/script/sayHello.jy
#    export APDEBUG=1
#    ./autoplot.jar -J-Xmx8G -h --script=sayHello.jy  # note this should result in: java -Xmx4G -Djava.awt.headless=true -jar autoplot.jar org.autoplot.AutoplotUI  --script=sayHello.jy
#  

JAVA_ARGS=""
AP_ARGS=""

memIsImplicit=1

for i in "$@"; do
   if [ "$APDEBUG" == "1" ]; then    
       echo "arg: \"$i\""
   fi
   if [[ $i == -J-Xmx* ]]; then
      JAVA_ARGS="${JAVA_ARGS} ${i:2}";
      memIsImplicit=0
   elif [[ $i == -J* ]]; then
      JAVA_ARGS="${JAVA_ARGS} ${i:2}";
   elif [[ $i == '--headless' ]]; then
      JAVA_ARGS="${JAVA_ARGS} -Djava.awt.headless=true";
   elif [[ $i == '-h' ]]; then
      JAVA_ARGS="${JAVA_ARGS} -Djava.awt.headless=true";
   else
      AP_ARGS="${AP_ARGS} $i";
   fi
done

AP_ARGS=${AP_ARGS:1}

if [ $memIsImplicit == "1" ]; then 
   JAVA_ARGS="${JAVA_ARGS} -Xmx4G -Dautoplot.release.type=singlejar";
fi

if [ "$APDEBUG" == "1" ]; then 
   echo "JAVA_ARGS=${JAVA_ARGS}"
   echo "AP_ARGS=${AP_ARGS}"
fi

# make debugging easier by checking if this is actually the starter script being tested.
if [ "$0" == "./starterScript.sh" ]; then
   EXEC="echo";
   JARFILE="autoplot.jar"
else
   EXEC="";
   JARFILE=$0;
fi

if [ "$APDEBUG" == "1" ]; then 
   if [ "${JAVA_HOME}" -a \( -x "${JAVA_HOME}"/bin/java \) ]; then      
      echo $EXEC "${JAVA_HOME}"/bin/java ${JAVA_ARGS} -jar ${JARFILE} "${AP_ARGS}"
   else
      echo $EXEC /usr/bin/env java ${JAVA_ARGS} -jar ${JARFILE} "${AP_ARGS}"
   fi
fi


if [ "${JAVA_HOME}" -a \( -x "${JAVA_HOME}"/bin/java \) ]; then      
      $EXEC "${JAVA_HOME}"/bin/java ${JAVA_ARGS} -jar ${JARFILE} "${AP_ARGS}"
else
      $EXEC /usr/bin/env java ${JAVA_ARGS} -jar ${JARFILE} "${AP_ARGS}"
fi

exit








# JAR FILE DATA STARTS AFTER THIS TEXT #
#