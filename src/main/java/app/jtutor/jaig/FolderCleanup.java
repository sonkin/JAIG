package app.jtutor.jaig;


import java.io.File;

/**
 * Cleanup the folder
 */
public class FolderCleanup {

    public static void cleanFolder(String folderPath) {
        System.out.println("Cleaning folder: " + folderPath);

        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Invalid folder path");
            return;
        }

        cleaningFolder(folder);
    }

    private static void cleaningFolder(File folder) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleaningFolder(file);
                }
                String fileName = file.getName();
                if (fileName.endsWith("-request.txt") || fileName.endsWith("-response.txt") ||
                        fileName.endsWith("-backup") || fileName.endsWith("-parsed") ||
                        fileName.endsWith("-patched") || fileName.endsWith("-merged") ||
                        fileName.endsWith("-parsed-old") || fileName.endsWith(".rollback")) {
                    System.out.println("Deleting: " + file.getAbsolutePath());
                    // delete folder and its contents
                    if (file.isDirectory()) deleteFolderContents(file);
                    file.delete();
                }
            }
        }
    }

    private static void deleteFolderContents(File file) {
        File[] files = file.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolderContents(f);
                }
                f.delete();
            }
        }
    }
}
