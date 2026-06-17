package com.ruoyi.karaoke.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.karaoke.model.SplitAudioDTO;
import com.ruoyi.karaoke.service.VocalSeparator;
import com.ruoyi.karaoke.service.VocalSeparatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 人声分离策略上下文，按配置选择具体分离引擎。
 */
@Primary
@Service
public class VocalSeparatorContext implements VocalSeparator {

    private static final Logger log = LoggerFactory.getLogger(VocalSeparatorContext.class);

    @Value("${karaoke.vocal-separator.engine:onnx}")
    private String engine;

    private final Map<String, VocalSeparatorStrategy> strategies = new HashMap<>();

    public VocalSeparatorContext(List<VocalSeparatorStrategy> strategies) {
        for (VocalSeparatorStrategy strategy : strategies) {
            this.strategies.put(strategy.engine().toLowerCase(Locale.ROOT), strategy);
        }
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath) throws Exception {
        return separate(wavFilePath, null);
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath, Consumer<Integer> progressCallback) throws Exception {
        VocalSeparatorStrategy strategy = selectStrategy();
        log.info("使用 {} 引擎进行人声分离", strategy.engine());
        return strategy.separate(wavFilePath, progressCallback);
    }

    private VocalSeparatorStrategy selectStrategy() {
        String selectedEngine = normalizeEngine(engine);
        if ("auto".equals(selectedEngine)) {
            VocalSeparatorStrategy onnx = strategies.get("onnx");
            if (onnx != null && onnx.available()) {
                return onnx;
            }
            log.warn("ONNX 引擎不可用，auto 模式回退 DSP: {}", onnx == null ? "未注册 ONNX 策略" : onnx.unavailableReason());
            return requireStrategy("dsp");
        }

        VocalSeparatorStrategy strategy = requireStrategy(selectedEngine);
        if (!strategy.available()) {
            throw new ServiceException(strategy.engine().toUpperCase(Locale.ROOT) + " 引擎不可用: " + strategy.unavailableReason());
        }
        return strategy;
    }

    private VocalSeparatorStrategy requireStrategy(String engine) {
        VocalSeparatorStrategy strategy = strategies.get(engine);
        if (strategy == null) {
            throw new ServiceException("未找到人声分离引擎策略: " + engine);
        }
        return strategy;
    }

    private String normalizeEngine(String value) {
        if (value == null || value.trim().length() == 0) {
            return "onnx";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
