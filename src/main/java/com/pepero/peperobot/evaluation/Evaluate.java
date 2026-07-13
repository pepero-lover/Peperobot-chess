package com.pepero.peperobot.evaluation;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class Evaluate {

    // Game Phases
    private static final int OPENING = 0;
    private static final int ENDGAME = 1;
    private static final int MIDDLEGAME = 2;

    // Piece Types
    private static final int PAWN = 0;
    private static final int KNIGHT = 1;
    private static final int BISHOP = 2;
    private static final int ROOK = 3;
    private static final int QUEEN = 4;
    private static final int KING = 5;

    // Game phase scores
    private static final int OPENING_PHASE_SCORE = 6192;
    private static final int ENDGAME_PHASE_SCORE = 518;

    // Material score [game phase][piece]
    private static int[][] material_score = {
            // opening material score
            { 87, 337, 365, 477, 1025, 12000, -87, -337, -365, -477, -1025, -12000 },
            // endgame material score
            { 100, 281, 297, 512,  936, 12000, -100, -281, -297, -512,  -936, -12000 }
    };

    public static void setMaterialScore(int phase, int pieceType, int value) {
        material_score[phase][pieceType] = value;
        material_score[phase][pieceType + 6] = -value;
    }

    public static int getMaterialScore(int phase, int pieceType) {
        return material_score[phase][pieceType];
    }

    private static int bishop_unit = 4;
    private static int queen_unit = 9;

    public static int bishop_pair_bonus_opening = 30;
    public static int bishop_pair_bonus_endgame = 50;

    public static int bishop_mobility_opening = 2;
    public static int bishop_mobility_endgame = 3;
    public static int queen_mobility_opening = 1;
    public static int queen_mobility_endgame = 2;

    public static int knight_unit = 4;
    public static int knight_mobility_opening = 4;
    public static int knight_mobility_endgame = 2;

    // 룩은 열린 파일 보너스는 이미 있었지만 mobility(가동성) 항목이 빠져 있었음.
    // rook_unit은 기준 가동성(대략 절반 정도 열린 상태에서의 평균 공격 칸 수).
    private static int rook_unit = 7;
    public static int rook_mobility_opening = 2;
    public static int rook_mobility_endgame = 4;

    public static int space_unit = 6;

    public static int UNDEVELOPED_MINOR_PENALTY = 6;
    public static int CENTER_PAWN_DUO_BONUS = 24;
    public static int CENTRAL_FILE_HOLE_PENALTY = 18;

    // Positional piece scores [game phase][piece][square] (PeSTO)
    // NOTE: private final -> tuning target이 되려면 static (non-final)로 바꿔야 함.
    //       지금 단계에서는 리팩토링만 하는 거라 우선 그대로 둠.
    public static int[][][] positional_score = {
            // ========== OPENING ==========
            {
                    // pawn
                    {
                            0,   0,   0,   0,   0,   0,  0,   0,
                            98, 134,  61,  95,  68, 126, 34, -11,
                            -6,   7,  26,  31,  65,  56, 25, -20,
                            -14,  13,   6,  21,  16,  12, 17, -23,
                            -27,  -2,  -5,  12,  17,   6, 10, -25,
                            -26,  -4,  -4, -10,   3,   3, 33, -12,
                            -35,  -1, -20, -23, -15,  24, 38, -22,
                            0,   0,   0,   0,   0,   0,  0,   0
                    },
                    // knight
                    {
                            -167, -89, -34, -49,  61, -97, -15, -107,
                            -73, -41,  72,  36,  23,  62,   7,  -17,
                            -47,  60,  37,  65,  84, 129,  73,   44,
                            -9,  17,  19,  53,  37,  69,  18,   22,
                            -13,   4,  16,  13,  28,  19,  21,   -8,
                            -23,  -9,  12,  10,  19,  17,  25,  -16,
                            -29, -53, -12,  -3,  -1,  18, -14,  -19,
                            -105, -21, -58, -33, -17, -28, -19,  -23
                    },
                    // bishop
                    {
                            -29,   4, -82, -37, -25, -42,   7,  -8,
                            -26,  16, -18, -13,  30,  59,  18, -47,
                            -16,  37,  43,  40,  35,  50,  37,  -2,
                            -4,   5,  19,  50,  37,  37,   7,  -2,
                            -6,  13,  13,  26,  34,  12,  10,   4,
                            0,  15,  15,  15,  14,  27,  18,  10,
                            4,  15,  16,   0,   7,  21,  33,   1,
                            -33,  -3, -14, -21, -13, -12, -39, -21
                    },
                    // rook
                    {
                            32,  42,  32,  51, 63,  9,  31,  43,
                            27,  32,  58,  62, 80, 67,  26,  44,
                            -5,  19,  26,  36, 17, 45,  61,  16,
                            -24, -11,   7,  26, 24, 35,  -8, -20,
                            -36, -26, -12,  -1,  9, -7,   6, -23,
                            -45, -25, -16, -17,  3,  0,  -5, -33,
                            -44, -16, -20,  -9, -1, 11,  -6, -71,
                            -19, -13,   1,  17, 16,  7, -37, -26
                    },
                    // queen
                    {
                            -28,   0,  29,  12,  59,  44,  43,  45,
                            -24, -39,  -5,   1, -16,  57,  28,  54,
                            -13, -17,   7,   8,  29,  56,  47,  57,
                            -27, -27, -16, -16,  -1,  17,  -2,   1,
                            -9, -26,  -9, -10,  -2,  -4,   3,  -3,
                            -14,   2, -11,  -2,  -5,   2,  14,   5,
                            -35,  -8,  11,   2,   8,  15,  -3,   1,
                            -1, -18,  -9,  10, -15, -25, -31, -50
                    },
                    // king
                    {
                            -65,  23,  16, -15, -56, -34,   2,  13,
                            29,  -1, -20,  -7,  -8,  -4, -38, -29,
                            -9,  24,   2, -16, -20,   6,  22, -22,
                            -17, -20, -12, -27, -30, -25, -14, -36,
                            -49,  -1, -27, -39, -46, -44, -33, -51,
                            -14, -14, -22, -46, -44, -30, -15, -27,
                            1,   7,  -8, -64, -43, -16,   9,   8,
                            -15,  36,  12, -54,   8, -28,  24,  14
                    }
            },
            // ========== ENDGAME ==========
            {
                    // pawn
                    {
                            0,   0,   0,   0,   0,   0,   0,   0,
                            178, 173, 158, 134, 147, 132, 165, 187,
                            94, 100,  85,  67,  56,  53,  82,  84,
                            32,  24,  13,   5,  -2,   4,  17,  17,
                            13,   9,  -3,  -7,  -7,  -8,   3,  -1,
                            4,   7,  -6,   1,   0,  -5,  -1,  -8,
                            13,   8,   8,  10,  13,   0,   2,  -7,
                            0,   0,   0,   0,   0,   0,   0,   0
                    },
                    // knight
                    {
                            -58, -38, -13, -28, -31, -27, -63, -99,
                            -25,  -8, -25,  -2,  -9, -25, -24, -52,
                            -24, -20,  10,   9,  -1,  -9, -19, -41,
                            -17,   3,  22,  22,  22,  11,   8, -18,
                            -18,  -6,  16,  25,  16,  17,   4, -18,
                            -23,  -3,  -1,  15,  10,  -3, -20, -22,
                            -42, -20, -10,  -5,  -2, -20, -23, -44,
                            -29, -51, -23, -15, -22, -18, -50, -64
                    },
                    // bishop
                    {
                            -14, -21, -11,  -8, -7,  -9, -17, -24,
                            -8,  -4,   7, -12, -3, -13,  -4, -14,
                            2,  -8,   0,  -1, -2,   6,   0,   4,
                            -3,   9,  12,   9, 14,  10,   3,   2,
                            -6,   3,  13,  19,  7,  10,  -3,  -9,
                            -12,  -3,   8,  10, 13,   3,  -7, -15,
                            -14, -18,  -7,  -1,  4,  -9, -15, -27,
                            -23,  -9, -23,  -5, -9, -16,  -5, -17
                    },
                    // rook
                    {
                            13, 10, 18, 15, 12,  12,   8,   5,
                            11, 13, 13, 11, -3,   3,   8,   3,
                            7,  7,  7,  5,  4,  -3,  -5,  -3,
                            4,  3, 13,  1,  2,   1,  -1,   2,
                            3,  5,  8,  4, -5,  -6,  -8, -11,
                            -4,  0, -5, -1, -7, -12,  -8, -16,
                            -6, -6,  0,  2, -9,  -9, -11,  -3,
                            -9,  2,  3, -1, -5, -13,   4, -20
                    },
                    // queen
                    {
                            -9,  22,  22,  27,  27,  19,  10,  20,
                            -17,  20,  32,  41,  58,  25,  30,   0,
                            -20,   6,   9,  49,  47,  35,  19,   9,
                            3,  22,  24,  45,  57,  40,  57,  36,
                            -18,  28,  19,  47,  31,  34,  39,  23,
                            -16, -27,  15,   6,   9,  17,  10,   5,
                            -22, -23, -30, -16, -16, -23, -36, -32,
                            -33, -28, -22, -43,  -5, -32, -20, -41
                    },
                    // king
                    {
                            -74, -35, -18, -18, -11,  15,   4, -17,
                            -12,  17,  14,  17,  17,  38,  23,  11,
                            10,  17,  23,  15,  20,  45,  44,  13,
                            -8,  22,  24,  27,  26,  33,  26,   3,
                            -18,  -4,  21,  24,  27,  23,   9, -11,
                            -19,  -3,  11,  21,  23,  16,   7,  -9,
                            -27, -11,   4,  13,  14,   4,  -5, -17,
                            -53, -34, -21, -11, -28, -14, -24, -43
                    }
            }
    };

    private static final long[] file_masks = new long[64];
    private static final long[] rank_masks = new long[64];
    private static final long[] isolated_masks = new long[64];
    private static final long[] white_passed_masks = new long[64];
    private static final long[] black_passed_masks = new long[64];

    private static final int double_pawn_penalty_opening = -5;
    private static final int double_pawn_penalty_endgame = -10;
    private static final int isolated_pawn_penalty_opening = -5;
    private static final int isolated_pawn_penalty_endgame = -10;

    private static final int[] passed_pawn_bonus_opening = { 0, 5, 8, 12, 20, 30,  50,  70 };
    private static final int[] passed_pawn_bonus_endgame = { 0, 10, 30, 50, 75, 100, 150, 200 };

    private static final int semi_open_file_score = 10;
    private static final int open_file_score = 15;

    private static final long CENTRAL_FILES_MASK = file_masks[c1] | file_masks[d1] | file_masks[e1] | file_masks[f1];

    private static final int[] SAFETY_TABLE = {
            0,   0,   1,   2,   3,   5,   7,   9,  12,  15,
            18,  22,  26,  30,  35,  39,  44,  50,  56,  62,
            68,  75,  82,  89,  97, 105, 113, 122, 131, 140,
            150, 160, 170, 181, 192, 203, 215, 227, 239, 252,
            265, 278, 292, 306, 320, 335, 350, 366, 382, 398,
            415, 432, 450, 468, 487, 506, 526, 546, 567, 588,
            610, 632, 655, 678, 702, 726, 751, 776, 802, 828,
            855, 882, 910, 938, 967, 996, 1026, 1056, 1087, 1118,
            1150, 1182, 1215, 1248, 1282, 1316, 1351, 1386, 1422, 1458,
            1495, 1532, 1570, 1608, 1647, 1686, 1726, 1766, 1807, 1848
    };

    static {
        init_evaluation_masks();
    }

    private static long set_file_rank_mask(int file_number, int rank_number) {
        long mask = 0L;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                if (file_number != -1 && file == file_number) {
                    mask |= (1L << square);
                } else if (rank_number != -1 && rank == rank_number) {
                    mask |= (1L << square);
                }
            }
        }
        return mask;
    }

    private static void init_evaluation_masks() {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                file_masks[square] |= set_file_rank_mask(file, -1);
                rank_masks[square] |= set_file_rank_mask(-1, rank);
                isolated_masks[square] |= set_file_rank_mask(file - 1, -1);
                isolated_masks[square] |= set_file_rank_mask(file + 1, -1);
            }
        }

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;

                white_passed_masks[square] |= set_file_rank_mask(file - 1, -1);
                white_passed_masks[square] |= set_file_rank_mask(file, -1);
                white_passed_masks[square] |= set_file_rank_mask(file + 1, -1);
                for (int i = 0; i < (8 - rank); i++) {
                    white_passed_masks[square] &= ~rank_masks[(7 - i) * 8 + file];
                }

                black_passed_masks[square] |= set_file_rank_mask(file - 1, -1);
                black_passed_masks[square] |= set_file_rank_mask(file, -1);
                black_passed_masks[square] |= set_file_rank_mask(file + 1, -1);
                for (int i = 0; i < rank + 1; i++) {
                    black_passed_masks[square] &= ~rank_masks[i * 8 + file];
                }
            }
        }
    }

    private static int get_game_phase_score(Chessboard chessboard) {
        int white_piece_scores = 0, black_piece_scores = 0;

        for (int piece = N; piece <= Q; piece++) {
            white_piece_scores += Long.bitCount(chessboard.bitboards[piece]) * material_score[OPENING][piece];
        }
        for (int piece = n; piece <= q; piece++) {
            black_piece_scores += Long.bitCount(chessboard.bitboards[piece]) * -material_score[OPENING][piece];
        }

        return white_piece_scores + black_piece_scores;
    }

    private static int getTotalMaterial(Chessboard chessboard, boolean isWhite) {
        if (isWhite) {
            return Long.bitCount(chessboard.bitboards[P]) * 100
                    + Long.bitCount(chessboard.bitboards[N]) * 320
                    + Long.bitCount(chessboard.bitboards[B]) * 330
                    + Long.bitCount(chessboard.bitboards[R]) * 500
                    + Long.bitCount(chessboard.bitboards[Q]) * 900;
        } else {
            return Long.bitCount(chessboard.bitboards[p]) * 100
                    + Long.bitCount(chessboard.bitboards[n]) * 320
                    + Long.bitCount(chessboard.bitboards[b]) * 330
                    + Long.bitCount(chessboard.bitboards[r]) * 500
                    + Long.bitCount(chessboard.bitboards[q]) * 900;
        }
    }

    public static int getKingSafetyPenalty(Chessboard chessboard, int side) {
        int king_square = BitBoardUtils.getLS1BIndex(chessboard.bitboards[side == white ? K : k]);
        return evaluateKingSafety(chessboard, king_square, side);
    }

    public static int evaluateKingSafety(Chessboard chessboard, int king_square, int side) {
        int penalty = 0;
        long king_file = file_masks[king_square];
        int king_file_idx = king_square % 8;

        long own_pawns = chessboard.bitboards[(side == white) ? P : p];
        long enemy_pawns = chessboard.bitboards[(side == white) ? p : P];
        long enemy_queen = chessboard.bitboards[(side == white) ? q : Q];
        long enemy_rooks = chessboard.bitboards[(side == white) ? r : R];
        long enemy_knights = chessboard.bitboards[(side == white) ? n : N];
        long enemy_bishops = chessboard.bitboards[(side == white) ? b : B];

        boolean is_center = (king_file_idx == 3 || king_file_idx == 4);

        int king_rank_idx = king_square / 8;
        int rank_step = (side == white) ? -1 : 1;

        long immediate_shield_zone = 0L;
        long extended_shield_zone  = 0L;

        for (int df = -1; df <= 1; df++) {
            int f = king_file_idx + df;
            if (f < 0 || f > 7) continue;

            int r1 = king_rank_idx + rank_step;
            if (r1 >= 0 && r1 <= 7) immediate_shield_zone |= (1L << (r1 * 8 + f));

            int r2 = king_rank_idx + rank_step * 2;
            if (r2 >= 0 && r2 <= 7) extended_shield_zone |= (1L << (r2 * 8 + f));
        }

        int immediate_pawns = Long.bitCount(own_pawns & immediate_shield_zone);
        int extended_pawns  = Long.bitCount(own_pawns & extended_shield_zone);

        int shield_score = immediate_pawns * 2 + extended_pawns;

        if (!is_center) {
            if (shield_score < 3) penalty += 140;
            else if (shield_score < 5) penalty += 40;
        }

        long enemy_heavy_pieces = enemy_queen | enemy_rooks;
        boolean is_file_attacked = (enemy_heavy_pieces & king_file) != 0;

        if ((own_pawns & king_file) == 0) {
            int file_penalty = is_center ? 30 : 60;
            if ((enemy_pawns & king_file) == 0) {
                file_penalty += is_center ? 40 : 100;
            }
            if (enemy_heavy_pieces == 0) {
                file_penalty = 0;
            } else if (!is_file_attacked) {
                file_penalty /= 2;
            }
            penalty += file_penalty;
        }

        int king_rank = king_square / 8;
        long both_occ = chessboard.occupancies[both];

        long king_zone = Attacks.king_attacks[king_square] | (1L << king_square);

        int attack_weight = 0;
        int attackers_count = 0;
        int q_count = 0;

        long knights_bb = enemy_knights;
        while (knights_bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(knights_bb);
            if ((Attacks.knight_attacks[sq] & king_zone) != 0) {
                attackers_count++;
                attack_weight += 15;
            }
            knights_bb = BitBoardUtils.popBit(knights_bb, sq);
        }

        long bishops_bb = enemy_bishops;
        while (bishops_bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(bishops_bb);
            if ((Attacks.getBishopAttacks(sq, both_occ) & king_zone) != 0) {
                attackers_count++;
                attack_weight += 15;
            }
            bishops_bb = BitBoardUtils.popBit(bishops_bb, sq);
        }

        long rooks_bb = enemy_rooks;
        while (rooks_bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(rooks_bb);
            if ((Attacks.getRookAttacks(sq, both_occ) & king_zone) != 0) {
                attackers_count++;
                attack_weight += 25;
            }
            rooks_bb = BitBoardUtils.popBit(rooks_bb, sq);
        }

        long queens_bb = enemy_queen;
        while (queens_bb != 0) {
            int sq = BitBoardUtils.getLS1BIndex(queens_bb);
            if ((Attacks.getQueenAttacks(sq, both_occ) & king_zone) != 0) {
                attackers_count++;
                q_count++;
                attack_weight += 40;
            }
            queens_bb = BitBoardUtils.popBit(queens_bb, sq);
        }

        if (attackers_count >= 2 || q_count > 0) {
            if (shield_score == 0) {
                attack_weight = (attack_weight * 3) / 2;
            } else if (shield_score <= 2) {
                attack_weight = (attack_weight * 5) / 4;
            }

            int rank_distance = (side == white) ? (7 - king_rank) : king_rank;
            if (rank_distance >= 1) {
                attack_weight += (rank_distance * 25);
            }

            attack_weight = Math.min(attack_weight, 99);
            int current_penalty = SAFETY_TABLE[attack_weight];

            if (q_count == 0) {
                current_penalty = Math.min(current_penalty / 2, 150);
            }

            penalty += current_penalty;
        }

        return penalty >> 2;
    }

    private static int evaluateSpace(Chessboard chessboard) {
        long center_files = file_masks[c1] | file_masks[d1] | file_masks[e1] | file_masks[f1];

        int white_space = 0;
        long white_center_pawns = chessboard.bitboards[P] & center_files;
        while (white_center_pawns != 0) {
            int sq = BitBoardUtils.getLS1BIndex(white_center_pawns);
            int ranks_advanced = 6 - (sq / 8);
            if (ranks_advanced > 0) white_space += ranks_advanced;
            white_center_pawns = BitBoardUtils.popBit(white_center_pawns, sq);
        }

        int black_space = 0;
        long black_center_pawns = chessboard.bitboards[p] & center_files;
        while (black_center_pawns != 0) {
            int sq = BitBoardUtils.getLS1BIndex(black_center_pawns);
            int ranks_advanced = (sq / 8) - 1;
            if (ranks_advanced > 0) black_space += ranks_advanced;
            black_center_pawns = BitBoardUtils.popBit(black_center_pawns, sq);
        }

        return (white_space - black_space) * space_unit;
    }

    private static int getNonPawnMaterial(Chessboard chessboard, boolean white) {
        if (white) {
            return Long.bitCount(chessboard.bitboards[N]) * 320
                    + Long.bitCount(chessboard.bitboards[B]) * 330
                    + Long.bitCount(chessboard.bitboards[R]) * 500
                    + Long.bitCount(chessboard.bitboards[Q]) * 900;
        } else {
            return Long.bitCount(chessboard.bitboards[n]) * 320
                    + Long.bitCount(chessboard.bitboards[b]) * 330
                    + Long.bitCount(chessboard.bitboards[r]) * 500
                    + Long.bitCount(chessboard.bitboards[q]) * 900;
        }
    }

    private static int evaluateDevelopmentAndCenter(Chessboard chessboard) {
        int score = 0;

        long white_home_minors = (1L << 57) | (1L << 62) | (1L << 58) | (1L << 61);
        long white_still_home = (chessboard.bitboards[N] | chessboard.bitboards[B]) & white_home_minors;
        score -= Long.bitCount(white_still_home) * UNDEVELOPED_MINOR_PENALTY;

        long black_home_minors = (1L << 1) | (1L << 6) | (1L << 2) | (1L << 5);
        long black_still_home = (chessboard.bitboards[n] | chessboard.bitboards[b]) & black_home_minors;
        score += Long.bitCount(black_still_home) * UNDEVELOPED_MINOR_PENALTY;

        long white_d_file = chessboard.bitboards[P] & file_masks[d1];
        long white_e_file = chessboard.bitboards[P] & file_masks[e1];
        boolean white_duo = white_d_file != 0 && white_e_file != 0
                && (BitBoardUtils.getLS1BIndex(white_d_file) / 8) <= 4
                && (BitBoardUtils.getLS1BIndex(white_e_file) / 8) <= 4;
        if (white_duo) score += CENTER_PAWN_DUO_BONUS;

        long black_d_file = chessboard.bitboards[p] & file_masks[d1];
        long black_e_file = chessboard.bitboards[p] & file_masks[e1];
        boolean black_duo = black_d_file != 0 && black_e_file != 0
                && ((63 - Long.numberOfLeadingZeros(black_d_file)) / 8) >= 3
                && ((63 - Long.numberOfLeadingZeros(black_e_file)) / 8) >= 3;
        if (black_duo) score -= CENTER_PAWN_DUO_BONUS;

        int white_non_pawn_material = getNonPawnMaterial(chessboard, true);
        int black_non_pawn_material = getNonPawnMaterial(chessboard, false);

        boolean white_no_d_pawn = (chessboard.bitboards[P] & file_masks[d1]) == 0;
        boolean white_no_e_pawn = (chessboard.bitboards[P] & file_masks[e1]) == 0;
        int white_hole_penalty = (white_non_pawn_material >= black_non_pawn_material)
                ? CENTRAL_FILE_HOLE_PENALTY / 3 : CENTRAL_FILE_HOLE_PENALTY;
        if (white_no_d_pawn) score -= white_hole_penalty;
        if (white_no_e_pawn) score -= white_hole_penalty;

        boolean black_no_d_pawn = (chessboard.bitboards[p] & file_masks[d1]) == 0;
        boolean black_no_e_pawn = (chessboard.bitboards[p] & file_masks[e1]) == 0;
        int black_hole_penalty = (black_non_pawn_material >= white_non_pawn_material)
                ? CENTRAL_FILE_HOLE_PENALTY / 3 : CENTRAL_FILE_HOLE_PENALTY;
        if (black_no_d_pawn) score += black_hole_penalty;
        if (black_no_e_pawn) score += black_hole_penalty;

        return score;
    }

    // ===================================================================
    // 리팩터링 핵심: evaluate()의 계산 로직을 여기로 그대로 이동.
    // side flip 없이, "백 기준 opening/endgame raw score"만 반환한다.
    // ===================================================================
    public static class RawScores {
        public int scoreOpening;
        public int scoreEndgame;
        public int gamePhaseScore;
        public int gamePhase;
    }

    public static RawScores computeRawScores(Chessboard chessboard) {
        int game_phase_score = get_game_phase_score(chessboard);
        int game_phase;

        if (game_phase_score > OPENING_PHASE_SCORE) game_phase = OPENING;
        else if (game_phase_score < ENDGAME_PHASE_SCORE) game_phase = ENDGAME;
        else game_phase = MIDDLEGAME;

        int score_opening = 0, score_endgame = 0;
        int double_pawns;

        long white_occupancies = 0L, black_occupancies = 0L;
        for (int i = P; i <= K; i++) white_occupancies |= chessboard.bitboards[i];
        for (int i = p; i <= k; i++) black_occupancies |= chessboard.bitboards[i];
        long both_occupancies = white_occupancies | black_occupancies;

        for (int bb_piece = P; bb_piece <= k; bb_piece++) {
            long bitboard = chessboard.bitboards[bb_piece];

            while (bitboard != 0) {
                int square = BitBoardUtils.getLS1BIndex(bitboard);

                score_opening += material_score[OPENING][bb_piece];
                score_endgame += material_score[ENDGAME][bb_piece];

                switch (bb_piece) {
                    case P:
                        score_opening += positional_score[OPENING][PAWN][square];
                        score_endgame += positional_score[ENDGAME][PAWN][square];

                        double_pawns = Long.bitCount(chessboard.bitboards[P] & file_masks[square]);
                        if (double_pawns > 1) {
                            score_opening += (double_pawns - 1) * double_pawn_penalty_opening;
                            score_endgame += (double_pawns - 1) * double_pawn_penalty_endgame;
                        }

                        if ((chessboard.bitboards[P] & isolated_masks[square]) == 0) {
                            score_opening += isolated_pawn_penalty_opening;
                            score_endgame += isolated_pawn_penalty_endgame;
                        }

                        if ((white_passed_masks[square] & chessboard.bitboards[p]) == 0) {
                            int rank = 7 - (square / 8);
                            score_opening += passed_pawn_bonus_opening[rank];
                            score_endgame += passed_pawn_bonus_endgame[rank];
                        }
                        break;

                    case N:
                        score_opening += positional_score[OPENING][KNIGHT][square];
                        score_endgame += positional_score[ENDGAME][KNIGHT][square];

                        long knight_attacks = Attacks.knight_attacks[square] & ~chessboard.occupancies[white];
                        score_opening += (Long.bitCount(knight_attacks) - knight_unit) * knight_mobility_opening;
                        score_endgame += (Long.bitCount(knight_attacks) - knight_unit) * knight_mobility_endgame;
                        break;

                    case B:
                        score_opening += positional_score[OPENING][BISHOP][square];
                        score_endgame += positional_score[ENDGAME][BISHOP][square];

                        long bishop_attacks = Attacks.getBishopAttacks(square, both_occupancies);
                        score_opening += (Long.bitCount(bishop_attacks) - bishop_unit) * bishop_mobility_opening;
                        score_endgame += (Long.bitCount(bishop_attacks) - bishop_unit) * bishop_mobility_endgame;
                        if (square == 43 && (chessboard.bitboards[P] & (1L << 51)) != 0) {
                            score_opening -= 45; score_endgame -= 30;
                        }
                        if (square == 44 && (chessboard.bitboards[P] & (1L << 52)) != 0) {
                            score_opening -= 45; score_endgame -= 30;
                        }
                        break;

                    case R:
                        score_opening += positional_score[OPENING][ROOK][square];
                        score_endgame += positional_score[ENDGAME][ROOK][square];
                        if ((chessboard.bitboards[P] & file_masks[square]) == 0) {
                            score_opening += semi_open_file_score;
                            score_endgame += semi_open_file_score;
                        }
                        if (((chessboard.bitboards[P] | chessboard.bitboards[p]) & file_masks[square]) == 0) {
                            score_opening += open_file_score;
                            score_endgame += open_file_score;
                        }

                        long rook_attacks = Attacks.getRookAttacks(square, both_occupancies) & ~chessboard.occupancies[white];
                        score_opening += (Long.bitCount(rook_attacks) - rook_unit) * rook_mobility_opening;
                        score_endgame += (Long.bitCount(rook_attacks) - rook_unit) * rook_mobility_endgame;
                        break;

                    case Q:
                        score_opening += positional_score[OPENING][QUEEN][square];
                        score_endgame += positional_score[ENDGAME][QUEEN][square];

                        long queen_attacks = Attacks.getQueenAttacks(square, both_occupancies);
                        score_opening += (Long.bitCount(queen_attacks) - queen_unit) * queen_mobility_opening;
                        score_endgame += (Long.bitCount(queen_attacks) - queen_unit) * queen_mobility_endgame;
                        break;

                    case K:
                        score_opening += positional_score[OPENING][KING][square];
                        score_endgame += positional_score[ENDGAME][KING][square];

                        score_opening -= evaluateKingSafety(chessboard, square, white);
                        break;

                    case p:
                        int mirror_sq = square ^ 56;
                        score_opening -= positional_score[OPENING][PAWN][mirror_sq];
                        score_endgame -= positional_score[ENDGAME][PAWN][mirror_sq];

                        double_pawns = Long.bitCount(chessboard.bitboards[p] & file_masks[square]);
                        if (double_pawns > 1) {
                            score_opening -= (double_pawns - 1) * double_pawn_penalty_opening;
                            score_endgame -= (double_pawns - 1) * double_pawn_penalty_endgame;
                        }

                        if ((chessboard.bitboards[p] & isolated_masks[square]) == 0) {
                            score_opening -= isolated_pawn_penalty_opening;
                            score_endgame -= isolated_pawn_penalty_endgame;
                        }

                        if ((black_passed_masks[square] & chessboard.bitboards[P]) == 0) {
                            int rank = square / 8;
                            score_opening -= passed_pawn_bonus_opening[rank];
                            score_endgame -= passed_pawn_bonus_endgame[rank];
                        }
                        break;

                    case n:
                        score_opening -= positional_score[OPENING][KNIGHT][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][KNIGHT][square ^ 56];

                        long b_knight_attacks = Attacks.knight_attacks[square] & ~chessboard.occupancies[black];
                        score_opening -= (Long.bitCount(b_knight_attacks) - knight_unit) * knight_mobility_opening;
                        score_endgame -= (Long.bitCount(b_knight_attacks) - knight_unit) * knight_mobility_endgame;
                        break;

                    case b:
                        score_opening -= positional_score[OPENING][BISHOP][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][BISHOP][square ^ 56];

                        long b_bishop_attacks = Attacks.getBishopAttacks(square, both_occupancies);
                        score_opening -= (Long.bitCount(b_bishop_attacks) - bishop_unit) * bishop_mobility_opening;
                        score_endgame -= (Long.bitCount(b_bishop_attacks) - bishop_unit) * bishop_mobility_endgame;
                        if (square == 19 && (chessboard.bitboards[p] & (1L << 11)) != 0) {
                            score_opening += 45; score_endgame += 30;
                        }
                        if (square == 20 && (chessboard.bitboards[p] & (1L << 12)) != 0) {
                            score_opening += 45; score_endgame += 30;
                        }
                        break;

                    case r:
                        score_opening -= positional_score[OPENING][ROOK][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][ROOK][square ^ 56];

                        if ((chessboard.bitboards[p] & file_masks[square]) == 0) {
                            score_opening -= semi_open_file_score;
                            score_endgame -= semi_open_file_score;
                        }
                        if (((chessboard.bitboards[P] | chessboard.bitboards[p]) & file_masks[square]) == 0) {
                            score_opening -= open_file_score;
                            score_endgame -= open_file_score;
                        }

                        long b_rook_attacks = Attacks.getRookAttacks(square, both_occupancies) & ~chessboard.occupancies[black];
                        score_opening -= (Long.bitCount(b_rook_attacks) - rook_unit) * rook_mobility_opening;
                        score_endgame -= (Long.bitCount(b_rook_attacks) - rook_unit) * rook_mobility_endgame;
                        break;

                    case q:
                        score_opening -= positional_score[OPENING][QUEEN][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][QUEEN][square ^ 56];

                        long b_queen_attacks = Attacks.getQueenAttacks(square, both_occupancies);
                        score_opening -= (Long.bitCount(b_queen_attacks) - queen_unit) * queen_mobility_opening;
                        score_endgame -= (Long.bitCount(b_queen_attacks) - queen_unit) * queen_mobility_endgame;
                        break;

                    case k:
                        score_opening -= positional_score[OPENING][KING][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][KING][square ^ 56];

                        if ((chessboard.bitboards[p] & file_masks[square]) == 0) {
                            score_opening += semi_open_file_score;
                            score_endgame += semi_open_file_score;
                        }
                        if (((chessboard.bitboards[P] | chessboard.bitboards[p]) & file_masks[square]) == 0) {
                            score_opening += open_file_score;
                            score_endgame += open_file_score;
                        }

                        score_opening += evaluateKingSafety(chessboard, square, black);
                        break;
                }

                bitboard = BitBoardUtils.popBit(bitboard, square);
            }
        }

        if (Long.bitCount(chessboard.bitboards[B]) >= 2) {
            score_opening += bishop_pair_bonus_opening;
            score_endgame += bishop_pair_bonus_endgame;
        }
        if (Long.bitCount(chessboard.bitboards[b]) >= 2) {
            score_opening -= bishop_pair_bonus_opening;
            score_endgame -= bishop_pair_bonus_endgame;
        }

        int structural_bonus = evaluateSpace(chessboard) + evaluateDevelopmentAndCenter(chessboard);
        int material_diff = getTotalMaterial(chessboard, true) - getTotalMaterial(chessboard, false);

        if (material_diff > 0 && structural_bonus < 0) {
            structural_bonus /= 2;
        } else if (material_diff < 0 && structural_bonus > 0) {
            structural_bonus /= 2;
        }

        score_opening += structural_bonus;

        RawScores r = new RawScores();
        r.scoreOpening = score_opening;
        r.scoreEndgame = score_endgame;
        r.gamePhaseScore = game_phase_score;
        r.gamePhase = game_phase;
        return r;
    }

    // 기존 evaluate()는 computeRawScores()를 호출하는 얇은 wrapper로 변경.
    // 동작은 리팩터링 전과 100% 동일해야 함.
    public static int evaluate(Chessboard chessboard) {
        RawScores r = computeRawScores(chessboard);
        int score;

        if (r.gamePhase == MIDDLEGAME) {
            score = (r.scoreOpening * r.gamePhaseScore + r.scoreEndgame * (OPENING_PHASE_SCORE - r.gamePhaseScore)) / OPENING_PHASE_SCORE;
        } else if (r.gamePhase == OPENING) {
            score = r.scoreOpening;
        } else {
            score = r.scoreEndgame;
        }

        return (chessboard.side == white) ? score : -score;
    }
}