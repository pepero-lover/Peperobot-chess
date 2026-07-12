package com.pepero.peperobot.tuning;

import com.pepero.jcb.core.Chessboard;
import com.pepero.peperobot.evaluation.Evaluate;

import java.util.Collections;
import java.util.List;

public class TexelGradientTunerMain {

    public static void main(String[] args) throws Exception {
        List<EpdParser.TrainingPosition> data = EpdParser.load("engine tuning/quiet-labeled.epd");
        data = data.subList(0, 450000);
        Collections.shuffle(data, new java.util.Random(42)); // train/test 나누기 전 셔플

        List<PositionFeatures> features = FeatureExtractor.extractAll(data);

        int splitIdx = (int) (features.size() * 0.8);
        List<PositionFeatures> train = features.subList(0, splitIdx);
        List<PositionFeatures> test = features.subList(splitIdx, features.size());

        ParamSet params = ParamSet.loadFromEvaluate();

        // ---- 검증: 초기 파라미터로 계산한 eval이 Evaluate.evaluate()와 일치하는지 ----
        sanityCheck(data, features, params);

        double K = TexelGradientTuner.findBestK(train, params);

        System.out.println("=== 학습 시작 ===");
        TexelGradientTuner.train(train, test, params, K, 100, 1.0); // epoch=100, lr=1.0 (Adam이라 크게 잡아도 안정적)

        renormalize(params);

        System.out.println("=== 최종 파라미터 ===");
        printParams(params);

        exportPst(params);

        // params.applyToEvaluate(); // 실제로 반영하려면 주석 해제
    }


    private static void debugBreakdown(EpdParser.TrainingPosition pos, PositionFeatures pf, ParamSet params) {
        Chessboard board = new Chessboard(pos.fen);

        // 1) 원본 evaluate()가 실제로 만든 값 (기준점)
        Evaluate.RawScores full = Evaluate.computeRawScores(board);
        System.out.println("== 원본 ==");
        System.out.println("full.scoreOpening = " + full.scoreOpening);
        System.out.println("full.scoreEndgame = " + full.scoreEndgame);
        System.out.println("full.gamePhaseScore = " + full.gamePhaseScore);
        System.out.println("full.gamePhase = " + full.gamePhase);

        // 2) feature 기반 material+PST 기여분만 따로 계산
        double linearOp = 0, linearEg = 0;
        for (int[] f : pf.materialFeatures) {
            linearOp += f[1] * params.matOp[f[0]];
            linearEg += f[1] * params.matEg[f[0]];
        }
        for (int[] f : pf.pstFeatures) {
            linearOp += f[2] * params.pstOp[f[0]][f[1]];
            linearEg += f[2] * params.pstEg[f[0]][f[1]];
        }
        System.out.println("== feature 기반 ==");
        System.out.println("linearOp = " + linearOp);
        System.out.println("linearEg = " + linearEg);
        System.out.println("pf.residualOpening = " + pf.residualOpening);
        System.out.println("pf.residualEndgame = " + pf.residualEndgame);
        System.out.println("linearOp + residualOpening = " + (linearOp + pf.residualOpening));
        System.out.println("linearEg + residualEndgame = " + (linearEg + pf.residualEndgame));

        System.out.println("== 차이 ==");
        System.out.println("scoreOpening 차이 = " + (full.scoreOpening - (linearOp + pf.residualOpening)));
        System.out.println("scoreEndgame 차이 = " + (full.scoreEndgame - (linearEg + pf.residualEndgame)));
    }

    private static void sanityCheck(List<EpdParser.TrainingPosition> data,
                                     List<PositionFeatures> features, ParamSet params) {
        for (int i = 0; i < 20; i++) {
            var board = new com.pepero.jcb.core.Chessboard(data.get(i).fen);
            double featureEval = TexelGradientTuner.evalFromFeatures(features.get(i), params);
            var raw = com.pepero.peperobot.evaluation.Evaluate.computeRawScores(board);
            double directEval;
            if (raw.gamePhase == 2) {
                double wOp = raw.gamePhaseScore / 6192.0;
                directEval = wOp * raw.scoreOpening + (1 - wOp) * raw.scoreEndgame;
            } else if (raw.gamePhase == 0) {
                directEval = raw.scoreOpening;
            } else {
                directEval = raw.scoreEndgame;
            }
            double diff = Math.abs(featureEval - directEval);
            if (diff > 0.01) {
                System.out.println("⚠️ MISMATCH at index " + i + ": feature=" + featureEval + " direct=" + directEval);
            }
        }
        System.out.println("Sanity check 완료 (mismatch 있으면 위에 경고 출력됨)");
    }

    private static void printParams(ParamSet p) {
        String[] names = {"PAWN", "KNIGHT", "BISHOP", "ROOK", "QUEEN"};
        for (int i = 0; i < 5; i++) {
            System.out.printf("%s_OP = %.1f, %s_EG = %.1f%n", names[i], p.matOp[i], names[i], p.matEg[i]);
        }

        System.out.println("PAWN PST OP (e4=28, d4=27): " + p.pstOp[0][28] + ", " + p.pstOp[0][27]);
        System.out.println("KNIGHT PST OP (center avg): " +
                (p.pstOp[1][27]+p.pstOp[1][28]+p.pstOp[1][35]+p.pstOp[1][36])/4.0);
        // PST는 양이 많으니 필요하면 파일로 export하는 게 낫습니다
    }

    private static void exportPst(ParamSet p) {
        String[] names = {"pawn", "knight", "bishop", "rook", "queen"};
        System.out.println("=== OPENING ===");
        for (int i = 0; i < 5; i++) {
            System.out.println("// " + names[i]);
            for (int r = 0; r < 8; r++) {
                StringBuilder sb = new StringBuilder("        ");
                for (int f = 0; f < 8; f++) {
                    sb.append(Math.round(p.pstOp[i][r*8+f])).append(", ");
                }
                System.out.println(sb);
            }
        }
        System.out.println("=== ENDGAME ===");
        for (int i = 0; i < 5; i++) {
            System.out.println("// " + names[i]);
            for (int r = 0; r < 8; r++) {
                StringBuilder sb = new StringBuilder("        ");
                for (int f = 0; f < 8; f++) {
                    sb.append(Math.round(p.pstEg[i][r*8+f])).append(", ");
                }
                System.out.println(sb);
            }
        }
    }

    public static void renormalize(ParamSet p) {
        for (int piece = 0; piece < 5; piece++) {
            double meanOp = mean(p.pstOp[piece]);
            double meanEg = mean(p.pstEg[piece]);

            p.matOp[piece] += meanOp;
            p.matEg[piece] += meanEg;

            for (int sq = 0; sq < 64; sq++) {
                p.pstOp[piece][sq] -= meanOp;
                p.pstEg[piece][sq] -= meanEg;
            }
        }
    }

    private static double mean(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }
}