package simplifiedsimulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ScenarioReaderTest {

    private static InputStream xml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsItemsSortedByTime() {
        List<SpawnEvent> events = ScenarioReader.read(xml("""
                <?xml version='1.0' encoding='utf-8'?>
                <Data>
                  <Item><Type>2</Type><StartPoint>8</StartPoint><EndPoint>2</EndPoint><Time>6</Time></Item>
                  <Item><Type>0</Type><StartPoint>1</StartPoint><EndPoint>4</EndPoint><Time>0</Time></Item>
                  <Item><Type>3</Type><StartPoint>18</StartPoint><EndPoint>7</EndPoint><Time> 6 </Time></Item>
                </Data>
                """));

        assertEquals(List.of(
                new SpawnEvent(0, 1, 4, 0),
                new SpawnEvent(2, 8, 2, 6),
                new SpawnEvent(3, 18, 7, 6)), events);
    }

    @Test
    void emptyScenarioHasNoEvents() {
        assertTrue(ScenarioReader.read(xml("<Data/>")).isEmpty());
    }

    @Test
    void rejectsItemWithMissingField() {
        var e = assertThrows(ScenarioReader.ScenarioException.class, () -> ScenarioReader.read(xml(
                "<Data><Item><Type>2</Type><StartPoint>8</StartPoint><Time>0</Time></Item></Data>")));
        assertTrue(e.getMessage().contains("EndPoint"), e.getMessage());
    }

    @Test
    void rejectsNonNumericField() {
        assertThrows(ScenarioReader.ScenarioException.class, () -> ScenarioReader.read(xml(
                "<Data><Item><Type>car</Type><StartPoint>8</StartPoint><EndPoint>2</EndPoint>"
                        + "<Time>0</Time></Item></Data>")));
    }

    @Test
    void rejectsMalformedXml() {
        assertThrows(ScenarioReader.ScenarioException.class, () -> ScenarioReader.read(xml("<Data><Item>")));
    }

    @Test
    void rejectsDoctypeDeclarations() {
        assertThrows(ScenarioReader.ScenarioException.class, () -> ScenarioReader.read(xml("""
                <?xml version="1.0"?>
                <!DOCTYPE Data [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <Data><Item><Type>&x;</Type></Item></Data>
                """)));
    }

    @ParameterizedTest
    @CsvSource({"small, 150", "medium, 400", "large, 1200"})
    void readsBuiltInScenarios(String name, int expectedCount) throws Exception {
        try (InputStream in = ScenarioReaderTest.class.getResourceAsStream("/scenarios/" + name + ".xml")) {
            List<SpawnEvent> events = ScenarioReader.read(in);
            assertEquals(expectedCount, events.size());
        }
    }
}
