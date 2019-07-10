package se.kth.jdbl.pom.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;

/**
 * Outputs Transfer Events to a logger.
 */
public class LoggingTransferListener extends AbstractTransferListener {

    private final static Logger logger =
            LoggerFactory.getLogger(LoggingRepositoryListener.class);

    private final LogLevel infoLogLevel;

    private final LogLevel errorLogLevel;

    /**
     * Default Constructor.
     * <p>
     * Logs events with level INFO and errors with WARN.
     */
    public LoggingTransferListener() {
        this(LogLevel.INFO, LogLevel.WARN);
    }

    /**
     * Creates a new TransferListener with user defined log levels.
     *
     * @param infoLogLevel  log level for "info"-type messages.
     * @param errorLogLevel log level for "error" messages.
     */
    LoggingTransferListener(LogLevel infoLogLevel, LogLevel errorLogLevel) {
        this.infoLogLevel = infoLogLevel;
        this.errorLogLevel = errorLogLevel;
    }

    @Override
    public void transferFailed(TransferEvent event) {
        log(errorLogLevel, "{} failed: {}.", getTransferType(event),
                event.getException().getMessage());
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        log(infoLogLevel, "{}: {}{}.", getTransferType(event),
                event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        log(infoLogLevel, "{} completed: {}{}.", getTransferType(event),
                event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    private String getTransferType(TransferEvent event) {
        String transferType = "Downloading";
        if (event.getRequestType() == TransferEvent.RequestType.PUT) {
            transferType = "Uploading";
        }
        return transferType;
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
