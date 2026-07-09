package org.takesome.kaylasEngine.fileLoader;

public class FileAttributes {

    @SuppressWarnings("unused")
    private String filename;
    @SuppressWarnings("unused")
    private String hash;
    @SuppressWarnings("unused")
    private int size;
    @SuppressWarnings("unused")
    private String localPath;
    private String replaceMask;

    public int getSize() {
        return size;
    }

    public String getFilename() {
        return filename;
    }

    public String getHash() {
        return hash;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setReplaceMask(String replaceMask) {
        this.replaceMask = replaceMask;
    }

    public String getReplaceMask() {
        return replaceMask;
    }
}
