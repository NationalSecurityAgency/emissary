#!/usr/bin/env python3
"""Seed deterministic demo data for the Sentinel and Agents dashboards."""

import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

ELASTICSEARCH_URL = os.environ.get("ELASTICSEARCH_URL", "http://elasticsearch:9200").rstrip("/")
SENTINEL_INDEX = "sentinel-demo"
AGENTS_INDEX = "agents-demo"

AGENTS = [f"MobileAgent-{i:02d}" for i in range(1, 13)]
PLACES = ["FooPlace", "BarPlace", "BazPlace", "OutputPlace", "TransformPlace", "PickupPlace"]
PAYLOADS = [f"payload-{i:03d}.dat" for i in range(1, 31)]
NODES = [("host.domain.com", 8001), ("host2.domain.com", 8001)]


def request(method, path, body=None, content_type="application/json", ok_statuses=(200, 201, 202)):
    url = f"{ELASTICSEARCH_URL}{path}"
    data = body
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode("utf-8")
    elif isinstance(body, str):
        data = body.encode("utf-8")

    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": content_type})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read()
            if response.status not in ok_statuses:
                raise RuntimeError(f"{method} {path} returned {response.status}: {payload[:500]!r}")
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as e:
        if e.code in ok_statuses:
            return {}
        raise RuntimeError(f"{method} {path} returned {e.code}: {e.read()[:500]!r}") from e


def wait_for_elasticsearch():
    for attempt in range(60):
        try:
            request("GET", "/")
            return
        except Exception as e:
            if attempt == 59:
                raise
            print(f"Waiting for Elasticsearch ({attempt + 1}/60): {e}", flush=True)
            time.sleep(2)


def delete_demo_indices():
    for index in (SENTINEL_INDEX, AGENTS_INDEX):
        request("DELETE", f"/{index}", ok_statuses=(200, 404))


def bulk_index(index, pipeline, docs):
    lines = []
    for i, doc in enumerate(docs):
        lines.append(json.dumps({"index": {"_id": f"{index}-{i:04d}"}}, separators=(",", ":")))
        lines.append(json.dumps(doc, separators=(",", ":")))
    body = "\n".join(lines) + "\n"
    response = request(
        "POST",
        f"/{index}/_bulk?pipeline={pipeline}&refresh=true",
        body,
        content_type="application/x-ndjson",
    )
    if response.get("errors"):
        failures = [item for item in response.get("items", []) if item.get("index", {}).get("error")]
        raise RuntimeError(f"Bulk indexing failed for {index}: {json.dumps(failures[:3])}")


def place_key(place):
    return f"UNKNOWN.{place.upper()}.ID.http://host.domain.com:8001/{place}"


def make_sentinel_docs(now, rng):
    docs = []
    event_choices = [
        ("heartbeat", 24),
        ("agent_snapshot", 28),
        ("action_triggered", 22),
        ("rules_matched", 12),
        ("error", 10),
        ("lifecycle", 4),
    ]
    population = [choice for choice, weight in event_choices for _ in range(weight)]

    for i in range(180):
        occurred = now - timedelta(minutes=(179 - i) * 8)
        agent = rng.choice(AGENTS)
        place = rng.choice(PLACES)
        payload = rng.choice(PAYLOADS)
        duration = rng.randint(1, 180)
        event_type = rng.choice(population)
        node_name, node_port = rng.choice(NODES)

        level = "INFO"
        if event_type == "agent_snapshot":
            message = (
                f"In agent {agent} with currentForm DATA for {duration} minute(s)\n"
                f"\temissary.place.{place}.process({place}.java:66)\n"
                "\temissary.core.MobileAgent.go(MobileAgent.java:118)\n"
                "\tjava.base/java.lang.Thread.run(Thread.java:840)"
            )
        elif event_type == "action_triggered":
            level = "WARN"
            message = (
                "Sentinel detected possible locked agents -- "
                + json.dumps(
                    [
                        {
                            "agentName": agent,
                            "directoryEntry": f"http://host.domain.com:8001/{place}",
                            "shortName": payload,
                            "bundleFileName": payload,
                            "timeInMinutes": duration,
                        }
                    ],
                    separators=(",", ":"),
                )
            )
        elif event_type == "rules_matched":
            message = (
                "Sentinel rules matched -- "
                + json.dumps(
                    [
                        {"name": f"rule-{rng.randint(1, 8)}", "place": place},
                        {"name": "stuck-agent", "place": place},
                    ],
                    separators=(",", ":"),
                )
            )
        elif event_type == "error":
            level = "ERROR"
            message = f"Problem checking agent {agent}: simulated sentinel validation error"
        elif event_type == "lifecycle":
            message = rng.choice(["Sentinel initialized", "Sentinel stopped", "Sentinel is disabled"])
        else:
            message = "Sentinel is watching"

        docs.append(
            {
                "sequenceNumber": i + 1,
                "timestamp": int(occurred.timestamp() * 1000),
                "nanoseconds": i + 1,
                "level": level,
                "threadName": "Sentinel",
                "loggerName": "sentinel",
                "message": message,
                "mdc": {"shortName": payload, "serviceLocation": place_key(place)},
                "emissary": {"node": {"name": node_name, "port": node_port}},
            }
        )

    for node_name, node_port in NODES:
        for offset_hours, lifecycle_message in ((20, "Sentinel initialized"), (6, "Sentinel stopped")):
            occurred = now - timedelta(hours=offset_hours)
            docs.append(
                {
                    "sequenceNumber": len(docs) + 1,
                    "timestamp": int(occurred.timestamp() * 1000),
                    "nanoseconds": len(docs) + 1,
                    "level": "INFO",
                    "threadName": "Sentinel",
                    "loggerName": "sentinel",
                    "message": lifecycle_message,
                    "mdc": {},
                    "emissary": {"node": {"name": node_name, "port": node_port}},
                }
            )
    return docs


def make_agents_docs(now, rng):
    docs = []
    for step in range(12):
        occurred = now - timedelta(hours=23 - (step * 2))
        timestamp = occurred.isoformat(timespec="milliseconds").replace("+00:00", "Z")
        for i, agent in enumerate(AGENTS):
            if rng.random() < 0.58:
                place = rng.choice(PLACES)
                payload = rng.choice(PAYLOADS)
                status = (
                    f"{payload}({rng.randint(1, 3)}) - "
                    f"UNKNOWN.{place.upper()}.ID.http://host.domain.com:8001/{place}"
                )
            elif rng.random() < 0.04:
                status = "Closed"
            else:
                status = "Idle"

            node_name, node_port = NODES[i % len(NODES)]
            docs.append(
                {
                    "agent": {
                        "timestamp": timestamp,
                        "host": f"{node_name}:{node_port}",
                        "name": agent,
                        "status": status,
                    },
                    "emissary": {"node": {"name": node_name, "port": node_port}},
                }
            )
    return docs


def main():
    wait_for_elasticsearch()
    now = datetime.now(timezone.utc).replace(microsecond=0)
    rng = random.Random(20261030)

    sentinel_docs = make_sentinel_docs(now, rng)
    agents_docs = make_agents_docs(now, rng)

    delete_demo_indices()
    bulk_index(SENTINEL_INDEX, "emissary-sentinel", sentinel_docs)
    bulk_index(AGENTS_INDEX, "emissary-agents", agents_docs)

    sentinel_count = request("GET", f"/{SENTINEL_INDEX}/_count").get("count")
    agents_count = request("GET", f"/{AGENTS_INDEX}/_count").get("count")
    print(f"Seeded {sentinel_count} Sentinel events and {agents_count} agent snapshots.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Seed failed: {e}", file=sys.stderr, flush=True)
        sys.exit(1)
