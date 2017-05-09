#!/bin/bash

set -e

if [[ -n "${TPSP_DEBUG}" ]]; then
    set -x
fi

function usage() {
    echo -n \
         "Usage: $(basename "$0")
Builds and pulls container images using docker-compose.
"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]
then
    if [ "${1:-}" = "--help" ]
    then
        usage
    else
        echo "Installing Node.js dependencies"
        docker-compose \
            run --rm app-frontend install --pure-lockfile

        echo "Building JAR"
        docker-compose \
            run --rm --entrypoint ./sbt api-server assembly
    fi
fi
