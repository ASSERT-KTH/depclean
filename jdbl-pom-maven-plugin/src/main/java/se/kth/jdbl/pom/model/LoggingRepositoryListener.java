package se.kth.jdbl.pom.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;

/**
 * Outputs Repository Events to a logger.
 */
public class LoggingRepositoryListener extends AbstractRepositoryListener {

    private final static Logger logger =
            LoggerFactory.getLogger(LoggingRepositoryListener.class);

    private final LogLevel infoLogLevel;

    private final LogLevel errorLogLevel;

    /**
     * Default Constructor.
     * <p>
     * Logs events with level INFO and errors with WARN.
     */
    public LoggingRepositoryListener() {
        this(LogLevel.INFO, LogLevel.WARN);
    }

    /**
     * Creates a new RepositoryListener with user defined log levels.
     *
     * @param infoLogLevel  log level for "info"-type messages.
     * @param errorLogLevel log level for "error" messages.
     */
    LoggingRepositoryListener(LogLevel infoLogLevel, LogLevel errorLogLevel) {
        this.infoLogLevel = infoLogLevel;
        this.errorLogLevel = errorLogLevel;
    }

    @Override
    public void artifactDeployed(RepositoryEvent event) {
        log(infoLogLevel, "Deployed {} to {}.",
                event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDeploying(RepositoryEvent event) {
        log(infoLogLevel, "Deploying {} to {}.",
                event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDescriptorInvalid(RepositoryEvent event) {
        log(errorLogLevel, "Invalid artifact descriptor for {}: {}.",
                event.getArtifact(), event.getException().getMessage());
    }

    @Override
    public void artifactDescriptorMissing(RepositoryEvent event) {
        log(errorLogLevel, "Missing artifact descriptor for {}.",
                event.getArtifact());
    }

    @Override
    public void artifactInstalled(RepositoryEvent event) {
        log(infoLogLevel, "Installed {} to {}.",
                event.getArtifact(), event.getFile());
    }

    @Override
    public void artifactInstalling(RepositoryEvent event) {
        log(infoLogLevel, "Installing {} to {}.",
                event.getArtifact(), event.getFile());
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        log(infoLogLevel, "Resolved artifact {} from {}.",
                event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        log(infoLogLevel, "Downloading artifact {} from {}.",
                event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        log(infoLogLevel, "Downloaded artifact {} from {}.",
                event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactResolving(RepositoryEvent event) {
        log(infoLogLevel, "Resolving artifact {}.", event.getArtifact());
    }

    @Override
    public void metadataDeployed(RepositoryEvent event) {
        log(infoLogLevel, "Deployed {} to {}.",
                event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDeploying(RepositoryEvent event) {
        log(infoLogLevel, "Deploying {} to {}.",
                event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataInstalled(RepositoryEvent event) {
        log(infoLogLevel, "Installed {} to {}.",
                event.getMetadata(), event.getFile());
    }

    @Override
    public void metadataInstalling(RepositoryEvent event) {
        log(infoLogLevel, "Installing {} to {}.",
                event.getMetadata(), event.getFile());
    }

    @Override
    public void metadataInvalid(RepositoryEvent event) {
        log(errorLogLevel, "Invalid metadata {}.", event.getMetadata());
    }

    @Override
    public void metadataResolved(RepositoryEvent event) {
        log(infoLogLevel, "Resolved metadata {} from {}.",
                event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataResolving(RepositoryEvent event) {
        log(infoLogLevel, "Resolving metadata {} from {}.",
                event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDownloaded(RepositoryEvent event) {
        log(infoLogLevel, "Downloaded metadata {} from {}.",
                event.getMetadata(), event.getRepository());
    }

    @Override
    public void metadataDownloading(RepositoryEvent event) {
        log(infoLogLevel, "Downloading metadata {} from {}.",
                event.getMetadata(), event.getRepository());
    }

    private void log(LogLevel logLevel, String messageString, Object... arguments) {
        switch (logLevel) {
            case ERROR: {
                logger.error(messageString, arguments);
                break;
            }
            case WARN: {
                logger.warn(messageString, arguments);
                break;
            }
            case INFO: {
                logger.info(messageString, arguments);
                break;
            }
            case DEBUG: {
                logger.debug(messageString, arguments);
                break;
            }
            case TRACE: {
                logger.trace(messageString, arguments);
                break;
            }
        }
    }
}
