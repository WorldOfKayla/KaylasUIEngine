package org.takesome.kaylasEngine.fileLoader;

/**
 * нтерфейс для уведомления о событиях загрузки файлов.
 */
public interface IFileLoaderListener {
    void onFileAdd(FileAttributes fileAttribute);
    void onFilesRead();
    void onDownloadStart();
    void onFilesLoaded();
    void onNewFileFound(AbstractFileLoader fileLoader);
    void onCancel();
    void filesProcessed();
}
