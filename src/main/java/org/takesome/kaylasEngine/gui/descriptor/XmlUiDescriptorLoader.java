package org.takesome.kaylasEngine.gui.descriptor;

import com.google.gson.JsonParser;
import org.takesome.kaylasEngine.gui.components.Attributes;
import org.takesome.kaylasEngine.gui.components.Bounds;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.frame.FrameAttributes;
import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;
import org.takesome.kaylasEngine.gui.components.panel.PanelAttributes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** XML UI descriptor loader with style composition and component property support. */
public final class XmlUiDescriptorLoader {
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    public XmlUiDescriptorLoader() {
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature("http://xml.org/sax/features/external-general-entities", false);
        trySetFeature("http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Older XML providers may not expose JAXP access attributes.
        }
    }

    public Attributes load(String framePath) {
        requireXmlResource(framePath);
        try (InputStream inputStream = XmlUiDescriptorLoader.class.getClassLoader().getResourceAsStream(framePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + framePath);
            }
            return parse(inputStream);
        } catch (Exception error) {
            throw new RuntimeException("Failed to load XML UI attributes from path: " + framePath, error);
        }
    }

    private static void requireXmlResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("XML UI resource path must not be blank");
        }
        if (!resourcePath.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new IllegalArgumentException(
                    "KaylasUIEngine supports XML UI descriptors only: " + resourcePath
            );
        }
    }

    public Attributes parse(InputStream inputStream) throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();
        return parseUi(document.getDocumentElement());
    }

    private Attributes parseUi(Element root) throws ReflectiveOperationException {
        Attributes attributes = "frame".equalsIgnoreCase(root.getTagName())
                ? new FrameAttributes()
                : new ComponentAttributes();
        populateAttributes(root, attributes);
        Element panelsElement = firstDirectChild(root, "panels");
        if (panelsElement != null) {
            setField(attributes, "panels", parsePanels(panelsElement));
        }
        Element childrenElement = firstDirectChild(root, "childComponents");
        if (childrenElement != null) {
            setField(attributes, "childComponents", parseComponents(childrenElement));
        }
        return attributes;
    }

    private Map<String, OptionGroups> parsePanels(Element panelsElement) throws ReflectiveOperationException {
        Map<String, OptionGroups> panels = new LinkedHashMap<>();
        for (Element panelElement : directChildren(panelsElement, "panel")) {
            String id = valueOr(panelElement.getAttribute("id"), panelElement.getAttribute("name"));
            if (id != null && !id.isBlank()) {
                panels.put(id, parsePanel(panelElement));
            }
        }
        return panels;
    }

    private OptionGroups parsePanel(Element panelElement) throws ReflectiveOperationException {
        OptionGroups optionGroups = new OptionGroups();
        Element panelOptionsElement = firstDirectChild(panelElement, "panelOptions");
        if (panelOptionsElement != null) {
            setField(optionGroups, "panelOptions", parsePanelOptions(panelOptionsElement));
        }
        Element childComponentsElement = firstDirectChild(panelElement, "childComponents");
        if (childComponentsElement != null) {
            setField(optionGroups, "childComponents", parseComponents(childComponentsElement));
        }
        Element nestedPanelsElement = firstDirectChild(panelElement, "panels");
        if (nestedPanelsElement != null) {
            setField(optionGroups, "panels", parsePanels(nestedPanelsElement));
        }
        return optionGroups;
    }

    private PanelAttributes parsePanelOptions(Element panelOptionsElement) throws ReflectiveOperationException {
        PanelAttributes panelAttributes = new PanelAttributes();
        populateAttributes(panelOptionsElement, panelAttributes);

        Element listenersElement = firstDirectChild(panelOptionsElement, "listeners");
        if (listenersElement != null) {
            List<String> listeners = new ArrayList<>(panelAttributes.getListeners());
            for (Element listenerElement : directChildren(listenersElement, "listener")) {
                String name = valueOr(listenerElement.getAttribute("name"), listenerElement.getTextContent());
                if (name != null && !name.isBlank()) {
                    listeners.add(name.trim());
                }
            }
            setField(panelAttributes, "listeners", listeners);
        }

        Element boundsElement = firstDirectChild(panelOptionsElement, "bounds");
        if (boundsElement != null) {
            setField(panelAttributes, "bounds", parseBounds(boundsElement));
        }
        return panelAttributes;
    }

    private List<ComponentAttributes> parseComponents(Element childComponentsElement) throws ReflectiveOperationException {
        List<ComponentAttributes> components = new ArrayList<>();
        for (Element componentElement : directChildren(childComponentsElement, "component")) {
            components.add(parseComponent(componentElement));
        }
        return components;
    }

    private ComponentAttributes parseComponent(Element componentElement) throws ReflectiveOperationException {
        ComponentAttributes component = new ComponentAttributes();
        populateAttributes(componentElement, component);

        Element boundsElement = firstDirectChild(componentElement, "bounds");
        if (boundsElement != null) {
            setField(component, "bounds", parseBounds(boundsElement));
        }

        Element stylesElement = firstDirectChild(componentElement, "styles");
        if (stylesElement != null) {
            setField(component, "styles", parseTargetStyles(stylesElement));
        }

        Element styleClassesElement = firstDirectChild(componentElement, "styleClasses");
        if (styleClassesElement != null) {
            setField(component, "styleClasses", parseStyleClasses(styleClassesElement));
        }

        Element styleOverridesElement = firstDirectChild(componentElement, "styleOverrides");
        if (styleOverridesElement != null) {
            setField(component, "styleOverrides", parseStringProperties(styleOverridesElement));
        }

        Element propertiesElement = firstDirectChild(componentElement, "properties");
        if (propertiesElement != null) {
            setField(component, "properties", parseObjectProperties(propertiesElement));
        }

        Element scriptsElement = firstDirectChild(componentElement, "scripts");
        if (scriptsElement != null) {
            setField(component, "scripts", parseScripts(scriptsElement));
        }

        Element layoutConfigElement = firstDirectChild(componentElement, "layoutConfig");
        if (layoutConfigElement != null) {
            setField(component, "layoutConfig", parseLayoutConfig(layoutConfigElement));
        }

        Element childComponentsElement = firstDirectChild(componentElement, "childComponents");
        if (childComponentsElement != null) {
            setField(component, "childComponents", parseComponents(childComponentsElement));
        }

        Element nestedPanelsElement = firstDirectChild(componentElement, "panels");
        if (nestedPanelsElement != null) {
            setField(component, "panels", parsePanels(nestedPanelsElement));
        }

        return component;
    }

    private Map<String, String> parseTargetStyles(Element stylesElement) {
        Map<String, String> styles = new LinkedHashMap<>();
        for (Element styleElement : directChildren(stylesElement, "style")) {
            String target = styleElement.getAttribute("target");
            String name = valueOr(styleElement.getAttribute("name"), styleElement.getTextContent());
            if (!target.isBlank() && name != null && !name.isBlank()) {
                styles.put(target.trim(), name.trim());
            }
        }
        return styles;
    }

    private List<String> parseStyleClasses(Element styleClassesElement) {
        List<String> styles = new ArrayList<>();
        for (Element styleElement : directChildElements(styleClassesElement)) {
            String name = valueOr(styleElement.getAttribute("name"), styleElement.getTextContent());
            if (name != null && !name.isBlank()) {
                styles.add(name.trim());
            }
        }
        if (styles.isEmpty() && !styleClassesElement.getTextContent().isBlank()) {
            styles.addAll(splitList(styleClassesElement.getTextContent()));
        }
        return styles;
    }

    private Map<String, String> parseStringProperties(Element parent) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Element property : directChildElements(parent)) {
            String name = valueOr(property.getAttribute("name"), property.getAttribute("key"));
            String value = property.hasAttribute("value")
                    ? property.getAttribute("value")
                    : property.getTextContent();
            if (name != null && !name.isBlank()) {
                properties.put(name.trim(), value == null ? null : value.trim());
            }
        }
        return properties;
    }

    private Map<String, Object> parseObjectProperties(Element parent) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Element property : directChildElements(parent)) {
            String name = valueOr(property.getAttribute("name"), property.getAttribute("key"));
            String value = property.hasAttribute("value")
                    ? property.getAttribute("value")
                    : property.getTextContent();
            if (name != null && !name.isBlank()) {
                properties.put(name.trim(), parseLiteral(value, property.getAttribute("type")));
            }
        }
        return properties;
    }

    private Map<String, String> parseScripts(Element parent) {
        Map<String, String> scripts = new LinkedHashMap<>();
        for (Element script : directChildElements(parent)) {
            String event = valueOr(script.getAttribute("event"), script.getAttribute("name"));
            String path = valueOr(script.getAttribute("path"), script.getTextContent());
            if (event != null && !event.isBlank() && path != null && !path.isBlank()) {
                scripts.put(event.trim(), path.trim());
            }
        }
        return scripts;
    }

    private ComponentAttributes.LayoutConfig parseLayoutConfig(Element layoutConfigElement)
            throws ReflectiveOperationException {
        ComponentAttributes.LayoutConfig layoutConfig = new ComponentAttributes.LayoutConfig();
        for (Element configElement : directChildElements(layoutConfigElement)) {
            setField(layoutConfig, configElement.getTagName(), parseComponentConfig(configElement));
        }
        return layoutConfig;
    }

    private ComponentAttributes.ComponentConfig parseComponentConfig(Element configElement)
            throws ReflectiveOperationException {
        ComponentAttributes.ComponentConfig config = new ComponentAttributes.ComponentConfig();
        setField(config, "x", intAttr(configElement, "x", 0));
        setField(config, "y", intAttr(configElement, "y", 0));
        setField(config, "width", intAttr(configElement, "width", 0));
        setField(config, "height", intAttr(configElement, "height", 0));
        setField(config, "zIndex", intAttr(configElement, "zIndex", 0));
        return config;
    }

    private Bounds parseBounds(Element boundsElement) {
        return new Bounds(
                intAttr(boundsElement, "x", 0),
                intAttr(boundsElement, "y", 0),
                intAttr(boundsElement, "width", 0),
                intAttr(boundsElement, "height", 0)
        );
    }

    private void populateAttributes(Element element, Object instance) throws ReflectiveOperationException {
        for (int index = 0; index < element.getAttributes().getLength(); index++) {
            Attr attribute = (Attr) element.getAttributes().item(index);
            Field field = findField(instance.getClass(), attribute.getName());
            if (field == null) {
                continue;
            }
            field.setAccessible(true);
            field.set(instance, convertValue(field.getType(), attribute.getValue()));
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void setField(Object instance, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(instance.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(instance, value);
        }
    }

    private Object convertValue(Class<?> type, String value) {
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        if (List.class.isAssignableFrom(type)) {
            return splitList(value);
        }
        if (type == Object.class) {
            return inferLiteral(value);
        }
        return value;
    }

    private Object parseLiteral(String rawValue, String explicitType) {
        String value = rawValue == null ? "" : rawValue.trim();
        String type = explicitType == null ? "" : explicitType.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (type) {
                case "boolean", "bool" -> Boolean.parseBoolean(value);
                case "int", "integer" -> Integer.parseInt(value);
                case "long" -> Long.parseLong(value);
                case "float" -> Float.parseFloat(value);
                case "double", "number" -> Double.parseDouble(value);
                case "json" -> JsonParser.parseString(value);
                case "string" -> value;
                default -> inferLiteral(value);
            };
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private Object inferLiteral(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        if (value.matches("[-+]?\\d+")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return Long.parseLong(value);
            }
        }
        if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?")) {
            return Double.parseDouble(value);
        }
        return value;
    }

    private List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String token : value.split("[,\\s]+")) {
            if (!token.isBlank()) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private int intAttr(Element element, String name, int fallback) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private String valueOr(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Element firstDirectChild(Element parent, String tagName) {
        for (Element child : directChildElements(parent)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        for (Element child : directChildElements(parent)) {
            if (tagName.equals(child.getTagName())) {
                elements.add(child);
            }
        }
        return elements;
    }

    private List<Element> directChildElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = parent.getChildNodes();
        for (int index = 0; index < nodeList.getLength(); index++) {
            Node node = nodeList.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private void trySetFeature(String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            // Feature support depends on the JAXP provider.
        }
    }
}
