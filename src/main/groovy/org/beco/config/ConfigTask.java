package org.beco.config;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public class ConfigTask  extends DefaultTask {

    public final static String JSON_FILE_NAME = "beco-services.json";

    public final static Pattern VARIANT_PATTERN = Pattern.compile(
            "(?:([^\\p{javaUpperCase}]+)((?:\\p{javaUpperCase}]*)*)\\/)?([^\\/]*)");

    public final static Pattern FLAVOR_PATTERN = Pattern.compile("(\\p{javaUpperCase}[^\\p{javaUpperCase}]*)");

    private static final String STATUS_DISABLED = "1";
    private static final String STATUS_ENABLED = "2";

    private static final String OAUTH_CLIENT_TYPE_WEB = "3";

    private File intermediateDir;
    private String variantDir;
    private String packageNameXOR1;
    private TextResource packageNameXOR2;

    @OutputDirectory
    public File getIntermediateDir() {
        return intermediateDir;
    }

    @Input
    public String getVariantDir() { return variantDir; }

    @Input
    @Optional
    public String getPackageNameXOR1() {
        return packageNameXOR1;
    }

    @Input
    @Optional
    public TextResource getPackageNameXOR2() {
        return packageNameXOR2;
    }

    public void setIntermediateDir(File intermediateDir) {
        this.intermediateDir = intermediateDir;
    }

    public void setVariantDir(String variantDir) {
        this.variantDir = variantDir;
    }

    public void setPackageNameXOR1(String packageNameXOR1) {
        this.packageNameXOR1 = packageNameXOR1;
    }

    public void setPackageNameXOR2(TextResource packageNameXOR2) {
        this.packageNameXOR2 = packageNameXOR2;
    }

    @TaskAction
    public void action() throws IOException {
        File quickstartFile = null;
        List<String> fileLocations = getJsonLocations(variantDir);
        String searchedLocation = System.lineSeparator();
        for(String location : fileLocations){
            File jsonFile = getProject().file(location +'/'+ JSON_FILE_NAME);
            searchedLocation = searchedLocation + jsonFile.getPath() + System.lineSeparator();
            if(jsonFile.isFile()){
                quickstartFile = jsonFile;
                break;
            }
        }

        if(quickstartFile == null){
            quickstartFile = getProject().file(JSON_FILE_NAME);
            searchedLocation = searchedLocation + quickstartFile.getPath();
        }

        if(!quickstartFile.isFile()){
            throw new GradleException(
                    String.format(
                            "File %s is missing."
                                    + "The Services Plugin cannot function without it. %n Searched Location: %s",
                            quickstartFile.getName(), searchedLocation
                    )
            );
        }

        if (packageNameXOR1 == null && packageNameXOR2 == null) {
            throw new GradleException(
                    String.format(
                            "One of packageNameXOR1 or packageNameXOR2 are required: "
                                    + "packageNameXOR1: %s, packageNameXOR2: %s",
                            packageNameXOR1, packageNameXOR2));
        }

        getProject().getLogger().info("Parsing json file: " + quickstartFile.getPath());

        deleteFolder(intermediateDir);
        if(!intermediateDir.mkdirs()){
            throw new GradleException("Failed to create folder: " + intermediateDir);
        }

        JsonElement root =  new JsonParser().parse(Files.newReader(quickstartFile, Charsets.UTF_8));

        if(!root.isJsonObject()){
            throw new GradleException("Malformed root json");
        }

        JsonObject rootObject = root.getAsJsonObject();

        Map<String, String> resValues = new TreeMap<>();
        Map<String, Map<String, String>> resAttributes = new TreeMap<>();

        handleProjectNumberAndProjectId(rootObject, resValues);

        File values = new File(intermediateDir, "values");
        if(!values.exists() && !values.mkdirs()){
            throw new GradleException("Failed to create folder: " + values);
        }

        Files.asCharSink(new File(values, "beco_values.xml"), Charsets.UTF_8)
                .write(getValuesContent(resValues, resAttributes));
    }

    private void handleProjectNumberAndProjectId(JsonObject rootObject, Map<String, String> resValues)
            throws IOException {
        JsonObject projectInfo = rootObject.getAsJsonObject("project_info");
        if (projectInfo == null) {
            throw new GradleException("Missing project_info object");
        }

        JsonPrimitive apiKey = projectInfo.getAsJsonPrimitive("api_key");
        if (apiKey == null) {
            throw new GradleException("Missing project_info/api_key object");
        }

        resValues.put("be_apiKey", apiKey.getAsString());

        JsonPrimitive envId = projectInfo.getAsJsonPrimitive("environment_id");

        if (envId == null) {
            throw new GradleException("Missing project_info/environment_id object");
        }
        resValues.put("be_environmentId", envId.getAsString());

    }

    private static String getValuesContent(
            Map<String, String> values, Map<String, Map<String, String>> attributes){
        StringBuilder sb = new StringBuilder(256);

        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<resources>\n");

        for (Map.Entry<String, String> entry : values.entrySet()){
            String name = entry.getKey();
            sb.append("    <string name=\"").append(name).append("\" translatable=\"false\"");
            if (attributes.containsKey(name)) {
                for (Map.Entry<String, String> attr : attributes.get(name).entrySet()) {
                    sb.append(" ").append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"");
                }
            }
            sb.append(">").append(entry.getValue()).append("</string>\n");
        }

        sb.append("</resources>\n");

        return sb.toString();

    }


    private static void deleteFolder(final File folder){
        if(!folder.exists()){
            return;
        }
        File[] files = folder.listFiles();
        if(files != null){
            for(final File file : files){
                if (file.isDirectory()){
                    deleteFolder(file);
                }else{
                    if(!file.delete()){
                        throw new GradleException("Failed to delete: " + file);
                    }
                }
            }
        }
        if(!folder.delete()){
            throw new GradleException("Failed to delete: " + folder);
        }
    }

    private static List<String> splitVariantNames(String variant){
        if(variant == null){
            return new ArrayList<>();
        }
        List<String> flavors = new ArrayList<>();
        Matcher flavorMatcher = FLAVOR_PATTERN.matcher(variant);
        while (flavorMatcher.find()){
            String match = flavorMatcher.group(1);
            if(match!= null){
                flavors.add(match.toLowerCase());
            }
        }
        return flavors;
    }

    private static long countSlashes(String input){
        return input.codePoints().filter(x -> x == '/').count();
    }

    static List<String> getJsonLocations(String variantDirname){
        Matcher variantMatcher = VARIANT_PATTERN.matcher(variantDirname);
        List<String> fileLocations = new ArrayList<>();
        if(!variantMatcher.matches()){
            return fileLocations;
        }
        List<String> flavorNames = new ArrayList<>();
        if (variantMatcher.group(1) != null) {
            flavorNames.add(variantMatcher.group(1).toLowerCase());
        }
        flavorNames.addAll(splitVariantNames(variantMatcher.group(2)));
        String buildType = variantMatcher.group(3);
        String flavorName = variantMatcher.group(1) + variantMatcher.group(2);
        fileLocations.add("src/" + flavorName + "/" + buildType);
        fileLocations.add("src/" + buildType + "/" + flavorName);
        fileLocations.add("src/" + flavorName);
        fileLocations.add("src/" + buildType);
        fileLocations.add("src/" + flavorName + capitalize(buildType));
        fileLocations.add("src/" + buildType);
        String fileLocation = "src";
        for(String flavor : flavorNames) {
            fileLocation += "/" + flavor;
            fileLocations.add(fileLocation);
            fileLocations.add(fileLocation + "/" + buildType);
            fileLocations.add(fileLocation + capitalize(buildType));
        }
        fileLocations = fileLocations.stream().distinct().sorted(Comparator.comparing(ConfigTask::countSlashes)).collect(toList());
        return fileLocations;
    }

    public static String capitalize(String s){
        if(s.length() == 0) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
