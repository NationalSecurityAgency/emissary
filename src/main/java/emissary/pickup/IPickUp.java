package emissary.pickup;

import emissary.util.ClassComparator;

public interface IPickUp {

    void shutDown();

    static boolean isImplementation(Class<? extends Object> clazz) {
        return ClassComparator.isaImplementation(clazz, IPickUp.class);
    }
}
