package com.pepero.peperobot;

import com.pepero.peperobot.evaluation.Evaluate;
import com.pepero.peperobot.evaluation.ScoreMove;
import com.pepero.peperobot.hash.TranspositionTable;
import com.pepero.peperobot.uci.UCIManager;
import com.pepero.peperobot.uci.time_control.TimeControlVariables;
import com.pepero.jcb.bitboard.BitBoardUtils;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.GameVariants;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.hash.Zobrist;
import com.pepero.jcb.util.TimeUtils;

import java.nio.file.Path;
import java.util.Arrays;

import net.chesstango.piazzolla.syzygy.Syzygy;
import net.chesstango.piazzolla.syzygy.SyzygyPosition;

import static com.pepero.jcb.constant.BoardSquares.*;
import static com.pepero.jcb.constant.EncodedPieces.*;
import static com.pepero.jcb.constant.SideToMove.*;

public class Search implements Runnable {
    public static int MAX_THREADS = 1;
    private static Search[] workers;

    public static final int MAX_PLY = 256;
    public static final int VAL_WINDOW = 50;

    public static final int INFINITY   = 50000;
    public static final int MATE_VALUE = 49000;
    public static final int MATE_SCORE = 48000;

    public static final int FULL_DEPTH_MOVES = 4;
    public static final int REDUCTION_LIMIT = 3;

    private final int threadId;
    private final Chessboard chessboard;
    private final int maxDepth;

    public int ply;
    public long nodes = 0;
    public int[][] killer_moves = new int[2][MAX_PLY];
    public int[][] history_moves = new int[12][64];
    public boolean follow_pv;
    public boolean score_pv;
    public int[] pv_length = new int[MAX_PLY];
    public int[][] pv_table = new int[MAX_PLY][MAX_PLY];
    public int[] move_scores = new int[256];

    public static final int[] FUTILITY_MARGIN = { 0, 200, 300, 500 };

    public int best_move = 0;

    private static final int[][] reduction_table = new int[64][64];

    public static Syzygy tablebase;

    static {
        for (int depth = 1; depth < 64; depth++) {
            for (int move_count = 1; move_count < 64; move_count++) {
                reduction_table[depth][move_count] = (int) (0.5 + Math.log(depth) * Math.log(move_count) / 1.95);
            }
        }
    }

    public static void initSyzygy(String syzygyPath) {
        try {
            tablebase = Syzygy.open(syzygyPath);
            if (tablebase != null) {
                System.out.println("info string Syzygy tablebase loaded. Max pieces: " + tablebase.tb_largest());
            }
        } catch (Exception e) {
            System.out.println("info string Failed to load Syzygy tablebase: " + e.getMessage());
        }
    }

    public Search(int threadId, Chessboard chessboard, int maxDepth) {
        this.threadId = threadId;
        this.chessboard = chessboard;
        this.maxDepth = maxDepth;
    }

    private static long getTotalNodes() {
        long total = 0;
        if (workers != null) {
            for (Search worker : workers) {
                if (worker != null) total += worker.nodes;
            }
        }
        return total;
    }

    private int quiescence(Chessboard chessboard, int alpha, int beta){
        if (threadId == 0 && (nodes & 2047) == 0) {
            UCIManager.communicate();
        }

        nodes++;

        if (ply >= MAX_PLY) {
            return Evaluate.evaluate(chessboard);
        }

        int evaluation = Evaluate.evaluate(chessboard);

        if (evaluation >= beta) {
            return beta;
        }

        if (evaluation > alpha) {
            alpha = evaluation;
        }

        int[] move_list = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        ScoreMove.scoreQuiescenceMoves(this, chessboard, move_list, move_count);

        for (int count = 0; count < move_count; count++) {
            int move = ScoreMove.pickNextMove(this, count, move_count, move_list);

            if (!EncodeMove.getMoveCapture(move)) {
                break;
            }

            ply++;
            MoveGenerator.makeStandardMove(chessboard, move);

            int score = -quiescence(chessboard, -beta, -alpha);
            ply--;
            MoveGenerator.unmakeStandardMove(chessboard, move);

            if (TimeControlVariables.stopped) return 0;

            if (score > alpha) {
                alpha = score;
                if (score >= beta) {
                    return beta;
                }
            }
        }
        return alpha;
    }

    private int negamax(Chessboard chessboard, int alpha, int beta, int depth) {
        int score;
        int hash_flag = TranspositionTable.HASH_FLAG_ALPHA;

        if(ply != 0 && ChessboardUtils.getRepetitionCount(chessboard) > 2){
            return 0;
        }

        boolean pv_node = beta - alpha > 1;

        int tt_score = TranspositionTable.readHashEntry(this, chessboard, alpha, beta, depth);
        if(ply != 0 && tt_score != TranspositionTable.NO_HASH_ENTRY && !pv_node) {
            return tt_score;
        }

        int hash_move = TranspositionTable.readHashMove(chessboard);

        if (threadId == 0 && (nodes & 2047) == 0) {
            UCIManager.communicate();
        }

        if (ply >= MAX_PLY)
            return Evaluate.evaluate(chessboard);

        pv_length[ply] = ply;

        if (ply > 0 && tablebase != null) {
            long whiteBB = chessboard.bitboards[P] | chessboard.bitboards[N] | chessboard.bitboards[B] |
                    chessboard.bitboards[R] | chessboard.bitboards[Q] | chessboard.bitboards[K];
            long blackBB = chessboard.bitboards[p] | chessboard.bitboards[n] | chessboard.bitboards[b] |
                    chessboard.bitboards[r] | chessboard.bitboards[q] | chessboard.bitboards[k];

            if (Long.bitCount(whiteBB | blackBB) <= tablebase.tb_largest()) {
                SyzygyPosition pos = new SyzygyPosition();

                pos.setWhite(Long.reverseBytes(whiteBB));
                pos.setBlack(Long.reverseBytes(blackBB));
                pos.setKings(Long.reverseBytes(chessboard.bitboards[K] | chessboard.bitboards[k]));
                pos.setQueens(Long.reverseBytes(chessboard.bitboards[Q] | chessboard.bitboards[q]));
                pos.setRooks(Long.reverseBytes(chessboard.bitboards[R] | chessboard.bitboards[r]));
                pos.setBishops(Long.reverseBytes(chessboard.bitboards[B] | chessboard.bitboards[b]));
                pos.setKnights(Long.reverseBytes(chessboard.bitboards[N] | chessboard.bitboards[n]));
                pos.setPawns(Long.reverseBytes(chessboard.bitboards[P] | chessboard.bitboards[p]));

                pos.setRule50((byte) 0);
                pos.setCastling((byte) 0);

                byte epSquare = 0;
                if (chessboard.enpassant != no_sq) {
                    epSquare = (byte) (chessboard.enpassant ^ 56);
                }

                pos.setEp(epSquare);

                int flippedEp = (chessboard.enpassant == no_sq) ? 0 : (chessboard.enpassant ^ 56);
                pos.setEp((byte) flippedEp);

                pos.setTurn(chessboard.side == white);

                int wdl = tablebase.tb_probe_wdl(pos);

                if (wdl != Syzygy.TB_RESULT_FAILED) {
                    if (wdl == Syzygy.TB_WIN) {
                        return MATE_SCORE - ply;
                    } else if (wdl == Syzygy.TB_LOSS) {
                        return -MATE_SCORE + ply;
                    } else if (wdl == Syzygy.TB_DRAW || wdl == Syzygy.TB_CURSED_WIN || wdl == Syzygy.TB_BLESSED_LOSS) {
                        return 0;
                    }
                }
            }
        }

        if (depth <= 0) {
            return quiescence(chessboard, alpha, beta);
        }

        nodes++;

        boolean in_check = MoveGenerator.isSquareAttacked(chessboard,
                chessboard.side == white ?
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]):
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]),
                chessboard.side ^ 1);

        if(in_check) depth++;

        int legal_moves = 0;

        // [개선 포인트 2] NMP 츠크츠방 방어 로직 추가
        boolean has_non_pawn_material;
        if (chessboard.side == white) {
            has_non_pawn_material = (chessboard.bitboards[N] | chessboard.bitboards[B] |
                    chessboard.bitboards[R] | chessboard.bitboards[Q]) != 0;
        } else {
            has_non_pawn_material = (chessboard.bitboards[n] | chessboard.bitboards[b] |
                    chessboard.bitboards[r] | chessboard.bitboards[q]) != 0;
        }

        if (depth >= 3 && !in_check && ply != 0 && has_non_pawn_material){
            int saved_enpassant = chessboard.enpassant;
            long saved_hash_key = chessboard.hash_key;
            int saved_half_ply = chessboard.half_ply;

            ply++;

            if (chessboard.enpassant != no_sq) {
                chessboard.hash_key ^= Zobrist.enpassant_keys[chessboard.enpassant];
            }

            chessboard.side ^= 1;
            chessboard.hash_key ^= Zobrist.side_key;
            chessboard.enpassant = no_sq;

            int R = 2 + (depth / 6);
            int nmp_depth = Math.max(0, depth - 1 - R);
            score = -negamax(chessboard, -beta, -beta + 1, nmp_depth);

            ply--;
            chessboard.side ^= 1;
            chessboard.enpassant = saved_enpassant;
            chessboard.hash_key = saved_hash_key;
            chessboard.half_ply = saved_half_ply;

            if(TimeControlVariables.stopped) return 0;

            if(score >= beta){
                return beta;
            }
        }

        int[] move_list = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        if (follow_pv){
            enablePVScoring(move_list, move_count);
        }

        ScoreMove.scoreMoves(this, chessboard, move_list, move_count, hash_move);

        int moves_searched = 0;

        int tt_best_move = 0;

        for (int count = 0; count < move_count; count++) {
            int move = ScoreMove.pickNextMove(this, count, move_count, move_list);

            ply++;
            MoveGenerator.makeStandardMove(chessboard, move);

            legal_moves++;

            if (moves_searched == 0) {
                score = -negamax(chessboard, -beta, -alpha, depth - 1);
            } else {
                boolean is_killer = (move == killer_moves[0][ply] || move == killer_moves[1][ply]);

                boolean do_lmr = (moves_searched >= FULL_DEPTH_MOVES &&
                        depth >= REDUCTION_LIMIT &&
                        !in_check &&
                        !EncodeMove.getMoveCapture(move) &&
                        EncodeMove.getMovePromoted(move) == 0 &&
                        !is_killer);

                if (do_lmr) {
                    int d = Math.min(depth, 63);
                    int m = Math.min(moves_searched, 63);
                    int reduction = reduction_table[d][m];
                    if (reduction < 1) reduction = 1;

                    int lmr_depth = Math.max(0, depth - 1 - reduction);

                    score = -negamax(chessboard, -(alpha + 1), -alpha, lmr_depth);

                    if (score > alpha) {
                        score = -negamax(chessboard, -(alpha + 1), -alpha, depth - 1);
                    }
                } else {
                    score = -negamax(chessboard, -(alpha + 1), -alpha, depth - 1);
                }

                if (score > alpha && score < beta) {
                    score = -negamax(chessboard, -beta, -alpha, depth - 1);
                }
            }

            ply--;
            MoveGenerator.unmakeStandardMove(chessboard, move);

            if (TimeControlVariables.stopped) return 0;
            moves_searched++;

            if (score > alpha) {
                tt_best_move = move;
                hash_flag = TranspositionTable.HASH_FLAG_EXACT;

                if(!EncodeMove.getMoveCapture(move)) {
                    history_moves
                            [EncodeMove.getMovePiece(move)]
                            [EncodeMove.getMoveTarget(move)] += depth;
                }

                alpha = score;
                pv_table[ply][ply] = move;

                if (pv_length[ply + 1] - (ply + 1) >= 0)
                    System.arraycopy(pv_table[ply + 1], ply + 1, pv_table[ply],
                            ply + 1, pv_length[ply + 1] - (ply + 1));

                pv_length[ply] = pv_length[ply + 1];

                if (score >= beta) {
                    TranspositionTable.writeHashEntry(this, chessboard, beta, depth, TranspositionTable.HASH_FLAG_BETA, move);

                    if(!EncodeMove.getMoveCapture(move)){
                        killer_moves[1][ply] = killer_moves[0][ply];
                        killer_moves[0][ply] = move;
                    }
                    return beta;
                }
            }
        }

        if(legal_moves == 0){
            if (in_check){
                return -MATE_VALUE + ply;
            }
            else {
                return 0;
            }
        }

        TranspositionTable.writeHashEntry(this, chessboard, alpha, depth, hash_flag, tt_best_move);
        return alpha;
    }

    private void enablePVScoring(int[] move_list, int move_count) {
        follow_pv = false;
        for (int count = 0; count < move_count; count++){
            if (pv_table[0][ply] == move_list[count]) {
                score_pv = true;
                follow_pv = true;
                break;
            }
        }
    }

    @Override
    public void run() {
        long start_time_ms = TimeUtils.getTimeMs();
        int score;

        nodes = 0;
        follow_pv = false;
        score_pv = false;

        for (int[] killerMove : killer_moves) Arrays.fill(killerMove, 0);
        for (int[] historyMove : history_moves) Arrays.fill(historyMove, 0);
        for (int[] pvTable : pv_table) Arrays.fill(pvTable, 0);
        Arrays.fill(pv_length, 0);

        int alpha = -INFINITY;
        int beta = INFINITY;

        int last_best_move = 0;

        for (int current_depth = 1; current_depth <= maxDepth; current_depth++) {
            if (TimeControlVariables.stopped) break;

            follow_pv = true;
            score = negamax(chessboard, alpha, beta, current_depth);

            if (TimeControlVariables.stopped) {
                break;
            }

            if (score <= alpha || score >= beta) {
                alpha = -INFINITY;
                beta = INFINITY;
                current_depth--;
                continue;
            }

            alpha = score - VAL_WINDOW;
            beta = score + VAL_WINDOW;

            if (threadId == 0) {
                long stop_time = TimeUtils.getTimeMs();
                StringBuilder sb = new StringBuilder();

                sb.append("info");
                if (score > -MATE_VALUE && score < -MATE_SCORE) {
                    sb.append(" score mate ").append(-(score + MATE_VALUE) / 2 - 1);
                } else if (score > MATE_SCORE && score < MATE_VALUE) {
                    sb.append(" score mate ").append((MATE_VALUE - score) / 2 + 1);
                } else {
                    sb.append(" score cp ").append(score);
                }
                sb.append(" depth ").append(current_depth);
                sb.append(" nodes ").append(getTotalNodes());
                sb.append(" time ").append(stop_time - start_time_ms);
                sb.append(" pv ");

                for (int count = 0; count < pv_length[0]; count++) {
                    sb.append(EncodeMove.moveToString(pv_table[0][count],
                            chessboard.gameVariants == GameVariants.CHESS960)).append(" ");
                }
                System.out.println(sb);
            }

            if (!TimeControlVariables.stopped && pv_length[0] > 0) {
                best_move = pv_table[0][0];
            }

            if (TimeControlVariables.timeset) {
                int current_best_move = pv_table[0][0];
                long elapsedTime = TimeUtils.getTimeMs() - TimeControlVariables.starttime;

                long soft_time_limit = (current_best_move != last_best_move) ?
                        (long)(TimeControlVariables.optTime * 0.7) :
                        (long)(TimeControlVariables.optTime * 0.5);

                if (elapsedTime > soft_time_limit) {
                    break;
                }

                last_best_move = current_best_move;
            }
        }
    }

    public static void searchPosition(Chessboard chessboard, int depth){
        TimeControlVariables.stopped = false;
        workers = new Search[MAX_THREADS];
        Thread[] threads = new Thread[MAX_THREADS];

        for (int i = 0; i < MAX_THREADS; i++) {
            Chessboard threadLocalBoard = new Chessboard(chessboard);

            workers[i] = new Search(i, threadLocalBoard, depth);
            threads[i] = new Thread(workers[i]);
            threads[i].start();
        }

        for (int i = 0; i < MAX_THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int final_best_move = workers[0].best_move;

        if (final_best_move == 0) {
            final_best_move = workers[0].pv_table[0][0];
        }

        System.out.println("bestmove " +
                EncodeMove.moveToString(final_best_move,
                        chessboard.gameVariants == GameVariants.CHESS960));
    }
}