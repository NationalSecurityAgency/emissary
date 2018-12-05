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
     * Add an alternate view using an existing data container. Reference equality may maintained, so future changes to the
     * data container are maintained.
     * 
     * @param viewName The name of the view.
     * @param cont The container to add.
     */
    void addAlternateViewContainer(String viewName, IDataContainer cont);

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
     * Get an {@link IViewManager} that "Updates" an object that only supports the methods from previous versions of
     * {@link IBaseDataObject}, as defined in {@link IOriginalViewManager}, such that alternate implementations that have
     * not been updated can still be used.
     * 
     * @param oldView The views to wrap.
     * @return The views meeting the new api.
     */
    static IViewManager wrap(IOriginalViewManager oldView) {
        return new LegacyViewManagerWrapper(oldView);
    }
}
