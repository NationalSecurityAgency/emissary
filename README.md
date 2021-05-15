# Emissary

![Emissary Dark Knight - some code just wants to watch the core burn](emissary-knight.png) 


Table of Contents
=================

  * [Introduction](#introduction)
  * [Internals](#internals)
    * [Code Organization](#code-organization)
    * [Startup](#startup)
    * [Emissary Threads](#emissary-threads)
    * [Processing Flow](#processing-flow)
    * [Processing Routing](#processing-routing)
    * [Workflow and Stages (Study, Id, Coordinate, Transform, Analyze, Etc)](#workflow-and-stages)
    * [Routing](#routing)
    * [Parsers vs Places](#parsers-vs-places)
  * [Development](#development)
    * [Requirements](#requirements)
    * [Commands](#commands)
    * [Tips](#tips)
    * [IDE Integration](#ide-integration)
  * [Running Emissary](#running-emissary)
  * [Docker](#docker)
  * [Coding standards](#coding-standards)
  * [Troubleshooting](#troubleshooting)
  * [Contact Us](#contact-us)
  * [Security Questions](#security-questions)

### Additional Documentation
* [**Docker**](DOCKER.md)
* [**Emissary Commands**](EMISSARY-COMMANDS.md)
* [**Develoment/IDE Setup**](DEVELOPMENT.md)
* [**Security**](SECURITY.md)

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

### Code Organization

Emissary is laid out in the following package structure:

* **admin** - code that starts Places
* **analyze** - interfaces/abstract classes for Analyzers and Extractors
* **client** - client classes and response object
* **command** - JCommander classes
* **config** - code for parsing configuration files
* **core** - core data structures, including the IBaseDataObject
* **directory** - mapping of Places to functionality 
* **id** - functionality to implement identification routines
* **jni** - hooks for running JNI
* **kff** - "Known File Filter" - mean to hook in checking hashes against databases of hashes
* **log** - various add-ons to increased logging functionality
* **output** - code relating to tasks of writing and manipulating output
* **parser** - basic routines for providing parsing capability
* **pickup** - code for picking inputs
* **place** - various basic Places, including wrappers around other programming languages and execs
* **pool** - thread pooling 
* **roll** - Rollable framework for handling output
* **server** - embedded Jetty code and all the accompanying endpoints
* **test** - base test classes used in Emissary and other projects
* **transform** - base emissary places that transform the data
* **util** - assorted grab-bag of utilities for processing data/text

### Startup

There are two ways to run Emissary - through a "standalone" mode where a node runs in isolation and 
"cluster" where nodes connect and form P2P network.  All startup goes through the emissary.Emissary class.

### Emissary Threads

Emissary runs several threads to keep track of its processing.  If you `jstack <emissary pid>`
you will see all the running threads and what code they are currently executing.

**Emissary Threads**
  * "FileInput - target/data/InputData" (standalone) - watches directory and processes files that show up
  * "FileQueServer" (cluster) - communicated with the Feeder and gets files for processing
  * "HeartbeatManager" (cluster) - tracks the state of the cluster
  * "ResourceWatcher" - tracks the resource being consumed by Emissary/Place
  * "MoveSpool" - this tracks the incoming data sent to Emissary
  * "MobileAgent" {1..n} - these are the processing thread that work on the data
  
**Other Threads**
* **Typical Java threads**
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
* **Jetty threads** - Jetty threads used for handling requests
  * "org.eclipse.jetty.server.session.HashSessionManager" {1..n}
  * "qtp715521683" {1.n}

### Base Data Object

Data is sessionized and passed through Emissary in the form of a [BaseDataObject](src/main/java/emissary/core/BaseDataObject.java).

### Processing Flow

There are various adapters to abstract how data is picked up by Emissary.  Ultimately the data is received in PickUpPlace and is broken out
 into BaseDataObject sessions.  PickUpPlace then pulls a MobileAgent from the thread pool and hands off the session for processing in [assignToPooledAgent()](src/main/java/emissary/pickup/PickUpPlace.java).

### Processing Routing

MobileAgent walks a given session through all the relevant places.

There are two types of MobileAgent:
* [MobileAgent](src/main/java/emissary/core/MobileAgent.java) - Used for processing a single payload
* [HDMobileAgent](src/main/java/emissary/core/HDMobileAgent.java) - Used for processing a bundle of payloads

Processing starts when PickUpPlace's assignToPooledAgent() calls [agent.go(payload, startingLocation)](src/main/java/emissary/pickup/PickUpPlace.java)

The MobileAgent processing loop is handled in [agentControl()](src/main/java/emissary/core/MobileAgent.java)

To identify which ServiceProviderPlace the MobileAgent will visit next, it calls [getNextKey()](src/main/java/emissary/core/MobileAgent.java)

### Workflow and Stages

The Emissary data-driven workflow processes in stages. These are specified by [emissary.core.Stage](src/main/java/emissary/core/Stage.java). The stages of the workflow 
are used to control certain aspects of unwrapping and processing and help to ensure that the workflow can always make 
progress on the task at hand.

There are guidelines on how the workflow stages should be used. These guidelines are not currently enforced but 
deviations from these guidelines might be an indication that more thought should be applied.

Stage Name    | Description                  | Run in Parallel?
:-------------|:-----------------------------|:----------------:
STUDY         | Prepare, coordinate idents   | No
ID            | Identification phase         | No
COORDINATE    | Coordinate processing        | No
PRETRANSFORM  | Before transform hook        | Yes
TRANSFORM     | Transformation phase         | No
POSTTRANSFORM | After transform hook         | Yes
ANALYZE       | Analysis/metadata generation | Yes
VERIFY        | Verify output                | No
IO            | Output                       | No
REVIEW        | Finish off                   | No

* #### Study
  * The name for this stage came from the idea behind [Perl's Regex study](http://perldoc.perl.org/functions/study.html) 
method -- some work that can be done up-front to optimize or prepare for the remaining work to come.
  * This stage is designed to make no modifications to the data itself but can
be used for:
    * policy enforcement - ensuring that the incoming payload meets some minimum criteria or quality standards
    * provenance - emitting events at the very beginning of the workflow that might indicate to an external system that
   workflow has begun on a certain item.

* #### Id

   * The Id phase of the workflow is for identification of data. Service places operating in this phase
are expected to modify the currentForm and fileType of the payload. They are expected to not change
the payload bytes of the data or extract any other metadata, unwrap child payload objects, or extract
metadata.

* #### Coordinate

  * The coordinate phase of the workflow is a natural fit for the emissary.place.CoordinationPlace
which is designed to wrap any number of otherwise instantiated processing places and lock down
the workflow among them without regard to cost. So that we don't have to create and remember
an artificially low cost for the CoordinatePlace, setting into this phase of the work flow causes
it to run before any Transform or PreTransform places that it might be coordinating for.

* #### PreTransform

  * If there is the need to record provenance or export data for intermediate forms this is a place
where that could happen.

* #### Transform

  * The Transform phase of the workflow is expected to modify the bytes of the payload, set the current form to something 
new when appropriate, extract nested child payload objects, record metadata. 
  * **After a Transform phase service place the 
workflow engine will go back to the ID phase** and start evaluating for places that are interested in the currentForm.

* #### PostTransform

  * Since the other primary phases of the workflow had "hook" stages in between them, it seemed
good to have one here too. Nothing past this phase will cause the workflow engine to go back
to the ID phase of the workflow, things must continue forward in the workflow stages from here.

* #### Analyze

  * The analyze phase is designed to collect metadata and add value in ways that do not affect the
currentForm, fileType or bytes of the payload. In future versions Analyze places that apply
may be done in parallel.

* #### Verify

  * Little used, but serves as a catch-all point before the IO stage.

* #### IO

  * The IO stage is when data is available for output. This is a blocking point in the workflow as
all items in the payload family tree must be prepared to transition to the IO stage
before we can proceed.

* #### Review

  * Post IO most of the currentForms are stripped off as they are handled by the IO places.
The itinerary is available and could be used in post-processing provenance events.

### Routing

Decisions on how data gets sent to Places revolves around the concept of DirectoryEntry, which is also referred to as a 
Key within the code.  A DirectoryEntry is of the form:
FORM.SERVICENAME.STAGE.URL$EXPENSE

Routing uses a combination of the FORM, STAGE and EXPENSE to decide where to send the data.  The STAGEs define an order 
for the processing flow within the framework.  Places are bound to specific STAGEs and will not be run outside of that 
STAGE.  The current STAGE list and order is:

### Parsers vs Places

Emissary Places are for identifying, processing, transforming, and analyzing data of interest. 

As items
are unwrapped and processed the results are all kept together in a "family tree". This structure
is a List\<IBaseDataObject> in the code.

The Emissary Parser framework is designed to extract information from any data containers/wrappers on the payload that may contain information useful for identification or processing.

Parsers need to make a quick identification of the container format (i.e. Apache NiFi FlowFile). This is called
out by the emissary.parser.ParserFactory and uses the [emissary.parser.DataIdentifier](src/main/java/emissary/parser/DataIdentifier.java) as the
engine to perform the identification. The engine is configured by name and can be replaced
by anything that meets the required interface. The name of the format identified should
have parsers that meet the SessionParser interface configured into the factory.

Once the container is identified, items are parsed out of it, built into IBaseDataObject instances
by the SessionParser appropriate for that data type, and handed over to MobileAgent instances
for processing as they become available from the pool. 

It is important to have in mind that the
Parser framework is operating on the single-threaded side of the system and handing items over to the
multi-threaded (MobileAgent) side of the system. This necessitates that the parsers are fast enough
to keep the number of configured threads busy.

## Development

### Requirements

#### Java 1.8

Emissary requires [Java 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html).  
Emissary now supports both OpenJDK and Oracle JDK.  Be sure to use a recent version of
1.8 as there are bugs in earlier versions.

#### Apache Maven 3.3.9

Download and install [Apache Maven 3.3.9](http://maven.apache.org).

We recommend being familiar with the following Maven concepts:

* [5 Minute Tutorial and Basic Commands](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
* [Maven Lifecycles](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
* [Maven Profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html)

#### Preferred OS/Environment

Emissary is best developed and run in a Unix environment.

Preferred operating systems are Linux or macOS.

### Commands

#### Building With Maven

The main maven commands used for building Emissary are
```
# Build a compiled jar
mvn clean package

# Build compiled jar an install to .m2 repository for usage in other projects
mvn clean install

# Create a zip bundle that you can distribute
# Creates target/emissary-<version>-dist.tar.gz when successful built
mvn clean package -Pdist
```

Javadoc generation is also attached to *verify* phase.

#### Cleaning

Not a Maven command, but useful to get rid of any file not tracked by
git.  This will remove the jflex generated files, as well as any files
setup by your IDE.

```
# Removes files not tracked by git. Helps remove jflex generated and IDE generated files
git clean -dxf

# Removes everything in maven build directory (default is target)
mvn clean
```

#### Format Code

The autoformat profile is run unless you use '-DskipFormat'.  There is a java source code formatter that
uses [formatter.xml](contrib/formatter.xml)
 to format Java source code and to sort the pom files.  The 2 formatter are attached
to the *process-sources* lifecycle, which as you know from reading the lifecycles is run before *compile*.  So
typically you do not need to run this separately.  But you could run the following to just format everything:

```
mvn clean process-sources
```

#### Unit Tests

You can perform testing with the following commands
```
# Run all unit tests
mvn clean test

# Run tests with a certain number of cores (use #C to multiply by the number of cores - default in POM is .5C)
mvn -Dsurefire.forkCount=2C clean test

# Run only a single desired test (also can be done within the Intellij IDE)
mvn clean test -Dtest=SomeClassTest

# Skip tests - when you just wat the build and don't care for test verification
mvn clean package -DskipTests
```

More verbose information about failed tests will be under *target/surefire-tests*

Tests will execute in random order.  This is intentional to highlight dependencies between tests.


#### Code Quality

Code quality reports are also attached to the *verify* phase, but are in a couple of Maven profiles. To
activate all the code quality profiles, run the following.

```
mvn clean package site -Dcode-quality site:stage -DstagingDirectory=${PWD}/target/staging
```

The *"-DstagingDirectory"* is necessary to tell Maven where to write the site.  You may then view
the reports by opening target/site/project-reports.html.  The entire site 
is at target/staging/index.html

You can also run this to start a local server with the site
```
mvn clean package site -Dcode-quality site:run
```
Then you can view it at http://localhost:9000

#### Code Coverage

Since the whole *site* lifecycle can take some time, you can get just the code coverage with

```
mvn clean verify -Dcode-quality
```

Then open target/site/jacoco/index.html in your browser to see code coverage.

### Logging

Logging is handled by [logback](Logback Home).  By default, the file logback.xml in the \<configDir\> will be 
used.  You can point to another file with the *--logbackConfig* argument.  Feel free to modify the file
in target/config if you want different logging.  The logback.xml is configured to only log to the console 
currently.

### Classpath

The classpath is setup in one of 2 ways.  

* #### Development
  * If the emissary script is a sibling to pom.xml, it is assumed you are running from the git checkout. 
  A file named .mvn-classpath will be created using a Maven command then the Emissary class is launched.

* #### Distribution
  * If the *emissary* script does not have a pom.xml file as a sibling, then it is assuming
you are running from distribution.  If you run `mvn clean package -Pdist`, a tar.gz file
will be placed in the target directory.  Unpack that file, change into the directory and you will
see the *emissary* but no pom, so it will load class from the lib directory

### IDE Integration

For help with IDE integration see our [DEVELOPMENT README](DEVELOPMENT.md)

## Running Emissary

There is one bash script in Emissary that runs everything.  It is in the top level Emissary directory.
This script setups up a few environment variables and Java system properties.  It also sets up the 
classpath in a couple of ways.  More on that in a minute.  The script then runs the 
[emissary.Emissary](src/main/java/emissary/Emissary.java) class
which has several [JCommander](http://jcommander.org/) commands available to handle different functions.

```
./emissary
```

### Emissary Commands

For information on emissary commands, checkout out the [Emissary Commands README](EMISSARY-COMMANDS.md)

For basic information about Emissary commands, you can run
```
./emissary help
```

If you want to see more detailed information on a command, add the command name after help.  For example, see all the 
arguments with descriptions for the *what* command, run:

```
./emissary help what
```

### Tips

#### Running in Continuous Integration

If you wanted to run CI on a jenkins server or something similar, use
the following command.  

```
mvn clean install site -Dcode-quality -DskipFormat
```

The *skipFormat* is used because sort-pom and the source code
formatter plugin rewrite files in the git working directory.  This
would not be good and could cause conflicts on the next
checkout.

#### Remote Debug a Maven Test

It is straightforward to attach a remote debugger to your tests, as
long as you tell the surefire plugin about it.  Because Emissary runs 
tests in jvm forks, the mvnDebug command will not work.  Here is an 
example of running.  You have already seen the *-Dtest=* option.  The 
*-Dmaven.surefire.debug* option will stop the JVM until a remote
debugger attaches, and you can then step through the code.

```
mvn clean test -Dtest=ServerCommandTest -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE"
```

#### Potentially Speed Up the Development Cycle

The above commands all have 'clean' in the command.  It is safest to do that.  But it is 
possible to omit the 'clean' and maven will then only compile files that have changed.  This can
speed up things, but for Emissary it is unlikely to speed up much.

## Docker

For information on how to use Emissary with Docker, see our [Docker README](DOCKER.md)

## Coding Standards

Many of coding standards are defined in the [formatter config file](contrib/formatter.xml).  Here are some 
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

### Hanging Tests

Emissary uses fake host names in some of the tests.  If you happen to be on a Verizon network, you
may get redirected for unknown hosts to 92.242.140.21.  To test this run

```
nslookup somehost
```

If you have this situation, then it is recommended to use [Google's DNS servers](https://developers.google.com/speed/public-dns/docs/using) 

## Contact Us

If you have any questions or concerns about this project, you can contact us at: emissarysupport@evoforge.org

### Security Questions

For security questions and vulnerability reporting, please refer to [SECURITY.md](SECURITY.md)
