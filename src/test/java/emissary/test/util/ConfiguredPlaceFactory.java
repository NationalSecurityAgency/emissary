package emissary.test.util;

import emissary.config.ConfigEntry;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.place.IServiceProviderPlace;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/**
 * Create instances of a {@link emissary.place.ServiceProviderPlace} with custom configurations for testing. Removes
 * need for a separate .cfg file and simplifies process of using multiple configurations in the same test class.
 * Override priority is as follows:
 * <ol>
 * <li>Highest priority configs are passed as params into the {@link #buildPlace(ConfigEntry...)} method</li>
 * <li>Next priority configs are defaults passed into the {@link #ConfiguredPlaceFactory} constructor</li>
 * <li>Lowest priority configs are found in the main {@code T} class .cfg file, if it exists</li>
 * </ol>
 *
 * @param <T> The Emissary place to create variations of
 */
public class ConfiguredPlaceFactory<T extends IServiceProviderPlace> {
    private final String placeName;
    private final Constructor<T> placeConstructor;
    private final Configurator defaultConfigurator;

    public ConfiguredPlaceFactory(Class<T> place, ConfigEntry... defaultConfigs) {
        placeName = place.getName();
        try {
            placeConstructor = place.getDeclaredConstructor(Configurator.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ConfiguredPlaceFactory instance for " + placeName, e);
        }

        defaultConfigurator = loadConfigFile(place);
        addAndReplaceConfigEntries(defaultConfigurator, defaultConfigs);
    }

    /**
     * Gets the configurations for a given {@link emissary.place.ServiceProviderPlace} that are used in production from its
     * .cfg file. Config files are expected to match the names of the place. E.g. {@code foo.BarPlace.java} expects
     * {@code foo.BarPlace.cfg}. If no such file exists, an empty {@link Configurator} object will be returned.
     *
     * @param place The place to get configurations for
     * @return Configurations based on the place's name if it exists, otherwise an empty {@link Configurator}
     */
    private Configurator loadConfigFile(Class<T> place) {
        try {
            return ConfigUtil.getConfigInfo(place);
        } catch (IOException e) {
            return new ServiceConfigGuide();
        }
    }

    /**
     * Create a new instance of a {@link emissary.place.ServiceProviderPlace} for testing with optional configurations.
     * Configs override any matching instances found in the actual .cfg file and/or default test configs.
     *
     * @param optionalConfigs list of new or overriding place configurations
     * @return new instance of place
     */
    public T buildPlace(ConfigEntry... optionalConfigs) {
        try {
            Configurator classConfigs = getClassConfigs(optionalConfigs);
            return placeConstructor.newInstance(classConfigs);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance of " + placeName, e);
        }
    }

    public Configurator getClassConfigs(ConfigEntry... optionalConfigs) {
        if (optionalConfigs.length == 0) {
            return defaultConfigurator;
        }
        Configurator configurator = copyConfigurator(defaultConfigurator);
        addAndReplaceConfigEntries(configurator, optionalConfigs);
        return configurator;
    }

    private static void addAndReplaceConfigEntries(Configurator configurator, ConfigEntry... configEntries) {
        Set<String> encounteredKeys = new HashSet<>();
        for (ConfigEntry entry : configEntries) {
            String key = entry.getKey();
            // Keep track of keys we've removed, so we don't accidentally remove them more than once
            if (!encounteredKeys.contains(key)) {
                removeAllConfigEntriesForKey(configurator, key);
                encounteredKeys.add(key);
            }
            configurator.addEntry(key, entry.getValue());
        }
    }

    private static void removeAllConfigEntriesForKey(Configurator configurator, String key) {
        for (String value : configurator.findEntries(key)) {
            configurator.removeEntry(key, value);
        }
    }

    private static Configurator copyConfigurator(Configurator source) {
        Configurator target = new ServiceConfigGuide();
        source.getEntries().forEach(entry -> target.addEntry(entry.getKey(), entry.getValue()));
        return target;
    }
}
