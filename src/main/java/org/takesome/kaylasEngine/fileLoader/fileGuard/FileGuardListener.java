package org.takesome.kaylasEngine.fileLoader.fileGuard;

import java.io.File;

public interface FileGuardListener {
    void  onFilesChecked(int filesDeleted);
    void onDirCheck(String dir);
    void onFileCheck(File file);
}
