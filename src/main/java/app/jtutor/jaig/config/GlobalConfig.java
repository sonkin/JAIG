package app.jtutor.jaig.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper=true)
public class GlobalConfig extends Config {
    public static GlobalConfig INSTANCE = new GlobalConfig();
    
    private String openAIApiKey;
    private String key;
    private String endpoint;
    private String deploymentIdOrModel;
    private String promptsLibraryShortHint;
    private List<String> javaFileNameRegexps;
    private boolean gptProxy;

    private Map<String, String> promptsLibrary;
    private String mergePrompt;
    private String mergeIncompletePrompt;

    public void parseYamlConfig() {
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("JAIG/JAIG.yaml");
        } catch (FileNotFoundException e) {
            System.out.println("Configuration file JAIG.yaml is not found!");
            System.exit(-1);
        }
        Map<String, Object> yamlData = yaml.load(inputStream);
        setOpenAIApiKey(openAIApiKey = (String) yamlData.get("openAIApiKey"));
        setKey((String) yamlData.get("key"));
        setEndpoint((String) yamlData.get("endpoint"));
        setDeploymentIdOrModel((String) yamlData.get("deploymentIdOrModel"));
        setModel( (String) yamlData.get("model"));
        setTemperature((Double) yamlData.get("temperature"));

        setGptProxy((Boolean) yamlData.getOrDefault("gptProxy", false));
        setGenerateResponse((Boolean) yamlData.getOrDefault("generateResponse", false));
        setCreatePatch((Boolean) yamlData.getOrDefault("createPatch", false));
        setParseJavaCode((Boolean) yamlData.getOrDefault("parseJavaCode", false));
        setApplyPatch((Boolean) yamlData.getOrDefault("applyPatch", false));
        setCreateMerge((Boolean) yamlData.getOrDefault("createMerge", false));
        setCreateSrcBackup((Boolean) yamlData.getOrDefault("createSrcBackup", false));
        setWriteResponseToSrc((Boolean) yamlData.getOrDefault("writeResponseToSrc", false));
        setApplyMerge((Boolean) yamlData.getOrDefault("applyMerge", false));
        setSeed((Integer) yamlData.getOrDefault("seed", null));

        setPreParseCountdown((Integer) yamlData.getOrDefault("preParseCountdown", 5)); // Assuming default is 5 if not provided
        setSrcFolder((String) yamlData.get("srcFolder"));
        setTestFolder((String) yamlData.get("testFolder"));
        setPromptsLibraryShortHint((String) yamlData.get("promptsLibraryShortHint"));
        setCreateRollback(yamlData.containsKey("createRollback"));
        setJavaFileNameRegexps((List<String>) yamlData.get("javaFileNameRegexp"));
        setApplyRollback((Boolean) yamlData.getOrDefault("applyRollback", false));

        /*
        This is how YAML looks:
        promptsLibrary:
          explain:
            Explain the code fragment
          doc:
            Show documentation for this code fragment from Java or Spring

         */

        setPromptsLibrary((Map<String, String>) yamlData.get("promptsLibrary"));

        setMergePrompt((String) yamlData.get("mergePrompt"));
        setMergeIncompletePrompt((String) yamlData.get("mergeIncompletePrompt"));
    }
}
