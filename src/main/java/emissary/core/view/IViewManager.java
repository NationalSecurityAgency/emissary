package emissary.core.view;

import java.util.Map;

import emissary.core.blob.IDataContainer;

public interface IViewManager extends IOriginalViewManager {

    IDataContainer getAlternateViewContainer(String viewName);

    IDataContainer addAlternateView(String viewName);

    Map<String, IDataContainer> getAlternateViewContainers();

    boolean removeView(String name);

    IViewManager clone();

    static IViewManager wrap(IOriginalViewManager oldView) {
        return new LegacyViewManagerWrapper(oldView);
    }
}
