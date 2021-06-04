#!/bin/bash

# Check input parameters
if [ $# -ne 2 ]; then
    echo "ERROR: Missing arguments"
    echo -e "\t Usage: ./composer.sh <inputFile> <targetFolder>"
    exit -1
fi
INPUT_FILE=$1
TARGET_FOLDER=$2

# Create JAR (if needed)
JAR="target/chaos-echo-composer-1.jar"
echo -n "Creating composer..." 
mvn clean install &> /dev/null 
echo "done!"

# Create and fill target folder
if [ ! -d "$TARGET_FOLDER" ]; then
    echo -n "Creating target folder..."
    mkdir $TARGET_FOLDER
    echo "done!"
fi
echo -n "Creating Docker compose deployment..."
cp configs/logstash.conf $TARGET_FOLDER
java -jar $JAR $INPUT_FILE "${TARGET_FOLDER}/docker-compose.yml"
echo "done!"