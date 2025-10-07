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
 * <i>Note</i>: setting the value of a {@link ConfigEntry} to {@code null} will remove existing instances of the
 * configuration.
 *
 * @param <T> The Emissary place to create variations of
 */
public class ConfiguredPlaceFactory<T extends IServiceProviderPlace> {
    private String placeName;
    private Constructor<T> placeConstructor;
    private Configurator defaultConfigurator;

    public ConfiguredPlaceFactory(Class<T> place, ConfigEntry... defaultConfigs) {
        this(place, true, defaultConfigs);
    }

    public ConfiguredPlaceFactory(Class<T> place, boolean useCfgFile, ConfigEntry... defaultConfigs) {
        initializeFactory(place, useCfgFile ? loadConfigFile(place) : new ServiceConfigGuide(), defaultConfigs);
    }

    public ConfiguredPlaceFactory(Class<T> place, Configurator baseConfigurator, ConfigEntry... defaultConfigs) {
        initializeFactory(place, baseConfigurator, defaultConfigs);
    }

    private void initializeFactory(Class<T> place, Configurator baseConfigurator, ConfigEntry... defaultConfigs) {
        placeName = place.getName();
        placeConstructor = getPlaceConstructor(place);
        defaultConfigurator = baseConfigurator;
        addAndReplaceConfigEntries(defaultConfigurator, defaultConfigs);
    }

    private Constructor<T> getPlaceConstructor(Class<T> place) {
        try {
            return place.getDeclaredConstructor(Configurator.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create ConfiguredPlaceFactory instance for " + place.getName(), e);
        }
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
     * <p>
     * <i>Note</i>: setting the value of a {@link ConfigEntry} to {@code null} will remove existing instances of the
     * configuration.
     *
     * @param optionalConfigs list of new or overriding place configurations
     * @return new instance of place
     */
    public T buildPlace(ConfigEntry... optionalConfigs) {
        try {
            Configurator classConfigs = newInstanceConfigurator(optionalConfigs);
            return placeConstructor.newInstance(classConfigs);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance of " + placeName, e);
        }
    }

    private Configurator newInstanceConfigurator(ConfigEntry... optionalConfigs) {
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
            if (entry.getValue() != null) {
                configurator.addEntry(key, entry.getValue());
            }
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
