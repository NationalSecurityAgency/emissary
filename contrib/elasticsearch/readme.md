
# ELK Samples

Helpful setup guide: https://www.elastic.co/guide/en/kibana/8.3/docker.html

Note: These commands are meant to be run from the root of the Emissary project 

## Elasticsearch and Kibana 

Run Elasticsearch:
```shell
docker network create elastic
docker run --name elasticsearch --hostname elasticsearch --net elastic -p 9200:9200 -p 9300:9300 -e discovery.type=single-node -t docker.elastic.co/elasticsearch/elasticsearch:8.3.2
```

Copy the generated password and enrollment token and save them in a secure location. These values are shown only when you start Elasticsearch for the first time. 
You’ll use these to enroll Kibana with your Elasticsearch cluster and log in.
```shell
export  ESPASSWORD="<generated password>"
```

Run Kibana:
```shell
docker run --name kibana --hostname kibana --net elastic -p 5601:5601 docker.elastic.co/kibana/kibana:8.3.2
```

Get certs:
```shell
mkdir target/tls
docker cp elasticsearch:/usr/share/elasticsearch/config/certs/http_ca.crt target/tls/
docker cp elasticsearch:/usr/share/elasticsearch/config/certs/http.p12 target/tls/
export ESCRTPASS=`docker exec -it elasticsearch bin/elasticsearch-keystore show xpack.security.http.ssl.keystore.secure_password|sed 's/\r//'`
openssl pkcs12 -in target/tls/http.p12 -out target/tls/http.key.pem -nocerts -nodes -passin env:ESCRTPASS
unset $ESCRTPASS
```

Test connection:
```shell
curl --cacert target/tls/http_ca.crt -u elastic https://localhost:9200
```

Run Filebeat:
```shell
docker run --rm --name filebeat --net elastic \
  -v "$(pwd)/contrib/elasticsearch/filebeat.agents.docker.yml:/usr/share/filebeat/filebeat.yml:ro" \
  -v "$(pwd)/target/out:/var/log/emissary" \
  -v "$(pwd)/target/tls:/etc/tls" \
  -e "ESPASSWORD=${ESPASSWORD}" \
  docker.elastic.co/beats/filebeat:8.3.2 filebeat -e --strict.perms=false
```

## Sample 1: Agents Output

Start up Emissary and then ingest agents logs to Elasticsearch:
```shell
curl -u emissary --anyauth http://localhost:8001/api/agents/log >> target/out/agents.json
```
