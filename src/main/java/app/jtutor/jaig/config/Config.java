package app.jtutor.jaig.config;

import lombok.Data;

@Data
public class Config {
    // GPT model configuration
    private String model;
    private Double temperature;

    // PHASE 2: should we send the request to AI and get the response
    private boolean generateResponse;
    // PHASE 3: create or update patch for previous code changes
    private boolean createPatch;
    // PHASE 4: should we parse code if number of "package ..." === number of classes and interfaces
    private boolean parseJavaCode;
    // PHASE 5: should we patch code if .patch file is found
    private boolean applyPatch;
    // PHASE 5: should we merge code
    private boolean createMerge;
    // PHASE 6.1: keep backup when replacing files in src/main/java
    private boolean createSrcBackup;
    // PHASE 6.2: write the parsed files to src/main/java
    private boolean writeResponseToSrc;
    // PHASE 7: create rollback
    private boolean createRollback;
    // PHASE 8: apply merge
    private boolean applyMerge;

    // should we rollback the changes before re-running the prompt
    // this is useful if the prompt is not good and we want to re-run it
    // and we don't want to keep the changes in the sources
    private boolean applyRollback;

    // how many seconds to wait before parsing the code:
    // this time is given to stop parsing and next steps
    // if the generated result is not what was expected
    private int preParseCountdown = 5;

    // seed for GPT to generate the same result for the same prompt (make it deterministic)
    private Integer seed;

}
