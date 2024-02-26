package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code Parser is splitting GPT response with the code into .java files
 * and organizing files into folders based on the package name.
 * For its work, every Java class should start with package ...
 * Name of the package is mapped to the folder name then.
 */
public class CodeParser {

    public String parse(String inputFile, String parsedFolder) {
        List<String> lines;

        try {
            lines = Files.readAllLines(Paths.get(inputFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error while reading the input file: " + e.getMessage());
            return null;
        }

        List<String> section = new ArrayList<>();
        String packageName = null;
        String className = null;
        boolean inClass = false;

        for (String line : lines) {
            if (line.startsWith("package ")) {
                // Write the previous section to a file
                if (packageName != null && className != null) {
                    writeFile(packageName, className, section, parsedFolder, inputFile);
                    section.clear();
                    className = null;
                }

                // Start a new section
                packageName = line.substring("package ".length(), line.length() - 1).replace('.', '/');
                inClass = true;  // Reset the inClass flag
            }
            else if (line.startsWith("}")) { // End of a class - we assume the correct indentation is used
                section.add(line);
                inClass = false;  // We are now outside a class
            } else if (className == null) { // trying to find the class name (or name for enum, interface, etc.)
                className = findClassName(line);
                if (className != null && className.length()>0) {
                    className += ".java";
                    inClass = true;
                }
            }

            // Only add the line to the section if we are inside a class
            if (inClass) {
                section.add(line);
            }
        }

        // Write the last section to a file
        if (packageName != null && className != null) {
            writeFile(packageName, className, section, parsedFolder, inputFile);
        }
        return parsedFolder;
    }

    // Method to find the class name (or interface name, enum name, etc.)
    // in the code line using the regex patterns in JAIG.yml (javaFileNameRegexp)
    private String findClassName(String line) {
        List<String> javaFileNameRegexps = GlobalConfig.INSTANCE.getJavaFileNameRegexps();
        for (String regex : javaFileNameRegexps) {
            Pattern pattern = Pattern.compile("^" + regex + ".*\\{$");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static void writeFile(String packageName, String className,
                                  List<String> section, String localCopyFolder,
                                  String inputFile) {
        String fullDirectoryPathLocalCopy = localCopyFolder + "/" + packageName;
        // adding the JAIG header with a path to the response from GPT
        section.add(0, JAIGJavaHeader.INSTANCE.generate(inputFile));

        try {
            System.out.println("Writing " + className + " to " + fullDirectoryPathLocalCopy);
            Files.createDirectories(Paths.get(fullDirectoryPathLocalCopy));
            Files.write(Paths.get(fullDirectoryPathLocalCopy, className), section, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error while writing to file: " + e.getMessage());
        }
    }

}
