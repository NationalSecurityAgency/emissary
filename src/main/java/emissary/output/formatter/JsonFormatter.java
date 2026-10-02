package emissary.output.formatter;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.core.channels.SeekableByteChannelFactory;
import emissary.directory.DirectoryEntry;
import emissary.output.formatter.filter.FilterEmit.EmitMode;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/** JSON formatter using Jackson. */
public class JsonFormatter extends AbstractFormatter {

    protected ObjectMapper jsonMapper;

    @Override
    protected String defaultName() {
        return "JSON";
    }

    @Override
    public void initialize(final Configurator configG, @Nullable final String name, final Configurator formatterConfig) {
        super.initialize(configG, name, formatterConfig);
        initJsonMapper();
    }

    /** Create the Jackson object mapper. */
    protected void initJsonMapper() {
        jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new IbdoModule());
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.addMixIn(IBaseDataObject.class, emitMode == EmitMode.CONTENT ? IbdoContentOnlyMixin.class : IbdoMixin.class);
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

    /** Serializes metadata parameters, dropping filtered keys and values. */
    class IbdoParameterFilter extends SimpleBeanPropertyFilter {

        private static final char KEY_REPLACEMENT = '_';

        @Override
        public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {

            String key = writer.getName();
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) ((Map<?, ?>) pojo).get(key);

            if (!isMetadataAllowed(key)) {
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
            final Iterator<Object> it = values.iterator();
            if (!it.hasNext()) {
                return List.of();
            }
            final Object first = it.next();
            if (!it.hasNext()) {
                return isMetadataAllowed(key, first) ? List.of(first) : List.of();
            }
            final Set<Object> keep = new LinkedHashSet<>();
            if (isMetadataAllowed(key, first)) {
                keep.add(first);
            }
            while (it.hasNext()) {
                final Object value = it.next();
                if (isMetadataAllowed(key, value)) {
                    keep.add(value);
                }
            }
            return keep;
        }

        protected String transform(String name) {
            return normalize(strip(name.toUpperCase(Locale.ROOT)));
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
            jgen.writeStartObject();
            JavaType javaType = provider.constructType(IBaseDataObject.class);
            BeanDescription beanDesc = provider.getConfig().introspect(javaType);
            JsonSerializer<Object> serializer = BeanSerializerFactory.instance.findBeanOrAddOnSerializer(provider, javaType, beanDesc,
                    provider.isEnabled(MapperFeature.USE_STATIC_TYPING));

            jgen.writeObjectField("id", dropOffUtil.getBestIdFrom(ibdo));
            jgen.writeObjectField("processedTimestamp", TimeUtil.getCurrentDateFullISO8601());

            serializer.unwrappingSerializer(null).serialize(ibdo, jgen, provider);

            if (isContentEmitAllowed() && isContentAllowed(ibdo, PRIMARY_VIEW_NAME)) {
                byte[] payload = ibdo.data();
                if (payload != null && payload.length > 0) {
                    jgen.writeObjectField("payload", payload);
                }
            }

            if (isContentEmitAllowed()) {
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
            }

            jgen.writeEndObject();
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

    /** Mixin for {@link EmitMode#CONTENT}: all {@link IbdoMixin} fields except the metadata parameters. */
    abstract static class IbdoContentOnlyMixin extends IbdoMixin {
        @JsonIgnore
        @Override
        abstract Map<String, Collection<Object>> getParameters();
    }
}
