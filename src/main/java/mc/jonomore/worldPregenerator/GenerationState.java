package mc.jonomore.worldPregenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GenerationState {
    private int currentIndex = 0;
    private int totalSeeds = 0;
    private int successCount = 0;
    private int failureCount = 0;
    private long startTime = 0;
    private List<FailedSeedEntry> failedSeeds = new ArrayList<>();
    private String currentStep = "IDLE";
    private String currentWorldName = null;
    private File currentWorldFolder = null;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(File.class, (JsonSerializer<File>) (src, typeOfSrc, context) -> new JsonPrimitive(src.getPath()))
            .registerTypeAdapter(File.class, (JsonDeserializer<File>) (json, typeOfT, context) -> new File(json.getAsString()))
            .setPrettyPrinting()
            .create();

    public GenerationState() {}

    public void save(File stateFile) throws IOException {
        try (Writer writer = new FileWriter(stateFile)) {
            GSON.toJson(this, writer);
        }
    }

    public static GenerationState load(File stateFile) throws IOException {
        try (Reader reader = new FileReader(stateFile)) {
            return GSON.fromJson(reader, GenerationState.class);
        }
    }

    // Getters and Setters
    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }

    public int getTotalSeeds() { return totalSeeds; }
    public void setTotalSeeds(int totalSeeds) { this.totalSeeds = totalSeeds; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public List<FailedSeedEntry> getFailedSeeds() { return failedSeeds; }
    public void addFailedSeed(FailedSeedEntry entry) { this.failedSeeds.add(entry); }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public String getCurrentWorldName() { return currentWorldName; }
    public void setCurrentWorldName(String currentWorldName) { this.currentWorldName = currentWorldName; }

    public File getCurrentWorldFolder() { return currentWorldFolder; }
    public void setCurrentWorldFolder(File currentWorldFolder) { this.currentWorldFolder = currentWorldFolder; }

    public double getProgress() {
        if (totalSeeds == 0) return 0;
        return (double) currentIndex / totalSeeds;
    }

    public long estimateTimeRemaining() {
        if (currentIndex == 0 || startTime == 0) return -1;
        long elapsed = System.currentTimeMillis() - startTime;
        double timePerWorld = (double) elapsed / currentIndex;
        return (long) (timePerWorld * (totalSeeds - currentIndex));
    }
}
