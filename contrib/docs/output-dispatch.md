# Output dispatch and tracing

`OutputDispatchPlace` is an opt-in output pipeline. The existing `DropOffPlace`,
filters, and default place list continue to operate as before. Use a separate
test deployment or place-list configuration to iterate on the new pipeline.

## Enable the dispatcher

In the place list for a test deployment, select:

```properties
PLACE = "@{URL}/OutputDispatchPlace"
```

The class inventory includes this name. The shipped dispatcher configuration
creates a JSON sink and a metadata-only JSON sink:

```properties
OUTPUT_SINK = "emissary.output.sink.JsonSink"
OUTPUT_SINK = "emissary.output.sink.JsonMetadataSink"
```

Each sink loads its standard class configuration through `ConfigUtil`:
`JsonSink.cfg` or `JsonMetadataSink.cfg`. The latter sets `EMIT=metadata` and
shares the JSON serializer. Normal configuration flavors are handled by the
existing configuration framework.

An optional `NAME:CLASS` label identifies the destination and its trace endpoint;
it does not select a configuration file or act as a flavor. Programmatic callers
can supply a `Configurator` directly when initializing a sink.

## Configure output

Each sink has its own compiled policy. `POLICY_STAGE` entries execute in file
order, with the first decisive rule/filter winning **within each gate**.

```properties
POLICY_STAGE = "rule deny payload outputable=false"
POLICY_STAGE = "rule deny view PDF.PRIVATE_*"
POLICY_STAGE = "rule deny param SECRET_*"
POLICY_STAGE = "rule deny value EMAIL=*@example.com"
POLICY_DEFAULT = "allow"
EMIT = "both"
```

The gates are:

- Every listed and nested record must pass payload eligibility. A denial skips
  the entire supplied family; it is not a failed write.
- Views, including primary content named `PrimaryView`, pass the content gate.
- Parameters pass a key gate, then a separate gate for every original value.
- `EMIT=metadata` excludes primary content and views; `EMIT=content` excludes
  parameters. Envelope fields still follow the serializer's output format.

`POLICY_DEFAULT=deny` applies to every gate. An allow-list for a parameter value
therefore needs an allowed payload, an allowed key, and an allowed value:

```properties
POLICY_DEFAULT = "deny"
POLICY_STAGE = "rule allow payload *"
POLICY_STAGE = "rule allow view *"
POLICY_STAGE = "rule allow param EMAIL"
POLICY_STAGE = "rule allow value EMAIL=*@example.com"
```

Without those allow rules, every gate denies by default. The trace response's
`posture` field is `allowlist` when `POLICY_DEFAULT=deny` and `denylist` when
`POLICY_DEFAULT=allow`.

Invalid configured sinks or policies fail initialization. Built-in sinks are
prepared and validated before any output resources are opened. Names are unique
case-insensitively and rolling sinks must own distinct journal directories.

A place-level `OUTPUT_PATH` is an output **root**: sinks `JSON` and `JSONM` use
`<root>/json` and `<root>/jsonm`. Without it, the default sink configurations use
their own configured paths. Custom labels get a separate subdirectory.

JSON preserves sorted/deduplicated string values. Mixed/non-string values retain
their original order and types. Parameter names are uppercased, the longest
configured `STRIP_PARAM_PREFIX` is removed, and unsupported characters become
underscores. XML filters original typed values before joining and encoding them.

## Trace a candidate

The endpoint uses the **running sink's policy**, not a reloaded configuration:

```http
POST /api/output/OutputDispatchPlace/sinks/JSON/trace
Content-Type: application/json
```

```json
{
  "target": "parameterValue",
  "fileType": "PDF",
  "form": "DOCUMENT",
  "outputable": true,
  "name": "EMAIL",
  "value": "person@example.com"
}
```

For example, with an HTTP development node:

```bash
curl -X POST 'http://localhost:8001/api/output/OutputDispatchPlace/sinks/JSON/trace' \
  -H 'Content-Type: application/json' \
  -H 'X-Requested-By: output-trace' \
  --data '{"target":"parameterValue","fileType":"PDF","name":"EMAIL","value":"person@example.com"}'
```

Targets are `payload`, `primaryContent`, `view`, `parameter`, and
`parameterValue`. `name` is required for the last three; `value` is required for
`parameterValue`, and explicit JSON `null` is a value rather than a key question.

Payload facts default to `fileType=UNKNOWN`, `form=UNKNOWN`, `shortName=trace`,
and `outputable=true`. Supply the facts of the record you want to simulate.
Optional context is `content` (base64 bytes), `views` (names to base64 bytes),
`parameters` (keys to arrays of original values), and `members` (nested payload
facts/context). Submitted payloads are synthetic and never written by this call.
The place's `ELIDE_CONTENT` preprocessing is applied to the candidate's primary
content and reported separately.

The response includes `wouldEmit`, a readable explanation, the active policy,
original/output parameter names, preprocessing, and ordered structured `steps`.
Steps have a target, stage, detail, and either an evaluated outcome or
`status=NOT_EVALUATED`. Parent denials stop component evaluation; later stages
are never executed just to fill out a trace.

`wouldEmit` means the supplied family and candidate pass policy gates. Candidate
presence, serializer envelope fields, successful I/O, prior processing stages,
and records not supplied in the request are outside this simulation.

Unknown places/sinks return 404, invalid requests return 400, and missing custom
filter context returns 422 with `complete=false` and `missingContext`, without
an emission claim.

## Test in the UI

With the server running, open **Trace** in the navigation bar (or
`/emissary/OutputTest.action`). Choose the place and sink, then pick a target and
submit. The form posts to the trace endpoint and displays the JSON response,
including `wouldEmit`, `posture`, and the ordered steps.

The advanced fields accept a JSON parameters map, a views map of base64 values,
and a members array. Content is sent as UTF-8.

## Custom filters

Implement `OutputFilter` and configure it through `POLICY_STAGE = "filter ..."`.
Filters must be thread-safe after configuration, read-only, and free of output
side effects. Normal writing and tracing use the same short-circuiting evaluator.

Override `requiredContext()` with the context actually used by the filter:
`content`, `views`, and/or `parameters`. The conservative default requires all
three. Name/fact-only or value-only filters may return an empty set. Explicit
empty arrays/maps count as supplied context. Context requirements are checked
for all supplied family members before custom-filter simulation.

`ViewRequest.content()` exposes the candidate bytes for alternate views and
`PrimaryView`, so size filters can handle both consistently.
