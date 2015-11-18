#!/bin/bash

docker run \
     -v ${HOME}/.ivy2:/root/.ivy2 \
     -v ${HOME}/.sbt:/root/.sbt \
     -v ${PWD}:/otm-modeling \
     -w /otm-modeling \
     --rm=true \
     quay.io/azavea/scala:latest ./sbt assembly
