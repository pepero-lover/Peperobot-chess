package com.pepero.peperobot.evaluation;

import com.pepero.peperobot.Search;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.encode.EncodeMove;

import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.white;

public class ScoreMove {

    public static int scoreMove(Search search, Chessboard chessboard, int move, int hash_move){
        if (move == hash_move && hash_move != 0) {
            return 2000000;
        }

        if (search.score_pv) {
            if(search.pv_table[0][search.ply] == move){
                search.score_pv = false;
                return 20000;
            }
        }

        int promoted_piece = EncodeMove.getMovePromoted(move);
        int promotion_bonus = 0;

        if (promoted_piece == Q || promoted_piece == q) {
            promotion_bonus = 15000;
        } else if (promoted_piece != 0) {
            promotion_bonus = 10000;
        }

        if (EncodeMove.getMoveCapture(move)){
            int target_piece;
            int start_piece = (chessboard.side == white) ? p : P;

            long targetMask = 1L << EncodeMove.getMoveTarget(move);
            target_piece = start_piece;

            if ((chessboard.bitboards[start_piece + 1] & targetMask) != 0L) { target_piece = start_piece + 1; } // Knight
            else if ((chessboard.bitboards[start_piece + 2] & targetMask) != 0L) { target_piece = start_piece + 2; } // Bishop
            else if ((chessboard.bitboards[start_piece + 3] & targetMask) != 0L) { target_piece = start_piece + 3; } // Rook
            else if ((chessboard.bitboards[start_piece + 4] & targetMask) != 0L) { target_piece = start_piece + 4; } // Queen

            return MvvLva.MVV_LVA[EncodeMove.getMovePiece(move)][target_piece] + 10000 + promotion_bonus;

        } else {
            if (promotion_bonus > 0) return promotion_bonus;

            if(search.killer_moves[0][search.ply] == move) return 9000;
            else if(search.killer_moves[1][search.ply] == move) return 8000;
            else {
                int piece = EncodeMove.getMovePiece(move);
                int to = EncodeMove.getMoveTarget(move);
                // 기존 history_moves(내 수 기준)에 continuation history
                // (상대 직전 수 + 내 직전 수 문맥)를 더해 quiet move 순서를 정한다.
                return search.history_moves[piece][to] + search.getContHistScore(piece, to);
            }
        }
    }

    // [수정됨] hash_move 파라미터 추가
    public static void scoreMoves(Search search, Chessboard chessboard, int[] move_list, int move_count, int hash_move) {
        for (int count = 0; count < move_count; count++){
            search.move_scores[count] = scoreMove(search, chessboard, move_list[count], hash_move);
        }
    }

    // Quiescence Search용 (이제 TT에서 읽어온 hash_move를 넘겨받아 정렬에 반영)
    public static void scoreQuiescenceMoves(Search search, Chessboard chessboard, int[] move_list, int move_count, int hash_move) {
        for (int count = 0; count < move_count; count++){
            if (EncodeMove.getMoveCapture(move_list[count])) {
                search.move_scores[count] = scoreMove(search, chessboard, move_list[count], hash_move);
            } else {
                search.move_scores[count] = -1000000; // 조용한 수는 QS에서 제외
            }
        }
    }

    public static int pickNextMove(Search search, int stepIndex, int move_count, int[] move_list) {
        int bestIndex = stepIndex;
        int bestScore = search.move_scores[stepIndex];

        for (int i = stepIndex + 1; i < move_count; i++) {
            if (search.move_scores[i] > bestScore) {
                bestScore = search.move_scores[i];
                bestIndex = i;
            }
        }

        if (bestIndex != stepIndex) {
            int tempMove = move_list[stepIndex];
            move_list[stepIndex] = move_list[bestIndex];
            move_list[bestIndex] = tempMove;

            int tempScore = search.move_scores[stepIndex];
            search.move_scores[stepIndex] = search.move_scores[bestIndex];
            search.move_scores[bestIndex] = tempScore;
        }

        return move_list[stepIndex];
    }

    // 디버그용 출력 메서드 (hash_move 파라미터 추가)
    public static void printMoveScores(Search search, Chessboard chessboard, int[] move_list, int move_count, int hash_move){
        System.out.println("\n\n     Move scores\n");
        boolean isChess960 = chessboard.gameVariants == GameVariants.CHESS960;
        for(int count = 0; count < move_count; count++){
            System.out.println("     move: " + EncodeMove.moveToString(move_list[count], isChess960) +
                    " score : " + ScoreMove.scoreMove(search, chessboard, move_list[count], hash_move));
        }
    }
}