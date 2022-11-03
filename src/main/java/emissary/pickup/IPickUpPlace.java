/**
 * IPickUpPlace.java
 */
package emissary.pickup;

import emissary.place.IServiceProviderPlace;

public interface IPickUpPlace extends IServiceProviderPlace {
    String getDoneArea();

    String getInProcessArea();

    String getErrorArea();

    String getOversizeArea();

    long getMaximumContentLength();

    int getMinimumContentLength();

}
