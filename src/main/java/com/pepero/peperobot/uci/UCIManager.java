package com.pepero.peperobot.uci;

import com.pepero.peperobot.hash.TranspositionTable;
import com.pepero.peperobot.uci.time_control.TimeControlVariables;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.util.TimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UCIManager {
    public static Chessboard chessboardUCI = new Chessboard(Chessboard.start_position);

    /*
        GUI -> isready
        Engine -> readyok
        GUI -> ucinewgame

     */

    /**
     * A bridge function to interact between search and GUI input
     */
    public static void communicate() {
        // if time is up, break here
        if (TimeControlVariables.timeset && TimeUtils.getTimeMs() > TimeControlVariables.stoptime) {
            // tell the engine to stop calculating
            TimeControlVariables.stopped = true;
        }

        try {
            // read GUI input from STDIN (Non-blocking)
            if (System.in.available() > 0) {
                StringBuilder sb = new StringBuilder();
                while (System.in.available() > 0) {
                    sb.append((char) System.in.read());
                }

                String input = sb.toString();

                // match UCI "quit" command
                if (input.contains("quit")) {
                    TimeControlVariables.quit = true;
                    TimeControlVariables.stopped = true;
                }
                // match UCI "stop" command
                else if (input.contains("stop")) {
                    TimeControlVariables.stopped = true;
                }
            }
        } catch (Exception e) {
            // ignore IO exceptions during search
        }
    }

    // main UCI loop
    public static void uciLoop() throws IOException {
        // reset BufferedReader
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        // main game loop
        while (true){
            // get user / GUI input
            String input = userInput.readLine();

            // parse UCI "isready" command
            if(input.equals("isready")){
                System.out.println("readyok");
            }

            // parse UCI "position" command
            else if(input.startsWith("position")){
                // call parse position function
                UCIParse.parsePosition(chessboardUCI, input);

                // clear hash table
                TranspositionTable.clearTT();
            }

            // parse UCI "ucinewgame" command
            else if(input.equals("ucinewgame")){
                // call parse position function
                chessboardUCI.setStartPos();

                // clear hash table
                TranspositionTable.clearTT();
            }

            // parse UCI "go" command
            else if(input.startsWith("go")){
                UCIParse.parseGo(chessboardUCI, input);
            }

            // parse UCI "quit" command
            else if(input.equals("quit")){
                // quit from the chess engine program execution
                TimeControlVariables.quit = true;
                TimeControlVariables.stopped = true;
                break;
            }

            // parse UCI "uci" command
            else if(input.equals("uci")){
                // print engine info
                System.out.println("id name Peperobot");
                System.out.println("id author pepero-lover");
                System.out.println("uciok");
            }
        }
    }
}
