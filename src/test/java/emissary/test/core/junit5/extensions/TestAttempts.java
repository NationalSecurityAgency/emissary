package emissary.test.core.junit5.extensions;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.Preconditions;
import org.opentest4j.TestAbortedException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Attempts a test multiple times until it passes or the max amount of attempts is reached (default is 3).
 *
 * This is an attempt to replace our old retry rule from junit 4.
 */
@Target({METHOD, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Execution(SAME_THREAD)
@ExtendWith(TestAttempts.AttemptTestExtension.class)
@TestTemplate
public @interface TestAttempts {

    /* Max attempts */
    int value() default 3;

    /**
     * JUnit extension to retry failed test attempts
     */
    class AttemptTestExtension implements TestTemplateInvocationContextProvider, TestExecutionExceptionHandler {

        protected static final ExtensionContext.Namespace EXTENSION_CONTEXT_NAMESPACE = ExtensionContext.Namespace.create(AttemptTestExtension.class);

        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            // check to see if the method is annotated with @TestAttempts
            return AnnotationUtils.isAnnotated(context.getRequiredTestMethod(), TestAttempts.class);
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            return StreamSupport.stream(splitTestTemplateInvocationContexts(context), false);
        }

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) {
            handleTestAttemptFailure(context.getParent().orElseThrow(() -> new UnsupportedOperationException("No template context found")),
                    throwable);
        }

        protected static Spliterator<TestTemplateInvocationContext> splitTestTemplateInvocationContexts(ExtensionContext context) {
            return Spliterators.spliteratorUnknownSize(getTestTemplateInvocationContextProvider(context), Spliterator.ORDERED);
        }

        protected static AcceptFirstPassingAttempt getTestTemplateInvocationContextProvider(ExtensionContext context) {
            ExtensionContext.Store store = context.getStore(EXTENSION_CONTEXT_NAMESPACE);
            String key = context.getRequiredTestMethod().toString();
            return store.getOrComputeIfAbsent(key, k -> createTestTemplateInvocationContextProvider(context), AcceptFirstPassingAttempt.class);
        }

        protected static AcceptFirstPassingAttempt createTestTemplateInvocationContextProvider(ExtensionContext context) {
            TestAttempts retryTest = AnnotationUtils.findAnnotation(context.getRequiredTestMethod(), TestAttempts.class)
                    .orElseThrow(() -> new UnsupportedOperationException("Missing @TestAttempts annotation."));
            int maxAttempts = retryTest.value();
            Preconditions.condition(maxAttempts > 0, "Total test attempts need to be greater than 0");
            return new AcceptFirstPassingAttempt(maxAttempts);
        }

        protected void handleTestAttemptFailure(ExtensionContext context, Throwable throwable) {
            AcceptFirstPassingAttempt testAttempt = getTestTemplateInvocationContextProvider(context);
            testAttempt.failed();

            if (testAttempt.hasNext()) {
                // trick junit into not failing the test by aborting the attempt
                throw new TestAbortedException(
                        String.format(Locale.getDefault(), "Test attempt %d of %d failed, retrying...", testAttempt.exceptions,
                                testAttempt.maxAttempts),
                        throwable);
            } else {
                // all attempts failed, so fail the test
                throw new AssertionError(
                        String.format(Locale.getDefault(), "Test attempt %d of %d failed", testAttempt.exceptions, testAttempt.maxAttempts),
                        throwable);
            }
        }

        /**
         * This class stops returning iterations after the first passed test attempt or the max number of attempts is reached.
         */
        private static class AcceptFirstPassingAttempt implements Iterator<TestTemplateInvocationContext> {

            protected final int maxAttempts;
            protected int attempts;
            protected int exceptions;

            private AcceptFirstPassingAttempt(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            /**
             * Iterator has next on the following conditions 1. this is the first attempt, or 2. no attempt has passed, and we have
             * not reached the max attempts
             *
             * @return true if there are more attempts, false otherwise
             */
            @Override
            public boolean hasNext() {
                return isFirstAttempt() || (hasNoPassingAttempts() && hasMoreAttempts());
            }

            /**
             * Get the next TestTemplateInvocationContext
             *
             * @return TestTemplateInvocationContext
             * @throws NoSuchElementException if there is not another item
             */
            @Override
            public TestTemplateInvocationContext next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                ++attempts;
                return new TestAttemptsInvocationContext(maxAttempts);
            }

            void failed() {
                ++exceptions;
            }

            boolean isFirstAttempt() {
                return attempts == 0;
            }

            boolean hasNoPassingAttempts() {
                return attempts == exceptions;
            }

            boolean hasMoreAttempts() {
                return attempts != maxAttempts;
            }
        }

        /**
         * Represents the context of a single invocation of a test template.
         */
        static class TestAttemptsInvocationContext implements TestTemplateInvocationContext {

            final int maxAttempts;

            public TestAttemptsInvocationContext(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            @Override
            public String getDisplayName(int invocationIndex) {
                return "Attempt " + invocationIndex + " of " + maxAttempts;
            }
        }
    }
}

