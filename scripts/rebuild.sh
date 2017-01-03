#!/bin/bash

# Usage:
# scripts/rebuild.sh

# 1. Build the modeling assembly JAR
# 2. Copy the JAR to the project root, mounted in the vagrant VM
# 3. Restart the tile service within the Vagrant VM

set -e
set -x

vagrantvm="modeling"
context="modeling"

# remove any previously built versions of the JAR
rm -f tile/target/scala-2.10/*.jar

# Build the combined JAR
./sbt assembly

# Get the name of the assembly. Using `head` when getting the
# `jarpath` is a precaution. We only expect one JAR to be built, but
# the other commands assume that $jarpath will be a single path
# string.
jarpath=`ls tile/target/scala-2.10/*.jar | head -n 1`

# The modeling project directory is shared into the VM at
# /opt/modeling and the tile service loads the JAR from that path
cp $jarpath .
# Restart the tile service container to load the new JAR
vagrant ssh $vagrantvm -c "sudo docker restart otm-modeling-tile"
