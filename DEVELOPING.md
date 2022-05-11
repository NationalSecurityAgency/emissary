# Developing Emissary

Table of Contents
=================
* [Overview](#overview)
* [Requirements](#requirements)
* [IDE integration](#ide-integration)
* [Coding Standards](#coding-standards)
* [Helpful Commands](#helpful-commands)
* [Running Emissary](#running-emissary)
* [Docker](#docker)
* [Troubleshooting](#troubleshooting)

## Overview

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
* **output** - code relating to task of writing and manipulating output
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

There are two ways to run Emissary -
* **standalone mode** - node runs in isolation 
* **cluster mode** - nodes connect and form P2P network

All startup goes through the [emissary.Emissary](src/main/java/emissary/Emissary.java)
class.

### Base Data Object

Data is sessionized and passed through Emissary in the form of a [BaseDataObject](src/main/java/emissary/core/BaseDataObject.java).

### Processing Flow

There are various adapters to abstract how data is picked up by Emissary. Ultimately the data is received in PickUpPlace
and is broken out into BaseDataObject sessions.  [PickUpPlace](src/main/java/emissary/pickup/PickUpPlace.java) then pulls 
a MobileAgent from the thread pool and hands off the session for processing in _assignToPooledAgent_.

### Routing

Decisions on how data gets sent to Places revolves around the concept of DirectoryEntry, which is also referred to as a
Key within the code.  A DirectoryEntry is of the form:

FORM.SERVICENAME.STAGE.URL$EXPENSE

Routing uses a combination of the FORM, STAGE and EXPENSE to decide where to send the data.  The STAGEs define an order
for the processing flow within the framework.  Places are bound to specific STAGEs and will not be run outside that
STAGE.

MobileAgent walks a given session through all the relevant places.

There are two types of MobileAgent:
* [MobileAgent](src/main/java/emissary/core/MobileAgent.java) - Used for processing a single payload
* [HDMobileAgent](src/main/java/emissary/core/HDMobileAgent.java) - Used for processing a bundle of payloads

Processing starts when [PickUpPlace's](src/main/java/emissary/pickup/PickUpPlace.java) assignToPooledAgent() calls _agent.go(payload, startingLocation)_

The [MobileAgent](src/main/java/emissary/core/MobileAgent.java) processing loop is handled in _agentControl()_

To identify which [ServiceProviderPlace](src/main/java/emissary/place/ServiceProviderPlace.java) the [MobileAgent](src/main/java/emissary/core/MobileAgent.java) will visit next, it calls _getNextKey()_

### Stages

The Emissary data driven workflow has stages. These are specified by [emissary.core.Stage](src/main/java/emissary/core/Stage.java).
The stages of the workflow are used to control certain aspects of unwrapping and processing and help to ensure that the
workflow can always make progress on the task at hand.

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

#### Study

The name for this stage came from the idea behind [Perl's Regex study](http://perldoc.perl.org/functions/study.html)
method -- some work that can be done up-front to optimize or prepare for the remaining work to come.
This stage is designed to make no modifications to the data itself but can be used for:
* policy enforcement - ensuring that the incoming payload meets some minimum criteria or quality standards
* provenance - emitting events at the very beginning of the workflow that might indicate to an external system that
  workflow has begun on a certain item.

#### Id

Id phase of the workflow is for identification of data. Service places operating in this phase
are expected to modify the currentForm and fileType of the payload. They are expected to not change
the payload bytes of the data or extract any other metadata, unwrap child payload objects, or extract
metadata.

#### Coordinate

Coordinate phase of the workflow is a natural fit for the emissary.place.CoordinationPlace
which is designed to wrap any number of otherwise instantiated processing places and lock down
the workflow among them without regard to cost. So that we don't have to create and remember
an artificially low cost for the CoordinatePlace, setting into this phase of the work flow causes
it to run before any Transform or PreTransform places that it might be coordinating for.

#### PreTransform

If there is the need to record provenance or export data for intermediate forms this is a place
where that could happen.

#### Transform

Transform phase of the workflow is expected to modify the bytes of the payload, set the current form to something
new when appropriate, extract nested child payload objects, record metadata. After a Transform phase service place the
workflow engine will go back to the ID phase and start evaluating for places that are interested in the currentForm.

#### PostTransform

Nothing past this phase will cause the workflow engine to go back
to the ID phase of the workflow, things must continue forward in the workflow stages from here.

#### Analyze

Analyze phase is designed to collect metadata and add value in ways that do not affect the
currentForm, fileType or bytes of the payload. In future versions Analyze places that apply
may be done in parallel.

#### Verify

Little used, but serves as a catch-all point before the IO stage.

#### IO

The IO stage is when data is available for output. This is a blocking point in the workflow as
all items in the payload family tree must be prepared to transition to the IO stage
before we can proceed.

#### Review

Post IO most of the currentForms are stripped off as they are handled by the IO places.
The itinerary is available and could be used in post-processing provenance events.

### Parsers vs Places

Emissary Places are for identifying, processing, transforming, and analyzing data of interest.

As items are unwrapped and processed the results are all kept together in a "family tree". This structure
is a List\<IBaseDataObject> in the code.

The Emissary Parser framework is designed to extract information from any data containers/wrappers on the payload that
may contain information useful for identification or processing.

Parsers need to make a quick identification of the container format (i.e. Apache NiFi FlowFile). This is called
out by the [emissary.parser.ParserFactory](src/main/java/emissary/parser/ParserFactory.java) and uses the
[emissary.parser.DataIdentifier](src/main/java/emissary/parser/DataIdentifier.java) as the
engine to perform the identification. The engine is configured by name and can be replaced
by anything that meets the required interface. The name of the format identified should
have parsers that meet the [SessionParser](src/main/java/emissary/parser/SessionParser.java) interface 
configured into the factory.

Once the container is identified, items are parsed out of it, built into IBaseDataObject instances
by the SessionParser appropriate for that data type, and handed over to MobileAgent instances
for processing as they become available from the pool.

It is important to have in mind that the
Parser framework is operating on the single-threaded side of the system and handing items over to the
multi-threaded (MobileAgent) side of the system. This necessitates that the parsers are fast enough
to keep the number of configured threads busy.

## Requirements

### Preferred OS/Environment

Emissary is best developed and run in a Unix environment.

Preferred operating systems are Linux or macOS.

### Java 11

Java 1.8 is no longer supported in this codebase, only 
[Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html). 

### Apache Maven 3.6+

Download and install [Apache Maven 3.6+](http://maven.apache.org).

We recommend being familiar with the following Maven concepts:

* [5 Minute Tutorial and Basic Commands](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
* [Maven Lifecycles](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
* [Maven Profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html)

## IDE integration

The pom.xml file is set up to use a different *project.build.directory* depending on the IDE you are using.  The
reason for this is so that running Maven on the command line and using your IDE will not interfere with each
other.  So keep in mind, your IDE will not build to the *./target* directory.

You can run/debug the [emissary.Emissary](src/main/java/emissary/Emissary.java) class in your IDE.  Just setup the same 
arguments you would use on the command line.  Running tests in your IDE is also supported.  IntelliJ and Netbeans will 
read the surefire plugin configuration and use it.  Eclipse does not read the surefire configuration, so the process
is explained below.

### IntelliJ

When [IntelliJ](https://www.jetbrains.com/idea/) runs a Maven project, a system property named
*idea.version* is set.  We use this property to activate the *intellij* profile in the
pom and change the *project.build.directory* to *${project.basedir}/target-idea*, which is a sibling to the target
directory.  The `mvn clean` command will not remove this directory, but cleaning from IntelliJ should remove it.

Because we are using the jacoco plugin, we typically must put *@argLine* at the beginning of the surefire plugin
to allow jacoco to add what it needs.  See the second answer 
[here](http://stackoverflow.com/questions/23190107/cannot-use-jacoco-jvm-args-and-surefire-jvm-args-together-in-maven).
But that can cause IntelliJ to report this if you run a test:

```
Error: Could not find or load main class @{argLine}
```

The fix is to tell IntelliJ not to use that.  Uncheck **argLine** from
Preferences -> Build,Execution,Deployment -> Build Tools -> Maven -> Running Tests.
See [stackoverflow](http://stackoverflow.com/questions/24115142/intellij-error-when-running-unit-test-could-not-find-or-load-main-class-suref)
for more info

### Eclipse

When [Eclipse](https://eclipse.org/) runs with the [M2E](http://www.eclipse.org/m2e/) plugin, a system property named
*m2e.version* is set.  We can use that fact to activate the *eclipse* profile in the pom and then change
the *project.build.directory* to *${project.basedir}/target-eclipse*, which is a sibling to the default *target*directory.  
The `mvn clean` command will not remove this directory, but cleaning from Eclipse should remove it.

The *eclipse* profile also configures some exclusions for the m2e plugin, so it doesn't warn about not knowing how to
handle some Maven plugins used.

When running tests in Eclipse, so you will need to set the environment variable *PROJECT_BASE* to the directory
eclipse is using to build the project.  So in the run configuration of a test, add the following to the
Run Configuration -> Environment -> New

```
PROJECT_BASE = ${project_loc}/target-eclipse
```

Unfortunately you will have to do this for every test, that's Eclipse with the M2E plugin for you.

### Netbeans

Unfortunately, we could not find a system property [Netbeans](https://netbeans.org/) sets when running
a Maven project, so we are unable to automatically activate the *netbeans* profile like we can with
the *eclipse* and *intellij* profile.  Fortunately, it is easy to add a system property yourself.  
Open Netbean's Preferences -> Java -> Maven and then add `-Dnetbeans.ide` into the *Global Execution Options*.  
Afterwards, the *netbeans* profile will be activated in the pom and change
the *project.build.directory* to target-netbeans, which is a sibling to the target directory.  
The `mvn clean` command will not remove this directory, but cleaning from Netbeans should remove it.

## Coding Standards

Many of coding standards are defined the [formatter config file](contrib/formatter.xml). Here are some
additional things to consider when developing:

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

## Helpful Commands

### Cleaning

Not a Maven command, but useful to get rid of any file not tracked by
git.  This will remove the jflex generated files, as well as any files
setup by your IDE.

```
git clean -dxf
```

Remove everything under 'project.build.directory' (default is target) from the command line

```
mvn clean
```

### Format Code

The autoformat profile is run unless you use '-DskipFormat'.  There is a java source code formatter that
uses [formatter.xml](contrib/formatter.xml)
 to format Java source code and formatter to sort pom files.  The 2 formatter are attached
to the *process-sources* lifecycle, which as you know from reading the lifecycles is run before *compile*.  So
typically you do not need to run this separately.  But you could run the following to just format everything:

```
mvn clean process-sources
```

### Compile

Will compile all Java code under the [src/main](src/main) directory.  Useful if you change something and want to run Emissary
with the changes, but that is discussed in the section below about running.

```
mvn clean compile
```

### Test

You can perform testing with the following commands

To run all tests:
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

If you have a beefy box, you may see a speed-up in tests by setting surefire.forkCount to
something else.  You can hardcode a number, like 4 or use #C to multiply by the 
number of cores.  For example, but YMMV

```
mvn -Dsurefire.forkCount=2C clean test
```

If you want to run just one test file from the command line, do this:

```
mvn clean test -Dtest=SomeClassTest
```

You should never do this, but if you need it, add the '-DskipTest' option to the Maven command
to avoid running any tests

### Package

Create a jar with

```
mvn clean package
```

### Verify

Currently, there are no integration tests running.  But when there are, those only run during the *verify*
lifecycle, which is run before install.  So if you just wanted to run up to the integration tests, do

```
mvn clean verify
```

Javadoc's generation is also attached to *verify* phase.

### Code Quality

Code quality reports are also attached to the *verify* phase, but are in a couple of Maven profiles. To
activate all the code quality profiles, run the following.

```
mvn clean package site -Dcode-quality site:stage -DstagingDirectory=${PWD}/target/staging
```

The *"-DstagingDirectory"* is necessary to tell Maven where to write the site. You may then view
the reports by opening target/site/project-reports.html.  The entire site 
is at target/staging/index.html

You can also run this to start a local server with the site
```
mvn clean package site -Dcode-quality site:run
```
Then you can view it at http://localhost:9000

### Code Coverage

Since the whole *site* lifecycle can take some time, you can get just the code coverage with

```
mvn clean verify -Dcode-quality
```

Then open target/site/jacoco/index.html in your browser to see code coverage.

### Install

The *install* task runs after *verify* and copies any included
artifacts made in the *package* phase to your local Maven repo.  This
repository is usually ~/.m2/repository unless you set to be somewhere
else.

Run the *install* task with

```
mvn clean install
```

If any phase prior to *install* fails, no artifacts will be copied.

### Making a Distribution

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

### Remote Debug a Maven Test

It is straightforward to attach a remote debugger to your tests, as
long as you tell the surefire plugin about it.  Because Emissary runs 
tests in jvm forks, the mvnDebug command will not work.  Here is an 
example of running.  You have already seen the *-Dtest=* option.  The 
*-Dmaven.surefire.debug* option will stop the JVM until a remote
debugger attaches, and you can then step through the code.

```
mvn clean test -Dtest=ServerCommandTest -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE"
```

## Running Emissary

There is one bash script in Emissary that runs everything.  It is in the top level Emissary directory.
This script sets up a few environment variables and Java system properties.  It also sets up the 
classpath in a couple of ways.  The script then runs the [emissary.Emissary](src/main/java/emissary/Emissary.java) class
which has several [JCommander](http://jcommander.org/) commands available to handle different functions.

### Classpath

The classpath is set up differently depending on the method and location used to run: 

#### Development

If the *emissary* script is a sibling to pom.xml, it is assumed you are running
from the git checkout.   If a file named .mvn-classpath exists, that file is used to set up the classpath.  If that file
does not exist, it is created using a Maven command then the Emissary class is launched.  _NOTE_ if any
dependencies are changed in the pom, then this file needs to be removed and regenerated.

#### Distribution

If the *emissary* script does not have a pom.xml file as a sibling, then it is assuming
you are running from distribution.  If you run `mvn clean package -Pdist`, a tar.gz file
will be placed in the target directory.  Unpack that file, change into the directory, and you will
see the *emissary* but no pom, so it will load class from the lib directory

### Logging

Logging is handled by [logback](http://https://logback.qos.ch/).  By default, the file logback.xml in the \<configDir\> 
will be used.  You can point to another file with the *--logbackConfig* argument.  Feel free to modify the file
in target/config if you want different logging.  The logback.xml is configured to only log to the console
currently.

### Threads

Emissary runs several threads to keep track of its processing.  If you `jstack <emissary pid>`
you will see all the running threads and what code they are currently executing.

* **Emissary**
  * "FileInput-target/data/InputData" (standalone) - watches directory and processes files that show up
  * "FileQueServer" (cluster) - communicated with the Feeder and gets files for processing
  * "HeartbeatManager" (cluster) - tracks the state of the cluster
  * "ResourceWatcher" - tracks the resource being consumed by Emissary/Place
  * "MoveSpool" - this tracks the incoming data sent to Emissary
  * "MobileAgent" {1..n} - these are the processing thread that work on the data
* **Java**
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
* **Jetty**
  * "org.eclipse.jetty.server.session.HashSessionManager" {1..n}
  * "qtp715521683" {1.n}
  
### Debugging

The _emissary_ script allows you to remote debug any command.  Simply use `DEBUG=true` before the command, and it will 
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

For info on how to set up remote debugging in your IDE, reference the
following articles:

- for [Eclipse](https://www.eclipse.org/community/eclipse_newsletter/2017/june/article1.php)
- for [IntelliJ](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html)

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

## Troubleshooting

### Hanging Tests

Emissary uses fake host names in some tests.  If you happen to be on a Verizon network, you
may get redirected for unknown hosts to 92.242.140.21.  To test this run

```
nslookup somehost
```

If you have this situation, then it is recommended to use [Google's DNS servers](https://developers.google.com/speed/public-dns/docs/using).
