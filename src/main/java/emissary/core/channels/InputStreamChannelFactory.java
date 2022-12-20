package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.InputStream;
import java.util.function.BiFunction;

/**
 * Channel factory used to provide channel-like access to input stream data. Channel instances provided by these
 * factories are immutable.
 */
public class InputStreamChannelFactory {
    private InputStreamChannelFactory() {}

    /**
     * Creates a factory instance used to provide channel-like access to input stream data.
     *
     * @param size stream length, if known. A negative value forces the factory to work out the size upon first create
     * @param inputStreamFactory factory for the input stream access
     * @return a channel factory instance enabling channel-like access to the underlying input stream data
     */
    public static ImmutableChannelFactory<InputStreamChannel<?>> createFactory(final long size,
            final InputStreamFactory<?> inputStreamFactory) {
        Validate.notNull(inputStreamFactory, "Required: inputStream not null");

        return ImmutableChannelFactory.createFactory(() -> new InputStreamChannel<>(size, inputStreamFactory));
    }

    /**
     * Creates a factory implementation based on an {@link InputStreamFactory}. Unlike the other
     * {@link InputStreamChannelFactory#createFactory} overload, this overload allows the use of a custom
     * {@link InputStreamChannel} subtype so that explicit {@link InputStream} subtypes can be returned by the factory.
     *
     * @param size stream length, if known. A negative value forces the factory to work out the size upon first create
     * @param inputStreamFactory factory for the input stream access
     * @param channelProvider function reference used to create the specific type of {@link InputStreamChannel}.
     * @param <T> Type of InputStream subtype for which this factory will provide channel-like access
     * @param <U> Type of InputStreamChannel that works with these specific InputStream subtypes
     * @return a channel factory instance enabling channel-like access to the underlying input stream data
     */
    public static <T extends InputStream, U extends InputStreamChannel<T>> ImmutableChannelFactory<U> createFactory(final Long size,
            final InputStreamFactory<T> inputStreamFactory, BiFunction<Long, InputStreamFactory<T>, U> channelProvider) {
        Validate.notNull(inputStreamFactory, "Required: inputStream not null");

        return ImmutableChannelFactory.createFactory(() -> channelProvider.apply(size, inputStreamFactory));
    }

}
