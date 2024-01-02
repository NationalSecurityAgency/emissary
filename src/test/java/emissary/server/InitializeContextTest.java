package emissary.server;

import emissary.core.EmissaryException;
import emissary.directory.EmissaryNode;
import emissary.test.core.junit5.UnitTest;

import jakarta.servlet.ServletContextEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InitializeContextTest extends UnitTest {

    InitializeContext context;
    EmissaryNode node;
    ServletContextEvent event;

    @BeforeEach
    @Override
    public void setUp() {
        node = mock(EmissaryNode.class);
        event = mock(ServletContextEvent.class);
        context = new InitializeContext(node);
    }

    @Test
    void test() throws EmissaryException {
        context.contextInitialized(event);
        verify(node, times(1)).configureEmissaryServer();
    }

}
