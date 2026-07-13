package com.pepero.peperobot;

import com.pepero.peperobot.evaluation.Evaluate;
import com.pepero.peperobot.evaluation.ScoreMove;
import com.pepero.peperobot.evaluation.SEE;
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
    public static final int VAL_WINDOW = 150;

    public static final int INFINITY   = 50000;
    public static final int MATE_VALUE = 49000;
    public static final int MATE_SCORE = 48000;

    public static final int FULL_DEPTH_MOVES = 3;
    public static final int REDUCTION_LIMIT = 3;

    private final int threadId;
    private final Chessboard chessboard;
    private final int maxDepth;

    public int ply;
    public long nodes = 0;
    public int[][] killer_moves = new int[2][MAX_PLY];
    public int[][] history_moves = new int[12][64];

    // history gravity/malus 적용을 위해, 이 노드(ply)에서 "실제로 탐색된" quiet move들을 기록해둔다.
    // (LMP로 건너뛴 수는 실제 평가를 받은 게 아니므로 포함하지 않는다)
    private static final int MAX_QUIET_TRACKED = 64;
    private final int[][] quiet_moves_at_ply = new int[MAX_PLY][MAX_QUIET_TRACKED];
    private final int[] quiet_count_at_ply = new int[MAX_PLY];

    // history 값이 무한정 커지지 않도록 gravity(감쇠) 방식으로 클램프하는 상한
    private static final int MAX_HISTORY = 16384;
    public boolean follow_pv;
    public boolean score_pv;
    public int[] pv_length = new int[MAX_PLY];
    public int[][] pv_table = new int[MAX_PLY][MAX_PLY];
    public int[] move_scores = new int[256];

    public static final int[] FUTILITY_MARGIN = { 0, 200, 300, 500 };

    // QS delta pruning 여유값 (졸 하나 정도의 안전 마진, 필요시 튜닝)
    private static final int DELTA_MARGIN = 200;

    public int best_move = 0;

    public static long nodeLimit = -1;

    private static final int[][] reduction_table = new int[64][64];

    public static Syzygy tablebase;

    private int root_ply;

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
        if (nodeLimit > 0 && MAX_THREADS == 1 && nodes >= nodeLimit) {
            TimeControlVariables.stopped = true;
        }

        if (ply >= MAX_PLY) {
            return Evaluate.evaluate(chessboard);
        }

        int alpha_orig = alpha;

        int tt_score = TranspositionTable.readHashEntry(this, chessboard, alpha, beta, 0);
        if (tt_score != TranspositionTable.NO_HASH_ENTRY) {
            return tt_score;
        }

        int hash_move = TranspositionTable.readHashMove(chessboard);

        boolean in_check = MoveGenerator.isSquareAttacked(chessboard,
                chessboard.side == white ?
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]):
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]),
                chessboard.side ^ 1);

        int evaluation = Evaluate.evaluate(chessboard);

        if (!in_check) {
            if (evaluation >= beta) {
                TranspositionTable.writeHashEntry(this, chessboard, beta, 0, TranspositionTable.HASH_FLAG_BETA, 0);
                return beta;
            }
            if (evaluation > alpha) {
                alpha = evaluation;
            }
        }

        int[] move_list = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);

        if (in_check) {
            ScoreMove.scoreMoves(this, chessboard, move_list, move_count, hash_move);
        } else {
            ScoreMove.scoreQuiescenceMoves(this, chessboard, move_list, move_count, hash_move);
        }

        int legal_moves = 0;
        int tt_best_move = 0;

        for (int count = 0; count < move_count; count++) {
            int move = ScoreMove.pickNextMove(this, count, move_count, move_list);

            if (!in_check && !EncodeMove.getMoveCapture(move)) {
                continue;
            }

            if (!in_check) {
                // 프로모션이나 메이트 스코어 근처에서는 물질 계산이 왜곡되므로 pruning 제외
                boolean near_mate = Math.abs(alpha) >= MATE_SCORE - MAX_PLY;
                boolean is_promotion = EncodeMove.getMovePromoted(move) != 0;

                if (!is_promotion && !near_mate) {
                    int captured_value = SEE.capturedValue(chessboard, move);
                    if (evaluation + captured_value + DELTA_MARGIN < alpha) {
                        continue;
                    }

                    if (SEE.see(chessboard, move) < 0) {
                        continue;
                    }
                }
            }

            ply++;
            MoveGenerator.makeStandardMove(chessboard, move);
            legal_moves++;

            int score = -quiescence(chessboard, -beta, -alpha);

            ply--;
            MoveGenerator.unmakeStandardMove(chessboard, move);

            if (TimeControlVariables.stopped) return 0;

            if (score > alpha) {
                alpha = score;
                tt_best_move = move;

                if (score >= beta) {
                    TranspositionTable.writeHashEntry(this, chessboard, beta, 0, TranspositionTable.HASH_FLAG_BETA, move);
                    return beta;
                }
            }
        }

        if (in_check && legal_moves == 0) {
            return -MATE_VALUE + ply;
        }

        int qs_hash_flag = (alpha > alpha_orig) ? TranspositionTable.HASH_FLAG_EXACT : TranspositionTable.HASH_FLAG_ALPHA;
        TranspositionTable.writeHashEntry(this, chessboard, alpha, 0, qs_hash_flag, tt_best_move);

        return alpha;
    }

    // depth가 깊을수록(=더 신뢰할 수 있는 정보일수록) 더 큰 bonus/malus를 준다.
    // 상한(1200)을 둬서 depth가 커져도 한 번에 history가 과도하게 흔들리지 않게 한다.
    private static int historyBonus(int depth) {
        return Math.min(1200, 16 * depth * depth);
    }

    // Gravity(감쇠) 방식 history 업데이트.
    // 새 bonus/malus를 그냥 더하는 대신, 현재 값이 클수록 실제 반영되는 양을 줄여서
    // (current - current*|bonus|/MAX_HISTORY) 값이 ±MAX_HISTORY 근처로 자연히 수렴하게 한다.
    // 이렇게 하면 오래된 기록이 무한정 쌓여 최근 정보를 압도하는 문제를 막을 수 있다.
    private void updateQuietHistory(int move, int bonus) {
        int piece = EncodeMove.getMovePiece(move);
        int to = EncodeMove.getMoveTarget(move);

        int clamped_bonus = Math.max(-MAX_HISTORY, Math.min(MAX_HISTORY, bonus));
        int current = history_moves[piece][to];

        history_moves[piece][to] = current + clamped_bonus - current * Math.abs(clamped_bonus) / MAX_HISTORY;
    }

    // 이 노드에서 cutoff/best move를 제외하고 "탐색은 했지만 더 나은 수에 밀린" quiet move들에게
    // malus를 주고, 최종 승자에게는 bonus를 준다. (History gravity + malus)
    private void applyQuietHistoryUpdate(int ply_index, int best_quiet_move, int bonus) {
        updateQuietHistory(best_quiet_move, bonus);

        int qcount = quiet_count_at_ply[ply_index];
        for (int i = 0; i < qcount; i++) {
            int qm = quiet_moves_at_ply[ply_index][i];
            if (qm != best_quiet_move) {
                updateQuietHistory(qm, -bonus);
            }
        }
    }

    private int negamax(Chessboard chessboard, int alpha, int beta, int depth) {
        int score;
        int hash_flag = TranspositionTable.HASH_FLAG_ALPHA;

        pv_length[ply] = ply;

        if(ply != 0 && ChessboardUtils.isRepetitionDraw(chessboard, root_ply)){
            return 0;
        }

        boolean pv_node = beta - alpha > 1;

        int tt_score = TranspositionTable.readHashEntry(this, chessboard, alpha, beta, depth);
        if(ply != 0 && tt_score != TranspositionTable.NO_HASH_ENTRY && !pv_node) {
            if (ChessboardUtils.getRepetitionCount(chessboard) == 0) {
                return tt_score;
            }
        }

        int hash_move = TranspositionTable.readHashMove(chessboard);

        if (threadId == 0 && (nodes & 2047) == 0) {
            UCIManager.communicate();
            if (nodeLimit > 0 && getTotalNodes() >= nodeLimit) {
                TimeControlVariables.stopped = true;
            }
        }

        if (ply >= MAX_PLY)
            return Evaluate.evaluate(chessboard);

        pv_length[ply] = ply;

        if (ply > 0 && tablebase != null && Long.bitCount(chessboard.occupancies[both]) <= tablebase.tb_largest()) {
            SyzygyPosition pos = new SyzygyPosition();

            pos.setWhite(Long.reverseBytes(chessboard.occupancies[white]));
            pos.setBlack(Long.reverseBytes(chessboard.occupancies[black]));
            pos.setKings(Long.reverseBytes(chessboard.bitboards[K] | chessboard.bitboards[k]));
            pos.setQueens(Long.reverseBytes(chessboard.bitboards[Q] | chessboard.bitboards[q]));
            pos.setRooks(Long.reverseBytes(chessboard.bitboards[R] | chessboard.bitboards[r]));
            pos.setBishops(Long.reverseBytes(chessboard.bitboards[B] | chessboard.bitboards[b]));
            pos.setKnights(Long.reverseBytes(chessboard.bitboards[N] | chessboard.bitboards[n]));
            pos.setPawns(Long.reverseBytes(chessboard.bitboards[P] | chessboard.bitboards[p]));

            pos.setRule50((byte) chessboard.half_ply);
            pos.setCastling((byte) 0);

            int flippedEp = (chessboard.enpassant == no_sq) ? 0 : (chessboard.enpassant ^ 56);
            pos.setEp((byte) flippedEp);
            pos.setTurn(chessboard.side == white);

            int[] results = new int[Syzygy.TB_MAX_MOVES];
            int rootRes = tablebase.tb_probe_root(pos, results);

            if (rootRes != Syzygy.TB_RESULT_FAILED) {
                int tbFrom = Syzygy.TB_GET_FROM(rootRes) ^ 56;
                int tbTo = Syzygy.TB_GET_TO(rootRes) ^ 56;
                int tbPromo = Syzygy.TB_GET_PROMOTES(rootRes);

                int[] root_moves = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
                int move_count = MoveGenerator.generateMoves(chessboard, root_moves);

                for (int count = 0; count < move_count; count++) {
                    int move = root_moves[count];

                    int moveFrom = EncodeMove.getMoveSource(move);
                    int moveTo = EncodeMove.getMoveTarget(move);
                    int movePromotion = EncodeMove.getMovePromoted(move);

                    if (moveFrom == tbFrom && moveTo == tbTo && (movePromotion == 0 || tbPromo == movePromotion)) {
                        int wdl = Syzygy.TB_GET_WDL(rootRes);
                        int dtz = Syzygy.TB_GET_DTZ(rootRes);

                        return (wdl == Syzygy.TB_WIN ? MATE_VALUE - dtz :
                                (wdl == Syzygy.TB_LOSS ? -MATE_VALUE + dtz : 0));
                    }
                }
            }
        }

        if (depth <= 0) {
            return quiescence(chessboard, alpha, beta);
        }

        nodes++;

        if (nodeLimit > 0 && getTotalNodes() >= nodeLimit) {
            TimeControlVariables.stopped = true;
        }

        boolean in_check = MoveGenerator.isSquareAttacked(chessboard,
                chessboard.side == white ?
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]):
                        BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]),
                chessboard.side ^ 1);

        if (in_check && ply < maxDepth + 5) {
            depth++;
        }

        // Internal Iterative Reduction (IIR):
        // TT에 이 노드에 대한 hash_move가 전혀 없다는 건 이 노드가 잘 탐색된 적이
        // 없다는 뜻이므로, depth를 한 칸 줄여서 먼저 대략적인 무브 오더링을 얻는다.
        // (in_check 노드는 이미 확장을 받았으므로 제외)
        if (depth >= 4 && hash_move == 0 && !in_check && TranspositionTable.TT_SIZE != 0) {
            depth--;
        }

        int legal_moves = 0;

        boolean has_non_pawn_material;
        if (chessboard.side == white) {
            has_non_pawn_material = (chessboard.bitboards[N] | chessboard.bitboards[B] |
                    chessboard.bitboards[R] | chessboard.bitboards[Q]) != 0;
        } else {
            has_non_pawn_material = (chessboard.bitboards[n] | chessboard.bitboards[b] |
                    chessboard.bitboards[r] | chessboard.bitboards[q]) != 0;
        }

        int static_eval = Evaluate.evaluate(chessboard);

        int king_danger = Evaluate.getKingSafetyPenalty(chessboard, chessboard.side);

        if (depth <= 5 && !in_check && !pv_node && Math.abs(beta) < MATE_SCORE) {
            int rfp_margin = 120 * depth;
            if (static_eval - rfp_margin >= beta) {
                return static_eval;
            }
        }

        if (depth >= 3 && !in_check && ply != 0 && has_non_pawn_material && king_danger <= 50){
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

        if (depth <= 3 && !in_check && !pv_node && Math.abs(alpha) < MATE_SCORE) {
            if (king_danger <= 40) {
                if (static_eval + FUTILITY_MARGIN[depth] <= alpha) {
                    return quiescence(chessboard, alpha, beta);
                }
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

        quiet_count_at_ply[ply] = 0;

        for (int count = 0; count < move_count; count++) {
            int move = ScoreMove.pickNextMove(this, count, move_count, move_list);

            boolean is_capture = EncodeMove.getMoveCapture(move);
            boolean is_promotion = EncodeMove.getMovePromoted(move) != 0;
            boolean is_killer_lmp = (move == killer_moves[0][ply] || move == killer_moves[1][ply]);

            // Late Move Pruning (Move Count Pruning):
            // 얕은 depth에서, 이미 충분히 많은 수를 본 뒤 나오는 "조용한" 후순위 수는
            // 아예 탐색하지 않고 건너뛴다. (LMR보다 한 단계 더 공격적인 프루닝)
            if (!pv_node && !in_check && depth <= 8
                    && !is_capture && !is_promotion && !is_killer_lmp
                    && Math.abs(alpha) < MATE_SCORE) {
                int lmp_threshold = 3 + depth * depth;
                if (moves_searched >= lmp_threshold) {
                    continue;
                }
            }

            // 실제로 탐색되는(=LMP로 건너뛰지 않은) quiet move만 gravity/malus 대상으로 기록한다.
            if (!is_capture && quiet_count_at_ply[ply] < MAX_QUIET_TRACKED) {
                quiet_moves_at_ply[ply][quiet_count_at_ply[ply]++] = move;
            }

            ply++;
            MoveGenerator.makeStandardMove(chessboard, move);

            legal_moves++;

            boolean gives_check = MoveGenerator.isSquareAttacked(chessboard,
                    chessboard.side == white ?
                            BitBoardUtils.getLS1BIndex(chessboard.bitboards[K]) :
                            BitBoardUtils.getLS1BIndex(chessboard.bitboards[k]),
                    chessboard.side ^ 1);

            if (moves_searched == 0) {
                score = -negamax(chessboard, -beta, -alpha, depth - 1);
            } else {
                boolean is_killer = (move == killer_moves[0][ply] || move == killer_moves[1][ply]);

                boolean do_lmr = (moves_searched >= FULL_DEPTH_MOVES &&
                        depth >= REDUCTION_LIMIT &&
                        !in_check &&
                        king_danger <= 40 &&
                        !gives_check &&
                        !EncodeMove.getMoveCapture(move) &&
                        EncodeMove.getMovePromoted(move) == 0 &&
                        !is_killer);

                if (do_lmr) {
                    int d = Math.min(depth, 63);
                    int m = Math.min(moves_searched, 63);
                    int reduction = reduction_table[d][m];

                    if (!pv_node) {
                        reduction++;
                    }

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

                alpha = score;
                pv_table[ply][ply] = move;

                if (pv_length[ply + 1] - (ply + 1) >= 0)
                    System.arraycopy(pv_table[ply + 1], ply + 1, pv_table[ply],
                            ply + 1, pv_length[ply + 1] - (ply + 1));

                pv_length[ply] = pv_length[ply + 1];

                if (score >= beta) {
                    TranspositionTable.writeHashEntry(this, chessboard, beta, depth, TranspositionTable.HASH_FLAG_BETA, move);

                    if(!EncodeMove.getMoveCapture(move)){
                        // fail-high를 낸 quiet move에는 bonus를, 이 노드에서 그보다 먼저
                        // 시도했다가 cutoff를 못 낸 다른 quiet move들에는 malus를 준다.
                        applyQuietHistoryUpdate(ply, move, historyBonus(depth));

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

        // cutoff 없이 노드가 끝난 경우(EXACT), 최종 best move가 quiet라면
        // fail-high 때보다 절반 크기의 bonus/malus로 history를 갱신한다.
        // (cutoff만큼 강한 신호는 아니지만, 이 노드에서 결국 가장 좋았던 수라는 정보는 유효하다)
        if (hash_flag == TranspositionTable.HASH_FLAG_EXACT && tt_best_move != 0
                && !EncodeMove.getMoveCapture(tt_best_move)) {
            applyQuietHistoryUpdate(ply, tt_best_move, historyBonus(depth) / 2);
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

        if (tablebase != null) {
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

                pos.setRule50((byte) chessboard.half_ply);
                pos.setCastling((byte) 0);

                int flippedEp = (chessboard.enpassant == no_sq) ? 0 : (chessboard.enpassant ^ 56);
                pos.setEp((byte) flippedEp);
                pos.setTurn(chessboard.side == white);

                int[] results = new int[Syzygy.TB_MAX_MOVES];
                int rootRes = tablebase.tb_probe_root(pos, results);

                if (rootRes != Syzygy.TB_RESULT_FAILED) {
                    int tbFrom = Syzygy.TB_GET_FROM(rootRes) ^ 56;
                    int tbTo = Syzygy.TB_GET_TO(rootRes) ^ 56;
                    int tbPromo = Syzygy.TB_GET_PROMOTES(rootRes);

                    int[] root_moves = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];
                    int move_count = MoveGenerator.generateMoves(chessboard, root_moves);

                    for (int count = 0; count < move_count; count++) {
                        int move = root_moves[count];

                        int moveFrom = EncodeMove.getMoveSource(move);
                        int moveTo = EncodeMove.getMoveTarget(move);
                        int movePromotion = EncodeMove.getMovePromoted(move);

                        if (moveFrom == tbFrom && moveTo == tbTo && (movePromotion == 0 || tbPromo == movePromotion)) {
                            best_move = move;

                            if (threadId == 0) {
                                int wdl = Syzygy.TB_GET_WDL(rootRes);
                                int dtz = Syzygy.TB_GET_DTZ(rootRes);

                                if(wdl == Syzygy.TB_WIN) {
                                    System.out.println("info depth 0 score mate " + (dtz / 2 + 1) +
                                            " pv " + EncodeMove.moveToString(move,
                                            chessboard.gameVariants == GameVariants.CHESS960));
                                } else if(wdl == Syzygy.TB_LOSS) {
                                    System.out.println("info depth 0 score mate " + ((-dtz) / 2 - 1) +
                                            " pv " + EncodeMove.moveToString(move,
                                            chessboard.gameVariants == GameVariants.CHESS960));
                                } else {
                                    System.out.println("info depth 0 score score 0 pv " + EncodeMove.moveToString(move,
                                            chessboard.gameVariants == GameVariants.CHESS960));
                                }
                            }

                            return;
                        }
                    }
                }
            }
        }

        root_ply = chessboard.ply;

        int alpha = -INFINITY;
        int beta = INFINITY;

        int last_best_move = 0;
        int fail_count = 0;

        for (int current_depth = 1; current_depth <= maxDepth; current_depth++) {
            if (TimeControlVariables.stopped) break;

            follow_pv = true;
            score = negamax(chessboard, alpha, beta, current_depth);

            if (TimeControlVariables.stopped) {
                break;
            }

            if (score <= alpha || score >= beta) {
                fail_count++;
                // 실패할 때마다 창을 지수적으로 넓힌다 (VAL_WINDOW * 2, 4, 8, 16, 32...)
                int expand = VAL_WINDOW * (1 << Math.min(fail_count, 5));

                if (score <= alpha) {
                    alpha = Math.max(-INFINITY, alpha - expand);
                } else {
                    beta = Math.min(INFINITY, beta + expand);
                }
                current_depth--;
                continue;
            }

            fail_count = 0;
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