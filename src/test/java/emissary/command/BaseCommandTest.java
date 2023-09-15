package emissary.command;

import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseCommandTest extends UnitTest {

    @Test
    void testFlavor() throws Exception {
        String flavorString = "NORMAL,cluster";
        List<String> args = new ArrayList<>();
        args.add("-b");
        args.add(System.getenv(ConfigUtil.PROJECT_BASE_ENV));
        args.add("--flavor");
        args.add(flavorString);
        TestBaseCommand cmd = TestBaseCommand.parse(TestBaseCommand.class, args);
        assertEquals(cmd.getFlavor(), flavorString);
    }
}


class TestBaseCommand extends BaseCommand {

    public TestBaseCommand() {}

    @Override
    public String getCommandName() {
        return "GOAWAY";
    }

    @Override
    public void run(CommandLine c) {
        setup();
    }
}
