# Emissary Commands

### Help

If you want to see more detailed information on a command, add the command name after help.  For example, see all the 
arguments with descriptions for the *what* command, run:

```
./emissary help what
```

### Common parameters

All other commands can leverage the (-b or --projectBase) argument, but it must match PROJECT_BASE.

The config directory is defaulted to <projectBase>/config
but can also be passed in with *(-c or --config)*.  When running from the git checkout, you should use
*target* as the projectBase.  Feel free to modify config files in target/config before you start.

See the *help -c <commandName>* for each command to get more info.

### What

This command will use the configured engines to identify the file.  Emissary currently only comes with the 
SizeIdPlace, so the id will be TINY or SMALL etc.  See that class for more info.  The *-i or --input* argument
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

Note, it is recommended to only run one Emissary process on any single node. You can configure the
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

The topology talks to the configured node (localhost:8001 by default) and talks to every node it knows about.
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
