package emissary.kff;

import emissary.kff.KffFilter.FilterType;
import emissary.test.core.junit5.UnitTest;

import jakarta.annotation.Nullable;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationStatus;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

class KffMemcachedTest extends UnitTest {

    private static final String TEST_ID_WITH_SPACES = "TEST ID";
    private static final String TEST_PAYLOAD = "TEST DATA";
    private static final String TEST_UNFORMATTED_ID_HASH = "01e44cd59b2c0e8acbb99647d579f74f91bde66e4a243dc212a3c8e8739c9957";
    private String expectedKey = "";
    @Nullable
    private MemcachedClient mockMemcachedClient = null;
    private boolean isBinaryConnection = false;
    @Nullable
    private String cacheResult = null;

    @BeforeEach
    public void setup() {
        mockMemcachedClient = createMockMemcachedClient();

    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        isBinaryConnection = false;
        validateMockitoUsage();
    }

    @Test
    void testKffMemcachedCreation() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.TRUE, TEST_ID_WITH_SPACES);
        mcdFilter.setPreferredAlgorithm("SHA-256");
        assertEquals("SHA-256", mcdFilter.getPreferredAlgorithm());
        assertEquals("KFF", mcdFilter.getName());
        assertEquals(FilterType.DUPLICATE, mcdFilter.getFilterType());
    }

    @Test
    void testThrowsWithNonAsciiAndDups() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.TRUE, TEST_ID_WITH_SPACES);
        ChecksumResults results = createSums(mcdFilter);
        assertThrows(IllegalArgumentException.class, () -> {
            mcdFilter.check(TEST_ID_WITH_SPACES, results);
        });
    }


    @Test
    void testNoHitNoStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.FALSE, Boolean.FALSE, TEST_UNFORMATTED_ID_HASH);
        assertFalse(mcdFilter.check(TEST_ID_WITH_SPACES, createSums(mcdFilter)), "Filter should not hit");
    }

    @Test
    void testHitNoStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.FALSE, Boolean.TRUE, null);
        assertTrue(mcdFilter.check(TEST_ID_WITH_SPACES, createSums(mcdFilter)), "Filter should hit");
    }

    @Test
    void testNoHitWithStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.FALSE, TEST_UNFORMATTED_ID_HASH);
        assertFalse(mcdFilter.check(TEST_ID_WITH_SPACES, createSums(mcdFilter)), "Filter should not hit");
    }

    @Test
    void testHitWithStoreIdDupe() throws Exception {
        isBinaryConnection = true;
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.TRUE, TEST_ID_WITH_SPACES);
        assertTrue(mcdFilter.check(TEST_ID_WITH_SPACES, createSums(mcdFilter)), "Filter should hit");
    }

    private static ChecksumResults createSums(KffMemcached mcd) throws NoSuchAlgorithmException {
        List<String> kffalgs = new ArrayList<>();
        kffalgs.add(mcd.getPreferredAlgorithm());
        return new ChecksumCalculator(kffalgs).digest(TEST_PAYLOAD.getBytes());
    }


    private KffMemcached createTestFilter(Boolean storeIdDupe, boolean simulateHit, @Nullable String expectedKey)
            throws IOException, NoSuchFieldException,
            IllegalAccessException {
        KffMemcached filter = new KffMemcached(TEST_ID_WITH_SPACES, "KFF", FilterType.DUPLICATE, mockMemcachedClient);
        setPrivateMembersForTesting(filter, storeIdDupe);
        if (simulateHit) {
            cacheResult = "FAKE FIND";
        } else {
            cacheResult = null;
        }
        this.expectedKey = expectedKey;
        return filter;
    }

    private static void checkForValidAscii(String key) {
        // Arbitrary string up to 250 bytes in length. No space or newlines for
        // ASCII mode
        if (key.length() > 250 || key.contains(" ") || key.contains("\n")) {
            throw new IllegalArgumentException("Invalid Key for ASCII Memcached");
        }
    }

    private static void setPrivateMembersForTesting(KffMemcached cacheFilter, @Nullable Boolean storeIdDupe)
            throws NoSuchFieldException, IllegalAccessException {

        // Overriding the protected attribute of the field for testing
        if (storeIdDupe != null) {
            Field storeIdDupeField = KffMemcached.class.getDeclaredField("storeIdDupe");
            storeIdDupeField.setAccessible(true);
            storeIdDupeField.set(cacheFilter, storeIdDupe);
        }

    }

    private MemcachedClient createMockMemcachedClient() {

        MemcachedClient localMockMemcachedClient = mock(MemcachedClient.class);

        when(localMockMemcachedClient.asyncGet(ArgumentMatchers.anyString())).thenAnswer((Answer<TestGetFuture<Object>>) invocation -> {
            Object[] args = invocation.getArguments();
            return new TestGetFuture<>(new CountDownLatch(1), 500, (String) args[0]);
        });

        when(localMockMemcachedClient.set(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(), ArgumentMatchers.any()))
                .thenAnswer((Answer<Future<Boolean>>) invocation -> {
                    Object[] args = invocation.getArguments();
                    String key = (String) args[0];

                    if (!key.equals(expectedKey)) {
                        throw new Exception("Key :" + key + " not equal to expected key: " + expectedKey);
                    }

                    if (!isBinaryConnection) {
                        checkForValidAscii(key);
                    }

                    return new OperationFuture<>(key, new CountDownLatch(1), 500, Executors.newFixedThreadPool(1));
                });

        return localMockMemcachedClient;
    }

    private class TestGetFuture<T> extends GetFuture<T> {

        public TestGetFuture(CountDownLatch l, long opTimeout, String key) {
            super(l, opTimeout, key, Executors.newFixedThreadPool(1));
        }

        @Override
        public boolean cancel(boolean ign) {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get(long duration, TimeUnit units) throws InterruptedException, TimeoutException, ExecutionException {
            return (T) cacheResult;
        }

        @Override
        public OperationStatus getStatus() {
            return new OperationStatus(true, "Done");
        }

        @Override
        public void set(Future<T> d, OperationStatus s) {
            throw new NotImplementedException("don't call set");
        }

        @Override
        public void setOperation(Operation to) {
            throw new NotImplementedException("don't call setOperation");
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

    }

}
