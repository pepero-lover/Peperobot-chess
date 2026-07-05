package com.pepero.peperobot.evaluation;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;

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
    private static final int[][] material_score = {
            // opening material score
            { 87, 337, 365, 477, 1025, 12000, -82, -337, -365, -477, -1025, -12000 },
            // endgame material score
            { 100, 281, 297, 512,  936, 12000, -94, -281, -297, -512,  -936, -12000 }
    };

    private static int bishop_unit = 4;
    private static int queen_unit = 9;

    private static int bishop_mobility_opening = 5;
    private static int bishop_mobility_endgame = 5;
    private static int queen_mobility_opening = 1;
    private static int queen_mobility_endgame = 2;

    // Positional piece scores [game phase][piece][square] (PeSTO)
    private static final int[][][] positional_score = {
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
    private static final int[] passed_pawn_bonus = { 0, 10, 30, 50, 75, 100, 150, 200 };

    private static final int semi_open_file_score = 10;
    private static final int open_file_score = 15;

    // Safety Table 추가
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

    // 킹 안전도 평가 (King Safety)
    private static int evaluateKingSafety(Chessboard chessboard, int king_square, int side) {
        int penalty = 0;
        long king_file = file_masks[king_square];
        long adj_files = isolated_masks[king_square];
        long danger_zone = king_file | adj_files;
        int king_file_idx = king_square % 8;

        long own_pawns = chessboard.bitboards[(side == white) ? P : p];
        long enemy_pawns = chessboard.bitboards[(side == white) ? p : P];
        long enemy_queen = chessboard.bitboards[(side == white) ? q : Q];
        long enemy_rooks = chessboard.bitboards[(side == white) ? r : R];
        long enemy_knights = chessboard.bitboards[(side == white) ? n : N];
        long enemy_bishops = chessboard.bitboards[(side == white) ? b : B];

        boolean is_center = (king_file_idx == 3 || king_file_idx == 4);
        int pawn_shield_count = Long.bitCount(own_pawns & danger_zone);

        if (!is_center) {
            if (pawn_shield_count < 2) penalty += 100;
            else if (pawn_shield_count < 3) penalty += 30;
        }
        if ((own_pawns & king_file) == 0) {
            penalty += is_center ? 30 : 60;
            if ((enemy_pawns & king_file) == 0) penalty += is_center ? 40 : 100;
        }

        int attack_weight = 0;
        int attackers_count = 0;

        if ((enemy_knights & danger_zone) != 0) {
            attackers_count++;
            attack_weight += 2;
        }
        if ((enemy_bishops & danger_zone) != 0) {
            attackers_count++;
            attack_weight += 2;
        }
        if ((enemy_rooks & danger_zone) != 0) {
            attackers_count++;
            attack_weight += 3;
        }
        if (enemy_queen != 0 && (enemy_queen & danger_zone) != 0) {
            attackers_count++;
            attack_weight += 5;
        }

        if (attackers_count >= 2 || (enemy_queen != 0 && (enemy_queen & danger_zone) != 0)) {
            if (pawn_shield_count == 0) {
                attack_weight *= 2;
            } else if (pawn_shield_count == 1) {
                attack_weight = (attack_weight * 3) / 2;
            }

            int king_rank = king_square / 8;

            // 🔥 A8 = 0 구조에 맞게 거리 계산 수정 (White 킹은 Rank 7에 존재)
            int rank_distance = (side == white) ? (7 - king_rank) : king_rank;

            if (rank_distance >= 2) {
                attack_weight += (rank_distance * 3);
            }

            attack_weight = Math.min(attack_weight, 99);
            int current_penalty = SAFETY_TABLE[attack_weight];

            if (enemy_queen == 0) {
                current_penalty = Math.min(current_penalty / 2, 150);
            }

            penalty += current_penalty;
        }

        return penalty;
    }

    public static int evaluate(Chessboard chessboard) {
        int game_phase_score = get_game_phase_score(chessboard);
        int game_phase;

        if (game_phase_score > OPENING_PHASE_SCORE) game_phase = OPENING;
        else if (game_phase_score < ENDGAME_PHASE_SCORE) game_phase = ENDGAME;
        else game_phase = MIDDLEGAME;

        int score = 0, score_opening = 0, score_endgame = 0;
        int double_pawns = 0;

        // Occupancies for Open Files calculation
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
                            score_opening += passed_pawn_bonus[rank];
                            score_endgame += passed_pawn_bonus[rank];
                        }
                        break;

                    case N:
                        score_opening += positional_score[OPENING][KNIGHT][square];
                        score_endgame += positional_score[ENDGAME][KNIGHT][square];
                        break;

                    case B:
                        score_opening += positional_score[OPENING][BISHOP][square];
                        score_endgame += positional_score[ENDGAME][BISHOP][square];

                        long bishop_attacks = Attacks.getBishopAttacks(square, both_occupancies);
                        score_opening += (Long.bitCount(bishop_attacks) - bishop_unit) * bishop_mobility_opening;
                        score_endgame += (Long.bitCount(bishop_attacks) - bishop_unit) * bishop_mobility_endgame;
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

                        if ((chessboard.bitboards[P] & file_masks[square]) == 0) {
                            score_opening -= semi_open_file_score;
                            score_endgame -= semi_open_file_score;
                        }
                        if (((chessboard.bitboards[P] | chessboard.bitboards[p]) & file_masks[square]) == 0) {
                            score_opening -= open_file_score;
                            score_endgame -= open_file_score;
                        }

                        // 🔥 백(White) 킹 안전도 패널티 적용
                        score_opening -= evaluateKingSafety(chessboard, square, white);
                        break;

                    // Evaluate Black Pieces
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
                            score_opening -= passed_pawn_bonus[rank];
                            score_endgame -= passed_pawn_bonus[rank];
                        }
                        break;

                    case n:
                        score_opening -= positional_score[OPENING][KNIGHT][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][KNIGHT][square ^ 56];
                        break;

                    case b:
                        score_opening -= positional_score[OPENING][BISHOP][square ^ 56];
                        score_endgame -= positional_score[ENDGAME][BISHOP][square ^ 56];

                        long b_bishop_attacks = Attacks.getBishopAttacks(square, both_occupancies);
                        score_opening -= (Long.bitCount(b_bishop_attacks) - bishop_unit) * bishop_mobility_opening;
                        score_endgame -= (Long.bitCount(b_bishop_attacks) - bishop_unit) * bishop_mobility_endgame;
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

        if (game_phase == MIDDLEGAME) {
            score = (score_opening * game_phase_score + score_endgame * (OPENING_PHASE_SCORE - game_phase_score)) / OPENING_PHASE_SCORE;
        } else if (game_phase == OPENING) {
            score = score_opening;
        } else if (game_phase == ENDGAME) {
            score = score_endgame;
        }

        if (game_phase == ENDGAME) {
        }

        return (chessboard.side == white) ? score : -score;
    }
}