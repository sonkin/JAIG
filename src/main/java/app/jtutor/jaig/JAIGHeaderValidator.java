package app.jtutor.jaig;

import java.io.File;
import java.util.List;

@FunctionalInterface
interface JAIGHeaderValidator {
    boolean validate(File originalFile, List<String> originalLines, List<String> revisedLines);
}
