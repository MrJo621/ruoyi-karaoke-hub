package com.ruoyi.karaoke.service;

/**
 * 人声/伴奏分离策略。
 */
public interface VocalSeparatorStrategy extends VocalSeparator {

    /**
     * 策略名称，对应配置 karaoke.vocal-separator.engine。
     */
    String engine();

    /**
     * 当前策略是否可用。
     */
    default boolean available() {
        return true;
    }

    /**
     * 策略不可用原因。
     */
    default String unavailableReason() {
        return "";
    }
}
