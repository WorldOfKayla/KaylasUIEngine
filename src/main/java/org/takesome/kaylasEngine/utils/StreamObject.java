package org.takesome.kaylasEngine.utils;

import org.takesome.kaylasEngine.utils.helper.IOHelper;
import org.takesome.kaylasEngine.utils.io.HInput;
import org.takesome.kaylasEngine.utils.io.HOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class StreamObject {
    /* public StreamObject(HInput input) */

    public final byte[] write() throws IOException {
        try (ByteArrayOutputStream array = IOHelper.newByteArrayOutput()) {
            try (HOutput output = new HOutput(array)) {
                write(output);
            }
            return array.toByteArray();
        }
    }

    public abstract void write(HOutput output) throws IOException;


    @FunctionalInterface
    public interface Adapter<O extends StreamObject> {
        O convert(HInput input);
    }
}