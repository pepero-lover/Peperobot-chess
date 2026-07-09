package com.pepero.peperobot.hash;

import com.pepero.peperobot.Search;
import com.pepero.jcb.core.Chessboard;

import java.util.Arrays;

import static com.pepero.peperobot.Search.MATE_SCORE;

public class TranspositionTable {
    // hash table size
    public static int TT_SIZE = 0x2000000;

    // no hash entry found constant
    public static final int NO_HASH_ENTRY = 100000;

    // transposition table hash flags
    public static final int HASH_FLAG_EXACT = 0;
    public static final int HASH_FLAG_ALPHA = 1;
    public static final int HASH_FLAG_BETA  = 2;

    // hash entry size
    private static final int ENTRY_SIZE = 24;

    // transposition table
    private static long[] tt_keys       = new long[TT_SIZE];   // "almost" unique chess position identifier
    private static int[]  tt_depths     = new int[TT_SIZE];    // current search depth
    private static int[]  tt_flags      = new int[TT_SIZE];    // flag the type of node (fail-low/fail-high/PV)
    private static int[]  tt_scores     = new int[TT_SIZE];    // score (alpha/beta/PV)
    private static int[]  tt_best_moves = new int[TT_SIZE];    // bestmove tt

    public static void resizeTT(int hashSizeMb) {
        if (hashSizeMb <= 0) {
            // disable transposition table
            TranspositionTable.TT_SIZE = 0;
            tt_keys       = new long[0];
            tt_depths     = new int[0];
            tt_flags      = new int[0];
            tt_scores     = new int[0];
            tt_best_moves = new int[0];
            return;
        }

        long bytes = (long) hashSizeMb * 1024 * 1024;

        long maxEntries = bytes / ENTRY_SIZE;

        if (maxEntries > Integer.MAX_VALUE) {
            maxEntries = Integer.MAX_VALUE;
        }

        int targetEntries = (int) maxEntries;

        targetEntries = Integer.highestOneBit(targetEntries);

        // resize TT size
        TranspositionTable.TT_SIZE = targetEntries;

        // reset all tt value
        tt_keys = new long[targetEntries];
        tt_depths = new int[targetEntries];
        tt_flags = new int[targetEntries];
        tt_scores = new int[targetEntries];
        tt_best_moves = new int[targetEntries];

        clearTT();
    }

    // reset transposition table
    public static void clearTT(){
        // reset transposition table arrays
        Arrays.fill(tt_keys,0);
        Arrays.fill(tt_depths,0);
        Arrays.fill(tt_flags,0);
        Arrays.fill(tt_scores,0);
        Arrays.fill(tt_best_moves,0);
    }

    public static int readHashMove(Chessboard chessboard) {
        if (TT_SIZE == 0) return 0;

        int index = (int) (chessboard.hash_key & (TT_SIZE - 1));
        if (tt_keys[index] == chessboard.hash_key) {
            return tt_best_moves[index];
        }
        return 0;
    }

    // read hash entry data
    public static int readHashEntry(Search search, Chessboard chessboard, int alpha, int beta, int depth){
        if (TT_SIZE == 0) return NO_HASH_ENTRY;

        int index = (int) (chessboard.hash_key & (TT_SIZE - 1));

        long tt_key  = tt_keys[index];
        int tt_depth = tt_depths[index];
        int tt_flag  = tt_flags[index];
        int tt_score = tt_scores[index];

        if(tt_key == chessboard.hash_key) {
            if(tt_depth >= depth){
                int score = tt_score;

                if(score < -MATE_SCORE) score += search.ply;
                if(score > MATE_SCORE)  score -= search.ply;

                if(tt_flag == HASH_FLAG_EXACT){
                    return score;
                }
                if(tt_flag == HASH_FLAG_ALPHA && score <= alpha){
                    return alpha;
                }
                if(tt_flag == HASH_FLAG_BETA && score >= beta){
                    return beta;
                }
            }
        }
        return NO_HASH_ENTRY;
    }

    public static void writeHashEntry(Search search, Chessboard chessboard, int score, int depth, int hash_flag, int best_move){
        if (TT_SIZE == 0) return;

        int index = (int) (chessboard.hash_key & (TT_SIZE - 1));

        if (tt_keys[index] == chessboard.hash_key && tt_depths[index] > depth && hash_flag != HASH_FLAG_EXACT) {
            return;
        }

        if(score < -MATE_SCORE) score -= search.ply;
        if(score > MATE_SCORE)  score += search.ply;

        // write hash entry data
        tt_keys[index] = chessboard.hash_key;
        tt_scores[index] = score;
        tt_flags[index] = hash_flag;
        tt_depths[index] = depth;
        tt_best_moves[index] = best_move;
    }
}