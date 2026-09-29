![Emissary Dark Knight - some code just wants to watch the core burn](emissary-knight.png) 

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Sonatype Central](https://maven-badges.sml.io/sonatype-central/gov.nsa.emissary/emissary/badge.svg)](https://maven-badges.sml.io/sonatype-central/gov.nsa.emissary/emissary) [![Java CI with Maven](https://github.com/NationalSecurityAgency/emissary/actions/workflows/maven-ci.yml/badge.svg)](https://github.com/NationalSecurityAgency/emissary/actions/workflows/maven-ci.yml) [![CodeQL](https://github.com/NationalSecurityAgency/emissary/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/NationalSecurityAgency/emissary/actions/workflows/codeql-analysis.yml) [![Lint Codebase](https://github.com/NationalSecurityAgency/emissary/actions/workflows/linter.yaml/badge.svg)](https://github.com/NationalSecurityAgency/emissary/actions/workflows/linter.yaml)

Table of Contents
=================

* [Introduction](#introduction)
* [Minimum Requirements](#minimum-requirements)
* [Getting Started](#getting-started)
* [Contact Us](#contact-us)


## Introduction

Emissary is a P2P based data-driven workflow engine that runs in a heterogeneous 
possibly widely dispersed, multi-tiered P2P network of compute resources. Workflow 
itineraries are not pre-planned as in conventional workflow engines, but are discovered as 
more information is discovered about the data. There is typically no user interaction in an 
Emissary workflow, rather the data is processed in a goal oriented fashion until it reaches 
a completion state.

Emissary is highly configurable, but in this base implementation
does almost nothing. Users of this framework are expected to provide
classes that extend [emissary.place.ServiceProviderPlace](src/main/java/emissary/place/ServiceProviderPlace.java) to perform
work on [emissary.core.IBaseDataObject](src/main/java/emissary/core/IBaseDataObject.java) payloads.

A variety of things can be done and the workflow is managed in
stages, e.g. [STUDY](DEVELOPING.md#study), [ID](DEVELOPING.md#id), [COORDINATE](DEVELOPING.md#coordinate), 
[TRANSFORM](DEVELOPING.md#transform), [ANALYZE](DEVELOPING.md#analyze), [IO](DEVELOPING.md#io), 
[REVIEW](DEVELOPING.md#review).

The classes responsible for directing the workflow are the
[emissary.core.MobileAgent](src/main/java/emissary/core/MobileAgent.java) and classes derived from it, which manage
the path of a set of related payload objects through the workflow and
the [emissary.directory.DirectoryPlace](src/main/java/emissary/directory/DirectoryPlace.java) which manages the available
services, their cost and quality and keep the P2P network connected.

### Maven Site and Javadoc hosted on GitHub Pages: https://code.nsa.gov/emissary/

## Minimum Requirements

- Linux or MacOSX operating system
- [JDK 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
- [Apache Maven 3.6.3+](http://maven.apache.org)

## Getting Started

Read through the [DEVELOPING.md](DEVELOPING.md) guide for information on installing required components, pulling the 
source code, building and running Emissary.

### Building

Run ```mvn clean package``` to compile, test, and package Emissary

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  9.132 s
[INFO] Finished at: 2022-01-10T22:31:05Z
[INFO] ------------------------------------------------------------------------
```

### Running

There is one bash script in Emissary that runs everything.  It is in the top level Emissary directory. The script runs 
the [emissary.Emissary](src/main/java/emissary/Emissary.java) class which has several [Picocli](https://picocli.info/) 
commands available to handle different functions.

#### No arguments

If the *emissary* script is run without any arguments, you will get a listing 
of all the configuration subcommands and a brief description.

```
./emissary
```

#### Help

Running `./emissary help` will give you the same output as running with no arguments.  If you want to see more
detailed information on a command, add the command name after help.  For example, see all the 
arguments with descriptions for the *server* command, run:

```
./emissary help server
```

#### Common parameters

The rest of commands all have *(-b or --projectBase)* arguments that can be set, but it must match PROJECT_BASE.

The config directory is defaulted to <projectBase>/config
but can also be passed in with *(-c or --config)*.  When running from the git checkout, you should use
*target* as the projectBase.  Feel free to modify config files in target/config before you start.

Logging is handled by logback. You can point to a custom file with the *--logbackConfig* argument.

See the *help -c <commandName>* for each command to get more info.

#### Server (Standalone)

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
url, you will need to enter the username and password defined in target/config/jetty-users.properties.

> [!CAUTION]
> Security Notice: Default Credentials
> 
> ***Change Default Credentials Immediately*** The usernames and passwords listed below are for demonstration
> purposes only. Using default credentials in a production environment poses a significant security risk. 
> 
> **Action Required**: Update your `jetty-users.properties` file with strong, unique passwords before deploying.
>
>| Default Username | Default Password |
>|------------------|------------------|
>| emissary         | emissary123      |
>| console          | console123       |

The default PickUpPlace is configured to read files from _target/data/InputData_.  If you copy
files into that directory, you will see Emissary process them.  Keep in mind, only toUpper and toLower are
configured, so the output will not be too interesting.

##### Pause

Stop the service from taking work

```
./emissary server --pause
```

##### Unpause

Allow a paused service to take work

```
./emissary server --unpause
```

##### Invalidate

Invalidate services that are refreshable. This is a "light" refresh that is a no-downtime approach that invalidates a
ServiceProviderRefreshablePlace. When the place is then pulled from DirectoryPlace, the place is recreated using the
same keys for DirectoryPlace and Namespace, but the configurator is reloaded and the place is able to reload a subset
of its configs.

```
./emissary server --invalidate
```

##### Refresh

Force refresh of services. This is a "hard" refresh, the server is paused and there is a wait for the MoblieAgents to
drain. Once the server is fully idle, all ServiceProviderRefreshablePlace existing keys are removed from DirectoyPlace
and Namespace, and the places are fully recreated. This allows for changes to service names, proxies, deny lists, etc.
The server is then unpaused to resume processing. Any failure in refresh would put the server in a bad state, so the
server is shutdown.

```
./emissary server --refresh
```

##### Stop

Shutdown the service

```
./emissary server --stop
```

##### Kill

Force the shutdown of the service

```
./emissary server --kill
```

#### Agents (Standalone)

The agents command shows the number of MobileAgents for the configured host and what those
agents are doing.  By default, the port is 9001, but you can use *-p or --port* to change that.  
Assuming you are running on 8001 from the server command above, try:

```
./emissary agents -p 8001
```

#### Pool (Standalone)

Pool is a collapsed view of agents for a node.  It, too, defaults to port 9001.  To run for the 
standalone server started above run

```
./emissary pool -p 8001
```

This command is more useful for a cluster as it a more digestible view of every node.

#### Env

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

#### Config

The config command allows you to see the effective configuration for a specified place/service/class. Since Emissary uses 
flavors, this command will show the resulting configuration of a class after all flavors have been applied. This command can
be used to connect to a running Emissary node by specifying the ```-h``` for host (default is localhost) and ```-p``` for
the port (default is 8001). To connect to a locally running Emissary on port 8001, any of the following commands will work:
```
./emissary config --place emissary.place.sample.ToLowerPlace
./emissary config --place emissary.place.sample.ToLowerPlace -h localhost -p 8001
```

Optionally, you can specify offline mode using ```--offline``` to use the configuration files specified in your local 
CONFIG_DIR:
```
./emissary config --place emissary.place.sample.ToLowerPlace --offline
```

In offline mode, you can provide flavors to see the differences in configurations:
```
./emissary config --place emissary.place.sample.ToLowerPlace --offline --flavor STANDALONE,TESTING
```

These are useful to see the effective configuration, but we can also run in a verbose mode to 
see all the configuration files along with the final output. This is controlled with the 
`--detailed` flag:
```
./emissary config --place emissary.place.sample.ToLowerPlace --detailed
```
or in offline mode:
```
./emissary config --place emissary.place.sample.ToLowerPlace --offline --detailed
```

#### YAML and TOML configuration (.yaml/.yml/.toml)

Places and services can alternatively be configured with YAML (`.yaml`/`.yml`) or TOML (`.toml`) files. A
`.cfg` request resolves dir-first across formats, so a migrated deployment keeps working without touching the request:
each location (config dir, then classpath, then old-style file) is checked for `.cfg` first, then `.yaml`, `.yml`,
`.toml`. In particular a `Bar.yaml` in `emissary.config.dir` overrides a shipped `Bar.cfg` in the jar, while a
`.cfg` still wins ties inside the same location — so pure-`.cfg` setups resolve exactly as before. A structured
name (`Bar.yaml`, `Bar.toml`) is exact and never falls back. `ConfigUtil.getConfigInfo(MyPlace.class)` tries the
cfg name first, so a place migrates by just dropping in the new file.

Everything else — flavors, `IMPORT_FILE`,
`@{VAR}` substitution, and the `config --place` inspection above — works the same.
Flavor and import names keep their suffix (`base-FLAVOR.yaml`, `"!import": other.yaml`), so a YAML base pairs with YAML
flavors/imports just like `.cfg` does today — migrate a base and its flavor files together, since a structured base only
sees same-suffix flavors and silently skips a leftover `.cfg` flavor.

Mapping to the cfg format (YAML first, TOML second):

| YAML | TOML | `.cfg` |
|---|---|---|
| `KEY: value` | `KEY = value` | `KEY = value` |
| `KEY: [a, b]` (sequence) | `KEY = ["a", "b"]` (array) | repeated `KEY = a` / `KEY = b` entries, in order |
| `NESTED: {ONE: x}` | `[NESTED]` + `ONE = x` | `NESTED_ONE = x` (nested maps flatten with `_`) |
| `"!remove": {KEY: v}` | `["!remove"]` + `KEY = v` | `KEY != v` (`"*"` removes all entries) |
| `KEY: [a, {"!remove": v}, b]` | `KEY = ["a", {"!remove" = v}, "b"]` | positional removal, evaluated in order |
| `"!import": file.yaml` | `"!import" = "file.toml"` | `IMPORT_FILE = file` |
| `"!opt-import": [a, b]` | `"!opt-import" = [a, b]` | `OPT_IMPORT_FILE` entries |

File-based flavors (`base-NAME.yaml` / `base-NAME.toml`, like `base-NAME.cfg`) work as with `.cfg`.

Notes: 
- Quote the `!` keys, since a bare `!` starts a YAML tag. 
- Quote any value that must stay a string. YAML resolves unquoted scalars to non-string types where `.cfg` kept
  every value as text: `yes`/`no`/`on`/`off` become booleans, `0xFF` becomes `255`, `1.10` becomes `1.1`, a leading-`0`
  digit sequence is read as **octal** (`0755` becomes `493`, `0644` becomes `420`), and a leading `+` is dropped
  (`+30` becomes `30`). Quoting preserves the value: `"0755"`, `"1.10"`, `"+30"`. Values that only *look* numeric are
  left alone, so `1.2.3`, `12345678901234567890` and dates such as `2024-01-01` or `2024-01-01T10:00:00Z` all stay
  strings; quoting them is still harmless and makes the intent explicit.
- Octal is the coercion most likely to surprise, because permission, umask and mode-style values are common in place
  configs and silently become different numbers rather than failing. TOML is stricter: it has no silent octal at all —
  integers must be canonical, so `0755` is a startup error and octal must be written `0o755` (hex `0xFF` and `+30`
  behave as in YAML).
- Give each mapping key only once — a duplicated key is a startup error in both formats (TOML forbids them, and
  the YAML parser is strict too).
- Merge keys (`<<: *base` in YAML, and a `"<<"` key in TOML) are rejected rather than merged, so flatten the mapping
  explicitly instead.
- Every key needs a value. A valueless key (YAML `KEY:`) is a startup error rather than a silently nulled entry; use
  `""` for a blank value and `"<null>"` to null the entry, as in cfgs.
- In TOML, dotted keys nest (`a.b = 1` becomes `A_B`), so quote dotted keys to keep them literal — and note that keys 
  after a `[table]` header belong to that table, while dotted keys never change the current table.
- An empty file is a valid config with no entries, as in cfgs.
- A YAML file is exactly one document. A second `---`-separated document is a startup error rather than a silently
  dropped config; split it into separate files instead.
- Every value must be a scalar. Nested mappings and sequences are only meaningful as keys' values; a tagged binary
  value (`!!binary`) is rejected rather than stored as a Java object string.
- Imports behave exactly like `.cfg` imports: `IMPORT_FILE`/`"!import"` failures are errors, `OPT_IMPORT_FILE` misses are
  skipped. Name the file with its suffix (e.g. `"!import": other.yaml`).
- Replace `ClassNameInventory` files rather than keeping both: inventory files are merged in filename order, and a key
  present in two files causes the later file to be skipped with an error.
- `ResourceReader.getConfigDataAsStream` still resolves only `.cfg`, so use `ConfigUtil.getConfigInfo` (or
  `findConfigDataName`) for configs that may be structured.

#### Server (Cluster)

Emissary is fun in standalone, but running cluster is more appropriate for real work.  The way to run clustered
is similar to the standalone, but you need to *-m cluster* to tell the node to connect to other nodes.  In
clustered mode Emissary will also start up the PickUpClient instead of the PickUpPlace, so you will need to
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

#### Feed (Cluster)

With nodes started on port 8001 and 9001, we need to start the feeder.  The feed command uses port 7001 by default,
but we need to set up a directory that the feeder will read from.  Files dropped into that directory will be available 
for worker nodes to take and the work should be distributed amongst the cluster.  Start up the feed with

```
mkdir ~/Desktop/feed1
./emissary feed -i ~/Desktop/feed1/
```

You should be able to hit http://localhost:8001, http://localhost:9001 and http://localhost:7001 in the browser and
look at the configured places.  Drop some files in the ~/Desktop/feed1 and see the 2 nodes process them.  It may 
take a minute for them to start processing

#### Agents (Cluster)

Agents in clustered mode again shows details about the mobileAgents.  It starts at with the node you 
configure (localhost:9001 by default), then calls out to all nodes it knows about and gets the same 
information.  Run it with:

```
./emissary agents --cluster
```

#### Pool (Cluster)

Pool in clustered mode also does the same as pool in standalone.  It starts at the node (locahost:9001) by default
then goes to all the nodes it knows about and aggregates a collapsed view of the cluster.  Run it with

```
./emissary pool --cluster
```

#### Topology (Clustered)

The topology talks to the configured node (localhost:8001 by default) and talks to every node it knows about.
The response is what all those nodes know about, so you can build up a network topology of your cluster.
Run it with 

```
./emissary topology
```

#### Running server with SSL

> [!CAUTION]
> Security Notice: SSL/TLS Configuration 
>
>The **demo/dev** profile disables SSL verification by default to reduce setup complexity for local evaluation.
>
> **For multi-host or production deployments:**
> * **Enable TLS:** Operators are responsible for ensuring all traffic is encrypted.
> * **mTLS:** Implement Mutual TLS where appropriate to verify the identity of both clients and servers.

The keystore and keystore password are in the [emissary.client.EmissaryClient-SSL.cfg](src/main/config/emissary.client.EmissaryClient-SSL.cfg) 
file.  Included and configured by default is a sample keystore you can use for testing this functionality. We do not 
recommend using the sample keystore in production environments.  To use your own keystore, change configuration values in the
[emissary.client.EmissaryClient-SSL.cfg](src/main/config/emissary.client.EmissaryClient-SSL.cfg) file.

Standalone

```
./emissary server -p 8443 --ssl --disableSniHostCheck
```

Clustered
```
./emissary server -p 8443 --ssl --disableSniHostCheck --mode cluster
./emissary server -p 9443 --ssl --disableSniHostCheck --mode cluster
mkdir ~/Desktop/feed1
./emissary feed -p 7443 --ssl --disableSniHostCheck -i ~/Desktop/feed1/
````

## Contact Us

### General Questions

If you have any questions or concerns about this project, you can contact us at: EmissarySupport@uwe.nsa.gov

### Security Questions

For security questions and vulnerability reporting, please refer to [SECURITY.md](SECURITY.md)
