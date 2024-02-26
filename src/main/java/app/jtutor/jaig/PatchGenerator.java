package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PatchGenerator {

    public static PatchGenerator INSTANCE = new PatchGenerator();
    public void createPatches(String originalFolder, String revisedFolder, String patchName) {
        try {
            System.out.println("\n********** JAIG Patch Creator **********");
            System.out.println("We found a patch "+patchName+" for you prompt. \n"+
                    "Therefore, we will check if you have any changes in java files "+
                    "and will re-apply these changes automatically.\n" +
                    "We will create/update a patch for " + originalFolder+"\n"+
                    "and apply it after JAIG code generation \n"+
                    "to preserve changes in the source code.\n"+
                    "To prevent it, remove "+patchName+" and run JAIG again.\n\n");

            List<String> changesDiff = generateFoldersDiff(new File(originalFolder), new File(revisedFolder));
            // Save the diff to a file
            File patchFile = new File(originalFolder+".patch");
            createOrDeletePatch(changesDiff, patchFile);
        } catch (IOException e) {
            System.out.println("ERROR when creating a patch: "+e.getMessage());
        }

    }

    public void createSaveToFilePatch(String originalFolder, String saveResponseTo, String patchName) {
        List<String> diff = new ArrayList<>();
        try {
            generateFilesDiff(
                    (File originalFile, List<String> originalLines, List<String> revisedLines) -> true,
                    new File(originalFolder + "/" + saveResponseTo), // original file
                    new File(saveResponseTo), // revised file
                    diff);
            // save the diff to a file
            File patchFile = new File(patchName);
            createOrDeletePatch(diff, patchFile);
        } catch (IOException e) {
            System.err.println("ERROR: cannot create a patch for "+ saveResponseTo);
            e.printStackTrace();
        }
    }

    private void createOrDeletePatch(List<String> diff, File patchFile) throws IOException {
        if (diff.isEmpty()) {
            if (patchFile.exists()) {
                System.out.println("No changes found, patch will not be updated.");
            } else {
                System.out.println("No changes found, patch was not created.");
            }
        } else {
            FileUtils.writeLines(patchFile, StandardCharsets.UTF_8.toString(), diff);
            System.out.println("Patch is written to " + patchFile.getPath());
        }
    }

    private DiffRowGenerator generator = DiffRowGenerator.create()
            .showInlineDiffs(false)
            .reportLinesUnchanged(true)
            .columnWidth(Integer.MAX_VALUE)
            .lineNormalizer(s->s)
            .build();

    public List<String> generateFoldersDiff(File originalFolder, File revisedFolder) throws IOException {
        List<String> unifiedDiff = new ArrayList<>();

        if (originalFolder.isDirectory() && revisedFolder.isDirectory()) {
            Collection<File> originalFiles = FileUtils.listFiles(originalFolder, new String[]{"java"}, true);
            Collection<File> revisedFiles = FileUtils.listFiles(revisedFolder, new String[]{"java"}, true);

            for (File originalFile : originalFiles) {
                if (!originalFile.isFile()) continue;
                String relativePath = originalFolder.toPath().relativize(originalFile.toPath()).toString();
                File revisedFile = new File(revisedFolder, relativePath);

                if (revisedFile.exists()) {
                    if (originalFile.isDirectory() && revisedFile.isDirectory()) {
                        unifiedDiff.addAll(generateFoldersDiff(originalFile, revisedFile));
                    } else if (originalFile.isFile() && revisedFile.isFile()) {
                        generateFilesDiff(JAIGJavaHeader.INSTANCE, originalFile, revisedFile, unifiedDiff);
                    }
                } else {
                    unifiedDiff.add("File not found in the revisedFolder folder: " + revisedFile.getPath());
                }
            }

        } else {
            throw new IllegalArgumentException("originalFolder and revisedFolder must be directories.");
        }

        return unifiedDiff;
    }

    private void generateFilesDiff(JAIGHeaderValidator jaigHeaderValidator,
                                   File originalFile, File revisedFile,
                                   List<String> unifiedDiff) throws IOException {
        List<String> originalLines = FileUtils.readLines(originalFile, StandardCharsets.UTF_8);
        List<String> revisedLines = FileUtils.readLines(revisedFile, StandardCharsets.UTF_8);

        // analyze if source file has JAIG header and if it was generated by the same JAIG prompt
        if (!jaigHeaderValidator.validate(originalFile, originalLines, revisedLines)) {
            System.out.println("Skipping creating patch for file "+originalFile+"\n"+
                    "       It was generated by a different JAIG prompt or no header was found.");
            return;
        }

        System.out.println("Creating patch for file "+originalFile);

        List<DiffRow> diffRows = generator.generateDiffRows(originalLines, revisedLines);
        long countChanges = diffRows.stream().filter(row -> row.getTag() != DiffRow.Tag.EQUAL).count();
        if (countChanges>0) {
            // if revised file is inside forlder src/main/java
            File srcMainJavaFolder = new File(GlobalConfig.INSTANCE.getSrcFolder());
            if (revisedFile.toPath().startsWith(srcMainJavaFolder.toPath())) {
                unifiedDiff.add("@" + revisedFile.getName());
            } else {
                unifiedDiff.add("@" + "/"+revisedFile.getPath());
            }
        }
        String previousAfter = null;
        int diffRowIndex = -1;
        List<String> newLines = new ArrayList<>();

        for (DiffRow row : diffRows) {
            diffRowIndex++;
            // Ignore created by JAIG file header
            if (row.getOldLine().contains("***") ||
                    row.getNewLine().contains("***")) continue;
            boolean isInsert = row.getTag() == DiffRow.Tag.INSERT ||
                    row.getTag() == DiffRow.Tag.CHANGE && row.getOldLine().isEmpty();
            boolean isDelete = row.getTag() == DiffRow.Tag.DELETE ||
                    row.getTag() == DiffRow.Tag.CHANGE && row.getNewLine().isEmpty();
            boolean isChange = row.getTag() == DiffRow.Tag.CHANGE;

            // append the block of "  new> ..." if we switch to deletion or insertion or unchanged
            if (!newLines.isEmpty() && !isChange) {
                unifiedDiff.addAll(newLines);
                newLines.clear();
                previousAfter = null;
            }

            if (isInsert) {
                previousAfter = addAfterLine(diffRowIndex, diffRows, previousAfter, unifiedDiff, true);
                unifiedDiff.add("   inserted> " + row.getNewLine());
            } else if (isDelete) {
                final String lineToDelete = row.getOldLine();
                // if deleted row is trivial, we need to show line after which it was deleted
                if (//lineToDelete.trim().length() > 3 &&
                    // check previousRow for uniqueness
                        diffRows.stream().filter(r ->
                                lineToDelete.trim().equals(r.getOldLine().trim())).count() == 1) {
                    // normal delete - no need in <after> line
                } else {
                    previousAfter = addAfterLine(diffRowIndex, diffRows, previousAfter, unifiedDiff, false);
                }
                unifiedDiff.add("    deleted> " + row.getOldLine());
            } else if (row.getTag() == DiffRow.Tag.CHANGE) {
                previousAfter = null; // we shouldn't look for previous <after> if we have replacement
                // if the first old line in not unique
                if (newLines.isEmpty() && diffRows.stream().filter(r->
                        r.getOldLine().trim().equals(
                                row.getOldLine().trim())).count()>1) {
                    // we should show the line after which the change was made
                    previousAfter = addAfterLine(diffRowIndex, diffRows, previousAfter, unifiedDiff, false);
                } else {
                    previousAfter = null;
                }
                unifiedDiff.add("        old> "+row.getOldLine());
                newLines.add("        new> "+row.getNewLine());
            }
        }
        // append the block of all "  new> ..." in the end
        if (!newLines.isEmpty()) {
            unifiedDiff.addAll(newLines);
            newLines.clear();
        }
    }

    /**
     *
     */ // find the previous row which is unique and not trivial
    private static String addAfterLine(int diffRowIndex, List<DiffRow> diffRows,
                                       String previousAfter, List<String> unifiedDiff,
                                       boolean includeShift
                                       // we do not know the shift for replacements and deletions,
                                       // only for insertions;
                                       //  for replacements and deletions we just find the next line
                                       // to be deleted or replaced
    ) {
        int shift = 1;
        int shiftWithoutInserts = 1;
        DiffRow previousRow = null;
        // we are looking for previous row which is unique and not trivial
        while (diffRowIndex -shift >= 0) {
            previousRow = diffRows.get(diffRowIndex -shift);
            DiffRow finalPreviousRow = previousRow;

            // check that previousRow is not trivial like closing bracket }
            if (previousRow.getOldLine().trim().length()>5 &&
                    // check previousRow for uniqueness
                    diffRows.stream().filter(r->
                            r.getOldLine().trim().equals(
                                    finalPreviousRow.getOldLine().trim())).count()==1) break;

            shift++;
            // we should skip INSERT changes when calculating shift
            // because they are not reflected in the source file
            if (previousRow.getTag() != DiffRow.Tag.INSERT) {
                shiftWithoutInserts++;
            }
        }
        if (previousRow != null && !previousRow.getOldLine().equals(previousAfter)) {
            if (includeShift && shiftWithoutInserts>1) {
                unifiedDiff.add("    after+"+shiftWithoutInserts+"> " + previousRow.getOldLine());
            } else {
                unifiedDiff.add("      after> " + previousRow.getOldLine());
            }
            previousAfter = previousRow.getOldLine();
        }
        return previousAfter;
    }

}

