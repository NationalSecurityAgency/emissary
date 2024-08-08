package emissary.core.channels;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckCloseChannelFactory implements SeekableByteChannelFactory {
    private final AtomicInteger instanceNumber = new AtomicInteger(0);

    public final List<Boolean> isClosedList = Collections.synchronizedList(new ArrayList<>());

    @Override
    public SeekableByteChannel create() {
        isClosedList.add(false);

        return new AbstractSeekableByteChannel() {
            final int myInstanceNumber = instanceNumber.getAndIncrement();

            @Override
            protected void closeImpl() {
                isClosedList.set(myInstanceNumber, true);
            }

            @Override
            protected int readImpl(ByteBuffer byteBuffer) {
                return 0;
            }

            @Override
            protected long sizeImpl() {
                return 1;
            }
        };
    }
}
