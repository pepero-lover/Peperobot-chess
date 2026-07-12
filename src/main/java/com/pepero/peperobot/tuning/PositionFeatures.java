package com.pepero.peperobot.tuning;

public class PositionFeatures {
    public int[][] materialFeatures; // 각 원소 = {pieceIdx(0~4), delta(+1/-1)}
    public int[][] pstFeatures;      // 각 원소 = {pieceIdx(0~4), squareIdx(0~63), delta(+1/-1)}
    public int residualOpening;
    public int residualEndgame;
    public int gamePhaseScore;
    public int gamePhase; // 0=OPENING, 1=ENDGAME, 2=MIDDLEGAME
    public double result;
    public boolean whiteToMove;
}