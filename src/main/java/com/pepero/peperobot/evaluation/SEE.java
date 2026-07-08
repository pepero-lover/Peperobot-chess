package com.pepero.peperobot.evaluation;

import com.pepero.jcb.bitboard.Attacks;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class SEE {

    private static final int[] PIECE_VALUE = {
            100, 320, 330, 500, 900, 20000,
            100, 320, 330, 500, 900, 20000
    };

    public static int see(Chessboard chessboard, int move) {
        int from = EncodeMove.getMoveSource(move);
        int to   = EncodeMove.getMoveTarget(move);
        int side = chessboard.side;

        int attacker_piece = EncodeMove.getMovePiece(move);
        boolean is_enpassant = EncodeMove.getMoveEnpassant(move);

        int victim_piece = getPieceOnSquare(chessboard, to, side ^ 1);
        if (victim_piece == -1 && !is_enpassant) {
            return 0;
        }

        int[] gain = new int[32];
        int d = 0;

        gain[0] = is_enpassant ? PIECE_VALUE[(side == white) ? p : P] : PIECE_VALUE[victim_piece];

        long occupied = chessboard.occupancies[both];
        occupied &= ~(1L << from);

        if (is_enpassant) {
            int captured_pawn_sq = (side == white) ? to + 8 : to - 8;
            occupied &= ~(1L << captured_pawn_sq);
        }

        long attackers = getAttackersTo(chessboard, to, occupied);

        int side_to_move = side ^ 1;
        int mover_value = PIECE_VALUE[attacker_piece];

        while (true) {
            d++;
            gain[d] = mover_value - gain[d - 1];

            // 더 이상 진행해도 이득이 없으면 조기 종료 (표준 SEE pruning)
            if (Math.max(-gain[d - 1], gain[d]) < 0) break;

            long side_occupancy = (side_to_move == white)
                    ? chessboard.occupancies[white]
                    : chessboard.occupancies[black];

            long side_attackers = attackers & occupied & side_occupancy;
            if (side_attackers == 0) break;

            int lva_sq = leastValuableAttackerSquare(chessboard, side_attackers, side_to_move);
            int lva_piece = getPieceOnSquare(chessboard, lva_sq, side_to_move);
            mover_value = PIECE_VALUE[lva_piece];

            occupied &= ~(1L << lva_sq);
            // 비숍/룩/퀸이 뒤에서 드러나는 x-ray 공격을 반영하기 위해 매번 재계산
            attackers = getAttackersTo(chessboard, to, occupied);

            side_to_move ^= 1;
        }

        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }

        return gain[0];
    }

    /**
     * QS의 delta pruning에서 쓸, 가벼운 "캡처되는 기물의 순수 가치"만 반환.
     * (재교환까지 계산하는 see()보다 훨씬 싸다)
     */
    public static int capturedValue(Chessboard chessboard, int move) {
        int side = chessboard.side;
        if (EncodeMove.getMoveEnpassant(move)) {
            return PIECE_VALUE[(side == white) ? p : P];
        }
        int victim = getPieceOnSquare(chessboard, EncodeMove.getMoveTarget(move), side ^ 1);
        return (victim == -1) ? 0 : PIECE_VALUE[victim];
    }

    private static long getAttackersTo(Chessboard chessboard, int square, long occupancy) {
        long attackers = 0L;

        attackers |= Attacks.pawn_attacks[black][square] & chessboard.bitboards[P];
        attackers |= Attacks.pawn_attacks[white][square] & chessboard.bitboards[p];

        attackers |= Attacks.knight_attacks[square] & (chessboard.bitboards[N] | chessboard.bitboards[n]);

        long bishop_attacks = Attacks.getBishopAttacks(square, occupancy);
        attackers |= bishop_attacks & (chessboard.bitboards[B] | chessboard.bitboards[b]
                | chessboard.bitboards[Q] | chessboard.bitboards[q]);

        long rook_attacks = Attacks.getRookAttacks(square, occupancy);
        attackers |= rook_attacks & (chessboard.bitboards[R] | chessboard.bitboards[r]
                | chessboard.bitboards[Q] | chessboard.bitboards[q]);

        attackers |= Attacks.king_attacks[square] & (chessboard.bitboards[K] | chessboard.bitboards[k]);

        return attackers & occupancy;
    }

    private static int leastValuableAttackerSquare(Chessboard chessboard, long attackers, int side) {
        int start = (side == white) ? P : p;
        for (int i = 0; i <= 5; i++) {
            long bb = chessboard.bitboards[start + i] & attackers;
            if (bb != 0L) return BitBoardUtils.getLS1BIndex(bb);
        }
        return -1;
    }

    private static int getPieceOnSquare(Chessboard chessboard, int square, int side) {
        long mask = 1L << square;
        int start = (side == white) ? P : p;
        for (int i = 0; i <= 5; i++) {
            if ((chessboard.bitboards[start + i] & mask) != 0L) return start + i;
        }
        return -1;
    }
}