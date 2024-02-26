package app.jtutor.jaig.command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Creates a new folder for the prompt and shifts all next folders down.
 *
 * CreateNewPromptFolder receives an existing folder name.
 * If the folder name has format NN_something, for example 05_service,
 * it should create a new folder with name NN+1_something, for example 06_something.
 *
 * It reads the name of the new folder from the console (the part of the folder name after _).
 * Additionally, it creates a new file with the entered name (without a number and _) and .txt extention.
 *
 * Also, it renames all next folders in the same parent folder;
 * for example, if we have folder 01_test, 02_test, 03_test, 04_test,
 * and we added folder 03_new, 03_test is renamed to 04_test, and 04_test is renamed to 05_test
 *
 * */
public class CreateNewPromptFolder {

    public static CreateNewPromptFolder INSTANCE = new CreateNewPromptFolder();

    public void execute(String folderFullName) {
        Path folderPath = Paths.get(folderFullName);
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
                System.out.println("An error occurred while creating the folder or file "+e.getMessage());
                return;
            }
        } else {
            System.out.println("The provided folder name does not match the expected format (NN_something).");
        }
    }
}
