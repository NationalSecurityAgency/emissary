package emissary.core.kryo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.google.common.collect.ArrayListMultimap;
import emissary.core.BDOKryoSerializer;
import emissary.core.BaseDataObject;

/**
 * This class provides a factory and thread local implementation for BaseDataObject (de)serialization. Kryo is not
 * thread safe and each thread needs its own instance of the Kryo object. In addition, for proper handing of the
 * serialization, Kryo objects must be configured identically for both reading and writing. Given the expense of
 * creating a kryo object, it is recommended that you use this ThreadLocal or object pooling.
 */
public final class EmissaryKryoFactory extends ThreadLocal<Kryo> {
    /**
     * ThreadLocal override for instantiating a Kryo object capable of reading/writing <code>BaseDataObjects</code>.
     * 
     * @return
     */
    @Override
    protected Kryo initialValue() {
        return buildKryo();
    }

    /**
     * Instantiates a Kryo object for (de)serializing BaseDataObjects. It should be noted that implementations have the
     * option to read/write either <code>ArrayList</code>s of BaseDataObjects or individual BaseDataObjects.
     * 
     * @return
     */
    public static Kryo buildKryo() {
        final DefaultClassResolver resolver = new DefaultClassResolver();
        final MapReferenceResolver mfr = new MapReferenceResolver();
        final Kryo kryo = new Kryo(resolver, mfr);
        kryo.setRegistrationRequired(true);
        // primitives are registered by default
        kryo.register(BaseDataObject.class, new BDOKryoSerializer(resolver));
        kryo.register(byte[].class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(ArrayListMultimap.class);
        kryo.register(StringBuilder.class);
        kryo.register(TreeMap.class);
        return kryo;
    }
}
