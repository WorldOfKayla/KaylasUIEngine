package org.takesome.kaylasEngine.utils.HTTP;

import java.util.List;

public class HTTPconf {
    private boolean useCaches, doInput, doOutput;
    private List<RequestProperty> requestProperties;

    public boolean isUseCaches() {
        return useCaches;
    }

    public boolean isDoInput() {
        return doInput;
    }

    public boolean isDoOutput() {
        return doOutput;
    }

    public List<RequestProperty> getRequestProperties() {
        return requestProperties;
    }
}
