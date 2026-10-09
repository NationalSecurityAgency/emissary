package emissary.output.formatter.filter;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.core.EmissaryRuntimeException;
import emissary.output.formatter.filter.content.sample.ContentMaxViewSizeFilter;
import emissary.output.formatter.filter.metadata.MetadataAllowFilter;
import emissary.output.formatter.filter.metadata.MetadataDenyFilter;
import emissary.output.formatter.filter.metadata.MetadataFilter;
import emissary.output.formatter.filter.metadata.MetadataFilterChain;
import emissary.output.formatter.filter.metadata.sample.MetadataFieldPatternFilter;
import emissary.output.formatter.filter.metadata.sample.MetadataMaxValueSizeFilter;
import emissary.output.formatter.filter.util.FilterClassFactory;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataFilterTest extends UnitTest {

    MetadataFilter getFilter(final Configurator config) {
        return FilterClassFactory.createFilterChain(config, FilterConfigKeys.METADATA_FILTER, MetadataFilter.class,
                "MetadataFilter", MetadataFilterChain::new);
    }

    @Test
    void testParameterDenyOnly() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("DENYLIST_FIELD", "DROP");
        config.addEntry("DENYLIST_PREFIX", "DROP_");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataDenyFilter.class, filter.getFilters().get(0), "deny list should lead the chain");

        assertFalse(filter.allows("DROP"), "denylisted field should be denied");
        assertFalse(filter.allows("DROP_ME"), "denylisted prefix should be denied");
        assertTrue(filter.allows("KEEP"), "unlisted field should be allowed");
        assertTrue(filter.allows("KEEP_ME"), "unlisted prefix field should be allowed");
    }

    @Test
    void testParameterValueDeny() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("DENYLIST_VALUE_KEY", "SECRET");

        MetadataFilter filter = getFilter(config);

        assertTrue(filter.allows("KEY", "public"), "non-denied value should be allowed");
        assertFalse(filter.allows("KEY", "SECRET"), "denied value should be rejected");
    }

    @Test
    void testParameterAllowOnly() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataAllowFilter.class.getName());
        config.addEntry("ALLOWLIST_FIELD", "KEEP");
        config.addEntry("ALLOWLIST_PREFIX", "KEEP_");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataAllowFilter.class, filter.getFilters().get(0), "allow list should lead the chain");

        assertTrue(filter.allows("KEEP"), "allowlisted field should be allowed");
        assertTrue(filter.allows("KEEP_ME"), "allowlisted prefix should be allowed");
        assertFalse(filter.allows("DROP"), "unlisted field should be denied");
        assertFalse(filter.allows("DROP_ME"), "unlisted prefix field should be denied");
    }

    @Test
    void testParameterAllowStar() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataAllowFilter.class.getName());
        config.addEntry("ALLOWLIST_FIELD", "*");

        MetadataFilter filter = getFilter(config);

        assertTrue(filter.allows("KEEP"), "all fields should be allowed when '*' is allowlisted");
        assertTrue(filter.allows("DROP"), "all fields should be allowed when '*' is allowlisted");
    }

    @Test
    void testParameterValueAllow() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataAllowFilter.class.getName());
        config.addEntry("ALLOWLIST_FIELD", "KEY");
        config.addEntry("ALLOWLIST_VALUE_KEY", "SECRET");

        MetadataFilter filter = getFilter(config);

        assertTrue(filter.allows("KEY"), "allowlisted field should be allowed");
        assertTrue(filter.allows("KEY", "SECRET"), "allowlisted value should be allowed");
        assertFalse(filter.allows("KEY", "public"), "non-allowlisted value should be rejected");
        assertFalse(filter.allows("OTHER"), "unlisted field should be denied");
    }

    @Test
    void testDenyAndAllowFiltersCanCoexist() {
        final Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("METADATA_FILTER", MetadataAllowFilter.class.getName());
        config.addEntry("DENYLIST_FIELD", "DROP");
        config.addEntry("ALLOWLIST_FIELD", "KEEP");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataDenyFilter.class, filter.getFilters().get(0), "deny filter should be first in declared order");
        assertInstanceOf(MetadataAllowFilter.class, filter.getFilters().get(1), "allow filter should follow in declared order");

        assertFalse(filter.allows("DROP"), "deny list denial should win");
        assertTrue(filter.allows("KEEP"), "field allowed by both should pass");
        assertFalse(filter.allows("OTHER"), "unlisted field should be denied by the allow filter");
    }

    @Test
    void testMetadataDenyAll() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("DENYLIST_FIELD", "*");
        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataDenyFilter.class, filter.getFilters().get(0), "declared deny filter should lead the chain");

        assertFalse(filter.allows("ANY"), "every field should be denied");
        assertFalse(filter.allows("ANY", "value"), "every field should be denied");
    }

    @Test
    void testStripPrefix() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("STRIP_PARAM_PREFIX", "APP_");

        MetadataFilter filter = getFilter(config);

        assertTrue(filter.stripPrefix("APP_FOO").equals("FOO"), "configured prefix should be stripped");
        assertTrue(filter.stripPrefix("OTHER").equals("OTHER"), "non-matching name should be unchanged");
    }

    @Test
    void testAcceptsEverythingByDefault() {
        MetadataFilterChain filter = (MetadataFilterChain) getFilter(new ServiceConfigGuide());
        assertTrue(filter.getFilters().isEmpty(), "no filters should be configured by default");
        assertTrue(filter.allows("ANY"), "anything should be allowed by default");
        assertTrue(filter.allows("ANY", "value"), "any value should be allowed by default");
    }

    @Test
    void testMetadataFieldPattern() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataFieldPatternFilter.class.getName());
        config.addEntry("METADATA_FIELD_PATTERN", "KEEP_.*|^ID$");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataFieldPatternFilter.class, filter.getFilters().get(0),
                "declared field pattern filter should be layered on the chain");

        assertTrue(filter.allows("KEEP_ME"), "matching key should be allowed");
        assertTrue(filter.allows("ID"), "matching key should be allowed");
        assertFalse(filter.allows("DROP"), "non-matching key should be denied");
    }

    @Test
    void testMetadataMaxValueSize() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataMaxValueSizeFilter.class.getName());
        config.addEntry("METADATA_MAX_VALUE_SIZE", "3");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataMaxValueSizeFilter.class, filter.getFilters().get(0), "declared value size filter should be layered on the chain");

        assertTrue(filter.allows("KEY", "ab"), "short value should be allowed");
        assertFalse(filter.allows("KEY", "abcde"), "long value should be denied");
        assertTrue(filter.allows("KEY", null), "null value should be allowed");
    }

    @Test
    void testRuntimeFiltersLayerOnDenyList() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("DENYLIST_FIELD", "DROP");
        config.addEntry("METADATA_FILTER", MetadataMaxValueSizeFilter.class.getName());
        config.addEntry("METADATA_MAX_VALUE_SIZE", "3");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataDenyFilter.class, filter.getFilters().get(0), "deny list should lead the chain");
        assertInstanceOf(MetadataMaxValueSizeFilter.class, filter.getFilters().get(1), "declared filter should follow the deny list");

        assertFalse(filter.allows("DROP"), "deny list denial should still apply");
        assertFalse(filter.allows("KEY", "abcde"), "declared filter denial should apply");
        assertTrue(filter.allows("KEY", "ab"), "value allowed by both filters should pass");
    }

    @Test
    void testFilterOrderFollowsDeclaredOrder() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", MetadataFieldPatternFilter.class.getName());
        config.addEntry("METADATA_FILTER", MetadataDenyFilter.class.getName());
        config.addEntry("METADATA_FIELD_PATTERN", "KEEP.*");
        config.addEntry("DENYLIST_FIELD", "DROP");

        MetadataFilterChain filter = (MetadataFilterChain) getFilter(config);
        assertInstanceOf(MetadataFieldPatternFilter.class, filter.getFilters().get(0),
                "filter declared before the deny filter should run first");
        assertInstanceOf(MetadataDenyFilter.class, filter.getFilters().get(1), "deny filter should run second");

        assertFalse(filter.allows("DROP"), "deny list denial should still apply");
    }

    @Test
    void testRuntimeConfiguredMetadataFilterIsTypeChecked() {
        Configurator config = new ServiceConfigGuide();
        config.addEntry("METADATA_FILTER", ContentMaxViewSizeFilter.class.getName());
        EmissaryRuntimeException e = assertThrows(EmissaryRuntimeException.class, () -> getFilter(config));
        assertTrue(e.getMessage().contains("is not a MetadataFilter"), "wrong filter type should be rejected");
    }
}
