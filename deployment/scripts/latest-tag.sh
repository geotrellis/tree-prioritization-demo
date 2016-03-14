#!/bin/bash

# Write the tag name and sha1 of the most recent tag to stdout
# Reference: https://git-scm.com/docs/git-for-each-ref
#            http://stackoverflow.com/questions/2021599/why-does-git-for-each-ref-fail-to-sort-tags-correctly

git for-each-ref --format='%(*committerdate:raw)%(committerdate:raw) %(refname) %(*objectname) %(objectname)' refs/tags \
    | sort -n -r \
    | head -1 \
    | awk '{ print $4, $3; }' \
    | sed -e 's/refs\/tags\///g'
