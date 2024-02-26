package app.jtutor.jaig;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatchApplier {
    public static PatchApplier INSTANCE = new PatchApplier();

    /**
     * Applies patch to all files mentioned in patch (like @file.java),
     * finds the file in sourceFolderPath
     * and saves the result to the destination folder
     *
     * @param sourceFolderPath - path to the folder where the files are located (usually prompt-parsed)
     * @param destinationFolderPath - path to the folder where the patched files should be saved (usually src/main/java)
     * @param patchFilePath - where the patch is located
     */
    public void patchFolder(String sourceFolderPath, String destinationFolderPath, String patchFilePath) {
        System.out.println("Patch folder "+sourceFolderPath+" with patch "+patchFilePath);
        System.out.println("Destination folder is "+destinationFolderPath);
        List<String> patchLines = null;
        try {
            patchLines = FileUtils.readLines(new File(patchFilePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("APPLY PATCH ERROR: Cannot read file "+patchFilePath);
        }
        List<String> patchFileLines = new ArrayList<>();
        String sourceFileName = null;
        String destinationFileName = null;
        String destinationSaveToFileName = null;
        for (String line : patchLines) {
            if (line.startsWith("@")) {
                // patch previous file lines
                if (sourceFileName != null && destinationFileName != null && patchFileLines.size() > 0) {
                    patchFile(sourceFileName, destinationFileName, patchFileLines);
                    patchFileLines.clear();
                }

                String fileName;
                if (line.startsWith("@/")) { // file outside src/main/java
                    destinationSaveToFileName = line.substring(2);;
                    fileName = new File(destinationSaveToFileName).getName();
                    System.out.println("*********** PATCHING "+fileName+" ***********");
                } else { // file inside src/main/java
                    fileName = line.substring(1);
                }
                // iterating the folder sourceFolderPath to find the file fileName
                try {
                    Optional<Path> first = Files.walk(Paths.get(sourceFolderPath))
                            .filter(f -> f.getFileName().toString().equals(fileName)).findFirst();
                    if (first.isPresent()) {
                        sourceFileName = first.get().toString();
                        // find the relative path of the file sourceFileName from the sourceFolderPath
                        String relativePath = Paths.get(sourceFolderPath).relativize(first.get()).toString();
                        destinationFileName = destinationFolderPath + "/" + relativePath;
                    } else {
                        System.out.println("APPLY PATCH ERROR: Cannot find file " + fileName + " in folder " + sourceFolderPath);
                    }
                } catch (IOException e) {
                    System.out.println("APPLY PATCH ERROR: Cannot read folder " + sourceFolderPath);
                }
            } else {
                patchFileLines.add(line);
            }
        }
        System.out.println("*********** PATCHING "+sourceFileName+" ***********");
        System.out.println("Patched file is "+destinationFileName);

        // patch last file in patchfile
        if (sourceFileName != null && destinationFileName != null && patchFileLines.size() > 0) {
            patchFile(sourceFileName, destinationFileName, patchFileLines);

            if (destinationSaveToFileName != null) {
                // copy destinationFileName to destinationSaveToFileName
                try {
                    System.out.println("Copying patched file " + destinationFileName + " to " + destinationSaveToFileName);
                    FileUtils.copyFile(new File(destinationFileName), new File(destinationSaveToFileName));
                } catch (IOException e) {
                    System.out.println("APPLY PATCH ERROR: Cannot copy file " + destinationFileName + " to " + destinationSaveToFileName);
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * Applies patch to the source file and saves the result to the destination file
     * @param sourceFileName - source file name
     *                       (the file which should be patched)
     * @param destinationFileName - destination file name
     *                       (where the patched result should be saved)
     * @param patchLines - patch lines
     *                       (the patch should be generated by JAIG)
     */
    private void patchFile(String sourceFileName, String destinationFileName, List<String> patchLines) {
        File sourceFile = new File(sourceFileName);
        List<String> sourceLines = null;
        try {
            sourceLines = FileUtils.readLines(sourceFile, StandardCharsets.UTF_8);
        } catch(Exception e) {
            System.out.println("APPLY PATCH WARN: Cannot read file "+sourceFile);
            System.exit(-1);
        }
        String allSourceLinesTrimJoined = sourceLines.stream().map(line->line.trim()).collect(Collectors.joining("\n"));
        // insertionsAfter represents the insertions after line <after>
        // For example:
        // after+4>         for (Material material : course1.getMaterials()) {
        // inserted>                 System.out.println("Content: " + material.getContent());
        // It should be:
        // "for (Material material : course1.getMaterials()) {" ->
        //      4 -> "              System.out.println("Content: " + material.getContent());"
        // The format is:
        //  <after>     <shift>  <lines to be inserted>
        Map<String, Map<Integer, String>> insertionsAfter = new LinkedHashMap<>();
        // deletionsAfter: <after> -> <lines to be deleted>
        Map<String, String> deletionsAfter = new LinkedHashMap<>();
        // deletions without location - line is removed from everywhere
        Set<String> deletions = new HashSet<>();
        // replacements without location - line
        // (or block with several lines, \n separated)
        // are replaced everywhere in the source
        Map<String, String> replacements = new HashMap<>();
        // replacementsAfter: <after> -> (<old> -> <new>)
        Map<String, Map<String, String>> replacementsAfter = new HashMap<>();

        String oldLines = ""; // <old> lines to be replaced by <new>
        String after = null;
        int shift = 1; // shift for the lines next to <after> line
        String old = null;
        String afterWARN = null;
        StringBuilder patchWARNs = new StringBuilder();
        // previous line was <old> - we continue <old> lines processing
        boolean continuedOldLines = false;
        // previous line was <after>
        boolean nextToAfterLine = false;

        for (String patchLine: patchLines) {
            String finalAfter = after;
            // continuedInsertions exclude cases when after> is related to insertions, not deletions
            if (after != null && patchLine.trim().startsWith("deleted>")) {
                nextToAfterLine = false;
                // check that line <after> exists in the source file
                if (!sourceLines.stream().anyMatch(line -> line.trim().equals(finalAfter.trim()))) {
                    afterWARN = after;
                    patchWARNs.append("\nPATCH WARN: cannot delete line, line <after> doesn't exist:\n" +
                            (shift > 1 ? "       after+" + shift + ">" : "      after>") + after + "\n" + patchLine);
                } else {
                    afterWARN = null;
                    String[] parts = patchLine.split("deleted>");
                    if (parts.length == 2) {
                        if (deletionsAfter.containsKey(after.trim())) {
                            deletionsAfter.merge(after.trim(), parts[1], (a,b) -> a+"\n"+b);
                        } else {
                            deletionsAfter.put(after.trim(), parts[1]);
                        }
                    }
                }
                // for deletions, we do not support multi-line <after> deletions now
                //after = null;
            } else if (after != null && patchLine.trim().startsWith("inserted>")) {
                nextToAfterLine = false;
                // if we already found patch WARN before,
                // and we have several lines to insert,
                // we want to show WARN message only once
                if (after.equals(afterWARN)) {
                    patchWARNs.append("\n"+patchLine.replace("inserted>", "         "));
                } else {
                    // check that line <after> exists in the source file
                    if (!sourceLines.stream().anyMatch(line -> line.trim().equals(finalAfter.trim()))) {
                        afterWARN = after;
                        patchWARNs.append("\nAPPLY PATCH WARN: cannot insert, line <after> doesn't exist:\n" +
                                (shift > 1 ? "       after+" + shift + ">" : "      after>") + after + "\n" + patchLine);
                    } else {
                        afterWARN = null;
                        String[] parts = patchLine.split("inserted> ");
                        if (parts.length == 2) {
                            if (insertionsAfter.containsKey(after.trim())) {
                                Map<Integer, String> insertions = insertionsAfter.get(after.trim());
                                insertions.merge(shift, parts[1], (a,b) -> a+"\n"+b);
                            } else {
                                Map<Integer, String> insertions = new HashMap<>();
                                insertions.put(shift, parts[1]);
                                insertionsAfter.put(after.trim(), insertions);
                            }
                        }
                    }
                }
            } else if (after != null && patchLine.trim().startsWith("new>")) {
                nextToAfterLine = false;
                continuedOldLines = false;
                // check that line <after> exists in the source file
                if (!sourceLines.stream().anyMatch(line -> line.trim().equals(finalAfter.trim()))) {
                    afterWARN = after;
                    patchWARNs.append("\nAPPLY PATCH WARN: cannot replace, line <after> doesn't exist:\n" +
                            "      after>" + after + "\n" + patchLine);
                } else {
                    afterWARN = null;
                    String[] parts = patchLine.split("new> ");
                    if (parts.length == 2) {
                        if (replacementsAfter.containsKey(after.trim())) {
                            Map<String, String> replacementsMap = replacementsAfter.get(after.trim());
                            replacementsMap.merge(oldLines, parts[1], (a,b) -> a+"\n"+b);
                        } else {
                            Map<String, String> replacementsMap = new LinkedHashMap<>();
                            replacementsMap.put(oldLines, parts[1]);
                            replacementsAfter.put(after.trim(), replacementsMap);
                        }
                    }
                }
            } else {
                if (nextToAfterLine && patchLine.trim().startsWith("old>") || continuedOldLines) {
                    // we are processing <old> lines (this one is the next)
                } else {
                    after = null;
                    nextToAfterLine = false;
                }

                if (patchLine.trim().startsWith("after>")) {
                    shift = 1;
                    String[] parts = patchLine.split("after> ");
                    if (parts.length == 2) {
                        after = parts[1];
                        nextToAfterLine = true;
                    }
                }

                if (patchLine.trim().startsWith("after+")) { // after with a shift
                    String shiftString = patchLine.trim().substring("after+".length(),
                            patchLine.trim().indexOf(">"));
                    shift = Integer.parseInt(shiftString);
                    String[] parts = patchLine.split("after\\+"+shift+"> ");
                    if (parts.length == 2) {
                        after = parts[1];
                        nextToAfterLine = true;
                    }
                }

                if (patchLine.trim().startsWith("new>") && !oldLines.isEmpty() ) {
                    String[] parts = patchLine.split("new> ");
                    if (parts.length == 2) {
                        replacements.merge(oldLines, parts[1], (a,b) -> a+"\n"+b);
                    }
                }

                if (patchLine.trim().startsWith("deleted>")) {
                    String[] parts = patchLine.split("deleted> ");
                    if (parts.length == 2) {
                        deletions.add(parts[1].trim());
                    }
                }

                if (patchLine.trim().startsWith("old>")) {
                    // we were not processing <old> lines (this one is the first)
                    // therefore we should start the new set of <old> lines
                    // to collect all lines which should be replaced by <new> lines
                    if (!continuedOldLines) {
                        oldLines = "";
                    }
                    String[] parts = patchLine.split("old> ");
                    if (parts.length == 2) {
                        old = parts[1];
                        if (!oldLines.isEmpty()) {
                            oldLines = oldLines+"\n"+old.trim();
                        } else {
                            oldLines = old.trim();
                        }
                        //oldLines = oldLines+"\n"+old.trim();
                        continuedOldLines = true;
                    }
                } else {
                    continuedOldLines = false;
                }

            }

        }
        if (!patchWARNs.isEmpty()) {
            System.out.println("*********** PATCHING "+sourceFile.getName()+" ***********");
            System.out.println("Found WARNs when applying patch to file "+sourceFile.getName()+".");
            System.out.println("Please apply these patches manually if needed:");
            System.out.println(patchWARNs);
        }

        // prepare inserts and deletions
        Set<Integer> linesToDelete = new HashSet<>();
        Map<Integer, String> linesToInsert = new HashMap<>();
        Map<Integer, String> linesToReplace = new HashMap<>();
        for (int i=0; i<sourceLines.size(); i++) {
            String line = sourceLines.get(i);
            String key = line.trim();
            if (deletions.contains(key)) {
                linesToDelete.add(i);
            }
            if (insertionsAfter.containsKey(key)) {
                Map<Integer, String> insertions = insertionsAfter.get(key);
                for (Integer iShift: insertions.keySet()) {
                    String insertionLines = insertions.get(iShift);
                    linesToInsert.put(i+iShift, insertionLines);
                }
            }
            if (deletionsAfter.containsKey(key)) {
                String deletionLines = deletionsAfter.get(key);
                String[] linesToDeleteInPatch = deletionLines.split("\n");
                String lineToFind = linesToDeleteInPatch[0];
                // finding line
                int iShift = 0;
                int deleteLineIndex = 0;
                // finding when the line to delete is started
                while (i+iShift<sourceLines.size() && !sourceLines.get(i+iShift).trim().equals(lineToFind.trim())) {
                    iShift++;
                }
                while(i+iShift<sourceLines.size() && deleteLineIndex<linesToDeleteInPatch.length) {
                    String lineToDeleteInSource = sourceLines.get(i+iShift);
                    String lineToDeleteInPatch = linesToDeleteInPatch[deleteLineIndex];
                    if (lineToDeleteInPatch.trim().equals(lineToDeleteInSource.trim())) {
                        linesToDelete.add(i+iShift);
                        deleteLineIndex++;
                    } else {
                        System.out.println(
                                "APPLY PATCH WARN: Line to delete in patch was not found in source:\n"+
                                        String.join("\n", linesToDeleteInPatch));
                    }
                    iShift++;
                }
                // skip lines which were already processed as they were after <after>
                i += linesToDeleteInPatch.length - 1;
            }

            if (replacementsAfter.containsKey(key)) {
                Map<String, String> replacementsMap = replacementsAfter.get(key);
                for (String oldLine: replacementsMap.keySet()) {
                    String[] oldLinesSplitted = oldLine.split("\n");
                    String lineToFind = oldLinesSplitted[0].trim(); // first line of <old> lines
                    String newLines = replacementsMap.get(oldLine);
                    String[] newLinesSplitted = newLines.split("\n");
                    // finding the first <old> line
                    int iShift = 0;
                    // finding when the line to replace is started
                    while (!sourceLines.get(i+iShift).trim().equals(lineToFind)) {
                        iShift++;
                        if (i+iShift == sourceLines.size()) {
                            System.out.println(
                                    "APPLY PATCH WARN: <old> line was not found: " + lineToFind);
                            break;
                        }
                    }
                    for (int j = 0; j<newLinesSplitted.length; j++) {
                        if (oldLinesSplitted[j].trim().equals(sourceLines.get(i + iShift + j).trim())) {
                            linesToReplace.put(i + iShift + j, newLinesSplitted[j]);
                        } else {
                            System.out.println(
                                    "APPLY PATCH WARN: <old> line was not found in source: " + oldLinesSplitted[j]);
                        }
                        //linesToReplace.put(i + iShift + j, newLinesSplitted[j]);
                    }
                }
                // skip lines which were already processed as they were after <after>
                i += replacementsMap.size() - 1;
            }
        }

        // apply inserts and updates
        List<String> resultLines = new ArrayList<>();
        for (int i=0; i<sourceLines.size(); i++) {
            String line = sourceLines.get(i);
            if (linesToInsert.containsKey(i)) {
                resultLines.add(linesToInsert.get(i));
            }
            if (!linesToDelete.contains(i) && !linesToReplace.containsKey(i)) {
                resultLines.add(line);
            }
            if (linesToReplace.containsKey(i)) {
                resultLines.add(linesToReplace.get(i));
            }
        }

        // apply replacements
        List<String> resultLinesWithReplacements = applyReplacements(resultLines, replacements);

        try {
            FileUtils.writeLines(new File(destinationFileName), resultLinesWithReplacements);
        } catch (IOException e) {
            System.out.println("APPLY PATCH WARN: wasn't able to save patched result to "+destinationFileName);
        }
        System.out.println("Patched file "+destinationFileName);

    }

    public static List<String> applyReplacements(List<String> sourceLines, Map<String, String> replacements) {
        String sourceString = String.join("\n", sourceLines);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String keyPattern = createRegexPatternFromMultiline(entry.getKey());
            sourceString = sourceString.replaceAll(keyPattern, "\n"+Matcher.quoteReplacement(entry.getValue()));
        }

        return new ArrayList<>(Arrays.asList(sourceString.split("\n")));
    }

    private static String createRegexPatternFromMultiline(String multiline) {
        String[] lines = multiline.split("\n");
        StringBuilder pattern = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            pattern.append(Pattern.quote(lines[i]));
            // If it's not the last line, append the whitespace pattern
            if (i < lines.length - 1) {
                pattern.append("\\s*?\n\\s*?");
            }
        }

        return pattern.toString();
    }

}
