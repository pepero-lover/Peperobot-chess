package com.pepero.peperobot.hash;

import com.pepero.peperobot.Search;
import com.pepero.jcb.core.Chessboard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static com.pepero.peperobot.Search.MATE_SCORE;

/**
 * 멀티스레드(Lazy SMP) 환경에서 안전하게 동작하는 트랜스포지션 테이블.
 *
 * 기존 구현은 key/depth/flag/score/bestMove를 각각 별도의 plain int[]/long[]에
 * 나눠 저장했기 때문에, 여러 스레드가 동시에 같은 슬롯에 write 하면
 *   1) 필드들 사이의 갱신 순서가 보이지 않아 다른 스레드가 "반쯤 갱신된" 엔트리를 읽을 수 있고
 *   2) 최악의 경우 포지션 A의 key와 포지션 B의 score/move가 뒤섞여 반환되는
 *      torn read(찢어진 엔트리) 문제가 발생할 수 있었습니다.
 *
 * 이를 해결하기 위해 체스 엔진에서 흔히 쓰는 "lockless hashing" 기법을 적용했습니다.
 *   - data  : depth / flag / score / bestMove 를 하나의 long 에 비트 패킹
 *   - check : hash_key XOR data
 * 읽을 때 check XOR data 를 다시 계산해 실제 hash_key와 비교합니다.
 * 두 필드(check, data)가 서로 다른 write 에 의해 반씩 섞이면 이 XOR 검증이
 * 실패하므로, 락 없이도 깨진 엔트리를 안전하게 걸러낼 수 있습니다.
 *
 * ---- 메모리 배리어 최적화 (AtomicLongArray → VarHandle acquire/release) ----
 * AtomicLongArray.get/set은 완전한 volatile(순차 일관성) 의미론이라 x86에서는
 * set()마다 mfence(store-load 배리어)가 들어갑니다. writeHashEntry는 거의 모든
 * 탐색 노드에서 호출되므로, 이 mfence 비용이 스레드 수만큼 누적되어 멀티스레드
 * 확장성을 심하게 깎아먹을 수 있습니다.
 *
 * 이 lockless 알고리즘이 실제로 요구하는 건 "check를 release로 쓰고 acquire로
 * 읽으면, 그보다 프로그램 순서상 앞선 data write가 acquire한 스레드에게도
 * 보이는" release-acquire happens-before 관계뿐입니다 (순차 일관성까지는 불필요).
 * 그래서 두 배열을 plain long[] + VarHandle의 getAcquire/setRelease로 접근합니다.
 *   - write: data를 (plain으로) 먼저 쓰고, check를 setRelease로 나중에 쓴다.
 *   - read : check를 getAcquire로 먼저 읽고, data를 (plain으로) 나중에 읽는다.
 * 이 순서(끝에 쓴 필드를 먼저 읽기)가 release-acquire 페어링의 핵심입니다.
 * x86에서 release store는 mfence 없이 일반 store로 컴파일되어 훨씬 저렴합니다.
 *
 * 배열 참조 자체(tt_checks/tt_data 필드)는 resize/clear 시 교체되므로 그대로
 * volatile 필드로 유지해, 배열 교체가 다른 스레드에도 즉시 보이게 합니다.
 */
public class TranspositionTable {
    // 해시 테이블 크기 (엔트리 개수, 항상 2의 거듭제곱)
    public static volatile int TT_SIZE = 0x2000000;

    // no hash entry found constant
    public static final int NO_HASH_ENTRY = 100000;

    // transposition table hash flags
    public static final int HASH_FLAG_EXACT = 0;
    public static final int HASH_FLAG_ALPHA = 1;
    public static final int HASH_FLAG_BETA  = 2;

    // 엔트리 하나당 크기: check(long, 8B) + data(long, 8B) = 16 bytes
    // (기존 24 bytes 대비 더 촘촘해서, 같은 hash MB 설정으로 더 많은 엔트리를 담을 수 있습니다)
    private static final int ENTRY_SIZE = 16;

    // ---- data 비트 패킹 레이아웃 (총 64비트) ----
    // [63:62] flag (2 bits)
    // [61:54] depth (8 bits, 0~255)
    // [53:32] score (22 bits, offset 인코딩으로 부호 없는 값으로 저장)
    // [31:0]  best_move (32 bits, 원본 int 값 그대로)
    private static final int MOVE_SHIFT  = 0;
    private static final int SCORE_SHIFT = 32;
    private static final int DEPTH_SHIFT = 54;
    private static final int FLAG_SHIFT  = 62;

    private static final long MOVE_MASK  = 0xFFFFFFFFL;       // 32 bits
    private static final long SCORE_MASK = 0x3FFFFFL;         // 22 bits
    private static final long DEPTH_MASK = 0xFFL;             // 8 bits
    private static final long FLAG_MASK  = 0x3L;               // 2 bits

    // score는 음수가 될 수 있으므로 offset을 더해 부호 없는 필드에 저장합니다.
    // score 범위는 대략 -INFINITY(-50000) ~ +INFINITY(50000) + ply 보정치 정도이므로
    // 여유 있게 1<<20 만큼 offset을 줍니다 (22 bits면 최대 4,194,303까지 표현 가능).
    private static final long SCORE_OFFSET = 1L << 20;

    // long[] 배열의 개별 원소에 acquire/release로 접근하기 위한 VarHandle.
    private static final VarHandle LONG_ARRAY = MethodHandles.arrayElementVarHandle(long[].class);

    // 트랜스포지션 테이블 본체
    // tt_checks[index] = hash_key XOR tt_data[index]
    // tt_data[index]   = packed(depth, flag, score, best_move)
    // 배열 참조 자체는 resize/clear 시 통째로 교체되므로 volatile로 유지한다.
    private static volatile long[] tt_checks = new long[TT_SIZE];
    private static volatile long[] tt_data   = new long[TT_SIZE];

    private static long packData(int depth, int flag, int score, int best_move) {
        long d = ((long) best_move & MOVE_MASK) << MOVE_SHIFT;
        d |= (((long) score + SCORE_OFFSET) & SCORE_MASK) << SCORE_SHIFT;
        d |= ((long) depth & DEPTH_MASK) << DEPTH_SHIFT;
        d |= ((long) flag & FLAG_MASK) << FLAG_SHIFT;
        return d;
    }

    private static int unpackMove(long data) {
        return (int) ((data >>> MOVE_SHIFT) & MOVE_MASK);
    }

    private static int unpackScore(long data) {
        return (int) (((data >>> SCORE_SHIFT) & SCORE_MASK) - SCORE_OFFSET);
    }

    private static int unpackDepth(long data) {
        return (int) ((data >>> DEPTH_SHIFT) & DEPTH_MASK);
    }

    private static int unpackFlag(long data) {
        return (int) ((data >>> FLAG_SHIFT) & FLAG_MASK);
    }

    // 해시 테이블 크기 재조정. 탐색이 진행 중이 아닐 때(UCI setoption 등) 호출된다는
    // 전제하에 synchronized로 배열 교체를 원자적으로 보이게 합니다.
    public static synchronized void resizeTT(int hashSizeMb) {
        if (hashSizeMb <= 0) {
            // disable transposition table
            TT_SIZE = 0;
            tt_checks = new long[0];
            tt_data   = new long[0];
            return;
        }

        long bytes = (long) hashSizeMb * 1024 * 1024;

        long maxEntries = bytes / ENTRY_SIZE;

        if (maxEntries > Integer.MAX_VALUE) {
            maxEntries = Integer.MAX_VALUE;
        }

        int targetEntries = (int) maxEntries;
        targetEntries = Integer.highestOneBit(Math.max(targetEntries, 1));

        // resize TT size + 새 배열로 교체 (이미 전부 0으로 초기화된 상태)
        TT_SIZE = targetEntries;
        tt_checks = new long[targetEntries];
        tt_data   = new long[targetEntries];
    }

    // reset transposition table
    public static synchronized void clearTT() {
        int size = TT_SIZE;
        // 새 배열로 교체하는 편이 기존 배열을 하나씩 0으로 채우는 것보다
        // 훨씬 빠르고, 참조 교체이므로 다른 스레드에서도 즉시 반영됩니다.
        tt_checks = new long[size];
        tt_data   = new long[size];
    }

    public static int readHashMove(Chessboard chessboard) {
        // resize/clear 도중 배열이 교체될 수 있으므로 로컬 변수로 한 번만 참조를 잡는다.
        long[] checks = tt_checks;
        long[] data   = tt_data;
        int size = TT_SIZE;
        if (size == 0) return 0;

        int index = (int) (chessboard.hash_key & (size - 1));

        // write 쪽에서 check를 마지막에 setRelease 하므로, read도 check를 먼저
        // getAcquire 해야 release-acquire happens-before가 성립해서 뒤이어 읽는
        // data가 그 check와 짝지어졌던(혹은 더 최신인) 값임이 보장된다.
        long check = (long) LONG_ARRAY.getAcquire(checks, index);
        long d = (long) LONG_ARRAY.getAcquire(data, index);

        // lockless 검증: 다른 스레드가 쓰는 도중에 읽었다면 이 등식이 성립하지 않는다.
        if ((check ^ d) == chessboard.hash_key) {
            return unpackMove(d);
        }
        return 0;
    }

    // read hash entry data
    public static int readHashEntry(Search search, Chessboard chessboard, int alpha, int beta, int depth){
        long[] checks = tt_checks;
        long[] data   = tt_data;
        int size = TT_SIZE;
        if (size == 0) return NO_HASH_ENTRY;

        int index = (int) (chessboard.hash_key & (size - 1));

        long check = (long) LONG_ARRAY.getAcquire(checks, index);
        long d = (long) LONG_ARRAY.getAcquire(data, index);

        if ((check ^ d) == chessboard.hash_key) {
            int tt_depth = unpackDepth(d);

            if (tt_depth >= depth) {
                int score = unpackScore(d);
                int tt_flag = unpackFlag(d);

                if (score < -MATE_SCORE) score += search.ply;
                if (score > MATE_SCORE)  score -= search.ply;

                if (tt_flag == HASH_FLAG_EXACT) {
                    return score;
                }
                if (tt_flag == HASH_FLAG_ALPHA && score <= alpha) {
                    return alpha;
                }
                if (tt_flag == HASH_FLAG_BETA && score >= beta) {
                    return beta;
                }
            }
        }
        return NO_HASH_ENTRY;
    }

    public static void writeHashEntry(Search search, Chessboard chessboard, int score, int depth, int hash_flag, int best_move){
        long[] checks = tt_checks;
        long[] data   = tt_data;
        int size = TT_SIZE;
        if (size == 0) return;

        int index = (int) (chessboard.hash_key & (size - 1));

        long old_check = (long) LONG_ARRAY.getAcquire(checks, index);
        long old_data  = (long) LONG_ARRAY.getAcquire(data, index);

        // 기존 엔트리가 (검증상) 유효하고, 더 깊은 depth로 이미 채워져 있으며,
        // 새로 쓰려는 게 EXACT가 아니라면 굳이 덮어쓰지 않는다 (기존 로직과 동일).
        if ((old_check ^ old_data) == chessboard.hash_key
                && unpackDepth(old_data) > depth
                && hash_flag != HASH_FLAG_EXACT) {
            return;
        }

        if (score < -MATE_SCORE) score -= search.ply;
        if (score > MATE_SCORE)  score += search.ply;

        long new_data  = packData(depth, hash_flag, score, best_move);
        long new_check = chessboard.hash_key ^ new_data;

        // lockless write: data -> check 순서로 쓴다.
        // 두 필드는 서로 다른 long 슬롯이라 "동시에" 원자적으로 바뀌진 않지만,
        // 읽는 쪽에서 check ^ data == hash_key 검증을 하기 때문에
        // 다른 스레드의 쓰기와 겹쳐서 절반씩 섞인 엔트리를 읽더라도
        // (거의 항상) 검증에 실패해 안전하게 버려진다.
        //
        // data는 plain(setRelease로 통일해도 무방하지만 setPlain이면 더 저렴)으로 먼저 쓰고,
        // check를 setRelease로 나중에 써서 release-acquire 페어링을 완성한다.
        // (read 쪽이 check를 getAcquire로 먼저 읽으므로, 여기서 check을 마지막에
        //  release로 써야 그보다 앞선 data write가 read 스레드에도 보이는 것이 보장된다.)
        LONG_ARRAY.setRelease(data, index, new_data);
        LONG_ARRAY.setRelease(checks, index, new_check);
    }
}