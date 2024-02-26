package app.jtutor.jaig.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper=true)
public class LocalConfig extends Config {
    // saves response from AI to the file
    private String saveResponseTo;

    // should we execute the generated Java code
    private boolean runJava;

    // should we write the parsed result to test/java
    private boolean writeResponseToTest;

    // should we prevent code merge
    private boolean noMerge;

    private List<String> mergeIncompleteList;

    private boolean mergeIncomplete;

}
