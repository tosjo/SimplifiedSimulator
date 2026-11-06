package simplifiedsimulator.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads scenario files. A scenario is a flat list of spawn events:
 *
 * <pre>{@code
 * <Data>
 *   <Item>
 *     <Type>2</Type>
 *     <StartPoint>8</StartPoint>
 *     <EndPoint>2</EndPoint>
 *     <Time>0</Time>
 *   </Item>
 *   ...
 * </Data>
 * }</pre>
 */
public final class ScenarioReader {

    private ScenarioReader() {
    }

    /**
     * Parses a scenario and returns its events ordered by spawn time.
     *
     * @throws ScenarioException if the document is not well-formed or an item is incomplete
     */
    public static List<SpawnEvent> read(InputStream input) {
        Document document = parse(input);
        NodeList items = document.getElementsByTagName("Item");
        List<SpawnEvent> events = new ArrayList<>(items.getLength());

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            events.add(new SpawnEvent(
                    intChild(item, "Type", i),
                    intChild(item, "StartPoint", i),
                    intChild(item, "EndPoint", i),
                    intChild(item, "Time", i)));
        }

        // Stable sort, so events with the same time keep their order from the file.
        events.sort(Comparator.comparingInt(SpawnEvent::time));
        return List.copyOf(events);
    }

    private static Document parse(InputStream input) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Scenario files never need DTDs or external entities.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(input);
            document.getDocumentElement().normalize();
            return document;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ScenarioException("Could not read scenario: " + e.getMessage(), e);
        }
    }

    private static int intChild(Element item, String tag, int index) {
        NodeList nodes = item.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            throw new ScenarioException("Item " + index + " has no <" + tag + "> element");
        }
        String text = nodes.item(0).getTextContent().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ScenarioException("Item " + index + ": <" + tag + "> is not a number: '" + text + "'", e);
        }
    }

    /** Thrown when a scenario file cannot be read. */
    public static final class ScenarioException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ScenarioException(String message) {
            super(message);
        }

        ScenarioException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
