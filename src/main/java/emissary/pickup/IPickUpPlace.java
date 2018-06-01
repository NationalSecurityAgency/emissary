/**
 * IPickUpPlace.java
 */
package emissary.pickup;

public interface IPickUpPlace extends emissary.place.IServiceProviderPlace {
    String getDoneArea();

    String getInProcessArea();

    String getErrorArea();

    String getOversizeArea();

    long getMaximumContentLength();

    int getMinimumContentLength();

}
