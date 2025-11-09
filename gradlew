#!/usr/bin/env sh
# Minimal Gradle wrapper script
DIR="$( cd "$( dirname "$0" )" && pwd )"
APP_HOME="$DIR"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
