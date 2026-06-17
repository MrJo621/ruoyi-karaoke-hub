package com.ruoyi.karaoke.service;

import com.ruoyi.karaoke.model.SplitAudioDTO;

import java.util.function.Consumer;

/**
 * 人声/伴奏分离策略接口。
 */
public interface VocalSeparator {

    SplitAudioDTO separate(String wavFilePath) throws Exception;

    /**
     * 带进度回调的分离（默认委托给 separate，ONNX 实现会实时回调）。
     * @param progressCallback 接收 0-100 的进度百分比
     */
    default SplitAudioDTO separate(String wavFilePath, Consumer<Integer> progressCallback) throws Exception {
        return separate(wavFilePath);
    }
}
