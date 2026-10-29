package emissary.output.sink;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.DirectoryEntry;
import emissary.output.DropOffUtil;
import emissary.output.policy.OutputPolicy;
import emissary.output.policy.OutputPolicy.EmitMode;
import emissary.output.policy.request.PayloadRequest;
import emissary.util.TimeUtil;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.ser.std.MapProperty;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Nullable;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/** Writes payloads as JSON under the sink's output policy. */
final class JsonWriter {

    private static final String STRIP_PARAM_PREFIX = "STRIP_PARAM_PREFIX";

    private final PolicyGate gate;
    private final DropOffUtil dropOffUtil;
    private final List<String> stripPrefixes = new ArrayList<>();
    private final ObjectMapper jsonMapper;

    /**
     * Payload facts for the record currently being serialized. Jackson serializes extracted records recursively on the same
     * thread, so the previous value is restored around each record.
     */
    private static final ThreadLocal<PayloadRequest> CURRENT_PAYLOAD = new ThreadLocal<>();

    JsonWriter(final Configurator configG, @Nullable final Configurator config, final OutputPolicy policy) {
        this.gate = new PolicyGate(JsonWriter.class, policy);
        this.dropOffUtil = new DropOffUtil(configG);
        if (config != null) {
            this.stripPrefixes.addAll(config.findEntriesAsSet(STRIP_PARAM_PREFIX));
            this.stripPrefixes.replaceAll(prefix -> prefix.toUpperCase(Locale.ROOT));
            this.stripPrefixes.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        }
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.registerModule(new IbdoModule());
        this.jsonMapper.registerModule(new JavaTimeModule());
        this.jsonMapper.addMixIn(IBaseDataObject.class,
                policy.emitMode() == EmitMode.CONTENT ? IbdoContentOnlyMixin.class : IbdoMixin.class);
        this.jsonMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        this.jsonMapper.setFilterProvider(new SimpleFilterProvider().addFilter("param_filter", new IbdoParameterFilter()));
    }

    void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        this.jsonMapper.writeValue(out, list);
    }

    /** Remove the first configured prefix from a parameter name. */
    private String stripMetadataPrefix(final String name) {
        for (final String prefix : this.stripPrefixes) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }

    /** The exact deterministic transformation used by both serialization and trace responses. */
    String outputParameterName(final String name) {
        final char[] chars = stripMetadataPrefix(name.toUpperCase(Locale.ROOT)).toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetterOrDigit(chars[i]) && chars[i] != '_' && chars[i] != '.') {
                chars[i] = '_';
            }
        }
        return new String(chars);
    }

    /** Serializes metadata parameters, dropping filtered keys and values. */
    class IbdoParameterFilter extends SimpleBeanPropertyFilter {

        @Override
        public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {

            String key = writer.getName();
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) ((Map<?, ?>) pojo).get(key);

            final PayloadRequest payload = CURRENT_PAYLOAD.get();
            if (payload == null || !JsonWriter.this.gate.allowsParam(payload, key)) {
                return;
            }

            final Collection<Object> write = filter(payload, key, values);
            if (CollectionUtils.isNotEmpty(write)) {
                jgen.writeFieldName(transform(key));
                ((MapProperty) writer).setValue(write);
                writer.serializeAsElement(write, jgen, provider);
            }
        }

        protected Collection<Object> filter(final PayloadRequest payload, final String key, final Collection<Object> values) {
            final List<Object> keep = new ArrayList<>();
            for (final Object value : values) {
                if (JsonWriter.this.gate.allowsValue(payload, key, value)) {
                    keep.add(value);
                }
            }
            // Preserve legacy string ordering. Arbitrary Object values retain their original order and types.
            if (keep.stream().allMatch(String.class::isInstance)) {
                final Set<String> sorted = new TreeSet<>();
                keep.forEach(value -> sorted.add((String) value));
                return new ArrayList<>(sorted);
            }
            return keep;
        }

        protected String transform(String name) {
            return JsonWriter.this.outputParameterName(name);
        }
    }

    /** Jackson module registering the IBDO serializer. */
    class IbdoModule extends SimpleModule {
        private static final long serialVersionUID = -8129967131240053241L;

        public IbdoModule() {
            addSerializer(IBaseDataObject.class, new IbdoSerializer());
        }
    }

    /** Writes the IBDO fields to JSON. */
    class IbdoSerializer extends JsonSerializer<IBaseDataObject> {

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(IBaseDataObject ibdo, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            final PayloadRequest previous = CURRENT_PAYLOAD.get();
            final PayloadRequest current = PayloadRequest.from(ibdo);
            CURRENT_PAYLOAD.set(current);
            try {
                jgen.writeStartObject();
                JavaType javaType = provider.constructType(IBaseDataObject.class);
                BeanDescription beanDesc = provider.getConfig().introspect(javaType);
                JsonSerializer<Object> serializer = BeanSerializerFactory.instance.findBeanOrAddOnSerializer(provider, javaType, beanDesc,
                        provider.isEnabled(MapperFeature.USE_STATIC_TYPING));

                jgen.writeObjectField("id", dropOffUtil.getBestIdFrom(ibdo));
                jgen.writeObjectField("processedTimestamp", TimeUtil.getCurrentDateFullISO8601());

                serializer.unwrappingSerializer(null).serialize(ibdo, jgen, provider);

                if (JsonWriter.this.gate.allowsView(current, OutputPolicy.PRIMARY_VIEW_NAME)) {
                    byte[] payload = ibdo.data();
                    if (payload != null && payload.length > 0) {
                        jgen.writeObjectField("payload", payload);
                    }
                }

                Map<String, byte[]> views = ibdo.getAlternateViews();
                if (views != null && !views.isEmpty()) {
                    Map<String, byte[]> filtered = new LinkedHashMap<>();
                    for (Map.Entry<String, byte[]> entry : views.entrySet()) {
                        if (JsonWriter.this.gate.allowsView(current, entry.getKey())) {
                            filtered.put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (!filtered.isEmpty()) {
                        jgen.writeObjectField("views", filtered);
                    }
                }

                jgen.writeEndObject();
            } finally {
                if (previous == null) {
                    CURRENT_PAYLOAD.remove();
                } else {
                    CURRENT_PAYLOAD.set(previous);
                }
            }
        }
    }

    /** Mixin selecting which IBDO fields and methods are serialized. */
    abstract static class IbdoMixin {
        @JsonProperty("internalId")
        abstract UUID getInternalId();

        @JsonProperty("creationTimestamp")
        abstract Instant getCreationTimestamp();

        @JsonProperty("shortName")
        abstract String shortName();

        @JsonProperty("parameters")
        @JsonFilter("param_filter")
        @JsonInclude(NON_EMPTY)
        abstract Map<String, Collection<Object>> getParameters();

        @JsonProperty("members")
        @JsonInclude(NON_EMPTY)
        abstract List<IBaseDataObject> getExtractedRecords();

        @JsonIgnore
        abstract byte[] data();

        @JsonIgnore
        abstract Map<String, byte[]> getAlternateViews();

        @JsonIgnore
        abstract SeekableByteChannelFactory getChannelFactory();

        @JsonIgnore
        abstract int dataLength();

        @JsonIgnore
        abstract String getHeaderEncoding();

        @JsonIgnore
        abstract int getNumChildren();

        @JsonIgnore
        abstract int getNumSiblings();

        @JsonIgnore
        abstract int getBirthOrder();

        @JsonIgnore
        abstract String getFontEncoding();

        @JsonIgnore
        abstract Map<String, String> getCookedParameters();

        @JsonIgnore
        abstract Set<String> getParameterKeys();

        @JsonIgnore
        abstract boolean isFileTypeEmpty();

        @JsonIgnore
        abstract String getFileType();

        @JsonIgnore
        abstract int getNumAlternateViews();

        @JsonIgnore
        abstract Set<String> getAlternateViewNames();

        @JsonIgnore
        abstract boolean isBroken();

        @JsonIgnore
        abstract String getFilename();

        @JsonIgnore
        abstract List<String> getAllCurrentForms();

        @JsonIgnore
        abstract DirectoryEntry getLastPlaceVisited();

        @JsonIgnore
        abstract DirectoryEntry getPenultimatePlaceVisited();

        @JsonIgnore
        abstract int getPriority();

        @JsonIgnore
        abstract int getExtractedRecordCount();

        @JsonIgnore
        abstract boolean isOutputable();

        @JsonIgnore
        abstract String getBroken();

        @JsonIgnore
        abstract String getProcessingError();
    }

    /** Mixin for content-only output: the parameters map is not emitted. */
    abstract static class IbdoContentOnlyMixin extends IbdoMixin {
        @JsonIgnore
        @Override
        abstract Map<String, Collection<Object>> getParameters();
    }
}
