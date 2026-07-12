package com.pepero.peperobot.tuning;

import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.EncodedPieces;
import com.pepero.jcb.core.Chessboard;
import com.pepero.peperobot.evaluation.Evaluate;

import java.util.ArrayList;
import java.util.List;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class FeatureExtractor {

    public static List<PositionFeatures> extractAll(List<EpdParser.TrainingPosition> data) {
        List<PositionFeatures> result = new ArrayList<>(data.size());
        List<Chessboard> boards = new ArrayList<>(data.size());

        System.out.println("Pass A: game phase + sparse feature 추출...");
        for (EpdParser.TrainingPosition pos : data) {
            Chessboard board = new Chessboard(pos.fen);
            boards.add(board);

            Evaluate.RawScores full = Evaluate.computeRawScores(board);

            PositionFeatures pf = new PositionFeatures();
            pf.gamePhaseScore = full.gamePhaseScore;
            pf.gamePhase = full.gamePhase;
            pf.result = pos.result;
            pf.whiteToMove = pos.whiteToMove;

            extractSparseFeatures(board, pf);
            result.add(pf);
        }

        System.out.println("Pass B: residual 계산 중 (material/PST 0으로 고정)...");
        Evaluate.zeroTunedMaterialAndPst();
        try {
            for (int i = 0; i < data.size(); i++) {
                Evaluate.RawScores residual = Evaluate.computeRawScores(boards.get(i));
                result.get(i).residualOpening = residual.scoreOpening;
                result.get(i).residualEndgame = residual.scoreEndgame;
            }
        } finally {
            Evaluate.restoreTunedMaterialAndPst();
        }

        System.out.println("Feature 추출 완료: " + result.size() + "개");
        return result;
    }
    // NOTE: EncodedPieces 순서가 P,N,B,R,Q,K,p,n,b,r,q,k = 0..11 이라고 가정합니다.
    // 실제 프로젝트 EncodedPieces 정의와 다르면 이 부분 인덱스 매핑을 맞춰주세요.
    private static void extractSparseFeatures(Chessboard board, PositionFeatures pf) {
        List<int[]> materialFeatures = new ArrayList<>();
        List<int[]> pstFeatures = new ArrayList<>();

        for (int bbPiece = P; bbPiece <= k; bbPiece++) {
            if (bbPiece == K || bbPiece == k) continue; // king 제외

            boolean isWhite = bbPiece <= Q; // P..Q(0~4) = 백
            int pieceType = isWhite ? bbPiece : bbPiece - 6; // 0~4로 정규화
            int delta = isWhite ? 1 : -1;

            long bitboard = board.bitboards[bbPiece];
            while (bitboard != 0) {
                int square = BitBoardUtils.getLS1BIndex(bitboard);
                int pstIdx = isWhite ? square : (square ^ 56);

                materialFeatures.add(new int[]{pieceType, delta});
                pstFeatures.add(new int[]{pieceType, pstIdx, delta});

                bitboard = BitBoardUtils.popBit(bitboard, square);
            }
        }

        pf.materialFeatures = materialFeatures.toArray(new int[0][]);
        pf.pstFeatures = pstFeatures.toArray(new int[0][]);
    }
}