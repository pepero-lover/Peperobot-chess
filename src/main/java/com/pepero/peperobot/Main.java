package com.pepero.peperobot;

import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.core.Chessboard;
import com.pepero.peperobot.uci.UCIManager;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.Initializer;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        // init all
        Initializer.init();

        Search.initSyzygy("syzygy/");

        // debug mode variable
        boolean debug = false;

        // if debugging
        if(debug) {
            System.out.println("        Debugging");

            ChessboardUtils.printChessBoard(UCIManager.chessboardUCI);

            Search.searchPosition(UCIManager.chessboardUCI, 10);
        } else {
            // connect to the GUI
            UCIManager.uciLoop();
        }
    }
}
