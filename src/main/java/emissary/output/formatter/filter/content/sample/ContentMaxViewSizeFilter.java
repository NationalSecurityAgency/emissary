package emissary.output.formatter.filter.content.sample;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.output.formatter.filter.content.ContentFilter;

/**
 * Content filter that rejects alternate views whose byte length exceeds the configured {@code CONTENT_MAX_VIEW_SIZE}.
 */
public class ContentMaxViewSizeFilter extends ContentFilter {

    public static final String MAX_VIEW_SIZE = "CONTENT_MAX_VIEW_SIZE";

    private long maxViewSize;

    @Override
    public void configure(final Configurator config) {
        super.configure(config);
        if (config == null) {
            return;
        }
        this.maxViewSize = config.findSizeEntry(MAX_VIEW_SIZE, 0);
    }

    @Override
    public boolean allows(final IBaseDataObject d, final String viewName) {
        byte[] view = d.getAlternateView(viewName);
        return view == null || view.length <= this.maxViewSize;
    }
}
