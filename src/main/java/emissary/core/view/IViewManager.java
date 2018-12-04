package emissary.core.view;

import java.util.Map;

import emissary.core.IBaseDataObject;
import emissary.core.blob.IDataContainer;

/**
 * Container for alternate views of data, with capability additional to the original {@link IBaseDataObject}.
 *
 */
public interface IViewManager extends IOriginalViewManager {

    /**
     * Get an alternate view by name.
     * 
     * @param viewName The name of the view.
     * @return The view or null if it is not present.
     */
    IDataContainer getAlternateViewContainer(String viewName);

    /**
     * Add an empty alternate view, which can then be written to.
     * 
     * @param viewName The name of the view.
     * @return An empty data container to write to.
     */
    IDataContainer addAlternateView(String viewName);

    /**
     * Get all the views as an umodifiable map.
     * 
     * @return All the views.
     */
    Map<String, IDataContainer> getAlternateViewContainers();

    /**
     * Remove a view.
     * 
     * @param name The name of the view to remove.
     * @return Whether a view was removed.
     */
    boolean removeView(String name);

    IViewManager clone();

    /**
     * Wrap an {@link IOriginalViewManager} such that it can be accessed via the {@link IViewManager} api.
     * 
     * @param oldView The views to wrap.
     * @return The views meeting the new api.
     */
    static IViewManager wrap(IOriginalViewManager oldView) {
        return new LegacyViewManagerWrapper(oldView);
    }
}
