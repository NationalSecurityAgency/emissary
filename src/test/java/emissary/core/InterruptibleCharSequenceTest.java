package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class InterruptibleCharSequenceTest extends UnitTest {
    public static String BACKTRACKER = "^(((((a+)*)*)*)*)*$";
    public static String INPUT = "aaaaab";

    // Uncomment to test overhead. This method does not perform any assertions.
    // @Test
    void testOverhead() {
        String regex = BACKTRACKER;
        String inputNormal = INPUT;
        InterruptibleCharSequence inputWrapped = new InterruptibleCharSequence(inputNormal);
        // warm up
        tryMatch(inputNormal, regex);
        tryMatch(inputWrapped, regex);
        int trials = 5;
        long stringTotalTime = 0;
        long interruptibleCharSequenceTotalTime = 0;
        for (int i = 1; i <= trials; i++) {
            logger.info("trial {} of {}", i, trials);
            long start = System.currentTimeMillis();
            tryMatch(inputNormal, regex);
            long end = System.currentTimeMillis();
            logger.info("String {}", end - start);
            stringTotalTime += end - start;
            start = System.currentTimeMillis();
            tryMatch(inputWrapped, regex);
            end = System.currentTimeMillis();
            logger.info("InterruptibleCharSequence {}", end - start);
            interruptibleCharSequenceTotalTime += end - start;
        }
        float overheadPercentage = (float) interruptibleCharSequenceTotalTime / stringTotalTime;
        logger.info("InterruptibleCharSequence overhead percentage: {}.", overheadPercentage);
    }

    public static boolean tryMatch(CharSequence input, String regex) {
        return Pattern.matches(regex, input);
    }

    public Thread tryMatchInThread(final CharSequence input, final String regex, final BlockingQueue<Object> atFinish) {
        Thread t = new Thread(() -> {
            boolean matcherResult;
            try {
                matcherResult = tryMatch(input, regex);
            } catch (InterruptedByTimeoutException interruptedByTimeoutException) {
                atFinish.offer(interruptedByTimeoutException);
                return;
            }
            atFinish.offer(matcherResult);
        });
        t.start();
        return t;
    }

    @Test
    void testNoninterruptibleString() throws InterruptedException {
        BlockingQueue<Object> blockingQueue = new LinkedBlockingQueue<>();
        Thread t = tryMatchInThread(INPUT, BACKTRACKER, blockingQueue);
        sleep(1000);
        t.interrupt();
        Object result = blockingQueue.take();
        assertEquals(Boolean.FALSE, result, "expected to not find a match");
    }

    @Test
    void testInterruptibleCharSequence() throws InterruptedException {
        long sleepMillis = 32;

        while (sleepMillis > 0) {
            BlockingQueue<Object> blockingQueue = new LinkedBlockingQueue<>();
            Thread t = tryMatchInThread(new InterruptibleCharSequence(INPUT), BACKTRACKER, blockingQueue);
            sleep(sleepMillis);
            if (Thread.State.TERMINATED == t.getState()) {
                sleepMillis /= 2;
                logger.info("Task completed, retrying with shorter sleep time: {} ms", sleepMillis);
                continue;
            }
            t.interrupt();
            Object result = blockingQueue.take();

            // Assert that InterruptedByTimeoutException was thrown
            if (result instanceof InterruptedByTimeoutException) {
                return;
            } else {
                fail("failed to interrupt InterruptibleCharSequence with given sleeping intervals");
            }

        }

    }

}
