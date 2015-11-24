#!/bin/bash

# Write the name of the latest tag to stdout if HEAD is equal to the
# latest tag, else exit 1

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

tagsha=`$DIR/latest-tag.sh | awk '{print $1;}' `
tagname=`$DIR/latest-tag.sh | awk '{print $2;}' `
headsha=`git rev-parse --verify HEAD`

if [ "$tagsha" == "$headsha" ]; then echo $tagname; else echo "NOT_ON_THE_LATEST_TAG"; exit 1; fi
