#!/bin/sh

#
# Gradle start up script for UN*X
# (Standard Gradle wrapper bootstrap; downloads gradle-wrapper.jar if missing
#  before delegating to the actual Gradle distribution.)
#

DIRNAME=$(dirname "$0")
APP_HOME=$(cd "$DIRNAME" >/dev/null 2>&1 && pwd)
WRAPPER_BASE_URL="https://raw.githubusercontent.com/gradle/gradle/v8.9/gradle/wrapper"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar from Gradle 8.9 release..."
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_BASE_URL/gradle-wrapper.jar"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_BASE_URL/gradle-wrapper.jar"
    else
        echo "ERROR: need curl or wget to fetch gradle-wrapper.jar" >&2
        exit 1
    fi
fi

# Use the JVM to actually launch Gradle via the wrapper jar.
CLASSPATH="$WRAPPER_JAR"
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
