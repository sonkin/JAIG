package app.jtutor.jaig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertFolderProcessor {
    public static InsertFolderProcessor INSTANCE = new InsertFolderProcessor();

    public void insertFolder(String folder) {
        Path folderPath = Paths.get(folder);
        String folderName = folderPath.getFileName().toString();
        Matcher matcher = Pattern.compile("(\\d{2})_(.*)").matcher(folderName);

        if (matcher.matches()) {
            int number = Integer.parseInt(matcher.group(1));
            number++;

            // Reading the new folder name from the console
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the new folder name (part after _): ");
            String newName = scanner.nextLine();
            scanner.close();

            String newFolderName = String.format("%02d_%s", number, newName);

            Path parentPath = folderPath.getParent();
            Path newFolderPath = parentPath == null ? Paths.get(newFolderName) : parentPath.resolve(newFolderName);

            try {
                Files.createDirectories(newFolderPath);
                System.out.println("New folder created: " + newFolderPath);

                // Creating a new file in the new folder
                File newFile = new File(newFolderPath.toFile(), newName + ".txt");
                if (newFile.createNewFile()) {
                    //Write a template content to the new file
                    String content = """
                        /path(s)/to/your/inputFiles
                        
                        Please provide a request for your specific needs within your project.
                        
                        #directives (src, package, test, merge etc.)
                        """;

                    BufferedWriter writer = new BufferedWriter(new FileWriter(newFile));
                    writer.write(content);

                    System.out.println("New file created: " + newFile);
                } else {
                    System.out.println("File already exists.");
                    return;
                }
                // Renaming the next folders
                if (parentPath != null) {
                    File parentFolder = parentPath.toFile();
                    File[] listOfFiles = parentFolder.listFiles();
                    if (listOfFiles != null) {
                        for (File file : listOfFiles) {
                            if (file.toPath().equals(newFolderPath)) { // Check if the folder is the newly created folder
                                continue; // Skip the renaming process for this folder
                            }
                            if (file.isDirectory()) {
                                String nextFolderName = file.getName();
                                Matcher nextMatcher = Pattern.compile("(\\d{2})_(.*)").matcher(nextFolderName);
                                if (nextMatcher.matches()) {
                                    int nextNumber = Integer.parseInt(nextMatcher.group(1));
                                    if (nextNumber >= number) {
                                        String newNameAfter = String.format("%02d_%s", nextNumber + 1, nextMatcher.group(2));
                                        File newDir = new File(parentFolder, newNameAfter);
                                        file.renameTo(newDir);
                                        System.out.println("Folder renamed: " + file + " to " + newDir);
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("An error occurred while creating the folder or file.");
                e.printStackTrace();
            }
        } else {
            System.out.println("The provided folder name does not match the expected format (NN_something).");
        }
    }
}
