# Emissary Releasing Workflow

Table of Contents
=================

* [Introduction](#introduction)
* [Versioning](#versioning)
* [Release Types](#release-types)
* [Publishing a Release](#publishing-a-release)

## Introduction

Release process uses GitHub actions and involves:
- Creating a release branch to perform the following actions:
  - Dry run a maven release
  - Remove the "-SNAPSHOT" suffix from the version and create scm tag
  - Create a GitHub release and upload artifacts
- If patch release, delete the patch branch
- For a formal release only, increment snapshot version on master branch 
- Publish the artifacts to GitHub Maven Repo

The release process used for this repository creates a branch for every release that is prefixed with `release/`. Tags are not added nor are releases performed on the `master` branch. The `master` branch is the target for merging new commits and for development releases (-SNAPSHOT) only. All patches will originate using a `patch/` prefixed branch and once complete a new `release/` branch will be created. 

## Versioning

While not strictly enforced, versioning generally follows the [Semantic Versioning](https://semver.org/) Guide. 

## Release Types

### Formal Release

Formal release for the project and is intended to be stable. 

```
Action: `Maven: Release`
Options:
- From: `Branch: master` 
- Type: `Release`
- Suffix: Leave blank
```
Creates a branch called `release/<version>` and performs release. One commit is added to `master` only to increment to the next snapshot version.


### Patch Release

Releases may have bugs or vulnerabilities with a high enough severity that requires a patch release to fix.

#### Create Patch Branch

Creating a patch branch can be done manually or using the GitHub action. The action was added as a convenience, and is not required as part
of the workflow. 

Note: When manually creating a patch branch, the name must start with `patch/` and must be based on a release branch, i.e. `release/<version>`.
```
Action: `Create a Patch Branch`
Options:
- From: `Branch: release/<version>`
```
Convenience action to simply create a branch called `patch/<version>`. The action must be run from a release branch, i.e. `release/<version>`.

#### Release Patch

Patch release for the project that intended to fix one or more bugs/vulnerabilities for a release.  Patches are not intended for new functionality 
unless it is directly supporting a bugfix/hotfix.

```
Action: `Maven: Release`
Options:
- From: `Branch: patch/<version>`
- Type: `Patch`
- Suffix: Leave blank
```
Releases patch fixes from a branch called `patch/<version>`, i.e. `patch/8.0.x`. A release branch called `release/<version>` is created
and the release is performed. When finished, the patch branch is deleted. No commits are made to `master`.

### Iterative Release (Milestone)

Like snapshots, iterative releases are not guaranteed to be stable, but allow for markers in the development stage and allow for testing and
feedback before a formal release. Currently, we support milestone releasing but the functionality can easily be extended to create other iterative
release types, e.g. alpha, beta, release candidates.

```
Action: `Maven: Release`
Options:
- From: `Branch: master`
- Type: `Milestone`
- Suffix: `-M1` <- this is just an example it can be anything, e.g. `M1` OR `-MILESTONE1`
```

Creates a branch called `release/<version><suffix>`, i.e. `release/8.0.0-M1`, and performs release. No commits are added to `master`.

## Publishing a Release

Publishing a release makes the artifacts available to other projects/teams. Artifacts are pushed to
[GitHub maven repository](https://github.com/orgs/NationalSecurityAgency/packages?repo_name=emissary).
```
Action: `Maven: Publish Packages to GitHub`
Options to use:
- From: `Branch: release/<version>`
```
Pushes release artifacts to a repo using `maven deploy`.
