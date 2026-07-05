package com.pepero.peperobot;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;

public class Test {
    public static void main(String[] args) {
        Chessboard chessboard = new Chessboard("r1bqkb1r/pppp1ppp/2n2n2/3Pp3/2P5/8/PP2PPPP/RNBQKBNR w KQkq e6 0 4");
        int[] move_list = new int[512];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);
        for(int i=0;i<move_count;i++) {
            System.out.println(EncodeMove.moveToString(move_list[i], false));
        }
    }
}
