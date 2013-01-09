#!/bin/bash
#
# Nagios plugin to monitor Java JMX (http://java.sun.com/jmx)attributes.
#
RDIR=`dirname $0`
export JAVA_OPTS="-Djava.util.logging.manager=java.util.logging.LogManager -Djava.util.logging.config.file=logging.properties"
/usr/bin/java $JAVA_OPTS -jar $RDIR/jmxMultiQuery.jar "$@"
