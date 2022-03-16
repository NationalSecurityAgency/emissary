# Emissary

![Emissary Dark Knight - some code just wants to watch the core burn](emissary-knight.png) 


Table of Contents
=================

* [Emissary](#emissary)
* [Table of Contents](#table-of-contents)
  * [Introduction](#introduction)
  * [Internals](#internals)
    * [Code organization](#code-organization)
    * [Startup](#startup)
    * [Threads](#threads)
      * [Java threads](#java-threads)
      * [Jetty threads](#jetty-threads)
      * [Emissary threads](#emissary-threads)
    * [Processing Flow](#processing-flow)
      * [Processing Routing](#processing-routing)
      * [Workflow](#workflow)
        * [Study](#study)
        * [Id](#id)
        * [Coordinate](#coordinate)
        * [PreTransform](#pretransform)
        * [Transform](#transform)
        * [PostTransform](#posttransform)
        * [Analyze](#analyze)
        * [Verify](#verify)
        * [IO](#io)
        * [Review](#review)
      * [Routing](#routing)
      * [Parsers vs Places](#parsers-vs-places)
  * [Development](#development)
    * [Requirements](#requirements)
      * [Linux or MacOSX operating system](#linux-or-macosx-operating-system)
      * [Java 1\.8](#java-18)
      * [Apache Maven 3\.2](#apache-maven-32)
      * [Git 1\.8\+](#git-18)
    * [Commands](#commands)
      * [Removing anything not being tracked by git](#removing-anything-not-being-tracked-by-git)
      * [Clean](#clean)
      * [Format code](#format-code)
      * [Compile](#compile)
      * [Test](#test)
      * [Test one file](#test-one-file)
      * [Skip tests](#skip-tests)
      * [Package](#package)
      * [Verify](#verify-1)
      * [Code quality](#code-quality)
      * [Code coverage](#code-coverage)
      * [Install](#install)
      * [Deploy](#deploy)
      * [Making a distribution](#making-a-distribution)
    * [Running in Continuous Integration](#running-in-continuous-integration)
      * [Remote debug a Maven test](#remote-debug-a-maven-test)
      * [Potentially speed up the development cycle](#potentially-speed-up-the-development-cycle)
    * [IDE integration](#ide-integration)
      * [Eclipse](#eclipse)
      * [IntelliJ](#intellij)
      * [Netbeans](#netbeans)
  * [Running Emissary](#running-emissary)
    * [Classpath](#classpath)
      * [Development](#development-1)
      * [Distribution](#distribution)
    * [No arguments](#no-arguments)
    * [Help](#help)
    * [Common parameters](#common-parameters)
    * [What](#what)
    * [Server (Standalone)](#server-standalone)
    * [Agents (Standalone)](#agents-standalone)
    * [Pool (Standalone)](#pool-standalone)
    * [Env](#env)
    * [Run](#run)
    * [Server (Cluster)](#server-cluster)
    * [Feed (Cluster)](#feed-cluster)
    * [Agents (Cluster)](#agents-cluster)
    * [Pool (Cluster)](#pool-cluster)
    * [Topology (Clustered)](#topology-clustered)
    * [Running server with SSL](#running-server-with-ssl)
    * [Debugging a command](#debugging-a-command)
      * [Setting up remote debugging in your IDE](#setting-up-remote-debugging-in-your-ide)
  * [Docker](#docker)
    * [Build Emissary Docker Image](#build-emissary-docker-image)
    * [Run Emissary with Docker](#run-emissary-with-docker)
    * [Cluster Mode using Docker Compose](#cluster-mode-using-docker-compose)
  * [Coding standards](#coding-standards)
  * [Troubleshooting](#troubleshooting)
    * [Hanging tests](#hanging-tests)
    * [Can't run tests in Eclipse](#cant-run-tests-in-eclipse)
    * [Running tests in IntelliJ throw an error "Error: Could not find or load main class @\{argLine\}"](#running-tests-in-intellij-throw-an-error-error-could-not-find-or-load-main-class-argline)
  * [Contact Us](#contact-us)
    * [Security related questions](#security-related-questions)

Created by [gh-md-toc](https://github.com/ekalinin/github-markdown-toc.go)

## Introduction

Emissary is a P2P based data-driven workflow engine that runs in a heterogeneous 
possibly widely dispersed, multi-tiered P2P network of compute resources. Workflow 
itineraries are not pre-planned as in conventional workflow engines, but are discovered as 
more information is discovered about the data. There is typically no user interaction in an 
Emissary workflow, rather the data is processed in a goal oriented fashion until it reaches 
a completion state.

Emissary is highly configurable, but in this base implementation
does almost nothing. Users of this framework are expected to provide
classes that extend emissary.place.ServiceProviderPlace to perform
work on emissary.core.IBaseDataObject payloads.

A variety of things can be done and the workflow is managed in
stages, i.e. [STUDY](#study), [ID](#id), [COORDINATE](#coordinate), [TRANSFORM](#transform), [ANALYZE](#analyze), [IO](#io), [REVIEW](#review).

The classes responsible for directing the workflow are the
emissary.core.MobileAgent and classes derived from it, which manage
the path of a set of related payload objects through the workflow and
the emissary.directory.DirectoryPlace which manages the available
services, their cost and quality and keep the P2P network connected.

## Internals

### Code organization

Emissary is laid out in the following package structure:

* admin - code that starts Places
* analyze - interfaces/abstract classes for Analyzers and Extractors
* client - client classes and response object
* command - JCommander classes
* config - code for parsing configuration files
* core - core data structures, including the IBaseDataObject
* directory - mapping of Places to functionality 
* id - functionality to implement identification routines
* jni - hooks for running JNI
* kff - "Known File Filter" - mean to hook in checking hashes against databases of hashes
* log - various add-ons to increased logging functionality
* parser - basic routines for providing parsing capability
* pickup - code for picking inputs
* place - various basic Places, including wrappers around other programming languages and execs
* pool - thread pooling 
* roll - Rollable framework for handling output
* server - embedded Jetty code and all the accompanying endpoints
* test - base test classes used in Emissary and other projects
* util - assorted grab-bag of utilities for processing data/text

### Startup

There are two ways to run Emissary - through a "standalone" mode where a node runs in isolation and 
"cluster" where nodes connect and form P2P network.  All startup goes through the emissary.Emissary class.

### Threads

Emissary runs several threads to keep track of its processing.  If you `jstack <emissary pid>`
you will see all the running threads and what code they are currently executing.

#### Java threads

Typical Java threads 

  * "Attach Listener"
  * "C1 CompilerThread" {1..n}
  * "Signal Dispatcher"
  * "Finalizer"
  * "Reference Handler"
  * "VM Thread"
  * "GC task thread" {1..n}
  * "VM Periodic Task Thread"
  * "DestroyJavaVM"
  * "RMI TCP Accept-0"
  * "Service Thread"

#### Jetty threads

Jetty threads used for handling requests

  * "org.eclipse.jetty.server.session.HashSessionManager" {1..n}
  * "qtp715521683" {1.n}

#### Emissary threads
  * "FileInput-target/data/InputData" (standalone) - watches directory and processes files that show up
  * "FileQueServer" (cluster) - communicated with the Feeder and gets files for processing
  * "HeartbeatManager" (cluster) - tracks the state of the cluster
  * "ResourceWatcher" - tracks the resource being consumed by Emissary/Place
  * "MoveSpool" - this tracks the incoming data sent to Emissary
  * "MobileAgent" {1..n} - these are the processing thread that work on the data

### Processing Flow

There are various adapters to abstract where data comes from.  These sources ultimately end up in PickUpPlace broken out
 sessions.  PickUpPlace then pulls a MobileAgent from the thread pool and hands off the session for processing.

#### Processing Routing

MobileAgent walks a given session through all the relevant places.  This happens by 
MobileAgent#go() -> MobileAgent#agentControl() -> MobileAgent#getNextKey() -> MobileAgent#getNextKeyFromDirectory which 
ultimately hooks into DirectoryPlace#nextKeys().

#### Workflow

The Emissary data driven workflow has stages. These are specified by emissary.core.Stage. The stages of the workflow 
are used to control certain aspects of unwrapping and processing and help to ensure that the workflow can always make 
progress on the task at hand.

There are guidelines on how the workflow stages should be used. These guidelines are not currently enforced but 
deviations from these guidelines might be an indication that more thought should be applied.

##### Study

The name for this stage came from the idea behind [Perl's Regex study](http://perldoc.perl.org/functions/study.html) 
method -- some work that can be done up-front to optimize or prepare for the remaining work to come.

This stage is designed to make no modifications to the data itself but can
be used for

  * policy enforcement - ensuring that the incoming payload meets some minimum criteria or quality standards
  * provenance - emitting events at the very beginning of the workflow that might indicate to an external system that
   workflow has begun on a certain item.

##### Id

The Id phase of the workflow is for identification of data. Service places operating in this phase
are expected to modify the currentForm and fileType of the payload. They are expected to not change
the payload bytes of the data or extract any other metadata, unwrap child payload objects, or extract
metadata.

##### Coordinate

The coordinate phase of the workflow is a natural fit for the emissary.place.CoordinationPlace
which is designed to wrap any number of otherwise instantiated processing places and lock down
the workflow among them without regard to cost. So that we don't have to create and remember
an artificially low cost for the CoordinatePlace, setting into this phase of the work flow causes
it to run before any Transform or PreTransform places that it might be coordinating for.

##### PreTransform

If there is the need to record provenance or export data for intermediate forms this is a place
where that could happen.

##### Transform

The Transform phase of the workflow is expected to modify the bytes of the payload, set the current form to something 
new when appropriate, extract nested child payload objects, record metadata. After a Transform phase service place the 
workflow engine will go back to the ID phase and start evaluating for places that are interested in the currentForm.

##### PostTransform

Since the other primary phases of the workflow had "hook" stages in between them, it seemed
good to have one here too. Nothing past this phase will cause the workflow engine to go back
to the ID phase of the workflow, things must continue forward in the workflow stages from here.

##### Analyze

The analyze phase is designed to collect metadata and add value in ways that do not affect the
currentForm, fileType or bytes of the payload. In future versions Analyze places that apply
may be done in parallel.

##### Verify

Little used, but serves as a catch-all point before the IO stage.

##### IO

The IO stage is when data is available for output. This is a blocking point in the workflow as
all items in the payload family tree must be prepared to transition to the IO stage
before we can proceed.

##### Review

Post IO most of the currentForms are stripped off as they are handled by the IO places.
The itinerary is available and could be used in post-processing provenance events.

#### Routing

Decisions on how data gets sent to Places revolves around the concept of DirectoryEntry, which is also referred to as a 
Key within the code.  A DirectoryEntry is of the form:

FORM.SERVICENAME.STAGE.URL$EXPENSE

Routing uses a combination of the FORM, STAGE and EXPENSE to decide where to send the data.  The STAGEs define an order 
for the processing flow within the framework.  Places are bound to specific STAGEs and will not be run outside of that 
STAGE.  The current STAGE list and order is:

Actual name | Description | Run in parallel?
------------|-------------|------------------
STUDY | prepare, coordinate idents | No
ID | identification phase | No
COORDINATE | Coordinate processing | No
PRETRANSFORM | before transform hook | Yes
TRANSFORM | transformation phase | No
POSTTRANSFORM | after transform hook | Yes
ANALYZE | analysis/metadata generation | Yes
VERIFY | verify for output | No
IO | output | No
REVIEW |  finish off | No

#### Parsers vs Places

Emissary Places are for processing, transforming, identifying, analyzing data of interest. As items
are unwrapped and processed the results are all kept together in a "family tree". This structure
is a List<IBaseDataObject> in the code. The list can be sorted in family order by using the
emissary.util.ShortNameComparator. 

Sometimes, for our own convenience, or for dealing with formats provided by up-stream systems,
the data arrives packaged in various formats. These containers can have multiple items that
should not be logically related in a family tree. The Emissary Parser framework deals with this
situation.

Parsers need to make a quick identification of the format of the container. This is called
out by the emissary.parser.ParserFactory and uses the emissary.parser.DataIdentifier as the
engine to perform the identification. The engine is configured by name and can be replaced
by anything that meets the required interface. The name of the format identified should
have parsers that meet the SessionParser interface configured into the factory.

Once the container is identified, items are parsed out of it, built into IBaseDataObject instances
by the SessionParser appropriate for that data type, and handed over to MobileAgent instances
for processing as they become available from the pool. It is important to have in mind that the
Parser framework is operating on the single-threaded side of the system and handing items over to the
multi-threaded (MobileAgent) side of the system. This implies that the parsers are fast enough
to keep the number of configured threads busy.

## Development

### Requirements

#### Linux or MacOSX operating system

Currently, developing and running natively on Windows is not
supported.  Use a virtualized Linux OS if you are using Windows.  

In terms of Linux distributions, the two most widely used on the Emissary
codebase are CentOS 6.X and Ubuntu 12.X.

#### Java 1.8

Java 1.6 and Java 1.7 are no longer supported in this codebase only 
[Java 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html).  
OpenJDK should work, although most testing is done with Oracle's JDK.  Be sure to use a recent version of
1.8 as there are bugs in earlier versions.

#### Apache Maven 3.2

Download and install [Apache Maven 3.2](http://maven.apache.org).
Some maven commands are shown below.  A great place to start with Maven is the
[5 min tutorial](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html).
Another great resource explains the
[lifecycles](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).
We cover [configuring maven](#configuring-maven) below with Emissary
specific settings.

As you go through this guide, keep in mind the steps in the [default lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html#Lifecycle_Reference)
are cumulative.  If you run *test*, Maven will run *compile* among other
steps first.  Similarly, when you run *package*, Maven will run
*compile* and *test* along with other steps first.

We also use [maven profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html).
To activate specific functionality.  To activate profile abc, you would add `-Pabc`.  Mutliple profiles can be
activated at once if you separate them with a comma.  Something like `-Pprofile-1,profile-2`. 

#### Git 1.8+

In order to check out the code, you must install [git](https://git-scm.com/).
Github has a
[page](https://help.github.com/articles/good-resources-for-learning-git-and-github/)
with links to some good tutorials.  The
[book by Scott Chacon](http://git-scm.com/book) is particularly good
and available to read online.

### Commands

#### Removing anything not being tracked by git

Not a Maven command, but useful to get rid of any file not tracked by
git.  This will remove the jflex generated files, as well as any files
setup by your IDE.

```
git clean -dxf
```

#### Clean

Remove everything under 'project.build.directory' which will be target from the command line

```
mvn clean
```

#### Format code

The autoformat profile is run unless you use '-DskipFormat'.  There is a java source code formatter that
uses a [special file](link here)
 to format Java source code and formatter to sort pom files.  The 2 formatter are attached
to the *process-sources* lifecycle, which as you know from reading the lifecycles is run before *compile*.  So
typically you do not need to run this separately.  But you could run the following to just format everything:

```
mvn clean process-sources
```

TODO: fill this out when we have a resources jar

#### Compile

Will compile all Java code under the src/main directory.  Useful if you change something and want to run Emissary
with the changes, but that is discussed in the section below about running.

```
mvn clean compile
```

#### Test

Run all tests

```
mvn clean test
```

Inspect the output to see what tests have failed.  More information
about failed tests will be under *target/surefire-tests* should need
to see more output for a failed test.

As configured, each run will execute the tests in random order.  This
is intentional to highlight dependencies between tests.

Currently, the POM used .5C for the forkCount when running tests.  This means
half the number of cores on your box.  So if you have 4 cores, it will use 2 forks.

If you have a beefy box, you may see a speed up in tests by setting surefire.forkCount to
something else.  You can hardcode a number, like 4 or use #C to multiply by the 
number of cores.  For example, but YMMV

```
mvn -Dsurefire.forkCount=2C clean test
```

#### Test one file

If you want to run just one test file from the command line, do this:

```
mvn clean test -Dtest=SomeClassTest
```

#### Skip tests

You should never do this, but if you need it, add the '-DskipTest' option to the Maven command
to avoid running any tests

#### Package

Create a jar with

```
mvn clean package
```

#### Verify

Currently, there are no integration tests running.  But when there are, those only run during the *verify*
lifecycle, which is run before install.  So if you just wanted to run up to the integration tests, do

```
mvn clean verify
```

Javadoc generation is also attached to *verify* phase.

#### Code quality

Code quality reports are also attached to the *verify* phase, but are in a couple of Maven profiles. To
activate all the code quality profiles, run the following.

```
mvn clean package site -Dcode-quality site:stage -DstagingDirectory=${PWD}/target/staging
```

Because we are viewing locally, the *stagingDirectory*
is necessary to tell Maven where to write the site.  You may then view
the reports by opening target/site/project-reports.html.  The entire site 
is at target/staging/index.html

You can also run this to start a local server with the site
```
mvn clean package site -Dcode-quality site:run
```
Then hit http://localhost:9000

#### Code coverage

Since the whole *site* lifecycle can take some time, you can get just the code coverage with

```
mvn clean verify -Dcode-quality
```

Then open target/site/jacoco/index.html in your browser to see code coverage.

If you want to see more of the code quality outputs, you must run the full site 
generation from [above](code-quality)

#### Install

The *install* task runs after *verify* and copies any included
artifacts made in the *package* phase to your local Maven repo.  This
repository is usually ~/.m2/repository unless you set to be somewhere
else.  See the [settings.xml.sample](settings.xml.sample) file for
details about moving that off.  It is useful, for example, when your
home directory is slow.  You can move your Maven repo to a faster
drive.

Run the *install* task with

```
mvn clean install
```

If any phase prior to *install* fails, no artifacts will be copied.

#### Deploy

TODO: Write this documentation when we figure it out

#### Making a distribution

Full distributions create a zip bundle that you can move to another computer. To make a distribution, run:

```
mvn clean package -Pdist
```

After a successful build, there will be a bundle located in the target directory:

```
target/emissary-<version>-dist.tar.gz
```

This zip file can be transferred to your cluster, extracted, configurations tweaked, then launched with the ```emissary```
script. Example to run a distribution:

```
tar -xvf emissary-<version>-dist.tar.gz
cd emissary-<version>
./emissary server -a 2
```

This starts a standalone emissary server. For more emissary options, see [running emissary](#running-emissary).

### Running in Continuous Integration

If you wanted to run CI on a jenkins server or something similar, use
the following command.  

```
mvn clean install site -Dcode-quality -DskipFormat
```

The *skipFormat* is used because sort-pom and the source code
formatter plugin rewrite files in the git working directory.  This
would not be good and could cause conflicts on the next
checkout.

#### Remote debug a Maven test

It is straightforward to attach a remote debugger to your tests, as
long as you tell the surefire plugin about it.  Because Emissary runs 
tests in jvm forks, the mvnDebug command will not work.  Here is an 
example of running.  You have already seen the *-Dtest=* option.  The 
*-Dmaven.surefire.debug* option will stop the JVM until a remote
debugger attaches, and you can then step through the code.

```
mvn clean test -Dtest=ServerCommandTest -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE"
```

#### Potentially speed up the development cycle

The above commands all have 'clean' in the command.  It is safest to do that.  But it is 
possible to omit the 'clean' and maven will then only compile files that have changed.  This can
speed up things, but for Emissary it is unlikely to speed up much.

### IDE integration

The poms are setup to use a different *project.build.directory* depending on the IDE you are using.  The
reason for this is so that running Maven on the command line and using your IDE will not interfere with each
other.  So keep in mind, your IDE will no longer build to the /target directory.

You can run/debug the emissary.Emissary class in your IDE.  Just setup the same arguments you would use
on the command line.  Running tests in your IDE is also supported.  IntelliJ and Netbeans will read the 
surefire plugin configuration and use it.  Eclipse does not read the surefire configuration, so the process
is explained below.  

#### Eclipse

When [Eclipse](https://eclipse.org/) runs with the [M2E](http://www.eclipse.org/m2e/), a system property named
*m2e.version* is set.  We can use that fact to activate the *eclipse* profile in the pom and then change
the *project.build.directory* to target-eclipse.  I will be a sibling to the target directory.  The `mvn clean` command 
will not remove this directory, but cleaning from Eclipse should remove it.

The *eclipse* profile also configures some exclusions for the m2e plugin so it doesn't warn about not knowing how to 
handle some of the Maven plugins used.

When running tests in Eclipse, so you will need to set the environment variable PROJECT_BASE to the directory
eclipse is using to build the project.  So in the run configuration of a test, add the following to the 
Run Configuration -> Environment -> New

```
PROJECT_BASE = ${project_loc}/target-eclipse
```

Unfortunately you will have to do this for every test, that's Eclipse with the M2E plugin for you.

#### IntelliJ

When [IntelliJ](https://www.jetbrains.com/idea/) runs a Maven project, a system property named
*idea.version* is set.  We use this property to activate the *intellij* profile in the 
 pom and change the *project.build.directory* to target-idea, which is a sibling to the target 
directory.  The `mvn clean` command will not remove this directory, but cleaning from IntelliJ should remove it.

Because we are using the jacoco plugin, we must put @argLine at the beginning of the surefire plugin
to allow jacoco to add what it needs.  See the second answer [here](http://stackoverflow.com/questions/23190107/cannot-use-jacoco-jvm-args-and-surefire-jvm-args-together-in-maven).
But that can cause IntelliJ to report this if you run a test

```
Error: Could not find or load main class @{argLine}
```

The fix is to tell IntelliJ not to use that.  Uncheck argLine from 
Preferences -> Build,Execution,Deployment -> Build Tools -> Maven -> Running Tests.
See [stackoverflow](http://stackoverflow.com/questions/24115142/intellij-error-when-running-unit-test-could-not-find-or-load-main-class-suref) 
for more info


#### Netbeans

Unfortunately, I could not find a system property [Netbeans](https://netbeans.org/) sets when running 
a Maven project, so we are unable to automatically activate the *netbeans* profile like we can with 
the *eclipse* and *intellij* profile.  Fortunately, it is easy to add a system property yourself.  
Open Netbean's Preferences -> Java -> Maven and then add `-Dnetbeans.ide` into the *Global Execution Options*.  
Afterwards, the *netbeans* profile will be activated in the pom and change 
the *project.build.directory* to target-netbeans, which is a sibling to the target directory.  
The `mvn clean` command will not remove this directory, but cleaning from Netbeans should remove it.

## Running Emissary

There is one bash script in Emissary that runs everything.  It is in the top level Emissary directory.
This script setups up a few environment variables and Java system properties.  It also sets up the 
classpath in a couple of ways.  More on that in a minute.  The script then runs the [emissary.Emissary](https://github.com/NationalSecurityAgency/emissary/blob/master/src/main/java/emissary/Emissary.java) class
which has several [JCommander](http://jcommander.org/) commands available to handle different functions.

### Classpath

The classpath is setup in one of 2 ways.  

#### Development
If the *emissary* script is a sibling to pom.xml, it is assumed you are running
from the git checkout.   If a file named .mvn-classpath exists, that file is used to setup the classpath.  If that file
does not exist, it created using a Maven command then the Emissary class is launched.  _NOTE_ if any
dependencies are changed in the pom, then this file needs to be removed and regenerated.

#### Distribution
If the *emissary* script does not have a pom.xml file as a sibling, then it is assuming
you are running from distribution.  If you run `mvn clean package -Pdist`, a tar.gz file
will be placed in the target directory.  Unpack that file, change into the directory and you will
see the *emissary* but no pom, so it will load class from the lib directory

### No arguments
If the *emissary* script is run without any arguments, you will get a listing 
of all the configuration subcommands and a brief description.

```
./emissary
```

### Help

Running `./emissary help` will give you the same output as running with no arguments.  If you want to see more
detailed information on a command, add the command name after help.  For example, see all the 
arguments with descriptions for the *what* command, run:

```
./emissary help what
```

### Common parameters

The rest of commands all have *(-b or --projectBase)* arguments that can be set, but it must match PROJECT_BASE.
The config directory is defaulted to <projectBase>/config
but can also be passed in with *(-c or --config)*.  When running from the git checkout, you should use
*target* as the projectBase.  Feel free to modify config files in target/config before you start.

Logging is handled by [logback](Logback Home).  By default, the file logback.xml in the \<configDir\> will be 
used.  You can point to another file with the *--logbackConfig* argument.  Feel free to modify the file
in target/config if you want different logging.  The logback.xml is configured to only log to the console 
currently.

See the *help -c <commandName>* for each command to get more info.

### What

This command will use the configured engines to identify the file.  Emissary currently only comes with the 
SizeIdPlace, so the id will TINY or SMALL etc.  See that class for more info.  The *-i or --input* argument
is required as well as *-b*.  Here is how to run the command

```
./emissary what -i <path to some file>
```

### Server (Standalone)

This command will start up an Emissary server and initialize all the places, 
a pickup place, and drop off filters that are configured.  It will start in 
standalone mode if *-m or --mode* is not specified.  By default, the number of 
MobileAgents is calculated based on the specs of the machine.  On modern computers, 
this can be high.  You can control the number of agents with *-a or --agents*.  Here 
is an example run.

```
./emissary server -a 2
```

Without further configuration, it will start on http://localhost:8001.  If you browse to that 
url, you will need to enter the username and password defined in target/config/jetty-users.properties,
which is emissary and emissary123.

The default PickUpPlace is configured to read files from _target/data/InputData_.  If you copy
files into that directory, you will see Emissary process them.  Keep in mind, only toUpper and toLower are
configured, so the output will be to interesting.

### Agents (Standalone)

The agents command shows you the number of MobileAgents for the configured host and what those
agents are doing.  By default, the port is 9001, but you can use *-p or --port* to change that.  
Assuming you are running on 8001 from the server command above, try:

```
./emissary agents -p 8001
```

### Pool (Standalone)

Pool is a collapsed view of agents for a node.  It too defaults to port 9001.  To run for the 
standalone server started above run

```
./emissary pool -p 8001
```

This command is more useful for a cluster as it a more digestible view of every node.

### Env

The Env Command requires a server to be running.  It will ask the server for some configuration
values, like PROJECT_BASE and BIN_DIR.  With no arguments, it will dump an unformatted json response.

```
./emissary env
```

But you can also dump a response suitable for sourcing in bash.

```
./emissary env --bashable
```

Starting  the Emissary server actually calls this endpoint and dumps out $PROJECT_BASE}/env.sh 
with the configured variables.  This is done so that shell scripts can `source $PROJECT_BASE}/env.sh` 
and then have those variable available without having to worry with configuring them elsewhere.

### Run

The Run command is a simple command to execute the main method of the given class.  For example

```
./emissary run emissary.config.ConfigUtil  <path_to_some_cfg_file>
```

If you need to pass flags to the main method, use *--* to stop parsing flags and simply pass them along.

```
./emissary run emissary.config.ExtractResource -- -o outputdir somefile
```

### Server (Cluster)

Emissary is fun in standalone, but running cluster is more appropriate for real work.  The way to run clustered
is similar to the standalone, but you need to *-m cluster* to tell the node to connect to other nodes.  In
clustered mode Emissary will also startup the PickUpClient instead of the PickUpPlace, so you will need to
start a feeder.

Look at the target/config/peers.cfg to see the rendezvous peers.  In this case, there are 3.  Nodes running
on port 8001 and 9001 are just Emissary nodes.  The node running on 7001 is the feeder.  So let's start up
8001 and 9001 in two different terminals.

```
./emissary server -a 2 -m cluster
./emissary server -a 2 -m cluster -p 9001
```

Because these nodes all know about ports 8001, 9001 and 7001, you will see errors in the logs as they
continue to try to connect.  

Note, in real world deployments we don't run multiple Emissary processes on the same node.  You can configure the
hostname with *-h*.

### Feed (Cluster)

With nodes started on port 8001 and 9001, we need to start the feeder.  The feed command uses port 7001 by default,
but we need to setup a directory that the feeder will read from.  Files dropped into that directory will be available 
for worker nodes to take and the work should be distributed amongst the cluster.  Let's startup the feed with

```
mkdir ~/Desktop/feed1
./emissary feed -i ~/Desktop/feed1/
```

You should be able to hit http://localhost:8001, http://localhost:9001 and http://localhost:7001 in the browser and
look at the configured places.  Drop some files in the ~/Desktop/feed1 and see the 2 nodes process them.  It may 
take a minute for them to start processing

### Agents (Cluster)

Agents in clustered mode again shows details about the mobileAgents.  It starts at with the node you 
configure (localhost:9001 by default), then calls out to all nodes it knows about and gets the same 
information.  Run it like so:

```
./emissary agents --cluster
```

### Pool (Cluster)

Pool in clustered mode also does the same pool in standalone.  It starts at the node (locahost:9001) by default
then goes to all the nodes it knows about and aggregates a collapsed view of the cluster.  Run it with

```
./emissary pool --cluster
```

### Topology (Clustered)

The topology talks to the configured node (localhost:8001 by default) and talks to every it knows about.
The response is what all those nodes know about, so you can build up a network topology of your cluster.
Run it with 

```
./emissary topology
```

### Running server with SSL

The keystore and keystore password are in the emissary.client.EmissaryClient-SSL.cfg file.  Included and configured
by default is a sample keystore you can use for testing this functionality.  It is not recommended to use in 
production environments.  To use your own keystore, change configuration values in the 
emissary.client.EmissaryClient-SSL.cfg file.

Standalone

```
./emissary server -p 8443 --ssl
```

Clustered
```
./emissary server -p 8443 --ssl --mode cluster
./emissary server -p 9443 --ssl --mode cluster
mkdir ~/Desktop/feed1
./emissary feed -p 7443 --ssl -i ~/Desktop/feed1/
````

### Debugging a command

The _emissary_ script allows you to remote debug any command.  Simply use `DEBUG=true` before the command and it will 
wait for a remote debugger to attach at port 8000.  If you want to change that port, use `DEBUG_PORT` instead.  
For example:

```
DEBUG=true ./emissary <command> <command_options>
```

or 

```
DEBUG_PORT=<someport> ./emissary <command> <command_options>
```

You can also debug what the script is doing by prepending the following

```
DEBUG_CMD=true
```

This will output the command that is going to be run and show you how JCommander is parsing the arguments.

#### Setting up remote debugging in your IDE

For info on how to setup remote debugging in your IDE, reference the
following articles:

- for
[Eclipse](http://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.jubula.client.ua.help%2Fhtml%2Freference%2Fnode47.html)
- for [IntelliJ](https://www.jetbrains.com/idea/help/run-debug-configuration-remote.html)

## Docker

Docker needs to be installed locally to build images. If developing on Linux, the maven plugin for Docker cannot be run 
with ```sudo```. Please see the linux-postinstall page in the Docker documentation for instructions to manage Docker as 
a non-root user. 

### Build Emissary Docker Image

Maven can be used to create the docker image. There is a profile that was created to run the docker image build that, by default,
has been turned off. We'll need to add the docker profile, along with the dist profile, to trigger an assembly. From the
project root, run the following maven command:

```
mvn clean package -Pdist,docker
```

Alternatively, we can use Docker directly. First run a full maven build and then run the ```docker build``` command:

```
mvn clean package -Pdist
docker build -f contrib/docker/Dockerfile . -t emissary:latest
```

### Run Emissary with Docker

Once the image is successfully built, the image should be in your list of local images. Run ```docker images``` and there
should be an entry for REPOSITORY:emissary and TAG:latest:

```
[emissary]$ docker images
   REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
   emissary            latest              e740d5f23a79        4 minutes ago       620MB
```

To run files through Emissary, we'll need to volume mount local directories into the running container. Let's create two
local directories for input/output to Emissary:

```
mkdir -p target/data target/localoutput
```

Now that we have two target directories, we can use the ```-v``` option to mount them into the container. To start 
Emissary, run the sample command:

```
docker run -it --rm -v ${PWD}/target/data:/opt/emissary/target/data:Z -v ${PWD}/target/localoutput:/opt/emissary/localoutput:Z --name emissary emissary
```

If you are running into permission issues on Linux, an option is to run the container with your uid and gid by adding 
```--user $(id -u):$(id -g)``` to  the above command.

Once Emissary starts up, we should see a log line that says: "Started EmissaryServer at http://localhost:8001." We now 
can copy files into the input directory for Emissary to process:

```
cp emissary-knight.png target/data/InputData/
```

When the processing has finish, the files will be moved to ```target/data/DoneData```. All extracted content can be 
found in the local ```target/localoutput``` directory. If using the default Emissary configuration, there should be 
output in the ```target/localoutput/json``` directory. Output should look similar to:

```
[emissary]$ ls target/localoutput/json/
202102281211000localhost.json_1ddf101f-0c32-4b10-ba14-da3521c8ec68.bgpart
202102281211000localhost.json_1ddf101f-0c32-4b10-ba14-da3521c8ec68.bgpart.bgjournal
```

To monitor Emissary, we need to connect to the running container. If we want to run the agents command,
we simply need to run the following command:

```
docker exec -it emissary ./emissary agents -p 8001 --mon
```

To see the environment settings:

```
docker exec -it emissary ./emissary env
```

If you want to connect to the web front-end for Emissary, some additional steps are need to configure jetty. We need to
give the container a hostname and pass that onto Emissary from the command line. Sample command:

```
docker run -it --rm --name emissary --hostname emissary-001 -p 8001:8001 emissary server -a 2 -p 8001 -s http -h emissary-001
```

Then from a browser, assuming container is running locally, go to http://localhost:8001/ to see the endpoints.

### Cluster Mode using Docker Compose
We can use a Docker compose file to simulate cluster mode. We'll start a feeder and two workers by default. To start the
cluster, run the sample docker-compose.yml file. From the root of the project, run:

```
docker-compose -f contrib/docker/docker-compose.yml up
```

Use docker copy to run a file through Emissary:

```
docker cp emissary-knight.png docker_emissary-feeder_1:/opt/emissary/target/data/InputData/
```

### Optionally Build and Test Emissary with a Docker Dev Image

Let's use the dev image to build Emissary with Maven and Java:
```
docker build . -t emissary:test -f contrib/docker/Dockerfile.dev
```
Once the build succeeds, we can start a container:
```
docker run -it --rm -p 8001:8001 --hostname emissary --name emissary emissary:test
```

## Coding standards

Many of coding standards are defined the formatter config file.  Here are some 
additional things to consider when developing.

* Never call System.exit for any reason.
* Never use Thread.stop for any reason.
* Never cut and paste any code -- refactor instead.
* Configure your editor to use spaces to indent code not tabs.
* Passing tests should be completely silent (no output).
* Never write to System.out except from main.
* Fix all compiler warnings and lint before committing.
* Fix all javadoc warnings before committing (will have to run mvn verify)
* The repository version should always compile and pass all the tests.
* If you fix a bug, add a test.
* If you answer a question, add some documentation.

## Troubleshooting

### Hanging tests

Emissary uses fake host names in some of the tests.  If you happen to be on a Verizon network, you
may get redirected for unknown hosts to 92.242.140.21.  To test this run

```
nslookup somehost
```

If you have this situation, then it is recommended to Google's DNS servers.  [Here](https://developers.google.com/speed/public-dns/docs/using) 
are some instructions from Google.

### Can't run tests in Eclipse

Did you read [the Eclipse section](#eclipse) carefully?

### Running tests in IntelliJ throw an error "Error: Could not find or load main class @{argLine}"

Did you read [the IntelliJ section](#intellij) carefully?

## Contact Us

If you have any questions or concerns about this project, you can contact us at: EmissarySupport@evoforge.org

### Security related questions

For security questions and vulnerability reporting, please refer to SECURITY.md
