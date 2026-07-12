package com.pepero.peperobot.tuning;

import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.core.Chessboard;
import com.pepero.peperobot.evaluation.Evaluate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpdParser {

    // c9 "1-0"; / c9 "0-1"; / c9 "1/2-1/2"; 형태의 result opcode 추출
    private static final Pattern RESULT_PATTERN =
            Pattern.compile("c9\\s+\"([^\"]+)\"");

    public static class TrainingPosition {
        public final String fen;
        public final double result;      // 1.0=백승, 0.5=무, 0.0=흑승 (항상 백 기준)
        public final boolean whiteToMove;

        public TrainingPosition(String fen, double result, boolean whiteToMove) {
            this.fen = fen;
            this.result = result;
            this.whiteToMove = whiteToMove;
        }
    }

    public static List<TrainingPosition> load(String path) throws IOException {
        List<TrainingPosition> positions = new ArrayList<>();
        int lineNo = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    TrainingPosition pos = parseLine(line);
                    if (pos != null) {
                        positions.add(pos);
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }
        }

        System.out.println("Loaded " + positions.size() + " positions, skipped " + skipped
                + " (total lines: " + lineNo + ")");
        return positions;
    }

    private static TrainingPosition parseLine(String line) {
        Matcher m = RESULT_PATTERN.matcher(line);
        if (!m.find()) return null;
        Double result = parseResult(m.group(1));
        if (result == null) return null;

        String fen = extractFenPrefix(line);
        if (fen == null) return null;

        try {
            FENValidator.validateString(fen);
        } catch (Exception e) {
            return null;
        }

        boolean whiteToMove = fen.split("\\s+")[1].equals("w");

        try {
            Chessboard chessboard = new Chessboard(fen);
            FENValidator.validateLogicalState(chessboard);
        } catch (Exception e) {
            return null;
        }

        return new TrainingPosition(fen, result, whiteToMove);
    }

    private static String extractFenPrefix(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 4) return null;

        // board turn castling enpassant, 4개만 사용
        return tokens[0] + " " + tokens[1] + " " + tokens[2] + " " + tokens[3];
    }

    private static Double parseResult(String resultStr) {
        switch (resultStr.trim()) {
            case "1-0":
                return 1.0;
            case "0-1":
                return 0.0;
            case "1/2-1/2":
            case "1/2":
                return 0.5;
            default:
                return null;
        }
    }
}