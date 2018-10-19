package emissary.kff;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

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

import emissary.kff.KffFilter.FilterType;
import emissary.test.core.UnitTest;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationStatus;
import org.apache.commons.lang.NotImplementedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class KffMemcachedTest extends UnitTest {

    private String testIdWithSpaces = "TEST ID";
    private String testPayload = "TEST DATA";
    private String testUnformattedIdHash = "01e44cd59b2c0e8acbb99647d579f74f91bde66e4a243dc212a3c8e8739c9957";
    private String expectedKey = "";
    private MemcachedClient mockMemcachedClient = null;
    private boolean isBinaryConnection = false;
    private String cacheResult = null;

    @Before
    public void setup() {
        mockMemcachedClient = createMockMemcachedClient();

    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        isBinaryConnection = false;
        validateMockitoUsage();
    }


    @Test(expected = IllegalArgumentException.class)
    public void testThrowsWithNonAsciiAndDups() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.TRUE, testIdWithSpaces);
        mcdFilter.check(testIdWithSpaces, createSums(mcdFilter));
    }


    @Test
    public void testNoHitNoStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.FALSE, Boolean.FALSE, testUnformattedIdHash);
        assertFalse("Filter should not hit", mcdFilter.check(testIdWithSpaces, createSums(mcdFilter)));
    }

    @Test
    public void testHitNoStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.FALSE, Boolean.TRUE, null);
        assertTrue("Filter should hit", mcdFilter.check(testIdWithSpaces, createSums(mcdFilter)));
    }

    @Test
    public void testNoHitWithStoreIdDupe() throws Exception {
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.FALSE, testUnformattedIdHash);
        assertFalse("Filter should not hit", mcdFilter.check(testIdWithSpaces, createSums(mcdFilter)));
    }

    @Test
    public void testHitWithStoreIdDupe() throws Exception {
        isBinaryConnection = true;
        KffMemcached mcdFilter = createTestFilter(Boolean.TRUE, Boolean.TRUE, testIdWithSpaces);
        assertTrue("Filter should hit", mcdFilter.check(testIdWithSpaces, createSums(mcdFilter)));
    }

    private ChecksumResults createSums(KffMemcached mcd) throws NoSuchAlgorithmException {
        List<String> kffalgs = new ArrayList<String>();
        kffalgs.add(mcd.getPreferredAlgorithm());
        return new ChecksumCalculator(kffalgs).digest(testPayload.getBytes());
    }


    private KffMemcached createTestFilter(Boolean storeIdDupe, Boolean simulateHit, String _expectedKey) throws IOException, NoSuchFieldException,
            IllegalAccessException {
        KffMemcached filter = new KffMemcached(testIdWithSpaces, "KFF", FilterType.Duplicate, mockMemcachedClient);
        setPrivateMembersForTesting(filter, storeIdDupe);
        if (simulateHit.booleanValue()) {
            cacheResult = "FAKE FIND";
        } else {
            cacheResult = null;
        }
        this.expectedKey = _expectedKey;
        return filter;
    }

    private void checkForValidAscii(String key) {
        // Arbitrary string up to 250 bytes in length. No space or newlines for
        // ASCII mode
        if (key.length() > 250 || key.contains(" ") || key.contains("\n")) {
            throw new IllegalArgumentException("Invalid Key for ASCII Memcached");
        }
    }

    private void setPrivateMembersForTesting(KffMemcached cacheFilter, Boolean storeIdDupe) throws NoSuchFieldException, IllegalAccessException {

        // Overriding the protected attribute of the field for testing
        if (storeIdDupe != null) {
            Field storeIdDupeField = KffMemcached.class.getDeclaredField("storeIdDupe");
            storeIdDupeField.setAccessible(true);
            storeIdDupeField.set(cacheFilter, storeIdDupe);
        }

    }

    private MemcachedClient createMockMemcachedClient() {

        MemcachedClient localMockMemcachedClient = mock(MemcachedClient.class);

        when(localMockMemcachedClient.asyncGet(Matchers.anyString())).thenAnswer(new Answer<TestGetFuture<Object>>() {
            @Override
            public TestGetFuture<Object> answer(InvocationOnMock invocation) throws Throwable {

                Object[] args = invocation.getArguments();
                return new TestGetFuture<Object>(new CountDownLatch(1), 500, (String) args[0]);
            }
        });

        when(localMockMemcachedClient.set(Matchers.anyString(), Matchers.anyInt(), Matchers.anyObject())).thenAnswer(new Answer<Future<Boolean>>() {
            @Override
            public Future<Boolean> answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                String key = (String) args[0];

                if (!key.equals(expectedKey)) {
                    throw new Exception("Key :" + key + " not equal to expected key: " + expectedKey);
                }

                if (!isBinaryConnection) {
                    checkForValidAscii(key);
                }

                return new OperationFuture<Boolean>(key, new CountDownLatch(1), 500, Executors.newFixedThreadPool(1));
            }
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
