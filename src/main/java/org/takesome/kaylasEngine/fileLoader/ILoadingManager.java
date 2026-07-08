package org.takesome.kaylasEngine.fileLoader;

/**
 * Interface for displaying information about the download/loading process.
 */
public interface ILoadingManager {

    /**
     * Toggles the visibility of the loading UI.
     */
    void toggleVisibility();

    /**
     * Sets loading text displayed in the UI.
     *
     * @param descriptionKey localization key for the loading description
     * @param titleKey       localization key for the loading title
     */
    void setLoadingText(String descriptionKey, String titleKey);
}
