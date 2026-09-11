package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.DirectoryEntry;
import emissary.output.io.DateFilterFilenameGenerator;
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
import com.fasterxml.jackson.databind.Module;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * JSON output formatter using Jackson, streaming directly to the rolled output.
 */
public class JsonFormatter extends AbstractRollableFormatter {

    protected boolean emitPayload = true;

    protected ObjectMapper jsonMapper;

    /** Thread-local IBDO reference set during serialization so the views filter can access the current payload. */
    private static final ThreadLocal<IBaseDataObject> CURRENT_IBDO = new ThreadLocal<>();

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator formatterConfig) {
        if (name == null) {
            setName("JSON");
        }
        super.initialize(configG, name, formatterConfig);
        this.emitPayload = this.formatterConfig.findBooleanEntry("EMIT_PAYLOAD", true);
        initFilenameGenerator();
        initJsonMapper();
    }

    @Override
    protected void initFilenameGenerator() {
        this.fileNameGenerator = new DateFilterFilenameGenerator(".json");
    }

    /**
     * Initialize the Jackson object mapper
     */
    protected void initJsonMapper() {
        jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new IbdoModule());
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.addMixIn(IBaseDataObject.class, emitPayload ? IbdoPayloadMixin.class : IbdoParameterMixin.class);
        jsonMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        // the id in addFilter must match the annotation for @JsonFilter
        jsonMapper.setFilterProvider(new SimpleFilterProvider()
                .addFilter("param_filter", new IbdoParameterFilter()));
    }

    @Override
    public void writeTo(final OutputStream out, final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        jsonMapper.writeValue(out, list);
    }

    @Override
    public byte[] convert(final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        return jsonMapper.writeValueAsBytes(list);
    }

    /**
     * Serializes the metadata parameter map, delegating allow/deny decisions to the shared deny-list logic inherited from
     * {@link AbstractFormatter}.
     */
    class IbdoParameterFilter extends SimpleBeanPropertyFilter {

        private static final char KEY_REPLACEMENT = '_';

        @Override
        public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {

            String key = writer.getName();
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) ((Map<?, ?>) pojo).get(key);

            if (!isMetadataAllowed(null, key)) {
                return;
            }

            final Collection<Object> write = filter(key, values);
            if (CollectionUtils.isNotEmpty(write)) {
                jgen.writeFieldName(transform(key));
                ((MapProperty) writer).setValue(write);
                writer.serializeAsElement(write, jgen, provider);
            }
        }

        protected Collection<Object> filter(final String key, final Collection<Object> values) {
            Set<Object> keep = new TreeSet<>();
            for (final Object value : values) {
                if (isMetadataAllowed(null, key, value)) {
                    keep.add(value);
                }
            }
            return keep;
        }

        protected String transform(String name) {
            return normalize(strip(name.toUpperCase(Locale.getDefault())));
        }

        protected String strip(String name) {
            return JsonFormatter.this.stripMetadataPrefix(name);
        }

        protected String normalize(String name) {
            boolean changed = false;
            char[] ch = name.toCharArray();
            for (int i = 0; i < ch.length; i++) {
                if (!Character.isLetterOrDigit(ch[i]) && Character.compare(ch[i], '_') != 0 && Character.compare(ch[i], '.') != 0) {
                    ch[i] = KEY_REPLACEMENT;
                    changed = true;
                }
            }
            if (changed) {
                return new String(ch);
            }

            return name;
        }
    }

    /**
     * Ibdo {@link Module} implementation that allows registration of serializers
     */
    class IbdoModule extends SimpleModule {
        private static final long serialVersionUID = -8129967131240053241L;

        public IbdoModule() {
            addSerializer(IBaseDataObject.class, new IbdoSerializer());
        }
    }

    /**
     * Add some fields to the ibdo before output This is only needed if custom fields need to be written for the ibdo
     */
    class IbdoSerializer extends JsonSerializer<IBaseDataObject> {

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(IBaseDataObject ibdo, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            CURRENT_IBDO.set(ibdo);
            try {
                jgen.writeStartObject();
                JavaType javaType = provider.constructType(IBaseDataObject.class);
                BeanDescription beanDesc = provider.getConfig().introspect(javaType);
                JsonSerializer<Object> serializer = BeanSerializerFactory.instance.findBeanOrAddOnSerializer(provider, javaType, beanDesc,
                        provider.isEnabled(MapperFeature.USE_STATIC_TYPING));

                jgen.writeObjectField("id", dropOffUtil.getBestIdFrom(ibdo));
                jgen.writeObjectField("processedTimestamp", TimeUtil.getCurrentDateFullISO8601());

                serializer.unwrappingSerializer(null).serialize(ibdo, jgen, provider);

                Map<String, byte[]> views = ibdo.getAlternateViews();
                if (views != null && !views.isEmpty()) {
                    Map<String, byte[]> filtered = new LinkedHashMap<>();
                    for (Map.Entry<String, byte[]> entry : views.entrySet()) {
                        if (isContentAllowed(ibdo, entry.getKey())) {
                            filtered.put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (!filtered.isEmpty()) {
                        jgen.writeObjectField("views", filtered);
                    }
                }

                jgen.writeEndObject();
            } finally {
                CURRENT_IBDO.remove();
            }
        }
    }

    /**
     * This class is used so we do not have to annotate the IBaseDataObject. Set custom annotations on the method signatures
     * to include/exclude fields in the ibdo.
     */
    abstract static class IbdoMixin {
        @JsonProperty("internalId")
        abstract UUID getInternalId();

        @JsonProperty("creationTimestamp")
        abstract Instant getCreationTimestamp();

        @JsonProperty("shortName")
        abstract String shortName();

        @JsonProperty("parameters")
        @JsonFilter("param_filter")
        abstract Map<String, Collection<Object>> getParameters();

        @JsonProperty("members")
        @JsonInclude(NON_EMPTY)
        abstract List<IBaseDataObject> getExtractedRecords();

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

    abstract static class IbdoParameterMixin extends IbdoMixin {
        @JsonIgnore
        abstract byte[] data();

        @JsonIgnore
        abstract Map<String, byte[]> getAlternateViews();
    }

    abstract static class IbdoPayloadMixin extends IbdoMixin {
        @JsonProperty("payload")
        @JsonInclude(NON_EMPTY)
        abstract byte[] data();

        @JsonIgnore
        abstract Map<String, byte[]> getAlternateViews();
    }
}
