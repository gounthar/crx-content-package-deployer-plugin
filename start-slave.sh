#!/bin/bash
PROJECT_ROOT=$(dirname "$0")
mkdir -p "${PROJECT_ROOT}/target"
if [[ ! -e "${PROJECT_ROOT}/target/slave.jar" ]]; then
    curl -fso "${PROJECT_ROOT}/target/slave.jar" http://localhost:8080/jenkins/jnlpJars/slave.jar
fi
exec java -jar "${PROJECT_ROOT}/target/slave.jar"
