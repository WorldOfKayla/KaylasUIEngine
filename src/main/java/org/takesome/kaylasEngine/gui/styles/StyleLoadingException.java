package org.takesome.kaylasEngine.gui.styles;

public final class StyleLoadingException extends Exception {
    private final boolean missingResource;

    public StyleLoadingException(String message) {
        this(message, null, false);
    }

    public StyleLoadingException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public StyleLoadingException(String message, boolean missingResource) {
        this(message, null, missingResource);
    }

    public StyleLoadingException(String message, Throwable cause, boolean missingResource) {
        super(message, cause);
        this.missingResource = missingResource;
    }

    public boolean isMissingResource() {
        return missingResource;
    }
}
