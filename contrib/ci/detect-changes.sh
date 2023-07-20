#! /usr/bin/env bash

# Check that the build hasn't changed anything from
# what is currently checked in to the repository

set -e

if [[ -n $(git status --porcelain --ignored=no) ]]; then
  echo 'The build changed files in worktree:'
  git status --short --ignored=no
  exit 1
else
  echo 'No changes detected.'
fi
