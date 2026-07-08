package org.takesome.kaylasEngine.gui.adapters.yaml;
/*
import com.google.gson.internal.Primitives;
import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.gui.adapters.FrameAttributesLoader;
import org.takesome.kaylasEngine.gui.components.Attributes;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.*;

public class YamlFrameAttributesLoader implements FrameAttributesLoader {
    protected final Yaml yaml = new Yaml();

    public YamlFrameAttributesLoader() {
    }

    @Override
    public Attributes getAttributes(String framePath) {
        Engine.LOGGER.warn("USING EXPERIMENTAL Yaml ADAPTER");
        try (InputStream inputStream = YamlFrameAttributesLoader.class.getClassLoader().getResourceAsStream(framePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + framePath);
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                return fromYaml(reader, ComponentAttributes.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load frame attributes from path: " + framePath, e);
        }
    }

    public <T> T fromYaml(Reader yamlReader, Class<T> classOfT) throws YAMLException {
        T object = yaml.loadAs(yamlReader, classOfT);
        return Primitives.wrap(classOfT).cast(object);
    }
}
*/