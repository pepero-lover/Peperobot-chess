package com.pepero.peperobot.tuning;

import com.pepero.peperobot.evaluation.Evaluate;

public class ParamSet {
    public double[] matOp = new double[5];
    public double[] matEg = new double[5];
    public double[][] pstOp = new double[5][64];
    public double[][] pstEg = new double[5][64];

    // Evaluate.java의 material_score / positional_score getter를 이용해 초기화
    // (PST는 배열이 private이므로 getter를 하나 더 추가해야 합니다 - 아래 참고)
    public static ParamSet loadFromEvaluate() {
        ParamSet ps = new ParamSet();
        for (int i = 0; i < 5; i++) {
            ps.matOp[i] = Evaluate.getMaterialScore(0, i);
            ps.matEg[i] = Evaluate.getMaterialScore(1, i);
            for (int sq = 0; sq < 64; sq++) {
                ps.pstOp[i][sq] = Evaluate.positional_score[0][i][sq];
                ps.pstEg[i][sq] = Evaluate.positional_score[1][i][sq];
            }
        }
        return ps;
    }

    public void applyToEvaluate() {
        for (int i = 0; i < 5; i++) {
            Evaluate.setMaterialScore(0, i, (int) Math.round(matOp[i]));
            Evaluate.setMaterialScore(1, i, (int) Math.round(matEg[i]));
            for (int sq = 0; sq < 64; sq++) {
                Evaluate.positional_score[0][i][sq] = (int) Math.round(pstOp[i][sq]);
                Evaluate.positional_score[1][i][sq] = (int) Math.round(pstEg[i][sq]);
            }
        }
    }
}