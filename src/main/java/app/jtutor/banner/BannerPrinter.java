package app.jtutor.banner;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class BannerPrinter {

    public static String banner = """
                      *        *         *
                   ***       **       ***
                 *****     ****    *******
               *******   ******   **********
             ********** ********* **************
           ************* ************* *************
          ***███████*****█████******███**██████████***
          *******███****███*███*****███**███***********
          *******███***███***███****███**███***████****
          **██***███**███████████***███**███*****██****
           *████████*███*******███**███**██████████***
            ************* *********** **************
               *********** ********* ************
                  *******   ******   ********
                    ****    ***     ***
                   **      *      **
                *       *      *
            """;
    public static final String ANSI_DARK_RED = "\u001B[31m";
    public static final String ANSI_RED = "\u001B[91m";
    public static final String ANSI_RESET = "\u001B[0m";

    public static void printBanner() {
        // split to lines and add 10 spaces in the beginning of each line
        banner = banner.replaceAll("(?m)^", "          ");
        // print banner symbol by symbol

        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        
        for (String line : banner.split("\n")) {
            for (char c : line.toCharArray()) {
                if (c=='█') out.print(ANSI_DARK_RED +c+ANSI_RESET);
                else if (c=='*') out.print(ANSI_RED +c+ANSI_RESET);
                else out.print(c);
            }
            out.println();
        }

        out.println();
    }

    public static void main(String[] args) {
        printBanner();
    }

    private static String readBannerFile()  {
        try {
            URI uri = BannerPrinter.class.getClassLoader().getResource("banner.txt").toURI();
            List<String> lines = Files.readAllLines(Paths.get(uri));
            return String.join(System.lineSeparator(), lines);
        } catch (Exception e) {
            System.out.println("Unable to read banner.txt file");
        }
        return "";
    }

}
