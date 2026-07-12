package com.pepero.peperobot.tuning;

import java.util.List;

public class TexelGradientTuner {

    private static final double OPENING_PHASE_SCORE = 6192.0;

    // ---- 국면 하나의 eval(params) 계산 ----
    public static double evalFromFeatures(PositionFeatures pf, ParamSet p) {
        double linearOp = 0, linearEg = 0;

        for (int[] f : pf.materialFeatures) {
            int piece = f[0], delta = f[1];
            linearOp += delta * p.matOp[piece];
            linearEg += delta * p.matEg[piece];
        }
        for (int[] f : pf.pstFeatures) {
            int piece = f[0], sq = f[1], delta = f[2];
            linearOp += delta * p.pstOp[piece][sq];
            linearEg += delta * p.pstEg[piece][sq];
        }

        double scoreOpening = linearOp + pf.residualOpening;
        double scoreEndgame = linearEg + pf.residualEndgame;

        // gamePhase: 0=OPENING, 1=ENDGAME, 2=MIDDLEGAME (Evaluate.RawScores 기준과 동일)
        if (pf.gamePhase == 2) {
            double wOp = pf.gamePhaseScore / OPENING_PHASE_SCORE;
            double wEg = 1.0 - wOp;
            return wOp * scoreOpening + wEg * scoreEndgame;
        } else if (pf.gamePhase == 0) {
            return scoreOpening;
        } else {
            return scoreEndgame;
        }
    }

    private static double sigmoid(double eval, double K) {
        return 1.0 / (1.0 + Math.pow(10, -K * eval / 400.0));
    }

    public static double computeError(List<PositionFeatures> data, ParamSet p, double K) {
        return data.parallelStream()
                .mapToDouble(pf -> {
                    double eval = evalFromFeatures(pf, p);
                    double predicted = sigmoid(eval, K);
                    double diff = pf.result - predicted;
                    return diff * diff;
                })
                .sum() / data.size();
    }

    public static double findBestK(List<PositionFeatures> data, ParamSet p) {
        double bestK = 1.0;
        double bestError = computeError(data, p, bestK);
        double step = 0.1;
        while (step > 0.0001) {
            boolean improved = true;
            while (improved) {
                improved = false;
                double upErr = computeError(data, p, bestK + step);
                if (upErr < bestError) { bestK += step; bestError = upErr; improved = true; continue; }
                if (bestK - step > 0) {
                    double downErr = computeError(data, p, bestK - step);
                    if (downErr < bestError) { bestK -= step; bestError = downErr; improved = true; }
                }
            }
            step /= 10;
        }
        System.out.println("Best K = " + bestK + " (error=" + bestError + ")");
        return bestK;
    }

    // ---- Adam 옵티마이저로 그래디언트 디센트 ----
    public static void train(List<PositionFeatures> trainData, List<PositionFeatures> testData,
                              ParamSet params, double K, int epochs, double learningRate) {

        double beta1 = 0.9, beta2 = 0.999, eps = 1e-8;
        double lnK400 = Math.log(10) * K / 400.0;

        // Adam moment 벡터 (params와 동일한 shape)
        double[] mMatOp = new double[5], vMatOp = new double[5];
        double[] mMatEg = new double[5], vMatEg = new double[5];
        double[][] mPstOp = new double[5][64], vPstOp = new double[5][64];
        double[][] mPstEg = new double[5][64], vPstEg = new double[5][64];

        int t = 0;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gMatOp = new double[5], gMatEg = new double[5];
            double[][] gPstOp = new double[5][64], gPstEg = new double[5][64];

            // ---- 그래디언트 누적 (병렬 처리) ----
            double[][] gMatOpAcc = trainData.parallelStream()
                    .map(pf -> {
                        double[] localMatOp = new double[5], localMatEg = new double[5];
                        double[][] localPstOp = new double[5][64], localPstEg = new double[5][64];

                        double eval = evalFromFeatures(pf, params);
                        double predicted = sigmoid(eval, K);
                        double dError_dEval = -2.0 * (pf.result - predicted)
                                * predicted * (1 - predicted) * lnK400;

                        double wOp, wEg;
                        if (pf.gamePhase == 2) {
                            wOp = pf.gamePhaseScore / OPENING_PHASE_SCORE;
                            wEg = 1.0 - wOp;
                        } else if (pf.gamePhase == 0) {
                            wOp = 1.0; wEg = 0.0;
                        } else {
                            wOp = 0.0; wEg = 1.0;
                        }

                        for (int[] f : pf.materialFeatures) {
                            int piece = f[0], delta = f[1];
                            localMatOp[piece] += dError_dEval * wOp * delta;
                            localMatEg[piece] += dError_dEval * wEg * delta;
                        }
                        for (int[] f : pf.pstFeatures) {
                            int piece = f[0], sq = f[1], delta = f[2];
                            localPstOp[piece][sq] += dError_dEval * wOp * delta;
                            localPstEg[piece][sq] += dError_dEval * wEg * delta;
                        }

                        // 4개 배열을 하나로 합쳐서 리턴 (병렬 reduce 편하게)
                        return new double[][]{localMatOp, localMatEg,
                                flatten(localPstOp), flatten(localPstEg)};
                    })
                    .reduce(
                            new double[][]{new double[5], new double[5], new double[5*64], new double[5*64]},
                            (a, b) -> {
                                double[][] r = new double[4][];
                                for (int i = 0; i < 4; i++) {
                                    r[i] = new double[a[i].length];
                                    for (int j = 0; j < a[i].length; j++) r[i][j] = a[i][j] + b[i][j];
                                }
                                return r;
                            }
                    );

            gMatOp = gMatOpAcc[0];
            gMatEg = gMatOpAcc[1];
            gPstOp = unflatten(gMatOpAcc[2]);
            gPstEg = unflatten(gMatOpAcc[3]);

            int n = trainData.size();
            t++;

            // ---- Adam 업데이트 ----
            for (int i = 0; i < 5; i++) {
                params.matOp[i] -= adamStep(gMatOp[i] / n, mMatOp, vMatOp, i, t, beta1, beta2, eps, learningRate);
                params.matEg[i] -= adamStep(gMatEg[i] / n, mMatEg, vMatEg, i, t, beta1, beta2, eps, learningRate);
                for (int sq = 0; sq < 64; sq++) {
                    params.pstOp[i][sq] -= adamStep(gPstOp[i][sq] / n, mPstOp[i], vPstOp[i], sq, t, beta1, beta2, eps, learningRate);
                    params.pstEg[i][sq] -= adamStep(gPstEg[i][sq] / n, mPstEg[i], vPstEg[i], sq, t, beta1, beta2, eps, learningRate);
                }
            }

            if (epoch % 5 == 0 || epoch == epochs - 1) {
                double trainErr = computeError(trainData, params, K);
                double testErr = computeError(testData, params, K);
                System.out.println("epoch=" + epoch + " trainErr=" + trainErr + " testErr=" + testErr);
            }
        }
    }

    private static double adamStep(double grad, double[] m, double[] v, int idx, int t,
                                    double beta1, double beta2, double eps, double lr) {
        m[idx] = beta1 * m[idx] + (1 - beta1) * grad;
        v[idx] = beta2 * v[idx] + (1 - beta2) * grad * grad;
        double mHat = m[idx] / (1 - Math.pow(beta1, t));
        double vHat = v[idx] / (1 - Math.pow(beta2, t));
        return lr * mHat / (Math.sqrt(vHat) + eps);
    }

    private static double[] flatten(double[][] arr) {
        double[] out = new double[arr.length * arr[0].length];
        int idx = 0;
        for (double[] row : arr) for (double val : row) out[idx++] = val;
        return out;
    }

    private static double[][] unflatten(double[] arr) {
        double[][] out = new double[5][64];
        int idx = 0;
        for (int i = 0; i < 5; i++) for (int j = 0; j < 64; j++) out[i][j] = arr[idx++];
        return out;
    }
}