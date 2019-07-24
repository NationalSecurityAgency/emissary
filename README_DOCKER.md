# Emissary in Docker

## Requirements
Please start with the guide, ```README.md```, which is available in the root of the project. This guide will help on 
getting started with Emissary and setting up your maven environment.

Additionally, Docker needs to be installed locally to build images. To download and install, see : https://www.docker.com/get-docker. If developing on 
Linux, the maven plugin for Docker cannot be run with ```sudo```. Please see: https://docs.docker.com/install/linux/linux-postinstall/ for post-install 
instructions to manage Docker as a non-root user.

To start docker (on Centos 7):
```
sudo systemctl start docker
```

## Build

### Using Maven
Maven is used to create the docker image. There is a profile that was created to run the docker image build that, by default,
has been turned off. We'll need to add the docker profile, along with the dist profile, to trigger an assembly. From the
project root, run the following maven command:
```
mvn clean package -Pdist,docker
```

### Manually
To run manually without maven running the docker commands, run a full maven build:
```
mvn clean package -Pdist
```

Run the docker build:
```
docker build -t emissary:latest .
```

## Run
Once the image is successfully built, the image should be in your list of local images. Run ```docker images``` and there
should be an entry for REPOSITORY:emissary and TAG:latest:
```
[~]$ docker images
   REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
   emissary            latest              e740d5f23a79        44 hours ago        620MB
   centos              7                   49f7960eb7e4        2 days ago          200MB
```

To run files through Emissary, we'll need to volume mount local directories into the running container. Let's create two local directories for 
input/output to Emissary:
```
mkdir input
mkdir output
```

Now that we have two target directories, we can use the ```-v``` option to mount them into the container. To start Emissary, run the sample command:
```
docker run -it --rm -v ${PWD}/input:/opt/emissary/target/data -v ${PWD}/output:/opt/emissary/localoutput --name emissary emissary
```

Once Emissary starts up, we should see a log line that says: "Started EmissaryServer at http://localhost:8001." We now can copy files into 
```input/InputData/``` for Emissary to process. When the processing has finish, the files will be moved to ```input/DoneData```. All extracted 
content can be found in the local ```output``` directory. Depending on your specific configuration, there should be output in the ```output/json``` 
directory.
## Container without a name
If the ```--name emissary``` flag is not specified, you can find the name/id of the container that is running the
Emissary image by running ```docker ps```:
```
[~]$ docker ps
   CONTAINER ID        IMAGE                     COMMAND                  CREATED             STATUS              PORTS               NAMES
   dda57bfdfa9e        emissary:latest           "./emissary server -a?"  17 seconds ago      Up 16 seconds       8001/tcp            priceless_galileo
```
## Logs
To see the Emissary logs from the docker container, run:
```
docker logs emissary
```
## Monitor Emissary
To monitor Emissary in a container, we need to connect to the running container. If we want to run the agents command,
we simply need to run the following command:
```
docker exec -it emissary ./emissary agents -p 8001 --mon
```
To see the environment settings:
```
docker exec -it emissary ./emissary env
```
To attach to the running container, run:
```
docker exec -it emissary /bin/bash
```
## Emissary Cluster Mode
We can use a Docker compose file to simulate cluster mode. We'll start a feeder and two workers by default. To start the cluster, run the 
sample docker-compose.yml file:
```
docker-compose up
```
Once the cluster has started, we can manage input and output by looking at the volumes that were created by the compose file. The volume
 location can be found in ```/var/lib/docker/volumes/```. Simply move a file into the input directory for Emissary to process it:
```
cp emissary-knight.png /var/lib/docker/volumes/emissary_input/_data/InputData/
```
## Emissary UI
If you want to connect to the web front-end for Emissary, some additional steps are need to configure jetty. We need to
give the container a hostname and pass that onto Emissary from the command line. Sample command:
```
docker run -it --rm --name emissary --hostname emissary-001 -p 8001:8001 emissary server -a 2 -p 8001 -s http -h emissary-001
```
Then from a browser, go to http://localhost:8001/ to see the endpoints.

## Basic Testing with Docker Compose
If you like to test an emissary cluster automatically ingesting a set of sample files you can place any files you'd like to test within the
```src/test/resources/test_input``` directory then build a Docker image using the Dockerfile-test_feeder file in addition to the Docker image
created earlier.  This can easily be done by executing the following command:
```
sudo docker build -f Dockerfile-test_feeder -t emissary-feeder-test:latest --build-arg PROJ_VERS=$(./emissary version | grep Version: | 
awk {'print $3 " " '}) --build-arg IMG_NAME=latest .
```
Then execute ```docker-compose -f docker-compose.test.yml up``` to start the cluster and execute ```docker-compose down or CTRL+C``` to exit the
docker-compose. Alternatively you could execute ```./test_script.sh``` to execute a cursory check that the cluster will successfully ingest 
emissary-knight.png in a timely manner.
 