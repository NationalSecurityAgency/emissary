package emissary.server.api;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.output.OutputDispatchPlace;
import emissary.output.policy.Decision.Outcome;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.PolicyTrace;
import emissary.output.policy.request.PayloadRequest;
import emissary.output.sink.ISink;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Simulates output policy against a synthetic family using the running sink's compiled policy. */
@Path("")
public class OutputTrace {

    static final int MAX_PAYLOADS = 100;
    static final int MAX_DEPTH = 20;
    static final int MAX_VIEWS = 20;
    static final int MAX_PARAMETERS = 100;
    static final int MAX_PARAMETER_VALUES = 100;
    static final long MAX_FAMILY_BYTES = RequestSizeLimitFilter.MAX_REQUEST_BYTES;

    @POST
    @Path("/output/{place}/sinks/{sink}/trace")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response trace(@PathParam("place") final String placeName, @PathParam("sink") final String sinkName,
            final Request request) {
        final OutputDispatchPlace place;
        try {
            final Object registered = Namespace.lookup(placeName);
            if (!(registered instanceof OutputDispatchPlace)) {
                return error(Response.Status.NOT_FOUND, "No output dispatcher named " + placeName);
            }
            place = (OutputDispatchPlace) registered;
        } catch (NamespaceException e) {
            return error(Response.Status.NOT_FOUND, "No output dispatcher named " + placeName);
        }
        final ISink sink = place.getSink(sinkName);
        if (sink == null) {
            return error(Response.Status.NOT_FOUND, "No sink named " + sinkName);
        }
        try {
            validate(request);
            final String violation = limitViolation(request);
            if (violation != null) {
                return error(Response.Status.REQUEST_ENTITY_TOO_LARGE, violation);
            }
            final OutputPolicy policy = sink.getPolicy();
            final List<String> missing = new ArrayList<>();
            missingContext(request, policy.requiredContext(), "payload", missing);
            if (!missing.isEmpty()) {
                return Response.status(422).entity(Map.of("complete", false, "missingContext", missing,
                        "policy", policy.describe(), "posture", posture(policy))).build();
            }
            final IBaseDataObject payload = payload(request);
            final List<String> preprocessing = new ArrayList<>();
            elideContent(place, payload, "payload", preprocessing);
            final PayloadRequest facts = PayloadRequest.from(payload);
            final PolicyTrace trace;
            switch (request.target) {
                case "payload":
                    trace = policy.tracePayload(facts);
                    break;
                case "primaryContent":
                    trace = policy.traceView(facts, OutputPolicy.PRIMARY_VIEW_NAME);
                    break;
                case "view":
                    trace = policy.traceView(facts, request.name);
                    break;
                case "parameter":
                    trace = policy.traceParam(facts, request.name, null);
                    break;
                case "parameterValue":
                    trace = policy.traceValue(facts, request.name, request.value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown trace target: " + request.target);
            }
            final String outputName = request.target.startsWith("parameter") ? sink.outputParameterName(request.name) : request.name;
            return Response.ok(new Result(placeName, sinkName, request, outputName, facts, policy, trace, preprocessing)).build();
        } catch (IllegalArgumentException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    private static Response error(final Response.Status status, final String message) {
        return Response.status(status).entity(Map.of("error", message)).build();
    }

    private static String posture(final OutputPolicy policy) {
        return policy.defaultOutcome() == Outcome.DENY ? "allowlist" : "denylist";
    }

    /** Bound the simulated family before it is materialized into data objects and traced. */
    private static String limitViolation(final PayloadInput input) {
        final Limits limits = new Limits();
        limits.visit(input, "payload", 1);
        return limits.violation;
    }

    private static final class Limits {
        private int payloads;
        private long bytes;
        private String violation;

        void visit(final PayloadInput input, final String path, final int depth) {
            if (this.violation != null) {
                return;
            }
            if (depth > MAX_DEPTH) {
                this.violation = path + " exceeds maximum nesting depth of " + MAX_DEPTH;
                return;
            }
            if (++this.payloads > MAX_PAYLOADS) {
                this.violation = "Request exceeds maximum of " + MAX_PAYLOADS + " payloads";
                return;
            }
            this.bytes += length(input.content) + length(input.fileType) + length(input.form) + length(input.shortName);
            if (input instanceof Request) {
                final Request request = (Request) input;
                this.bytes += length(request.target) + length(request.name) + length(request.value);
            }
            if (input.views != null) {
                if (input.views.size() > MAX_VIEWS) {
                    this.violation = path + " exceeds maximum of " + MAX_VIEWS + " views";
                    return;
                }
                input.views.forEach((name, value) -> this.bytes += length(name) + length(value));
            }
            if (input.parameters != null) {
                if (input.parameters.size() > MAX_PARAMETERS) {
                    this.violation = path + " exceeds maximum of " + MAX_PARAMETERS + " parameters";
                    return;
                }
                input.parameters.forEach((name, values) -> {
                    this.bytes += length(name);
                    if (values != null) {
                        if (values.size() > MAX_PARAMETER_VALUES) {
                            this.violation = path + " parameter '" + name + "' exceeds maximum of " + MAX_PARAMETER_VALUES + " values";
                            return;
                        }
                        values.forEach(value -> this.bytes += length(String.valueOf(value)));
                    }
                });
                if (this.violation != null) {
                    return;
                }
            }
            if (this.bytes > MAX_FAMILY_BYTES) {
                this.violation = "Request exceeds maximum of " + MAX_FAMILY_BYTES + " bytes of payload, view, and parameter data";
                return;
            }
            if (input.members != null) {
                for (int i = 0; i < input.members.size(); i++) {
                    visit(input.members.get(i), path + ".members[" + i + "]", depth + 1);
                }
            }
        }

        private static long length(final Object value) {
            if (value == null) {
                return 0;
            }
            if (value instanceof byte[]) {
                return ((byte[]) value).length;
            }
            return String.valueOf(value).length();
        }
    }

    /** ELIDE_CONTENT applies to the whole simulated family, matching what the dispatcher writes for nested members. */
    private static void elideContent(final OutputDispatchPlace place, final IBaseDataObject data, final String path,
            final List<String> preprocessing) {
        if (place.elidesContent(data)) {
            place.prepareContent(data);
            preprocessing.add(path + " content replaced by ELIDE_CONTENT before policy evaluation");
        }
        final List<IBaseDataObject> members = data.getExtractedRecords();
        if (members != null) {
            for (int i = 0; i < members.size(); i++) {
                elideContent(place, members.get(i), path + ".members[" + i + "]", preprocessing);
            }
        }
    }

    private static void validate(final Request request) {
        if (request == null || request.target == null) {
            throw new IllegalArgumentException("A target is required");
        }
        if (!Set.of("payload", "primaryContent", "view", "parameter", "parameterValue").contains(request.target)) {
            throw new IllegalArgumentException("Unknown trace target: " + request.target);
        }
        if (Set.of("view", "parameter", "parameterValue").contains(request.target)
                && (request.name == null || request.name.isEmpty())) {
            throw new IllegalArgumentException("A name is required for " + request.target);
        }
        if ("parameterValue".equals(request.target) && !request.valuePresent) {
            throw new IllegalArgumentException("A value is required for parameterValue (explicit null is supported)");
        }
        validatePayload(request);
    }

    private static void validatePayload(final PayloadInput input) {
        if (input == null || input.fileType == null || input.form == null || input.shortName == null) {
            throw new IllegalArgumentException("Payload fileType, form, and shortName cannot be null");
        }
        if (input.members != null) {
            input.members.forEach(OutputTrace::validatePayload);
        }
    }

    private static void missingContext(final PayloadInput input, final Set<String> required, final String path,
            final List<String> missing) {
        for (final String context : required) {
            final boolean supplied;
            switch (context) {
                case "content":
                    supplied = input.content != null;
                    break;
                case "views":
                    supplied = input.views != null;
                    break;
                case "parameters":
                    supplied = input.parameters != null;
                    break;
                default:
                    supplied = false;
                    break;
            }
            if (!supplied) {
                missing.add(path + "." + context);
            }
        }
        if (input.members != null) {
            for (int i = 0; i < input.members.size(); i++) {
                missingContext(input.members.get(i), required, path + ".members[" + i + "]", missing);
            }
        }
    }

    private static IBaseDataObject payload(final PayloadInput input) {
        final IBaseDataObject data = DataObjectFactory.getInstance(input.content == null ? new byte[0] : input.content,
                input.shortName, input.form);
        data.setFileType(input.fileType);
        data.setOutputable(input.outputable);
        if (input.parameters != null) {
            data.putParameters(input.parameters);
        }
        if (input.views != null) {
            input.views.forEach(data::addAlternateView);
        }
        if (input.members != null) {
            final List<IBaseDataObject> members = new ArrayList<>();
            input.members.forEach(member -> members.add(payload(member)));
            data.setExtractedRecords(members);
        }
        return data;
    }

    /** Per-record facts and optional filter context. Bytes use JSON's normal base64 representation. */
    public static class PayloadInput {
        @JsonProperty
        public String fileType = "UNKNOWN";
        @JsonProperty
        public String form = "UNKNOWN";
        @JsonProperty
        public String shortName = "trace";
        @JsonProperty
        public boolean outputable = true;
        @JsonProperty
        @Nullable
        public byte[] content;
        @JsonProperty
        @Nullable
        public Map<String, byte[]> views;
        @JsonProperty
        @Nullable
        public Map<String, List<Object>> parameters;
        @JsonProperty
        @Nullable
        public List<PayloadInput> members;
    }

    public static final class Request extends PayloadInput {
        @JsonProperty
        @Nullable
        public String target;
        @JsonProperty
        @Nullable
        public String name;
        @Nullable
        private Object value;
        private boolean valuePresent;

        @JsonProperty("value")
        public void setValue(@Nullable final Object value) {
            this.value = value;
            this.valuePresent = true;
        }
    }

    /** Explicit JSON properties rather than relying on policy value objects' non-bean accessors. */
    public static final class Result {
        @JsonProperty
        public final String place;
        @JsonProperty
        public final String sink;
        @JsonProperty
        public final String target;
        @JsonProperty
        @Nullable
        public final String originalName;
        @JsonProperty
        @Nullable
        public final String outputName;
        @JsonProperty
        public final String policy;
        @JsonProperty
        public final String posture;
        @JsonProperty
        public final Map<String, Object> facts;
        @JsonProperty
        public final boolean complete = true;
        @JsonProperty
        public final boolean wouldEmit;
        @JsonProperty
        public final String explanation;
        @JsonProperty
        public final String scope = "Policy simulation for the supplied family and candidate; presence and successful I/O are not simulated";
        @JsonProperty
        public final List<String> preprocessing;
        @JsonProperty
        public final List<Step> steps;

        Result(final String place, final String sink, final Request request, final String outputName, final PayloadRequest facts,
                final OutputPolicy policy,
                final PolicyTrace trace, final List<String> preprocessing) {
            this.place = place;
            this.sink = sink;
            this.target = request.target;
            this.originalName = request.name;
            this.outputName = outputName;
            this.policy = policy.describe();
            this.posture = posture(policy);
            this.facts = Map.of("fileType", facts.fileType(), "form", facts.form(), "language", facts.language(),
                    "outputable", facts.outputable(), "shortName", facts.payload().shortName());
            this.wouldEmit = trace.emitted();
            this.explanation = trace.describe();
            this.preprocessing = List.copyOf(preprocessing);
            final List<Step> steps = new ArrayList<>();
            trace.steps().forEach(step -> steps.add(new Step(step)));
            this.steps = Collections.unmodifiableList(steps);
        }
    }

    public static final class Step {
        @JsonProperty
        public final String target;
        @JsonProperty
        public final String stage;
        @JsonProperty
        public final String status;
        @JsonProperty
        @Nullable
        public final String outcome;
        @JsonProperty
        @Nullable
        public final String detail;

        Step(final PolicyTrace.Step step) {
            this.target = step.target();
            this.stage = step.stage();
            this.status = step.decision() == null ? "NOT_EVALUATED" : "EVALUATED";
            this.outcome = step.decision() == null ? null : step.decision().outcome().name();
            this.detail = step.detail();
        }
    }
}
