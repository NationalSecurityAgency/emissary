package emissary.core.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import emissary.core.IBaseDataObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Utility class to deserialize Family Trees from a serialzed Kryo stream/file.
 */
public class BDODeserialzerIterator implements Iterator<List<IBaseDataObject>>, AutoCloseable {

    static final EmissaryKryoFactory KRYO = new EmissaryKryoFactory();
    static final int BUFF_SIZE = 8192;
    final InputStream src;
    final Kryo k = KRYO.get();
    ArrayList<IBaseDataObject> next;
    Input i;

    public BDODeserialzerIterator(InputStream src) {
        this.src = src;
        i = new Input(src, BUFF_SIZE);
        // initialize the iterator
        next = getNext();
    }

    public BDODeserialzerIterator(Path path) throws IOException {
        this(Files.newInputStream(path, StandardOpenOption.READ));
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public List<IBaseDataObject> next() {
        if (next == null) {
            throw new NoSuchElementException("No further elements");
        }
        List<IBaseDataObject> toReturn = next;
        next = getNext();
        return toReturn;
    }

    @Override
    public void close() {
        i.close();
    }

    private ArrayList<IBaseDataObject> getNext() {
        return i.eof() ? null : k.readObject(i, ArrayList.class);
    }
}
