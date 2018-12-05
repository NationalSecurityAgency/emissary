package emissary.core.view;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import emissary.core.MetadataDictionary;
import emissary.core.NamespaceException;
import emissary.core.blob.DataException;
import emissary.core.blob.IDataContainer;
import emissary.core.blob.SelectingDataContainer;

/**
 * Standard implementation of {@link IViewManager}.
 *
 */
public class ViewManager implements IViewManager {

    /**
     * 
     */
    private static final long serialVersionUID = -6591038506218822977L;
    /** The views sorted */
    private Map<String, IDataContainer> views = new TreeMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumAlternateViews() {
        return views.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public byte[] getAlternateView(String arg1) {
        IDataContainer cont = getAlternateViewContainer(arg1);
        return cont != null && cont.data().length != 0 ? cont.data() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public ByteBuffer getAlternateViewBuffer(String arg1) {
        IDataContainer cont = getAlternateViewContainer(arg1);
        return cont != null ? cont.dataBuffer() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void addAlternateView(String name, byte[] data) {
        if (data == null || data.length == 0) {
            removeView(name);
            return;
        }
        addAlternateView(name).setData(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void addAlternateView(String name, byte[] data, int offset, int length) {
        if (data == null || length == 0) {
            removeView(name);
            return;
        }
        addAlternateView(name).setData(data, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void appendAlternateView(String name, byte[] data) {
        appendAlternateView(name, data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void appendAlternateView(String name, byte[] data, int offset, int length) {
        IDataContainer cont = getAlternateViewContainer(name);
        if (cont == null) {
            cont = addAlternateView(name);
        }
        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size());
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.position(offset);
            buf.limit(offset + length);
            channel.write(buf);
        } catch (IOException e) {
            throw new DataException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAlternateViewNames() {
        return views.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public Map<String, byte[]> getAlternateViews() {
        return views.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().data(),
                (x, y) -> x,
                TreeMap::new));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataContainer getAlternateViewContainer(String name) {
        String mappedName = getMappedName(name);
        return views.get(mappedName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataContainer addAlternateView(String name) {
        String mappedName = getMappedName(name);
        IDataContainer container = new SelectingDataContainer();
        views.put(mappedName, container);
        return container;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAlternateViewContainer(String name, IDataContainer cont) {
        if (cont == null) {
            removeView(name);
            return;
        }

        String mappedName = getMappedName(name);
        views.put(mappedName, cont);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, IDataContainer> getAlternateViewContainers() {
        return Collections.unmodifiableMap(views);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeView(String name) {
        String mappedName = getMappedName(name);
        return views.remove(mappedName) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IViewManager clone() {
        ViewManager result = new ViewManager();
        result.views = views.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    try {
                        return entry.getValue().clone();
                    } catch (CloneNotSupportedException e) {
                        throw new DataException(e);
                    }
                },
                (x, y) -> x,
                TreeMap::new));
        return result;
    }

    /**
     * Lookup the mapped name for the view.
     * 
     * @param name The base name
     * @return A mapped name if one is configued.
     */
    private String getMappedName(String name) {
        String mappedName = name;
        try {
            final MetadataDictionary dict = MetadataDictionary.lookup();
            mappedName = dict.map(name);
        } catch (NamespaceException ex) {
            // ignore
        }
        return mappedName;
    }
}
