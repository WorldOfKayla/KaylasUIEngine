package org.takesome.kaylasEngine.fileLoader;

/**
 * Интерфейс для уведомления о событиях загрузки файлов.
 */
public interface IFileLoaderListener {
    void onFileAdd(FileAttributes fileAttribute);
    void onFilesRead();
    void onDownloadStart();
    void onFilesLoaded();
    void onNewFileFound(AbstractFileLoader fileLoader);

    /**
     * Preferred download callback for concurrent loaders. The attribute is passed explicitly so
     * listeners do not need to read a shared volatile currentFile value while several downloads run.
     */
    default void onNewFileFound(AbstractFileLoader fileLoader, FileAttributes fileAttribute) {
        onNewFileFound(fileLoader);
    }

    void onCancel();
    void filesProcessed();
}
