package simplifiedsimulator.ui;

import java.util.List;

/** Combines the statistics CSV of both duel sides into one file. */
public final class DuelExport {

    private DuelExport() {
    }

    /**
     * @param csvA statistics of side A, as written by {@code Statistics.writeMovementsCsv}
     * @param csvB the same for side B; its header is dropped
     * @return one CSV with {@code side} and {@code controller} columns in front
     */
    public static String merge(String nameA, String csvA, String nameB, String csvB) {
        List<String> a = nonBlankLines(csvA);
        List<String> b = nonBlankLines(csvB);
        StringBuilder out = new StringBuilder("side,controller,").append(a.isEmpty() ? "" : a.get(0)).append('\n');
        append(out, "A", nameA, a);
        append(out, "B", nameB, b);
        return out.toString();
    }

    private static void append(StringBuilder out, String side, String name, List<String> lines) {
        String prefix = side + ",\"" + name.replace("\"", "\"\"") + "\",";
        for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
            out.append(prefix).append(line).append('\n');
        }
    }

    private static List<String> nonBlankLines(String csv) {
        return csv.lines().filter(l -> !l.isBlank()).toList();
    }
}
