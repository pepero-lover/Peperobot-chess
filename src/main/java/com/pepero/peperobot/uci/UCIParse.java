package com.pepero.peperobot.uci;

import com.pepero.peperobot.Search;
import com.pepero.peperobot.uci.time_control.TimeControlVariables;
import com.pepero.jcb.constant.MoveCache;
import com.pepero.jcb.constant.SideToMove;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.jcb.util.TimeUtils;

import java.util.StringTokenizer;

import static com.pepero.jcb.constant.EncodedPieces.*;

public class UCIParse {

    // parse user/GUI move string input (e.g. "e7e8q")

    /**
     * Parse string move input
     *
     * @param command move input on uci/gui
     * @param chessboard chess board
     * @return parsed move
     * (if move is a legal move, returns move. otherwise, returns 0)
     */
    private static int parseMove(Chessboard chessboard, String command) {
        // create move list instance
        int[] move_list = MoveCache.SEARCH_MOVE_CACHE.get()[chessboard.ply];

        // generate moves
        int count = MoveGenerator.generateMoves(chessboard,move_list);

        // parse target square
        int source_square = (command.charAt(0) - 'a') + (8 - (command.charAt(1) - '0')) * 8;
        int target_square = (command.charAt(2) - 'a') + (8 - (command.charAt(3) - '0')) * 8;

        // loop over the moves within a move list
        for (int move_count = 0; move_count < count; move_count++){
            // init move
            int move = move_list[move_count];

            // make sure source & target squares are available within the generated move
            if(source_square == EncodeMove.getMoveSource(move) &&
                    target_square == EncodeMove.getMoveTarget(move)){
                // init promoted piece
                int promoted_piece = EncodeMove.getMovePromoted(move);

                // promoted piece is available
                if(promoted_piece != 0) {
                    // promoted to queen
                    if ((promoted_piece == Q || promoted_piece == q) && command.charAt(4) == 'q')
                        // return legal move
                        return move;

                        // promoted to rook
                    else if ((promoted_piece == R || promoted_piece == r) && command.charAt(4) == 'r')
                        // return legal move
                        return move;

                        // promoted to bishop
                    else if ((promoted_piece == B || promoted_piece == b) && command.charAt(4) == 'b')
                        // return legal move
                        return move;

                        // promoted to knight
                    else if ((promoted_piece == N || promoted_piece == n) && command.charAt(4) == 'n')
                        // return legal move
                        return move;

                    // continue the loop on possible wrong promotions (e.g. "e7e8f")
                    continue;
                }

                // return legal move
                return move;
            }
        }

        // return illegal move
        return 0;
    }


    /*
        Example UCI commands to init position on chess board

        // init start position
        position startpos

        // init start position and make the moves on chess board
        position startpos moves e2e4 e7e5

        // init position from FEN string
        position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1

        // init position from fen string and moves on chess board
        position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 moves e2a6 e8g8
     */

    /**
     * Parse Position command input
     *
     * @param chessboard chessboard
     * @param command the command that starts with "position "
     */
    public static void parsePosition(Chessboard chessboard,String command){
        // init string tokenizer (for more efficiency)
        StringTokenizer current_command = new StringTokenizer(command);

        if (!current_command.hasMoreTokens()) return;

        // removes the "position" token
        current_command.nextToken();

        // a type of starting position
        String type = current_command.nextToken();

        // parse UCI "fen" command
        if(type.equals("fen")){
            // position fen r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1

            StringBuilder sb = new StringBuilder();

            for(int i=0;i<6;i++){ // fen tokens count : 6
                // fen position
                // white turn or black turn
                // castling rights
                // enpassant move
                // half move
                // full move
                if(current_command.hasMoreTokens()) {
                    sb.append(current_command.nextToken()).append(" ");
                }

                // fen init failed
                else {
                    sb.setLength(0);
                    sb.append(Chessboard.start_position);

                    break;
                }
            }

            // init chess board with position from FEN string
            ChessboardUtils.parseFen(chessboard, sb.toString());
        }

        // parse UCI "startpos" command (and also catching input error)
        else {
            // init chess board with start position
            chessboard.setStartPos();
        }

        // moves available
        if(current_command.hasMoreTokens()){
            String token = current_command.nextToken();

            // make sure the token is "moves"
            if(token.equals("moves")){
                while (current_command.hasMoreTokens()){
                    // next moves
                    String next = current_command.nextToken();

                    // loop over moves within a move string
                    int move = parseMove(chessboard, next);

                    // make move on the chessboard
                    MoveGenerator.makeStandardMove(chessboard, move);
                }
            }
        }
    }


    /*
        Example UCI commands to make engine search for the best move

        // fixed depth search
        go depth 64

     */

    /**
     * Parse go command
     *
     * @param chessboard chessboard
     * @param command move input on uci/gui
     */
    public static void parseGo(Chessboard chessboard, String command){
        // init depth
        int depth = -1;

        // reset time values

        TimeControlVariables.time = -1;
        TimeControlVariables.inc = 0;
        TimeControlVariables.movetime = -1;
        TimeControlVariables.movestogo = 30;
        TimeControlVariables.timeset = false;
        TimeControlVariables.stopped = false;

        String[] tokens = command.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            try {
                if (tokens[i].equals("depth")) {
                    depth = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("wtime") && chessboard.side == SideToMove.white) {
                    TimeControlVariables.time = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("btime") && chessboard.side == SideToMove.black) {
                    TimeControlVariables.time = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("winc") && chessboard.side == SideToMove.white) {
                    TimeControlVariables.inc = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("binc") && chessboard.side == SideToMove.black) {
                    TimeControlVariables.inc = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("movestogo")) {
                    TimeControlVariables.movestogo = Integer.parseInt(tokens[i + 1]);
                } else if (tokens[i].equals("movetime")) {
                    TimeControlVariables.movetime = Integer.parseInt(tokens[i + 1]);
                }
            } catch (Exception e) {
                // ignore parsing exception
            }
        }

        // start timer
        TimeControlVariables.starttime = TimeUtils.getTimeMs();

        // when the remaining time is given
        if (TimeControlVariables.movetime != -1) {
            TimeControlVariables.timeset = true;

            long mTime = TimeControlVariables.movetime - 50;
            mTime = Math.max(mTime, 50);

            TimeControlVariables.optTime = mTime;
            TimeControlVariables.maxTime = mTime;
            TimeControlVariables.stoptime = TimeControlVariables.starttime + mTime;

        }
        else if (TimeControlVariables.time != -1) {
            TimeControlVariables.timeset = true;

            long time = TimeControlVariables.time;
            long inc = TimeControlVariables.inc;
            int movestogo = TimeControlVariables.movestogo;

            if (movestogo == 0) movestogo = 30;

            long optTime = (time / Math.max(movestogo, 30)) + (inc * 3 / 4);
            long maxTime = Math.min(time / 5, optTime * 5);

            optTime -= 50;
            maxTime -= 50;

            optTime = Math.max(optTime, 50);
            maxTime = Math.max(maxTime, 50);

            TimeControlVariables.optTime = optTime;
            TimeControlVariables.maxTime = maxTime;
            TimeControlVariables.stoptime = TimeControlVariables.starttime + maxTime;
        }

        // if depth is not given
        if (depth == -1) {
            // max depth value
            depth = Search.MAX_PLY;
        }

        // search position
        Search.searchPosition(chessboard, depth);
    }
}
