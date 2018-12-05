package emissary.core.view;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import emissary.core.IBaseDataObject;
import emissary.core.blob.ChangeNofifyingDataContainer;
import emissary.core.blob.IDataContainer;
import emissary.core.blob.SelectingDataContainer;

/**
 * An {@link IViewManager} that "Updates" an object that only supports the methods from previous versions of
 * {@link IBaseDataObject}, as defined in {@link IOriginalViewManager}, such that alternate implementations that have
 * not been updated can still be used.
 *
 */
@SuppressWarnings("deprecation")
class LegacyViewManagerWrapper implements IViewManager {

    /**
     * 
     */
    private static final long serialVersionUID = -1275029449834352314L;
    /** The object meeting the legacy api only. */
    private IOriginalViewManager oldView;

    /**
     * Wrap an object that only meets the legacy api.
     * 
     * @param oldView
     */
    LegacyViewManagerWrapper(IOriginalViewManager oldView) {
        this.oldView = oldView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumAlternateViews() {
        return oldView.getNumAlternateViews();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getAlternateView(String arg1) {
        return oldView.getAlternateView(arg1);
    }

    @Override
    public ByteBuffer getAlternateViewBuffer(String arg1) {
        return oldView.getAlternateViewBuffer(arg1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAlternateView(String name, byte[] data) {
        oldView.addAlternateView(name, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAlternateView(String name, byte[] data, int offset, int length) {
        oldView.addAlternateView(name, data, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendAlternateView(String name, byte[] data) {
        oldView.appendAlternateView(name, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void appendAlternateView(String name, byte[] data, int offset, int length) {
        oldView.appendAlternateView(name, data, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAlternateViewNames() {
        return oldView.getAlternateViewNames();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, byte[]> getAlternateViews() {
        return oldView.getAlternateViews();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataContainer getAlternateViewContainer(String viewName) {
        byte[] bs = getAlternateView(viewName);
        if (bs == null) {
            return null;
        }
        IDataContainer dc = addAlternateView(viewName);
        dc.setData(bs);
        return dc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDataContainer addAlternateView(String viewName) {
        SelectingDataContainer dc = new SelectingDataContainer();
        ChangeNofifyingDataContainer cndc = new ChangeNofifyingDataContainer(dc);
        cndc.registerChangeListener(changedDc -> addAlternateView(viewName, changedDc.data()));
        return cndc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAlternateViewContainer(String viewName, IDataContainer cont) {
        addAlternateView(viewName, cont != null ? cont.data() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, IDataContainer> getAlternateViewContainers() {
        return getAlternateViews().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> getAlternateViewContainer(e.getKey())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeView(String name) {
        boolean ret = getAlternateView(name) != null;
        addAlternateView(name, (byte[]) null);
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IViewManager clone() {
        LegacyViewManagerWrapper clone = new LegacyViewManagerWrapper(oldView);
        return clone;
    }

}
