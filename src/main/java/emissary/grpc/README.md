# gRPC Support in Emissary

Emissary provides a built-in framework for building `ServiceProviderPlace` implementations that
communicate with external gRPC services. The framework manages connection pooling, automatic
retries, and both synchronous and asynchronous invocation patterns. Framework users only need to 
implement request/response logic.

## Class Hierarchy

```
ServiceProviderPlace
└── GrpcRoutingPlace          – base for places with one or more gRPC endpoints
    └── GrpcConnectionPlace   – convenience subclass for the common single-endpoint case
```

Both abstract classes are in the `emissary.grpc` package. All concrete places extend one of them
and override `process(IBaseDataObject)`.

---

## Single-Endpoint Places (`GrpcConnectionPlace`)

`GrpcConnectionPlace` is the right base for places that talk to exactly one gRPC service. The host
and port are read from two required config keys and the connection pool is wired up automatically.

**Required config keys:**

| Key | Description |
|---|---|
| `GRPC_HOST` | Hostname or DNS target of the gRPC service |
| `GRPC_PORT` | Port number of the gRPC service |

**Minimal implementation:**

```java
public class MyInferencePlace extends GrpcConnectionPlace {

    public MyInferencePlace(String configFile, String dir, String placeLoc) throws IOException {
        super(configFile, dir, placeLoc);
    }

    @Override
    public void process(IBaseDataObject o) {
        MyRequest request = MyServiceGrpc.MyRequest.newBuilder()
                .setPayload(ByteString.copyFrom(o.data()))
                .build();

        MyResponse response = invokeGrpc(
                MyServiceGrpc::newBlockingStub,
                MyServiceBlockingStub::runInference,
                request);

        o.setParameter("RESULT", response.getLabel());
    }
}
```

`invokeGrpc(stubFactory, callLogic, request)` borrows a channel from the pool, constructs the
stub, executes the call, returns the channel, and retries on transient failures — all
transparently.

For asynchronous invocation, use `invokeGrpcAsync(stubFactory, callLogic, request)` which returns
a `CompletableFuture<R>`.

---

## Multi-Endpoint Places (`GrpcRoutingPlace`)

`GrpcRoutingPlace` is the right base when a single place must talk to several distinct gRPC
endpoints (e.g., different model servers, different regions). Each endpoint is identified by a
**Target-ID** string that appears as a suffix in the config keys.

**Required config keys (one pair per endpoint):**

| Key | Description |
|---|---|
| `GRPC_HOST_<Target-ID>` | Hostname or DNS target for this endpoint |
| `GRPC_PORT_<Target-ID>` | Port number for this endpoint |

**Example config for two endpoints:**

```
GRPC_HOST_model-a = model-a.internal
GRPC_PORT_model-a = 8001
GRPC_HOST_model-b = model-b.internal
GRPC_PORT_model-b = 8002
```

**Invoking by Target-ID:**

```java
MyResponse response = invokeGrpc(
        "model-a",
        MyServiceGrpc::newBlockingStub,
        MyServiceBlockingStub::runInference,
        request);
```

`GrpcSamplePlace` (in the `emissary.grpc.sample` package) shows sequential and parallel
multi-endpoint invocation patterns using `hostnameTable.keySet()` and `invokeGrpcAsync`.

---

## Connection Pooling

`ConnectionFactory` manages a pool of `ManagedChannel` instances backed by
[Apache Commons Pool2](https://commons.apache.org/proper/commons-pool/). Channels are borrowed
before each RPC call and returned (or invalidated) on completion. The pool config is shared
across all endpoints of a given place.

**Pool configuration keys (all optional):**

| Key | Default | Description |
|---|---|---|
| `GRPC_KEEP_ALIVE_MILLIS` | `60000` | Idle ping interval (ms) |
| `GRPC_KEEP_ALIVE_TIMEOUT_MILLIS` | `30000` | Ping ACK timeout (ms) |
| `GRPC_KEEP_ALIVE_WITHOUT_CALLS` | `false` | Ping even when no active RPCs |
| `GRPC_LOAD_BALANCING_POLICY` | `round_robin` | `round_robin` or `pick_first` |
| `GRPC_MAX_INBOUND_MESSAGE_BYTE_SIZE` | `4194304` (4 MiB) | Max inbound message size |
| `GRPC_MAX_INBOUND_METADATA_BYTE_SIZE` | `8192` (8 KiB) | Max inbound metadata size |
| `GRPC_POOL_MAX_SIZE` | `8` | Max total connections |
| `GRPC_POOL_MAX_IDLE_CONNECTIONS` | `8` | Max idle connections |
| `GRPC_POOL_MIN_IDLE_CONNECTIONS` | `0` | Min idle connections |
| `GRPC_POOL_BLOCK_EXHAUSTED` | `true` | Block threads when pool is empty |
| `GRPC_POOL_MAX_BORROW_WAIT_MILLIS` | `10000` | Max wait time when pool is blocked (ms) |
| `GRPC_POOL_ERODING_FACTOR` | `-1.0` | Idle shrink rate; `-1` disables erosion |
| `GRPC_POOL_RETRIEVAL_ORDER` | `LIFO` | `LIFO` or `FIFO` |

---

## Retry Logic

`RetryHandler` wraps every invocation with exponential backoff using
[resilience4j](https://resilience4j.readme.io/). By default, retries are triggered for
`UNAVAILABLE`, `DEADLINE_EXCEEDED`, and `RESOURCE_EXHAUSTED` gRPC status codes, and for
`PoolException` (exhausted connection pool). Subclasses can override `retryOnException(Throwable)`
to customize which exceptions trigger a retry.

**Retry configuration keys (all optional):**

| Key | Default | Description |
|---|---|---|
| `GRPC_RETRY_MAX_ATTEMPTS` | `4` | Max attempts including the initial one |
| `GRPC_RETRY_INITIAL_WAIT_MILLIS` | `64` | Wait before first retry (ms) |
| `GRPC_RETRY_MAX_WAIT_MILLIS` | `1000` | Cap on per-retry wait (ms) |
| `GRPC_RETRY_MULTIPLIER` | `2.0` | Backoff multiplier |
| `GRPC_RETRY_NUM_FAILS_BEFORE_WARN` | `3` | Failures before escalating to WARN |

With defaults, the four attempts wait at most 64 ms, 128 ms, and 256 ms between retries (total
≤ 448 ms before the final failure is thrown).

---

## Downstream Extension Example

The following pattern illustrates how a downstream project might extend `GrpcConnectionPlace` to
send documents to an external ML inference service over gRPC.

```java
/**
 * Runs documents through one or more remote ML models via gRPC.
 */
public class MlInferencePlace extends GrpcConnectionPlace {

    private static final String MODEL_IDENTIFIER = "MODEL_IDENTIFIER";
    private List<MlModel> models;

    public MlInferencePlace(String configFile, String dir, String placeLoc) throws IOException {
        super(configFile, dir, placeLoc);
        configureModels();
    }

    private void configureModels() {
        models = configG.findEntries(MODEL_IDENTIFIER).stream()
                .map(name -> ConfigUtil.getSubConfig(configG, name + "_"))
                .map(cfg -> ConfigUtil.instantiateFromConfig(MlModel.class, cfg))
                .collect(Collectors.toList());
    }

    @Override
    public void process(IBaseDataObject o) {
        for (MlModel model : models) {
            ModelInferRequest request = model.generateRequest(o);
            ModelInferResponse response = invokeGrpc(
                    InferenceServiceGrpc::newBlockingStub,
                    InferenceServiceBlockingStub::modelInfer,
                    request);
            model.handleResponse(response, o);
        }
    }
}
```

Key points in this pattern:

- The place delegates model-specific request construction and response handling to separate
  `MlModel` objects loaded from config. This keeps the place thin and the model logic isolated.
- Multiple models are invoked sequentially per document; each can write its own metadata back
  onto the `IBaseDataObject`.
- The gRPC channel pool, retry logic, and connection lifecycle are entirely handled by
  `GrpcConnectionPlace` — the subclass only describes *what* to call and *how* to interpret the
  result.
- Constructor validation (e.g., checking server reachability or querying server metadata) can be
  done immediately after `super(...)` returns, before the place is registered in the directory.

**Sample config for this place:**

```
# gRPC connection
GRPC_HOST = inference.internal
GRPC_PORT = 8001

# Models to run (each has its own sub-config block)
MODEL_IDENTIFIER = sentiment
MODEL_IDENTIFIER = toxicity

# sentiment model sub-config
sentiment_MODEL_NAME   = sentiment-v2
sentiment_OUTPUT_FIELD = SENTIMENT_SCORE

# toxicity model sub-config
toxicity_MODEL_NAME   = toxicity-v1
toxicity_OUTPUT_FIELD = TOXICITY_SCORE
```