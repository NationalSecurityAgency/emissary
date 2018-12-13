package emissary.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class BDOKryoSerializer extends Serializer<BaseDataObject> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(BDOKryoSerializer.class);
    /*
     * Allows us to modify legacy versions of serialized data by delegating to a legacy reader and adapt to current
     * versions. Adds one byte of overhead.
     */
    protected static final byte CURRENT_VERSION = 0x01;


    final ClassResolver clzResolver;

    public BDOKryoSerializer(ClassResolver clzResolver) {
        this.clzResolver = clzResolver;
    }


    @Override
    public void write(Kryo kryo, Output output, BaseDataObject t) {
        output.writeByte(CURRENT_VERSION);
        kryo.writeObjectOrNull(output, t.data(), byte[].class);
        output.writeString(t.getFilename());
        output.writeLong(t.internalId.getMostSignificantBits());
        output.writeLong(t.internalId.getLeastSignificantBits());
        kryo.writeObject(output, t.currentForm);
        kryo.writeObjectOrNull(output, t.procError, StringBuilder.class);
        kryo.writeObject(output, t.history);
        kryo.writeObjectOrNull(output, t.fontEncoding, String.class);
        // parameters
        writeMultimap(kryo, output, t.parameters);
        output.writeInt(t.numChildren, true);
        output.writeInt(t.numSiblings, true);
        output.writeInt(t.birthOrder, true);
        kryo.writeObject(output, t.multipartAlternative);
        kryo.writeObjectOrNull(output, t.header, byte[].class);
        kryo.writeObjectOrNull(output, t.footer, byte[].class);
        output.writeString(t.headerEncoding);
        output.writeString(t.classification);
        kryo.writeObjectOrNull(output, t.brokenDocument, StringBuilder.class);
        output.writeInt(t.priority, true);
        output.writeLong(t.creationTimestamp.getTime());
        kryo.writeObjectOrNull(output, t.extractedRecords, ArrayList.class);

    }

    /**
     * Reads serialized base data object.
     * 
     * @param kryo
     * @param input
     * @param type
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public BaseDataObject read(Kryo kryo, Input input, Class<BaseDataObject> type) {
        final BaseDataObject bdo = new BaseDataObject();
        // read the version, but we don't need to do anything right now.
        final byte ver = input.readByte();
        if (CURRENT_VERSION != ver) {
            throw new IllegalStateException("Unsupported version number found: " + ver);
        }
        bdo.setData(kryo.readObjectOrNull(input, byte[].class));
        bdo.setFilename(input.readString());
        bdo.internalId = new UUID(input.readLong(), input.readLong());
        bdo.currentForm = kryo.readObject(input, ArrayList.class);
        bdo.procError = kryo.readObjectOrNull(input, StringBuilder.class);
        bdo.history = kryo.readObject(input, ArrayList.class);
        bdo.fontEncoding = kryo.readObjectOrNull(input, String.class);
        readMultimap(kryo, input, bdo.parameters);
        bdo.numChildren = input.readInt(true);
        bdo.numSiblings = input.readInt(true);
        bdo.birthOrder = input.readInt(true);
        bdo.multipartAlternative = kryo.readObject(input, TreeMap.class);
        bdo.header = kryo.readObjectOrNull(input, byte[].class);
        bdo.footer = kryo.readObjectOrNull(input, byte[].class);
        bdo.headerEncoding = input.readString();
        bdo.classification = input.readString();
        bdo.brokenDocument = kryo.readObjectOrNull(input, StringBuilder.class);
        bdo.priority = input.readInt(true);
        bdo.creationTimestamp = new Date(input.readLong());
        bdo.extractedRecords = kryo.readObjectOrNull(input, ArrayList.class);
        return bdo;
    }

    protected void writeMultimap(Kryo kryo, Output output, ListMultimap<String, Object> multimap) {
        output.writeInt(multimap.size(), true);
        for (final Map.Entry<String, Object> entry : multimap.entries()) {
            Object value = entry.getValue();
            if (clzResolver.getRegistration(value.getClass()) == null) {
                LOGGER.warn("Found class {} in a parameter map, not registered with Kryo. Converting to String which may result in loss.",
                        value.getClass());
                value = value.toString();
            }
            output.writeString(entry.getKey());
            kryo.writeClassAndObject(output, value);
        }
    }

    protected void readMultimap(Kryo kryo, Input input, ListMultimap<String, Object> multimap) {
        final int size = input.readInt(true);
        for (int i = 0; i < size; ++i) {
            final String key = input.readString();
            final Object value = kryo.readClassAndObject(input);
            multimap.put(key, value);
        }
    }

}
