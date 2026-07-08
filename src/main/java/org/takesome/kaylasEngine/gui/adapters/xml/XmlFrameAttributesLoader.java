package org.takesome.kaylasEngine.gui.adapters.xml;

import org.takesome.kaylasEngine.gui.adapters.FrameAttributesLoader;
import org.takesome.kaylasEngine.gui.components.Attributes;
import org.takesome.kaylasEngine.gui.components.Bounds;
import org.takesome.kaylasEngine.gui.components.ComponentAttributes;
import org.takesome.kaylasEngine.gui.components.frame.OptionGroups;
import org.takesome.kaylasEngine.gui.components.panel.PanelAttributes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlFrameAttributesLoader implements FrameAttributesLoader {
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    public XmlFrameAttributesLoader() {
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(false);
    }

    @Override
    public Attributes getAttributes(String framePath) {
        try (InputStream inputStream = XmlFrameAttributesLoader.class.getClassLoader().getResourceAsStream(framePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + framePath);
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            return parseUi(document.getDocumentElement());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load XML UI attributes from path: " + framePath, e);
        }
    }

    private Attributes parseUi(Element root) throws ReflectiveOperationException {
        ComponentAttributes attributes = new ComponentAttributes();
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
            if (id == null || id.isBlank()) {
                continue;
            }
            panels.put(id, parsePanel(panelElement));
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
            setField(component, "styles", parseStyles(stylesElement));
        }

        Element layoutConfigElement = firstDirectChild(componentElement, "layoutConfig");
        if (layoutConfigElement != null) {
            setField(component, "layoutConfig", parseLayoutConfig(layoutConfigElement));
        }

        Element childComponentsElement = firstDirectChild(componentElement, "childComponents");
        if (childComponentsElement != null) {
            setField(component, "childComponents", parseComponents(childComponentsElement));
        }

        return component;
    }

    private Map<String, String> parseStyles(Element stylesElement) {
        Map<String, String> styles = new LinkedHashMap<>();
        for (Element styleElement : directChildren(stylesElement, "style")) {
            String target = styleElement.getAttribute("target");
            String name = styleElement.getAttribute("name");
            if (!target.isBlank() && !name.isBlank()) {
                styles.put(target, name);
            }
        }
        return styles;
    }

    private ComponentAttributes.LayoutConfig parseLayoutConfig(Element layoutConfigElement) throws ReflectiveOperationException {
        ComponentAttributes.LayoutConfig layoutConfig = new ComponentAttributes.LayoutConfig();
        for (Element configElement : directChildElements(layoutConfigElement)) {
            ComponentAttributes.ComponentConfig componentConfig = parseComponentConfig(configElement);
            setField(layoutConfig, configElement.getTagName(), componentConfig);
        }
        return layoutConfig;
    }

    private ComponentAttributes.ComponentConfig parseComponentConfig(Element configElement) throws ReflectiveOperationException {
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
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attribute = (Attr) element.getAttributes().item(i);
            String attributeName = attribute.getName();
            if ("id".equals(attributeName) && findField(instance.getClass(), "id") == null) {
                continue;
            }
            Field field = findField(instance.getClass(), attributeName);
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
        if (field == null) {
            return;
        }
        field.setAccessible(true);
        field.set(instance, value);
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
        return value;
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
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }
}
