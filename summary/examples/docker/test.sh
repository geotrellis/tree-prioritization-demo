#!/usr/bin/env bash

set -e

printf "\nCleanup Existing Container (errors ok)\n"
docker kill otm-modeling-summary || true
docker rm otm-modeling-summary || true

DATAHUB_AWS_PROFILE=otm-test
printf "Tiles will be requested using the $DATAHUB_AWS_PROFILE AWS profile\n"

docker run \
  --detach \
  --name otm-modeling-summary \
  --publish 8090:8090 \
  --volume $HOME/.aws:/root/.aws \
  --volume $PWD/spark-jobserver.conf:/opt/spark-jobserver/spark-jobserver.conf \
  --env AWS_PROFILE=$DATAHUB_AWS_PROFILE \
  --log-driver syslog \
  quay.io/azavea/spark-jobserver:latest --driver-memory 512M

printf "Wait For Service Startup\n"
sleep 3

printf "Submit Jar\n"
curl --silent --data-binary @../../../combined/target/scala-2.10/otm-modeling-assembly-1.0.0.jar 'http://localhost:8090/jars/summary'

printf "\nCreate Context\n"
curl --silent --data "" 'http://localhost:8090/contexts/summary-context'

printf "\nGet Histogram\n"
curl --silent --data-binary @../request-histogram.json 'http://localhost:8090/jobs?sync=true&context=summary-context&appName=summary&classPath=org.opentreemap.modeling.HistogramJob'

printf "\nGet Point Values\n"
curl --silent --data-binary @../request-values.json 'http://localhost:8090/jobs?sync=true&context=summary-context&appName=summary&classPath=org.opentreemap.modeling.PointValuesJob'

printf "\nCleanup\n"
docker kill otm-modeling-summary || true
docker rm otm-modeling-summary || true
