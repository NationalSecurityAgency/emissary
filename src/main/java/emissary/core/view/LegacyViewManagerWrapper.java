package emissary.core.view;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import emissary.core.blob.ChangeNofifyingDataContainer;
import emissary.core.blob.IDataContainer;
import emissary.core.blob.SelectingDataContainer;

@SuppressWarnings("deprecation")
class LegacyViewManagerWrapper implements IViewManager {

    /**
     * 
     */
    private static final long serialVersionUID = -1275029449834352314L;
    private IOriginalViewManager oldView;

    LegacyViewManagerWrapper(IOriginalViewManager oldView) {
        this.oldView = oldView;
    }

    @Override
    public int getNumAlternateViews() {
        return oldView.getNumAlternateViews();
    }

    @Override
    public byte[] getAlternateView(String arg1) {
        return oldView.getAlternateView(arg1);
    }

    @Override
    public ByteBuffer getAlternateViewBuffer(String arg1) {
        return oldView.getAlternateViewBuffer(arg1);
    }

    @Override
    public void addAlternateView(String name, byte[] data) {
        oldView.addAlternateView(name, data);
    }

    @Override
    public void addAlternateView(String name, byte[] data, int offset, int length) {
        oldView.addAlternateView(name, data, offset, length);
    }

    @Override
    public void appendAlternateView(String name, byte[] data) {
        oldView.appendAlternateView(name, data);
    }

    @Override
    public void appendAlternateView(String name, byte[] data, int offset, int length) {
        oldView.appendAlternateView(name, data, offset, length);
    }

    @Override
    public Set<String> getAlternateViewNames() {
        return oldView.getAlternateViewNames();
    }

    @Override
    public Map<String, byte[]> getAlternateViews() {
        return oldView.getAlternateViews();
    }

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

    @Override
    public IDataContainer addAlternateView(String viewName) {
        SelectingDataContainer dc = new SelectingDataContainer();
        ChangeNofifyingDataContainer cndc = new ChangeNofifyingDataContainer(dc);
        cndc.registerChangeListener(changedDc -> addAlternateView(viewName, changedDc.data()));
        return cndc;
    }

    @Override
    public Map<String, IDataContainer> getAlternateViewContainers() {
        return getAlternateViews().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> getAlternateViewContainer(e.getKey())));
    }

    @Override
    public boolean removeView(String name) {
        boolean ret = getAlternateView(name) != null;
        addAlternateView(name, null);
        return ret;
    }

    @Override
    public IViewManager clone() {
        LegacyViewManagerWrapper clone = new LegacyViewManagerWrapper(oldView);
        return clone;
    }

}
