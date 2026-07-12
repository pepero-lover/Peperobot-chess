package com.pepero.peperobot;

import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.encode.EncodeMove;
import com.pepero.peperobot.tuning.EpdParser;

import java.io.IOException;

public class Test {
    public static void main(String[] args) throws IOException {
        EpdParser.load("engine tuning/quiet-labeled.epd");
    }
}
