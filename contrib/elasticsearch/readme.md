# Local Metrics Stack

Helpers for running a local Elasticsearch/Kibana/Grafana stack and exploring Emissary logs.

Note: These commands are meant to be run from the root of the Emissary project.

## Quick start: Kibana, Grafana, and seeded dashboards

Start the stack with Docker Compose:

```shell
docker compose -f contrib/elasticsearch/docker-compose.yml up -d
```

This starts:

- Elasticsearch on <http://localhost:9200>
- Kibana on <http://localhost:5601>
- Grafana on <http://localhost:3000> (`admin` / `admin`, or anonymous viewer)
- A one-shot setup container that installs both ingest pipelines and index templates
- Filebeat, tailing Sentinel and agent JSON logs and applying the matching pipelines
- A one-shot container that imports the Kibana `Sentinel Overview` and `Agents Overview` dashboards
- A one-shot `demo-data` container that seeds deterministic data into `sentinel-demo` and `agents-demo`

Security is intentionally disabled in this stack. Use it for local development only.

Open either UI immediately:

- Kibana: **Dashboard -> Sentinel Overview** or **Agents Overview**
- Grafana: **Dashboards -> Emissary -> Emissary Sentinel** or **Emissary Agents**

The demo data container re-seeds both dashboards every time it runs, so a fresh
`docker compose up` always has data to explore. It deletes only the `sentinel-demo`
and `agents-demo` indices.

### Enable Sentinel JSON logging

By default Sentinel logs go to the console. To write the JSON log file that Filebeat
tails, start Emissary with `LOG_SENTINEL=true`:

```shell
LOG_SENTINEL=true ./emissary server -a 5 -p 8001 -s http
```

The log file is written to `${PROJECT_BASE}/logs/sentinel-<node>-<port>.json`, which
is the repository's `logs/` directory by default. If your logs live elsewhere, set
`SENTINEL_LOG_DIR` before starting the stack:

```shell
SENTINEL_LOG_DIR=/path/to/logs docker compose -f contrib/elasticsearch/docker-compose.yml up -d
```

### Agent snapshots

Snapshots from `/api/agents/log` are JSON lines. Append them to a file that matches
`agents*.json` in `target/out` (the default), or set `AGENTS_LOG_DIR`:

```shell
curl -u emissary --anyauth http://localhost:8001/api/agents/log >> target/out/agents.json
AGENTS_LOG_DIR=/path/to/agent/logs docker compose -f contrib/elasticsearch/docker-compose.yml up -d
```

### Ingest pipelines

The pipelines enrich logs without changing the Java code:

- `ingest/sentinel-pipeline.json` parses tracker JSON embedded in Sentinel messages and adds
  `sentinel.event.type`, `sentinel.action`, `sentinel.agent.*`, `sentinel.place.*`,
  `sentinel.rule.*`, and `sentinel.stack_trace`. It also derives
  `emissary.node.name` and `emissary.node.port` from the `sentinel-<node>-<port>.json`
  filename in `log.file.path`, and adds `event.ingested`, `event.ingest_lag_ms`,
  `sentinel.error.signature`, and `sentinel.stack_trace_signature`.
- `ingest/agents-pipeline.json` parses `/api/agents/log` snapshots and adds
  `agent.state`, `agent.payload.short_name`, `agent.payload.count`,
  `agent.last_place.name`, `agent.last_place.key`, the same `emissary.node.*`
  fields derived from `agent.host` (`<node>:<port>`), and the same ingest timing
  fields.

Matching index templates are in `ingest/sentinel-index-template.json` and
`ingest/agents-index-template.json`.

### Dashboards

Kibana imports `dashboards/sentinel.ndjson` and `dashboards/agents.ndjson`.

Sentinel Overview includes KPI cards, events over time, event types, top places,
top affected agents, duration distribution, a node comparison table, and saved
searches for actions/errors and stack traces. The data view is `sentinel-*`.

Agents Overview includes agent/busy/idle/host/payload KPIs, snapshots over time by
state, state breakdown, top agents, payloads, last places, a node comparison table,
and recent/busy snapshot tables. The data view is `agents-*`.

Both Kibana saved-search tables include the `emissary.node.name` column, so you can
filter the data view or dashboard with `emissary.node.name: <node>`.

Grafana is provisioned from `grafana/provisioning` and `grafana/dashboards` and uses
two Elasticsearch data sources, one for each index pattern. Both Grafana dashboards
have a multi-select **Node** variable at the top; selecting a node filters every
panel, while the **Node comparison** table always shows all nodes side by side.
They also expose **Sentinel/Agents datasource** and **Kibana URL** variables so the
same dashboard JSON can be pointed at another environment without editing panels.

### Production deployment notes

These artifacts target Elasticsearch 8.x, Kibana 8.15, and Grafana 11.3. Validate
against the production versions before importing.

- The ingest pipelines set missing node metadata to `unknown` / `0`, so new
  unmatched logs remain visible instead of disappearing from the node filter.
- The index templates intentionally do not set `number_of_shards`,
  `number_of_replicas`, or `priority`; production cluster defaults and any ILM or
  template overlays should own those settings.
- Index patterns are `sentinel-*` and `agents-*`. If production uses a different
  naming scheme, update the index templates, the Grafana datasource index patterns,
  the Kibana index-pattern objects, and the Filebeat output index together.
- Existing historical indices need the new `emissary.node.*` mapping and a backfill
  before node-filtered panels will include them. Reindex through the updated
  template or update the mapping and set missing values to `unknown` / `0`.
- Kibana objects keep stable IDs (`sentinel-overview`, `agents-overview`, etc.).
  Import into a dedicated Kibana space, or rewrite the IDs, if production already
  uses those IDs. The local setup imports with `overwrite=true`.
- Kibana has no node dropdown; filter with `emissary.node.name: <node>` in KQL.
- `event.ingest_lag_ms` compares the log timestamp with ingest time. Backfilled
  history will show a large lag by design; use recent documents when alerting on it.
- TLS, authentication, secrets, and ILM are expected to be provided by the target
  production Elasticsearch/Kibana/Grafana deployment.

Reload after changing a pipeline or index template:

```shell
docker compose -f contrib/elasticsearch/docker-compose.yml run --rm elasticsearch-setup
```

Reload after changing the Kibana dashboard ndjson:

```shell
docker compose -f contrib/elasticsearch/docker-compose.yml run --rm kibana-setup
```

Re-seed demo data:

```shell
docker compose -f contrib/elasticsearch/docker-compose.yml run --rm demo-data
```

Stop the stack with:

```shell
docker compose -f contrib/elasticsearch/docker-compose.yml down
```

Add `-v` to the `down` command to also delete the Elasticsearch and Grafana volumes.

## Manual setup with security enabled

Helpful setup guide: <https://www.elastic.co/guide/en/kibana/8.3/docker.html>

### Elasticsearch and Kibana

Run Elasticsearch:

```shell
docker network create elastic
docker run --name elasticsearch --hostname elasticsearch --net elastic -p 9200:9200 -p 9300:9300 -e discovery.type=single-node -t docker.elastic.co/elasticsearch/elasticsearch:8.3.2
```

Copy the generated password and enrollment token and save them in a secure location. These values are shown only when you start Elasticsearch for the first time.
You'll use these to enroll Kibana with your Elasticsearch cluster and log in.

```shell
export ESPASSWORD="<generated password>"
```

Run Kibana:

```shell
docker run --name kibana --hostname kibana --net elastic -p 5601:5601 docker.elastic.co/kibana/kibana:8.3.2
```

Get certs:

```shell
mkdir -p target/tls
docker cp elasticsearch:/usr/share/elasticsearch/config/certs/http_ca.crt target/tls/
docker cp elasticsearch:/usr/share/elasticsearch/config/certs/http.p12 target/tls/
export ESCRTPASS=`docker exec -it elasticsearch bin/elasticsearch-keystore show xpack.security.http.ssl.keystore.secure_password|sed 's/\r//'`
openssl pkcs12 -in target/tls/http.p12 -out target/tls/http.key.pem -nocerts -nodes -passin env:ESCRTPASS
unset ESCRTPASS
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
