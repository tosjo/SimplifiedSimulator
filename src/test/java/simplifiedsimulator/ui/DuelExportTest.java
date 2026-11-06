package simplifiedsimulator.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DuelExportTest {

    private static final String HEADER = "movement,label,completed\n";

    @Test
    void mergesBothSidesUnderOneHeaderWithSideColumns() {
        String a = HEADER + "W-T,\"West\",10\nE-T,\"East\",5\n";
        String b = HEADER + "W-T,\"West\",12\n";
        List<String> lines = DuelExport.merge("Vehicle actuated", a, "Fixed time", b).lines().toList();
        assertEquals(List.of(
                "side,controller,movement,label,completed",
                "A,\"Vehicle actuated\",W-T,\"West\",10",
                "A,\"Vehicle actuated\",E-T,\"East\",5",
                "B,\"Fixed time\",W-T,\"West\",12"), lines);
    }

    @Test
    void blankLinesAreSkipped() {
        String a = HEADER + "\nW-T,\"West\",10\n\n";
        assertEquals(2, DuelExport.merge("A", a, "B", HEADER).lines().count());
    }

    @Test
    void sidesWithoutRowsGiveJustTheHeader() {
        assertEquals("side,controller,movement,label,completed\n", DuelExport.merge("A", HEADER, "B", HEADER));
    }

    @Test
    void quotesInControllerNamesAreEscaped() {
        String merged = DuelExport.merge("My \"best\" one", HEADER + "W-T,\"West\",1\n", "B", HEADER);
        assertEquals("A,\"My \"\"best\"\" one\",W-T,\"West\",1", merged.lines().toList().get(1));
    }
}
