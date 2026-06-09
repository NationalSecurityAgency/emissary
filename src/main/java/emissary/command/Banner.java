package emissary.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Banner {

    static final String DEFAULT_TEXT = """
             ________  ________ _____ _____  ___  ________   __
            |  ___|  \\/  |_   _/  ___/  ___|/ _ \\ | ___ \\ \\ / /
            | |__ | .  . | | | \\ `--.\\ `--./ /_\\ \\| |_/ /\\ V /\s
            |  __|| |\\/| | | |  `--. \\`--. \\  _  ||    /  \\ / \s
            | |___| |  | |_| |_/\\__/ /\\__/ / | | || |\\ \\  | | \s
            \\____/\\_|  |_/\\___/\\____/\\____/\\_| |_/\\_| \\_| \\_/ \s
            """;

    private final String bannerText;

    public Banner() {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("banner.txt");
        if (in == null) {
            bannerText = DEFAULT_TEXT;
        } else {
            String bText = DEFAULT_TEXT;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(System.getProperty("line.separator")).append(line);
                }
                bText = System.getProperty("line.separator") + result.toString();
            } catch (IOException ignored) {
                // will be DEFAULT_TEXT;
            }
            bannerText = bText;
        }
    }

    @SuppressWarnings("SystemOut")
    public void dump() {
        System.out.println(bannerText);
    }
}
