package org.takesome.kaylasEngine.gui.adapters.json5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.adapters.FrameAttributesLoader;
import org.takesome.kaylasEngine.gui.components.Attributes;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class Json5FrameAttributesLoader implements FrameAttributesLoader {
    private final Gson gson;

    public Json5FrameAttributesLoader() {
        this.gson = new GsonBuilder()
                .setLenient() // Позволяет использовать нестрогий синтаксис JSON5
                .create();
    }

    @Override
    public Attributes getAttributes(String framePath) {
        Attributes attributes = null;
        try (InputStream inputStream = Json5FrameAttributesLoader.class.getClassLoader().getResourceAsStream(framePath)) {
            if (inputStream == null) {
                String message = "Resource not found: " + framePath;
                Engine.LOGGER.error(message);
                throw new FileNotFoundException(message);
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                attributes = gson.fromJson(reader, ComponentAttributes.class);
                validateAttributes(attributes);
            }
        } catch (JsonParseException e) {
            Engine.LOGGER.warn("Failed to parse JSON5 file: " + framePath + "{}", e);
            throw new RuntimeException("Failed to parse JSON5 attributes from path: " + framePath, e);
        } catch (IOException e) {
            Engine.LOGGER.warn("IOException occurred while loading frame attributes from path: " + framePath, e);
            throw new RuntimeException("Failed to load frame attributes from path: " + framePath, e);
        }
        return attributes;
    }

    private void validateAttributes(Attributes attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Loaded attributes are null");
        }
        if (attributes.getChildComponents() == null || attributes.getChildComponents().isEmpty()) {
            //throw new IllegalArgumentException("Attributes must contain at least one child component");
        }
    }
}
