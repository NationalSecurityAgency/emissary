# Emissary In Docker

### Intro

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

If you want to connect to the web front-end for Emissary, some additional steps are needed to configure jetty. We need to
give the container a hostname and pass that onto Emissary from the command line. Sample command:

```
docker run -it --rm --name emissary --hostname emissary-001 -p 8001:8001 emissary server -a 2 -p 8001 -s http -h emissary-001
```

Then from a browser, assuming a container is running locally, go to http://localhost:8001/ to see the endpoints.

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
