package org.takesome.kaylasEngine.gui.adapters.xml;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;

public class XMLParser {

    public <T> T fromXml(Reader xml, Class<T> classOfT) throws XmlSyntaxException, XmlIOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(xml));
            Element root = doc.getDocumentElement();
            return parseElement(root, classOfT);
        } catch (Exception e) {
            throw new XmlIOException(e);
        }
    }

    private <T> T parseElement(Element element, Class<T> classOfT) throws XmlSyntaxException, XmlIOException {
        try {
            T object = classOfT.getDeclaredConstructor().newInstance();

            // Set attributes
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                Field field = getField(classOfT, attr.getNodeName());
                if (field != null) {
                    field.setAccessible(true);
                    field.set(object, convertValue(attr.getNodeValue(), field.getType()));
                }
            }

            // Set nested elements (components)
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    Object childObject = parseElement(childElement, Object.class);

                    // Add child object to components list
                    Field componentsField = getField(classOfT, "components");
                    if (componentsField != null) {
                        componentsField.setAccessible(true);
                        List<Object> components = (List<Object>) componentsField.get(object);
                        if (components == null) {
                            components = new ArrayList<>();
                            componentsField.set(object, components);
                        }
                        components.add(childObject);
                    }
                }
            }

            return object;
        } catch (Exception e) {
            throw new XmlIOException(e);
        }
    }

    private Field getField(Class<?> classOfT, String fieldName) {
        try {
            return classOfT.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private Object convertValue(String value, Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else {
            return value;
        }
    }

    // Custom Exception classes for XML parsing
    public static class XmlIOException extends IOException {
        public XmlIOException(Throwable cause) {
            super(cause);
        }
    }

    public static class XmlSyntaxException extends Exception {
        public XmlSyntaxException(String message) {
            super(message);
        }
    }
}
