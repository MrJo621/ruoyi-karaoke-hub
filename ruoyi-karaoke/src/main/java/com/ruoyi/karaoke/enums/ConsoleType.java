package com.ruoyi.karaoke.enums;


import java.util.HashMap;
import java.util.Map;

public enum ConsoleType {
    HEARTBEAT("0", "心跳", "heartbeatStrategyService"),
    START("1", "开始播放", "startStrategyService"),
    STOP("2", "停止播放", "stopStrategyService"),
    RESTART("3", "重新播放", "restartStrategyService"),
    VOCALS("4", "开启原唱", "vocalsStrategyService"),
    ACCOMPANIMENT("5", "关闭原唱", "accompanimentStrategyService"),
    ADJUST_VOCALS("6", "调节人声", "adjustVocalsStrategyService"),
    ADJUST_ACCOMPANIMENT("7", "调节伴奏", "adjustAccompanimentStrategyService"),
    CUT_SONG("8", "切歌", "songCutStrategyService"),
    SEEK_POSITION("9", "跳转进度", "echoStrategyService"),
    SYNC_POSITION("10", "同步进度", "echoStrategyService"),
    LIST_SONGS("21", "歌单列表", "listSongsStrategyService"),
    ADD_SONGS("22", "添加歌单", "addSongsStrategyService"),
    REMOVE_SONG("23", "移除歌单", "removeSongStrategyService"),
    UP_SONG("24", "上移", "upSongStrategyService"),
    TOP_SONG("25", "置顶", "topSongStrategyService"),
    PREV_SONG("26", "上一曲", "echoStrategyService");

    private final String code;
    private final String info;
    private final String strategy;

    ConsoleType(String code, String info, String strategy) {
        this.code = code;
        this.info = info;
        this.strategy = strategy;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

    public String getStrategy() {
        return strategy;
    }

    // key为billType
    private static final Map<String, ConsoleType> mappings = new HashMap<>();

    static {
        for (ConsoleType consoleType : values()) {
            mappings.put(consoleType.code, consoleType);
        }
    }


    public static ConsoleType match(String code) {
        return mappings.get(code);
    }

    /**
     * 获取策略
     */
    public static String getStrategy(String code) {
        for (ConsoleType value : ConsoleType.values()) {
            if (value.getCode().equals(code)) {
                return value.getStrategy();
            }
        }
        return null;
    }
}
