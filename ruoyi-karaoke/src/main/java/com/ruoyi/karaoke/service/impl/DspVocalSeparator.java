package com.ruoyi.karaoke.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.karaoke.model.SplitAudioDTO;
import com.ruoyi.karaoke.service.VocalSeparatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 基于 DSP 中置声道提取的人声/伴奏分离（ONNX 模型不可用时的回退方案）。
 *
 * 原理：在大多数立体声录音中，人声居中混音（左右声道等幅），
 * 而伴奏乐器分布在立体声场的不同位置。
 *
 * 人声 ≈ (L+R)/2
 * 伴奏 ≈ L/R 中抵消中置声道后的侧向信号
 *
 * 纯 Java 实现，零外部依赖（仅 javax.sound.sampled）。
 */
@Component
public class DspVocalSeparator implements VocalSeparatorStrategy {

    private static final Logger log = LoggerFactory.getLogger(DspVocalSeparator.class);

    private static final double DEFAULT_CENTER_CANCEL_FACTOR = 1.0;
    private static final double DEFAULT_VOCAL_HIGH_PASS_HZ = 100.0;
    private static final double DEFAULT_VOCAL_LOW_PASS_HZ = 10000.0;
    private static final double DEFAULT_ACCOMP_GAIN = 1.6;
    private static final double DEFAULT_VOCAL_GAIN = 1.2;
    private static final double DEFAULT_ACCOMP_DRY_MIX = 0.12;
    private static final double DEFAULT_VOCAL_SIDE_SUPPRESS = 0.20;
    private static final String DEFAULT_ACCOMP_CANCEL_MODE = "full";

    /** 伴奏中置声道抵消强度。1.0 会尽量去掉居中的人声。 */
    private final double centerCancelFactor;

    /** 人声高通滤波截止频率 (Hz)，只去除低频轰鸣，不能太高。 */
    private final double vocalHighPassHz;

    /** 人声低通滤波截止频率 (Hz)，减少高频伴奏泄露。 */
    private final double vocalLowPassHz;

    /** 侧向伴奏抵消中置后整体会变小，这里做少量补偿。 */
    private final double accompGain;

    /** 人声提取后做少量补偿，避免听起来偏小。 */
    private final double vocalGain;

    /** 伴奏保留少量原始干声，避免中置抵消后音乐过薄。 */
    private final double accompDryMix;

    /** 人声音轨里抵消少量侧向信号，减少左右声场乐器串入。 */
    private final double vocalSideSuppress;

    /** 伴奏抵消模式：full=全频中置抵消，band=仅人声频段抵消。 */
    private final String accompCancelMode;

    /** 分块处理的块大小（采样点数），约 30 秒 */
    private static final int CHUNK_SAMPLES = 44100 * 30;

    public DspVocalSeparator(
            @Value("${karaoke.vocal-separator.dsp.center-cancel-factor:1.0}") double centerCancelFactor,
            @Value("${karaoke.vocal-separator.dsp.vocal-high-pass-hz:100.0}") double vocalHighPassHz,
            @Value("${karaoke.vocal-separator.dsp.vocal-low-pass-hz:10000.0}") double vocalLowPassHz,
            @Value("${karaoke.vocal-separator.dsp.accomp-gain:1.6}") double accompGain,
            @Value("${karaoke.vocal-separator.dsp.vocal-gain:1.2}") double vocalGain,
            @Value("${karaoke.vocal-separator.dsp.accomp-dry-mix:0.12}") double accompDryMix,
            @Value("${karaoke.vocal-separator.dsp.vocal-side-suppress:0.20}") double vocalSideSuppress,
            @Value("${karaoke.vocal-separator.dsp.accomp-cancel-mode:full}") String accompCancelMode) {
        this.centerCancelFactor = centerCancelFactor;
        this.vocalHighPassHz = vocalHighPassHz;
        this.vocalLowPassHz = vocalLowPassHz;
        this.accompGain = accompGain;
        this.vocalGain = vocalGain;
        this.accompDryMix = accompDryMix;
        this.vocalSideSuppress = vocalSideSuppress;
        this.accompCancelMode = normalizeCancelMode(accompCancelMode);
    }

    @Override
    public String engine() {
        return "dsp";
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath) throws Exception {
        return separateInternal(wavFilePath, null);
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath, Consumer<Integer> progressCallback) throws Exception {
        return separateInternal(wavFilePath, progressCallback);
    }

    private SplitAudioDTO separateInternal(String wavFilePath, Consumer<Integer> progressCallback) throws Exception {
        File wavFile = new File(wavFilePath);
        if (!wavFile.exists()) {
            throw new ServiceException("音频文件不存在: " + wavFilePath);
        }
        report(progressCallback, 1);
        log.info("DSP 人声分离开始: {}, centerCancelFactor={}, vocalHighPassHz={}, vocalLowPassHz={}, accompGain={}, vocalGain={}, accompDryMix={}, vocalSideSuppress={}, accompCancelMode={}",
                wavFilePath, centerCancelFactor, vocalHighPassHz, vocalLowPassHz, accompGain, vocalGain,
                accompDryMix, vocalSideSuppress, accompCancelMode);

        AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat format = ais.getFormat();
        float sampleRate = format.getSampleRate();

        if (format.getChannels() < 2) {
            ais.close();
            throw new ServiceException("人声分离仅支持立体声音频，当前为单声道: " + wavFilePath);
        }

        String basePath = wavFilePath.replaceFirst("\\.(wav|WAV)$", "");
        String accompPath = basePath + "-accompaniment.wav";
        String vocalsPath = basePath + "-vocals.wav";

        File accompFile = new File(accompPath);
        File vocalsFile = new File(vocalsPath);

        ByteArrayOutputStream accompBaos = new ByteArrayOutputStream();
        ByteArrayOutputStream vocalsBaos = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int bytesRead;
        long totalFrames = ais.getFrameLength();

        while ((bytesRead = ais.read(buffer)) != -1) {
            accompBaos.write(buffer, 0, bytesRead);
            vocalsBaos.write(buffer, 0, bytesRead);
        }
        ais.close();
        report(progressCallback, 10);

        byte[] accompBytes = accompBaos.toByteArray();
        byte[] vocalsBytes = vocalsBaos.toByteArray();
        accompBaos.close();
        vocalsBaos.close();

        int totalSamples = accompBytes.length / (2 * 2); // 2 channels * 2 bytes per sample
        int channels = 2;

        ByteBuffer accompBuf = ByteBuffer.wrap(accompBytes).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer vocalsBuf = ByteBuffer.wrap(vocalsBytes).order(ByteOrder.LITTLE_ENDIAN);

        ShortBuffer accompShort = accompBuf.asShortBuffer();
        ShortBuffer vocalsShort = vocalsBuf.asShortBuffer();

        int lastPct = 10;

        for (int start = 0; start < totalSamples; start += CHUNK_SAMPLES) {
            int end = Math.min(start + CHUNK_SAMPLES, totalSamples);
            int chunkLen = end - start;

            double[] L = new double[chunkLen];
            double[] R = new double[chunkLen];

            for (int i = 0; i < chunkLen; i++) {
                int idx = start + i;
                L[i] = vocalsShort.get(idx * 2) / 32768.0;
                R[i] = vocalsShort.get(idx * 2 + 1) / 32768.0;
            }

            double[] centerSignal = new double[chunkLen];

            for (int i = 0; i < chunkLen; i++) {
                centerSignal[i] = (L[i] + R[i]) / 2.0;
            }

            double[] vocalBand = applyHighPass(centerSignal, vocalHighPassHz, sampleRate);
            vocalBand = applyLowPass(vocalBand, vocalLowPassHz, sampleRate);

            for (int i = 0; i < chunkLen; i++) {
                double side = (L[i] - R[i]) / 2.0;
                double cancelSource = "band".equals(accompCancelMode) ? vocalBand[i] : centerSignal[i];
                double cancel = cancelSource * centerCancelFactor;
                double vocal = (vocalBand[i] - side * vocalSideSuppress) * vocalGain;
                vocal = clamp(vocal, -1.0, 1.0);

                int idx = start + i;
                vocalsShort.put(idx * 2, (short) (vocal * 32767.0));
                vocalsShort.put(idx * 2 + 1, (short) (vocal * 32767.0));

                double cancelledL = L[i] - cancel;
                double cancelledR = R[i] - cancel;
                double accompL = clamp((cancelledL * (1.0 - accompDryMix) + L[i] * accompDryMix) * accompGain, -1.0, 1.0);
                double accompR = clamp((cancelledR * (1.0 - accompDryMix) + R[i] * accompDryMix) * accompGain, -1.0, 1.0);
                accompShort.put(idx * 2, (short) (accompL * 32767.0));
                accompShort.put(idx * 2 + 1, (short) (accompR * 32767.0));
            }
            int pct = 10 + (int) (Math.min(end, totalSamples) * 75L / Math.max(totalSamples, 1));
            if (pct > lastPct) {
                lastPct = pct;
                report(progressCallback, pct);
                log.info("DSP 人声分离进度: {}%", pct);
            }
        }
        report(progressCallback, 88);

        AudioFormat outputFormat = new AudioFormat(sampleRate, 16, channels, true, false);

        ByteArrayInputStream accompBais = new ByteArrayInputStream(accompBytes);
        AudioInputStream accompAis = new AudioInputStream(accompBais, outputFormat, totalSamples);
        AudioSystem.write(accompAis, AudioFileFormat.Type.WAVE, accompFile);
        accompAis.close();

        ByteArrayInputStream vocalsBais = new ByteArrayInputStream(vocalsBytes);
        AudioInputStream vocalsAis = new AudioInputStream(vocalsBais, outputFormat, totalSamples);
        AudioSystem.write(vocalsAis, AudioFileFormat.Type.WAVE, vocalsFile);
        vocalsAis.close();
        report(progressCallback, 100);
        log.info("DSP 人声分离完成: vocals={}, accomp={}", vocalsPath, accompPath);

        SplitAudioDTO result = new SplitAudioDTO();
        result.setAccompanimentFilePath(accompPath);
        result.setVocalsFilePath(vocalsPath);

        return result;
    }

    private void report(Consumer<Integer> progressCallback, int progress) {
        if (progressCallback != null) {
            progressCallback.accept(Math.max(0, Math.min(progress, 100)));
        }
    }

    private double[] applyHighPass(double[] signal, double cutoffHz, double sampleRate) {
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        double alpha = rc / (rc + dt);
        double[] result = new double[signal.length];
        double prevOut = 0.0;
        double prevIn = signal.length > 0 ? signal[0] : 0.0;
        for (int i = 0; i < signal.length; i++) {
            double current = signal[i];
            double out = alpha * (prevOut + current - prevIn);
            result[i] = out;
            prevOut = out;
            prevIn = current;
        }
        return result;
    }

    private double[] applyLowPass(double[] signal, double cutoffHz, double sampleRate) {
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        double alpha = dt / (rc + dt);
        double[] result = new double[signal.length];
        double prevOut = signal.length > 0 ? signal[0] : 0.0;
        for (int i = 0; i < signal.length; i++) {
            prevOut = prevOut + alpha * (signal[i] - prevOut);
            result[i] = prevOut;
        }
        return result;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private String normalizeCancelMode(String mode) {
        if (mode == null || mode.trim().length() == 0) {
            return DEFAULT_ACCOMP_CANCEL_MODE;
        }
        String value = mode.trim().toLowerCase(Locale.ROOT);
        return "band".equals(value) ? "band" : "full";
    }
}
