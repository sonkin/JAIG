package app.jtutor.jaig;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateProcessor {

    static class KeyNotFoundException extends Exception {
        public KeyNotFoundException(String message) {
            super(message);
        }
    }

    private static String CONFIG_FILE;
    private static String templateFolder;
    private static Map<String, Object> config = new HashMap<>();

    public static void process(String configFile) {
        CONFIG_FILE = configFile;
        System.out.println("Processing templates for "+CONFIG_FILE);
        loadYamlConfig();
        templateFolder = (String) config.get("template");
        System.out.println("Template folder: "+templateFolder);
        if (templateFolder != null) {
            initVelocity(templateFolder);
            try {
                Path currentDir = Paths.get(".").toAbsolutePath().normalize();
                Path source = currentDir.resolve(templateFolder);
                Path destination = Paths.get(configFile).getParent();
                if (destination == null) destination = currentDir; // Default to current directory if there's no parent
                copyAndReplaceFolder(source, destination);
                System.out.println("All prompts are generated and saved in "+destination);
            } catch (IOException e) {
                System.err.println("ERROR when processing templates: "+e.getMessage());
            }
        } else {
            System.err.println("ERROR: Template folder is not defined in YAML config file: "+CONFIG_FILE);
        }
    }

    private static void initVelocity(String templateFolder) {
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "file");
        properties.setProperty("resource.loader.file.class",
                "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        properties.setProperty("resource.loader.file.path",
                templateFolder.toString());
        Velocity.init(properties);
    }

    private static void loadYamlConfig() {
        Yaml yaml = new Yaml();
        try (InputStream input = new FileInputStream(CONFIG_FILE);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Map<String, Object> yamlData = yaml.load(reader);
            for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
                config.put(entry.getKey(), entry.getValue());
            }

            // Generate variants for entity
            if (config.containsKey("entity")) {
                String entityValue = (String) config.get("entity");

                // Generate entities: courses (only if it's not provided in YAML)
                if (!config.containsKey("entities")) {
                    config.put("entities", generatePlural(entityValue));
                }

                // Entity: Course
                config.putIfAbsent("Entity", capitalize(entityValue));

                // Entities: Courses (only if it's not provided in YAML)
                if (!config.containsKey("Entities")) {
                    config.put("Entities", capitalize(generatePlural(entityValue)));
                }
            }

        } catch (IOException ex) {
            System.err.println("ERROR when loading YAML config: "+ex.getMessage());
        }
    }

    // Helper function to capitalize the first character of a string
    private static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    // Simple function to generate plural form
    private static String generatePlural(String word) {
        if (word.endsWith("s") || word.endsWith("z") || word.endsWith("x")
                || word.endsWith("sh") || word.endsWith("ch")) {
            return word + "es";
        } else if (word.endsWith("y") && !isVowel(word.charAt(word.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        } else {
            return word + "s";
        }
    }

    // Helper function to check if a character is a vowel
    private static boolean isVowel(char c) {
        return "AEIOUaeiou".indexOf(c) != -1;
    }

    private static void copyAndReplaceFolder(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = destination.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else if (src.toString().endsWith(".vm")) {
                    // Skip .vm files
                } else if (src.toString().endsWith(".txt") || src.toString().endsWith(".patch")) {
                    // Process all .txt and .patch files
                    System.out.println("Processing template: "+src.getFileName()+
                            " -> "+destination+"/"+dest.getFileName());
                    String content = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);

                    try {
                        content = replacePlaceholders(content, src.getParent(), src.getFileName().toString());

                        if (content.length()>0) {
                            Files.write(dest, content.getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (KeyNotFoundException keyNotFoundException) {
                        System.out.println("Skipping this template: "+src.getFileName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static String replacePlaceholders(String content, Path currentDir, String fileName) throws KeyNotFoundException {
        Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String key = matcher.group(1);
            if (key.endsWith(".vm")) {
                // processing Velocity template
                Path vmFilePath = currentDir.resolve(key);

                if (Files.exists(vmFilePath)) {
                    VelocityContext context = new VelocityContext();
                    for (Map.Entry<String, Object> entry : config.entrySet()) {
                        context.put(entry.getKey(), entry.getValue());
                    }

                    StringWriter writer = new StringWriter();

                    try {
                        // To this (get only the relative path)
                        Path templateFolderPath = Paths.get(templateFolder);
                        String relativePath = templateFolderPath.toAbsolutePath().relativize(vmFilePath).toString();
                        Template template = Velocity.getTemplate(relativePath);
                        template.merge(context, writer);

                        config.put(key, writer.toString());
                    } catch (ResourceNotFoundException | ParseErrorException | MethodInvocationException e) {
                        // Handle exceptions as needed
                        System.err.println("ERROR when processing Velocity template: " + e.getMessage());
                    }
                } else {
                    System.err.println("ERROR: Velocity Template file not found: " + vmFilePath);
                }
            } else {
                if (!config.containsKey(key)) {
                    if (!key.contains("|")) {
                        throw new KeyNotFoundException("Key is not defined in YAML: [[" + key + "]]");
                    }
                    String[] optionalKeyAndDefault = key.split("\\|", -1); // -1 to keep empty strings
                    String optionalKey = optionalKeyAndDefault[0];
                    String defaultValue = optionalKeyAndDefault[1];
                    Object valueForOptionalKey = config.getOrDefault(optionalKey, defaultValue);
                    config.put(optionalKey + "\\|" + defaultValue, valueForOptionalKey);
                    if (!config.containsKey(optionalKey)) {
                        System.out.println("WARNING: Key is not defined in YAML: [[" + optionalKey + "]] but used in template " + fileName);
                        System.out.println("    => using default value: " + valueForOptionalKey);
                    }
                }
            }
        }

        for (String key : config.keySet()) {
            content = content.replaceAll("\\[\\[" + key + "\\]\\]", config.get(key).toString());
        }

        return content;
    }
}
