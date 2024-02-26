package app.jtutor.jaig;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeExecutor {
    public static void executeJavaChatResponse(String response){
        String className = "Main";

        try {
            // filter Java code from anything else
            String[] lines = response.split("\n");
            boolean ignoreLines = true;
            boolean foundClass = false;
            StringBuilder code = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("import ")) {
                    code.append(line + "\n");
                }
                if (line.startsWith("public class")) {
                    ignoreLines = false;
                    foundClass = true;
                    Pattern pattern = Pattern.compile("public class (\\w+)");
                    Matcher matcher = pattern.matcher(line);

                    if (matcher.find()) {
                        className = matcher.group(1);  // Group 1 is the first group captured by ()
                    } else {
                        System.out.println("No class name found on this line:\n" + line);
                        System.exit(0);
                    }
                }
                if (!ignoreLines) {
                    code.append(line + "\n");
                }
                if (line.startsWith("}")) ignoreLines = true;
            }

            // for the case if we have only code with no class
            if (!foundClass) {
                System.out.println("Class not found! Trying to run the code, but probably you will need some imports...");
                code.append("public class Main {");
                for (String line : lines) {
                    if (line.endsWith(";") || line.contains("; //")) {
                        code.append(line);
                    }
                }
                code.append("}");
            }

            // Save source in .java file.
            File root = new File("./generated");
            File sourceFile = new File(root, className + ".java");
            sourceFile.getParentFile().mkdirs();
            if (sourceFile.exists()) {
                sourceFile.delete();
            }
            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(code.toString());
            }

            // Compile source file.
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            compiler.run(null, null, null, sourceFile.getPath());

            // Load and instantiate compiled class.
            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{root.toURI().toURL()});
            Class<?> cls = Class.forName(className, true, classLoader); // Should print "hello".
            Method main = cls.getDeclaredMethod("main", String[].class);

            // Convert params into list and invoke main
            String[] params = new String[0]; // Init params accordingly
            List<String> list = new ArrayList<>(Arrays.asList(params));
            main.invoke(null, (Object) list.toArray(new String[0]));
        } catch (Exception e) {
            System.out.println("Attempt to run the generated code was unsuccessful");
            e.printStackTrace();
        }
    }
}