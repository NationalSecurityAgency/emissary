#!/usr/bin/env bash

# Simple script to create a tarball of a git repo that only contains the desired branch and tags
# Run by providing branch or tag as argument.
# For example: sh contrib/repo-manual-mirror.sh 7.34.0

# Check if BRANCH_TO_MIRROR is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <branch-to-mirror>"
  exit 1
fi

BRANCH_TO_MIRROR=$1

# got to tmp location
cd /tmp/ || exit

# clone emissary
git clone -v --mirror git@github.com:NationalSecurityAgency/emissary.git
cd emissary.git || exit

# filter out refs that are not the desired branch or tags
git show-ref | grep -Pv "(refs/heads/${BRANCH_TO_MIRROR}\$|refs/tags)" | awk '{ print "/bin/git update-ref -d " $2}' | bash -x
cd ..

# tar it up
tar -czvf emissary.tgz."${BRANCH_TO_MIRROR}" emissary.git

# clean up cloned repo
rm -rf emissary.git