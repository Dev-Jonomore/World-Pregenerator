package mc.jonomore.worldPregenerator;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorHandler {
    private final Logger logger;

    public enum ErrorCategory {
        FATAL,      // Stop generation
        RECOVERABLE, // Retry
        SKIP         // Skip to next seed
    }

    public ErrorHandler(Logger logger) {
        this.logger = logger;
    }

    public ErrorCategory categorize(Exception e) {
        if (e instanceof SecurityException || e instanceof IllegalStateException) {
            return ErrorCategory.FATAL;
        }

        if (e instanceof java.io.IOException) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("permission denied") || msg.contains("no space left on device")) {
                return ErrorCategory.FATAL;
            }
            return ErrorCategory.RECOVERABLE;
        }

        // Default to SKIP for unknown world-specific issues
        return ErrorCategory.SKIP;
    }

    public void handleError(Exception e, String context) {
        ErrorCategory category = categorize(e);
        logger.log(Level.SEVERE, "Error during " + context + " [" + category + "]: " + e.getMessage(), e);
    }
}
