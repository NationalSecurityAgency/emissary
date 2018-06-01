package emissary.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Banner {

    static String DEFAULT_TEXT = " ________  ________ _____ _____  ___  ________   __\n"
            + "|  ___|  \\/  |_   _/  ___/  ___|/ _ \\ | ___ \\ \\ / /\n" + "| |__ | .  . | | | \\ `--.\\ `--./ /_\\ \\| |_/ /\\ V / \n"
            + "|  __|| |\\/| | | |  `--. \\`--. \\  _  ||    /  \\ /  \n" + "| |___| |  | |_| |_/\\__/ /\\__/ / | | || |\\ \\  | |  \n"
            + "\\____/\\_|  |_/\\___/\\____/\\____/\\_| |_/\\_| \\_| \\_/  \n";

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
            } catch (IOException e) {
                // will be DEFAULT_TEXT;
            }
            bannerText = bText;
        }
    }

    public void dump() {
        System.out.println(bannerText);
    }
}
