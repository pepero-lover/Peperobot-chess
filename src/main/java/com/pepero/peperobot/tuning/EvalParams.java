package com.pepero.peperobot.tuning;

import com.pepero.peperobot.evaluation.Evaluate;

public class EvalParams {
    public static final int BISHOP_PAIR_OP = 0;
    public static final int BISHOP_PAIR_EG = 1;
    public static final int BISHOP_MOB_OP  = 2;
    public static final int BISHOP_MOB_EG  = 3;
    public static final int KNIGHT_MOB_OP  = 4;
    public static final int KNIGHT_MOB_EG  = 5;
    public static final int QUEEN_MOB_OP   = 6;
    public static final int QUEEN_MOB_EG   = 7;
    public static final int SPACE_UNIT     = 8;
    public static final int UNDEV_PENALTY  = 9;
    public static final int CENTER_DUO     = 10;
    public static final int CENTRAL_HOLE   = 11;

    public static final int NUM_PARAMS = 12;

    public static final String[] NAMES = {
            "BISHOP_PAIR_OP", "BISHOP_PAIR_EG",
            "BISHOP_MOB_OP", "BISHOP_MOB_EG",
            "KNIGHT_MOB_OP", "KNIGHT_MOB_EG",
            "QUEEN_MOB_OP", "QUEEN_MOB_EG",
            "SPACE_UNIT",
            "UNDEV_PENALTY", "CENTER_DUO", "CENTRAL_HOLE"
    };

    public static double[] load() {
        double[] v = new double[NUM_PARAMS];
        v[BISHOP_PAIR_OP] = Evaluate.bishop_pair_bonus_opening;
        v[BISHOP_PAIR_EG] = Evaluate.bishop_pair_bonus_endgame;
        v[BISHOP_MOB_OP]  = Evaluate.bishop_mobility_opening;
        v[BISHOP_MOB_EG]  = Evaluate.bishop_mobility_endgame;
        v[KNIGHT_MOB_OP]  = Evaluate.knight_mobility_opening;
        v[KNIGHT_MOB_EG]  = Evaluate.knight_mobility_endgame;
        v[QUEEN_MOB_OP]   = Evaluate.queen_mobility_opening;
        v[QUEEN_MOB_EG]   = Evaluate.queen_mobility_endgame;
        v[SPACE_UNIT]     = Evaluate.space_unit;
        v[UNDEV_PENALTY]  = Evaluate.UNDEVELOPED_MINOR_PENALTY;
        v[CENTER_DUO]     = Evaluate.CENTER_PAWN_DUO_BONUS;
        v[CENTRAL_HOLE]   = Evaluate.CENTRAL_FILE_HOLE_PENALTY;
        return v;
    }

    public static void apply(double[] v) {
        Evaluate.bishop_pair_bonus_opening = (int) Math.round(v[BISHOP_PAIR_OP]);
        Evaluate.bishop_pair_bonus_endgame = (int) Math.round(v[BISHOP_PAIR_EG]);

        Evaluate.bishop_mobility_opening = Math.max(0, (int) Math.round(v[BISHOP_MOB_OP]));
        Evaluate.bishop_mobility_endgame = Math.max(0, (int) Math.round(v[BISHOP_MOB_EG]));

        Evaluate.knight_mobility_opening = Math.max(0, (int) Math.round(v[KNIGHT_MOB_OP]));
        Evaluate.knight_mobility_endgame = Math.max(0, (int) Math.round(v[KNIGHT_MOB_EG]));

        Evaluate.queen_mobility_opening = Math.max(0, (int) Math.round(v[QUEEN_MOB_OP]));
        Evaluate.queen_mobility_endgame = Math.max(0, (int) Math.round(v[QUEEN_MOB_EG]));

        Evaluate.space_unit = Math.max(0, (int) Math.round(v[SPACE_UNIT]));

        Evaluate.UNDEVELOPED_MINOR_PENALTY = Math.max(0, (int) Math.round(v[UNDEV_PENALTY]));
        Evaluate.CENTER_PAWN_DUO_BONUS     = Math.max(0, (int) Math.round(v[CENTER_DUO]));
        Evaluate.CENTRAL_FILE_HOLE_PENALTY = Math.max(0, (int) Math.round(v[CENTRAL_HOLE]));
    }
}