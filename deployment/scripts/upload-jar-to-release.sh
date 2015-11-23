#!/bin/sh

# Check if HEAD is the same as the most recent tag and, if so, create
# a Github release for that tag and upload the compiled jar
#
# This script requires that the github-release binary is on the PATH
# https://github.com/aktau/github-release
# Tested with version 0.6.2
# https://github.com/aktau/github-release/releases/tag/v0.6.2
#
# The script requires three environment variables to be set:
#
#   GITHUB_REPO=otm-modeling
#   GITHUB_USER=OpenTreeMap
#     This should be set to the organization under which the GITHUB_REPO
#     is located
#   GITHUB_TOKEN=<A personal access token created via https://github.com/settings/tokens>
#     This token needs push access to the GITHUB_REPO in order to create
#     releases and upload files

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

tagname=`$DIR/on-latest-tag.sh`

if [ $? -eq 0 ]
then
    echo "On tag $tagname" >&2

    # Check if the release exists. If not, create it.
    github-release info --tag $tagname
    if [ $? -ne 0 ]
    then
        echo "Creating release $tagname" >&2
        github-release release --tag $tagname --name $tagname
        if [ $? -ne 0 ]
        then
            echo "Failed to create the release. Check the error message for more detail." >&2
            exit 1
        fi
    fi

    # Upload the combined project assembly to the release
    outputjar="combined/target/scala-2.10/otm-modeling-assembly-$tagname.jar"
    releasejarname="otm-modeling-$tagname.jar"

    echo "Uploading $releasejarname" >&2
    github-release upload --tag $tagname --name $releasejarname --file $outputjar
    if [ $? -ne 0 ]
    then
        echo "Failed to upload the jar. A jar may have already been uploaded for $tagname. Check the error message for more detail." >&2
        exit 1
    fi

else
    echo "Not on the latest tag. Skipping jar upload." >&2
fi
