#!/usr/bin/env bash

set -e

printf "Cleanup Output From a Previous Run\n"
rm -f tile.png

printf "\nCleanup Existing Container (errors ok)\n"
docker kill otm-modeling-tile || true
docker rm otm-modeling-tile || true

MODELING_TILE_HOME=/opt/otm-modeling-tile

cp $PWD/../../target/scala-2.10/otm-modeling-tile-assembly-0.0.1.jar $PWD/home/otm-modeling-tile.jar

printf "Run Container\n"
docker run \
  --detach \
  --name otm-modeling-tile \
  --publish 8777:8777 \
  --volume $HOME/.aws:/root/.aws:ro \
  --volume $PWD/home/:$MODELING_TILE_HOME/ \
  --log-driver syslog \
  -w $MODELING_TILE_HOME \
  --entrypoint $MODELING_TILE_HOME/docker-entrypoint.sh \
  quay.io/azavea/spark:latest

printf "Wait For Service Startup\n"
sleep 5

printf "Get Class Breaks\n"
curl -d 'bbox=-13193642.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=nlcd-wm-ext-tms&weights=2&numBreaks=10&srid=3857' -X POST "http://localhost:8777/gt/breaks"

printf "\nGet Tile\n"
curl -d 'bbox=-13193642.578247702,3977047.2455633273,-13100389.403739786,4039419.8606440313&layers=nlcd-wm-ext-tms&weights=2&numBreaks=10&srid=3857&breaks=0,22,42,44,46,48,82,86,104,190' -X POST "http://localhost:8777/gt/tile/13/1408/3278.png" > tile.png
printf "\nopen tile.png to see the results\n"

printf "\nCleanup\n"
docker kill otm-modeling-tile || true
docker rm otm-modeling-tile || true
