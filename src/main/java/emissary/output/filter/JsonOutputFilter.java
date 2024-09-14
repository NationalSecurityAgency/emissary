package emissary.output.filter;

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
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * JSON Output filter using Jackson
 */
public class JsonOutputFilter extends AbstractRollableFilter {

    protected Set<String> denylistFields = new TreeSet<>();
    protected Set<String> denylistPrefixes = new TreeSet<>();
    protected Set<String> allowlistFields = new TreeSet<>();
    protected Set<String> allowlistPrefixes = new TreeSet<>();
    protected Map<String, Set<String>> denylistValues;
    protected Set<String> stripPrefixes;

    protected boolean emitPayload = true;

    protected ObjectMapper jsonMapper;

    @Override
    public void initialize(final Configurator theConfigG, @Nullable final String filterName, final Configurator theFilterConfig) {
        if (filterName == null) {
            setFilterName("JSON");
        }
        super.initialize(theConfigG, filterName, theFilterConfig);
        this.allowlistFields.addAll(this.filterConfig.findEntries("EXTRA_PARAM"));
        this.allowlistPrefixes.addAll(this.filterConfig.findEntries("EXTRA_PREFIX"));
        this.denylistFields.addAll(this.filterConfig.findEntries("DENYLIST_FIELD"));
        this.denylistPrefixes.addAll(this.filterConfig.findEntries("DENYLIST_PREFIX"));
        this.denylistValues = this.filterConfig.findStringMatchMultiMap("DENYLIST_VALUE_");
        this.stripPrefixes = this.filterConfig.findEntriesAsSet("STRIP_PARAM_PREFIX");
        this.emitPayload = this.filterConfig.findBooleanEntry("EMIT_PAYLOAD", true);
        initFilenameGenerator();
        initJsonMapper();
    }

    @Override
    protected void initFilenameGenerator() {
        this.fileNameGenerator = new DateFilterFilenameGenerator("json");
    }

    /**
     * Initialize the Jackson json object mapper
     */
    protected void initJsonMapper() {
        jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new IbdoModule());
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.addMixIn(IBaseDataObject.class, emitPayload ? IbdoPayloadMixin.class : IbdoParameterMixin.class);
        // the id in addFilter must match the annotation for JsonFilter
        jsonMapper.setFilterProvider(new SimpleFilterProvider().addFilter("param_filter", new IbdoParameterFilter()));
    }

    @Override
    public byte[] convert(final List<IBaseDataObject> list, final Map<String, Object> params) throws IOException {
        return jsonMapper.writeValueAsBytes(list);
    }

    class IbdoParameterFilter extends SimpleBeanPropertyFilter {

        protected final boolean outputAll;
        protected final boolean emptyDenylist;
        protected final boolean denylistStar;
        protected final boolean emptyAllowlist;
        protected final boolean allowlistStar;
        private static final char KEY_REPLACEMENT = '_';

        public IbdoParameterFilter() {
            // if all collections are empty, then output everything
            this.allowlistStar = (allowlistFields.contains("*") || allowlistFields.contains("ALL"));
            this.denylistStar = (denylistFields.contains("*") || denylistFields.contains("ALL"));
            this.emptyDenylist = CollectionUtils.isEmpty(denylistFields) && CollectionUtils.isEmpty(denylistPrefixes);
            this.emptyAllowlist = CollectionUtils.isEmpty(allowlistFields) && CollectionUtils.isEmpty(allowlistPrefixes);
            this.outputAll = emptyDenylist && (allowlistStar || emptyAllowlist);
        }

        @Override
        public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {

            String key = writer.getName();
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) ((Map<?, ?>) pojo).get(key);

            if (includeParameter(key)) {
                Collection<Object> write = filter(key, values);
                if (CollectionUtils.isNotEmpty(write)) {
                    // customize the key
                    jgen.writeFieldName(transform(key));

                    // only write the element
                    ((MapProperty) writer).setValue(write);
                    writer.serializeAsElement(write, jgen, provider);
                }
            }
        }

        protected boolean includeParameter(String key) {
            if (outputAll) {
                return true;
            }

            // check the allow/deny list first
            if (denylistFields.contains(key)) {
                return false;
            } else if (allowlistFields.contains(key)) {
                return true;
            }

            // see if there is a hit on the denylist prefix
            for (final String prefix : denylistPrefixes) {
                if (key.startsWith(prefix)) {
                    return false;
                }
            }

            // omit/emit all parameters if '*' or 'ALL'
            if (denylistStar) {
                return false;
            } else if (allowlistStar) {
                return true;
            }

            // there is a hit on the allow-list prefix, but it is on the deny-list
            for (final String prefix : allowlistPrefixes) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }

            // if we were only given an deny-list, output all keys
            return emptyAllowlist;
        }

        protected Collection<Object> filter(String key, Collection<Object> values) {
            Set<Object> keep = new TreeSet<>();
            for (final Object value : values) {
                if (!(denylistValues.containsKey(key) && denylistValues.get(key).contains(value.toString()))) {
                    keep.add(value);
                }
            }
            return keep;
        }

        protected String transform(String name) {
            return normalize(strip(name.toUpperCase(Locale.getDefault())));
        }

        protected String strip(String name) {
            for (final String prefix : stripPrefixes) {
                if (name.startsWith(prefix)) {
                    return name.substring(prefix.length());
                }
            }
            return name;
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
        public void serialize(IBaseDataObject ibdo, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            JavaType javaType = provider.constructType(IBaseDataObject.class);
            BeanDescription beanDesc = provider.getConfig().introspect(javaType);
            JsonSerializer<Object> serializer = BeanSerializerFactory.instance.findBeanOrAddOnSerializer(provider, javaType, beanDesc,
                    provider.isEnabled(MapperFeature.USE_STATIC_TYPING));

            // add some custom fields here
            jgen.writeObjectField("id", dropOffUtil.getBestIdFrom(ibdo));
            jgen.writeObjectField("processedTimestamp", TimeUtil.getCurrentDateFullISO8601());

            serializer.unwrappingSerializer(null).serialize(ibdo, jgen, provider);
            jgen.writeEndObject();
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

        @JsonProperty("views")
        @JsonInclude(NON_EMPTY)
        abstract Map<String, byte[]> getAlternateViews();
    }
}
