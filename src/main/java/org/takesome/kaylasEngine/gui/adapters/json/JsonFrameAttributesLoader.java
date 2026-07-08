package org.takesome.kaylasEngine.gui.adapters.json;

import com.google.gson.Gson;
import org.takesome.kaylasEngine.gui.adapters.FrameAttributesLoader;
import org.takesome.kaylasEngine.gui.components.Attributes;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonFrameAttributesLoader implements FrameAttributesLoader {
    private final Gson gson = new Gson();

    @Override
    public Attributes getAttributes(String framePath) {
        try (InputStream inputStream = JsonFrameAttributesLoader.class.getClassLoader().getResourceAsStream(framePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + framePath);
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                return gson.fromJson(reader, ComponentAttributes.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load frame attributes from path: " + framePath, e);
        }
    }

}
