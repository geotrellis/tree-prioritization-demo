#!/bin/bash

# Usage:
# $ SJS_HOST=33.33.34.48 SJS_PORT=8090 ./scripts/rebuild.sh

# 1. Build the combined modeling assembly JAR
# 2. POST the JAR to Spark Job Server
# 3. Restart the Spark context in Spark Job Server
# 4. Copy the JAR to the project root, mounted in the vagrant VM
# 5. Restart the tile service within the Vagrant VM

set -e
set -x

vagrantvm="modeling"
context="modeling"

: "${SJS_HOST?You need to set SJS_HOST. To connect to the Vagrant VM \"SJS_HOST=33.33.34.48\"}"
: "${SJS_PORT?You need to set SJS_PORT. To connect to the Vagrant VM \"SJS_PORT=8090\"}"
sjs="$SJS_HOST:$SJS_PORT"

# remove any previously built versions of the JAR
rm -f combined/target/scala-2.10/*.jar
# Build the combined JAR
./sbt assembly
# Get the name of the combined assembly. Using `head` when getting the
# `jarpath` is a precaution. We only expect one JAR to be built, but
# the other commands assume that $jarpath will be a single path
# string.
jarpath=`ls combined/target/scala-2.10/*.jar | head -n 1`
jarname=$(basename $jarpath)
appname="${jarname%.*}"

# POST the JAR to Spark Job Server
curl --data-binary "@$jarpath" "http://$sjs/jars/$appname"
# Recreate the Spark context so that the new JAR is loaded.
curl -X DELETE "http://$sjs/contexts/$context"
# Issuing POST immediately after the DELETE fails with a "context
# exits" error, so we sleep for a second.
sleep 1
curl -X POST "http://$sjs/contexts/$context"

# The modeling project directory is shared into the VM at
# /opt/modeling and the tile service loads the JAR from that path
cp $jarpath .
# Restart the tile service container to load the new JAR
vagrant ssh $vagrantvm -c "sudo docker restart otm-modeling-tile"
