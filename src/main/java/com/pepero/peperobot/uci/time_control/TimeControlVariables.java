package com.pepero.peperobot.uci.time_control;

public class TimeControlVariables {
    // exit from engine flag
    public static boolean quit = false;

    // exit from calculation
    public static boolean stopped = false;

    // UCI "movestogo" command moves counter
    public static int movestogo = 30;

    // UCI "movetime" command time counter
    public static int movetime = -1;

    // UCI "time" : target calculation time available for move
    public static long time = -1;

    // UCI "inc" command's time increment holder
    public static long inc = 0;

    // UCI "starttime" command time holder
    public static long starttime = 0;

    // UCI "stoptime" command time holder
    public static long stoptime = 0;

    public static long optTime = 0;

    public static long maxTime = 0;

    // variable to flag time control availability
    public static boolean timeset = false;
}
