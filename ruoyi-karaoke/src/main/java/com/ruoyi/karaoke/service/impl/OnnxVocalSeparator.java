package com.ruoyi.karaoke.service.impl;

import ai.onnxruntime.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.karaoke.model.SplitAudioDTO;
import com.ruoyi.karaoke.service.VocalSeparatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * 基于 ONNX Runtime + MDX-Net 模型的人声/伴奏分离。
 *
 * 支持固定帧数 chunked 模型（如 UVR_MDXNET_KARA_2）。
 * 自动从模型元数据检测输入形状，支持 classpath: 前缀加载模型。
 * 只负责 ONNX 引擎本身的加载与执行，策略选择由 VocalSeparatorContext 完成。
 */
@Service
public class OnnxVocalSeparator implements VocalSeparatorStrategy {

    private static final Logger log = LoggerFactory.getLogger(OnnxVocalSeparator.class);
    private static final int TARGET_SR = 44100;

    @Value("${karaoke.vocal-separator.onnx.model-path:${karaoke.vocal-separator.model-path:#{null}}}")
    private String modelPath;

    @Value("${karaoke.vocal-separator.onnx.hop-length:${karaoke.vocal-separator.hop-length:1024}}")
    private int hopLength;

    @Value("${karaoke.vocal-separator.onnx.mask-sharpness:${karaoke.vocal-separator.mask-sharpness:0.0}}")
    private double maskSharpness;

    @Value("${karaoke.vocal-separator.onnx.mask-threshold:${karaoke.vocal-separator.mask-threshold:0.5}}")
    private double maskThreshold;

    @Value("${karaoke.vocal-separator.onnx.intra-op-threads:1}")
    private int intraOpThreads;

    @Value("${karaoke.vocal-separator.onnx.inter-op-threads:1}")
    private int interOpThreads;

    @Value("${karaoke.vocal-separator.onnx.cpu-arena-allocator:false}")
    private boolean cpuArenaAllocator;

    @Value("${karaoke.vocal-separator.onnx.memory-pattern-optimization:false}")
    private boolean memoryPatternOptimization;

    @Value("${karaoke.vocal-separator.onnx.execution-provider:cpu}")
    private String executionProvider;

    @Value("${karaoke.vocal-separator.onnx.cuda-device-id:0}")
    private int cudaDeviceId;

    @Value("${karaoke.vocal-separator.onnx.coreml-flags:0}")
    private int coremlFlags;

    @Value("${karaoke.vocal-separator.onnx.graph-optimization-level:basic}")
    private String graphOptimizationLevel;

    @Value("${karaoke.vocal-separator.onnx.execution-mode:sequential}")
    private String executionMode;

    private OrtEnvironment env;
    private OrtSession session;
    private int nFft;
    private int freqBins;        // 模型期望的频率 bin 数（通常 = nFft/2，去 Nyquist）
    private int chunkFrames;     // 模型固定时间帧数
    private String inputName;
    private String outputName;
    private boolean onnxAvailable;
    private String unavailableReason = "";
    private final ThreadLocal<Consumer<Integer>> progressCallback = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        if (StringUtils.isBlank(modelPath)) {
            markUnavailable("ONNX 模型路径未配置");
            return;
        }

        String resolvedPath = resolveModelPath(modelPath);
        if (resolvedPath == null) {
            markUnavailable("ONNX 模型文件加载失败: " + modelPath);
            return;
        }

        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(parseOptimizationLevel(graphOptimizationLevel));
            applyExecutionMode(opts);
            opts.setIntraOpNumThreads(intraOpThreads);
            opts.setInterOpNumThreads(interOpThreads);
            opts.setCPUArenaAllocator(cpuArenaAllocator);
            opts.setMemoryPatternOptimization(memoryPatternOptimization);
            applyExecutionProvider(opts);
            session = env.createSession(resolvedPath, opts);

            inputName = session.getInputInfo().keySet().iterator().next();
            outputName = session.getOutputInfo().keySet().iterator().next();

            // 从模型元数据检测输入形状 (1, 4, freqBins, timeFrames)
            long[] inShape = ((TensorInfo) session.getInputInfo().get(inputName).getInfo()).getShape();
            freqBins = (int) inShape[2];
            chunkFrames = (int) inShape[3];

            // MDX-Net 模型去掉了 Nyquist bin，n_fft = freqBins * 2
            nFft = freqBins * 2;
            int samplesPerChunk = (chunkFrames - 1) * hopLength + nFft;

            onnxAvailable = true;
            log.info("ONNX 模型加载成功: {} | provider={} | graphOpt={} | execMode={} | shape=(1,4,{},{}) | n_fft={} hop={} chunkSamples={} intraThreads={} interThreads={} cpuArena={} memoryPattern={}",
                    resolvedPath, executionProvider, graphOptimizationLevel, executionMode,
                    freqBins, chunkFrames, nFft, hopLength, samplesPerChunk,
                    intraOpThreads, interOpThreads, cpuArenaAllocator, memoryPatternOptimization);

        } catch (Throwable e) {
            markUnavailable("ONNX 模型初始化失败: " + e.getMessage());
            log.error("ONNX 模型初始化失败", e);
        }
    }

    private void markUnavailable(String reason) {
        onnxAvailable = false;
        unavailableReason = reason;
        log.warn(reason);
    }

    private OrtSession.SessionOptions.OptLevel parseOptimizationLevel(String value) {
        String level = value == null ? "" : value.trim().toLowerCase();
        if ("none".equals(level) || "no".equals(level) || "off".equals(level)) {
            return OrtSession.SessionOptions.OptLevel.NO_OPT;
        }
        if ("extended".equals(level)) {
            return OrtSession.SessionOptions.OptLevel.EXTENDED_OPT;
        }
        if ("all".equals(level) || "full".equals(level)) {
            return OrtSession.SessionOptions.OptLevel.ALL_OPT;
        }
        return OrtSession.SessionOptions.OptLevel.BASIC_OPT;
    }

    private void applyExecutionMode(OrtSession.SessionOptions opts) {
        try {
            String mode = executionMode == null ? "" : executionMode.trim().toLowerCase();
            if (!"parallel".equals(mode)) {
                return;
            }
            Class<?> executionModeClass = Class.forName("ai.onnxruntime.OrtSession$SessionOptions$ExecutionMode");
            Object parallel = Enum.valueOf((Class<Enum>) executionModeClass.asSubclass(Enum.class), "PARALLEL");
            opts.getClass().getMethod("setExecutionMode", executionModeClass).invoke(opts, parallel);
        } catch (Throwable e) {
            log.warn("ONNX execution-mode={} 设置失败，继续使用默认模式: {}", executionMode, e.getMessage());
        }
    }

    private void applyExecutionProvider(OrtSession.SessionOptions opts) {
        String provider = executionProvider == null ? "cpu" : executionProvider.trim().toLowerCase();
        if (provider.length() == 0 || "cpu".equals(provider)) {
            return;
        }
        try {
            if ("cuda".equals(provider) || "gpu".equals(provider)) {
                opts.getClass().getMethod("addCUDA", int.class).invoke(opts, cudaDeviceId);
                log.info("ONNX CUDA provider 已启用: deviceId={}", cudaDeviceId);
                return;
            }
            if ("coreml".equals(provider)) {
                if (tryAddCoreMl(opts)) {
                    log.info("ONNX CoreML provider 已启用: flags={}", coremlFlags);
                }
                return;
            }
            log.warn("未知 ONNX execution-provider={}，继续使用 CPU", executionProvider);
        } catch (Throwable e) {
            log.warn("ONNX execution-provider={} 启用失败，继续使用 CPU: {}", executionProvider, e.getMessage());
        }
    }

    private boolean tryAddCoreMl(OrtSession.SessionOptions opts) {
        try {
            opts.getClass().getMethod("addCoreML", int.class).invoke(opts, coremlFlags);
            return true;
        } catch (NoSuchMethodException ignored) {
            // 不同 ONNX Runtime Java 版本的 CoreML API 签名不同，下面尝试 EnumSet 版本。
        } catch (Throwable e) {
            log.warn("ONNX CoreML int flags 启用失败: {}", e.getMessage());
            return false;
        }

        try {
            for (Class<?> nested : opts.getClass().getDeclaredClasses()) {
                if (!nested.getSimpleName().toLowerCase().contains("coreml")) {
                    continue;
                }
                if (!nested.isEnum()) {
                    continue;
                }
                EnumSet<?> flags = EnumSet.noneOf((Class<Enum>) nested.asSubclass(Enum.class));
                opts.getClass().getMethod("addCoreML", EnumSet.class).invoke(opts, flags);
                return true;
            }
        } catch (Throwable e) {
            log.warn("ONNX CoreML EnumSet flags 启用失败: {}", e.getMessage());
            return false;
        }
        log.warn("当前 onnxruntime Java 包未暴露 CoreML provider，继续使用 CPU");
        return false;
    }

    @Override
    public String engine() {
        return "onnx";
    }

    @Override
    public boolean available() {
        return onnxAvailable;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath, Consumer<Integer> progressCallback) throws Exception {
        try {
            this.progressCallback.set(progressCallback);
            return separate(wavFilePath);
        } finally {
            this.progressCallback.remove();
        }
    }

    @Override
    public SplitAudioDTO separate(String wavFilePath) throws Exception {
        if (!onnxAvailable) {
            throw new ServiceException("ONNX 引擎不可用: " + unavailableReason);
        }
        return separateWithOnnx(wavFilePath);
    }

    // ==================== ONNX 分离管线 ====================

    private SplitAudioDTO separateWithOnnx(String wavFilePath) throws Exception {
        File wavFile = new File(wavFilePath);
        if (!wavFile.exists()) {
            throw new ServiceException("音频文件不存在: " + wavFilePath);
        }
        reportProgress(1);
        log.info("ONNX 人声分离开始: {}", wavFilePath);

        // 1. 读取 WAV, 重采样到 44100Hz, 确保立体声
        float[][] audio = readAndResample(wavFilePath);
        reportProgress(5);
        float[] left = audio[0];
        float[] right = audio[1];
        int totalSamples = left.length;
        long outputBufferMb = totalSamples * 5L * Float.BYTES / 1024 / 1024;
        log.info("ONNX 音频读取完成: totalSamples={}, duration={}s, outputBuffer≈{}MB",
                totalSamples, totalSamples / TARGET_SR, outputBufferMb);

        // 2. 每个 chunk 的采样数 & stride（50% 重叠）
        int samplesPerChunk = (chunkFrames - 1) * hopLength + nFft;
        int stride = samplesPerChunk / 2;

        // 输出缓冲区（overlap-add）
        float[] outVocalsL = new float[totalSamples];
        float[] outVocalsR = new float[totalSamples];
        float[] outAccompL = new float[totalSamples];
        float[] outAccompR = new float[totalSamples];
        float[] outWeight = new float[totalSamples];
        log.info("ONNX 输出缓冲分配完成: {}MB", outputBufferMb);

        double[] fadeWindow = makeFadeWindow(samplesPerChunk, stride);

        int totalChunks = (totalSamples + stride - 1) / stride;
        log.info("ONNX 人声分离准备完成: samples={}, chunks={}, samplesPerChunk={}, stride={}",
                totalSamples, totalChunks, samplesPerChunk, stride);
        reportProgress(8);
        int chunkIdx = 0;
        int lastReportedPct = -1;
        for (int offset = 0; offset < totalSamples; offset += stride) {
            int end = Math.min(offset + samplesPerChunk, totalSamples);
            int actualLen = end - offset;

            // 取当前 chunk 音频片段（不足部分零填充）
            double[] chunkL = extractChunk(left, offset, actualLen, samplesPerChunk);
            double[] chunkR = extractChunk(right, offset, actualLen, samplesPerChunk);

            // 3. STFT → [freqBins][chunkFrames][2]
            double[][][] specL = StftUtils.stft(chunkL, nFft, hopLength, freqBins);
            double[][][] specR = StftUtils.stft(chunkR, nFft, hopLength, freqBins);

            // 4. 构建输入张量 & 推理
            float[] inputTensor = buildInputTensor(specL, specR, freqBins, chunkFrames);
            float[] rawOutput;
            try (OnnxTensor tensor = OnnxTensor.createTensor(env,
                         FloatBuffer.wrap(inputTensor),
                         new long[]{1, 4, freqBins, chunkFrames});
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {

                OnnxTensor outputTensor = (OnnxTensor) result.get(outputName)
                        .orElseThrow(() -> new ServiceException("ONNX 模型输出为空"));
                FloatBuffer outBuf = outputTensor.getFloatBuffer();
                rawOutput = new float[outBuf.remaining()];
                outBuf.get(rawOutput);
            }

            // 5. 硬二值 mask 分离：每个时频点 100% 归伴奏或人声，不留灰度。
            //    模型输出 = 伴奏幅度估计，与输入幅度比较做二值判决。
            double[][][] vocalsSpecL = new double[freqBins][chunkFrames][2];
            double[][][] vocalsSpecR = new double[freqBins][chunkFrames][2];
            double[][][] accompSpecL = new double[freqBins][chunkFrames][2];
            double[][][] accompSpecR = new double[freqBins][chunkFrames][2];

            for (int f = 0; f < freqBins; f++) {
                for (int t = 0; t < chunkFrames; t++) {
                    float alr = rawOutput[0 * freqBins * chunkFrames + f * chunkFrames + t];
                    float ali = rawOutput[1 * freqBins * chunkFrames + f * chunkFrames + t];
                    float arr = rawOutput[2 * freqBins * chunkFrames + f * chunkFrames + t];
                    float ari = rawOutput[3 * freqBins * chunkFrames + f * chunkFrames + t];

                    double inMagL = Math.sqrt(specL[f][t][0] * specL[f][t][0] + specL[f][t][1] * specL[f][t][1]);
                    double inMagR = Math.sqrt(specR[f][t][0] * specR[f][t][0] + specR[f][t][1] * specR[f][t][1]);
                    double accMagL = Math.sqrt(alr * alr + ali * ali);
                    double accMagR = Math.sqrt(arr * arr + ari * ari);

                    double eps = 1e-8;
                    double rawMaskL = Math.min(1.0, Math.max(0.0, accMagL / Math.max(inMagL, eps)));
                    double rawMaskR = Math.min(1.0, Math.max(0.0, accMagR / Math.max(inMagR, eps)));

                    // 掩码：≤0 时使用模型原始输出（最平滑），>0 时 sigmoid 锐化（分离更彻底）
                    double accMaskL, accMaskR;
                    if (maskSharpness <= 0) {
                        accMaskL = rawMaskL;
                        accMaskR = rawMaskR;
                    } else {
                        accMaskL = 1.0 / (1.0 + Math.exp(-maskSharpness * (rawMaskL - maskThreshold)));
                        accMaskR = 1.0 / (1.0 + Math.exp(-maskSharpness * (rawMaskR - maskThreshold)));
                    }

                    // 相位（原始输入相位）
                    double phaseL_re = inMagL > eps ? specL[f][t][0] / inMagL : 1.0;
                    double phaseL_im = inMagL > eps ? specL[f][t][1] / inMagL : 0.0;
                    double phaseR_re = inMagR > eps ? specR[f][t][0] / inMagR : 1.0;
                    double phaseR_im = inMagR > eps ? specR[f][t][1] / inMagR : 0.0;

                    // 伴奏 = 输入 × 二值 mask × 输入相位（幅度域）
                    accompSpecL[f][t][0] = inMagL * accMaskL * phaseL_re;
                    accompSpecL[f][t][1] = inMagL * accMaskL * phaseL_im;
                    accompSpecR[f][t][0] = inMagR * accMaskR * phaseR_re;
                    accompSpecR[f][t][1] = inMagR * accMaskR * phaseR_im;

                    // 人声 = 输入 × (1 - mask)（幅度域）
                    vocalsSpecL[f][t][0] = inMagL * (1.0 - accMaskL) * phaseL_re;
                    vocalsSpecL[f][t][1] = inMagL * (1.0 - accMaskL) * phaseL_im;
                    vocalsSpecR[f][t][0] = inMagR * (1.0 - accMaskR) * phaseR_re;
                    vocalsSpecR[f][t][1] = inMagR * (1.0 - accMaskR) * phaseR_im;
                }
            }

            double[] chunkVocalsL = StftUtils.istft(vocalsSpecL, nFft, hopLength, freqBins);
            double[] chunkVocalsR = StftUtils.istft(vocalsSpecR, nFft, hopLength, freqBins);
            double[] chunkAccompL = StftUtils.istft(accompSpecL, nFft, hopLength, freqBins);
            double[] chunkAccompR = StftUtils.istft(accompSpecR, nFft, hopLength, freqBins);

            // 6. Overlap-add（加 fade 窗口，避免 chunk 边界突变）
            for (int i = 0; i < samplesPerChunk && offset + i < totalSamples; i++) {
                double w = fadeWindow[i];
                outVocalsL[offset + i] += (float) (chunkVocalsL[i] * w);
                outVocalsR[offset + i] += (float) (chunkVocalsR[i] * w);
                outAccompL[offset + i] += (float) (chunkAccompL[i] * w);
                outAccompR[offset + i] += (float) (chunkAccompR[i] * w);
                outWeight[offset + i] += (float) w;
            }

            chunkIdx++;
            // 报告进度（只在百分比变化时回调，避免过于频繁）
            int pct = totalChunks > 0 ? (int) (chunkIdx * 100L / totalChunks) : 0;
            if (pct != lastReportedPct && pct < 100) {
                lastReportedPct = pct;
                log.info("人声分离进度: {}% (chunk {}/{})", pct, chunkIdx, totalChunks);
                Consumer<Integer> cb = progressCallback.get();
                if (cb != null) cb.accept(pct);
            }
        }

        // 7. 权重归一化
        reportProgress(96);
        for (int i = 0; i < totalSamples; i++) {
            if (outWeight[i] > 1e-10) {
                outVocalsL[i] /= outWeight[i];
                outVocalsR[i] /= outWeight[i];
                outAccompL[i] /= outWeight[i];
                outAccompR[i] /= outWeight[i];
            }
        }

        // 8. 峰值归一化（防止写入 16-bit WAV 时硬截断产生爆音）
        normalizePeak(outVocalsL, outVocalsR);
        normalizePeak(outAccompL, outAccompR);
        reportProgress(98);

        // 9. 写出 WAV
        String basePath = wavFilePath.replaceFirst("\\.(?i)wav$", "");
        String vocalsPath = basePath + "-vocals.wav";
        String accompPath = basePath + "-accompaniment.wav";

        writeStereoWav(vocalsPath, outVocalsL, outVocalsR);
        writeStereoWav(accompPath, outAccompL, outAccompR);
        reportProgress(100);

        log.info("人声分离完成 ({} chunks): vocals={}, accomp={}", chunkIdx, vocalsPath, accompPath);

        SplitAudioDTO result = new SplitAudioDTO();
        result.setAccompanimentFilePath(accompPath);
        result.setVocalsFilePath(vocalsPath);
        return result;
    }

    private void reportProgress(int progress) {
        Consumer<Integer> cb = progressCallback.get();
        if (cb != null) {
            cb.accept(Math.max(0, Math.min(progress, 100)));
        }
    }

    // ==================== 辅助方法 ====================

    private String resolveModelPath(String path) {
        if (path.startsWith("classpath:")) {
            String cp = path.substring("classpath:".length());
            try {
                Resource resource = new ClassPathResource(cp);
                File file = resource.getFile();
                if (file.exists()) {
                    log.info("从 classpath 加载模型: {}", file.getAbsolutePath());
                    return file.getAbsolutePath();
                }
            } catch (IOException ignored) {
                // JAR 内回退到临时文件
            }
            try {
                Resource resource = new ClassPathResource(cp);
                File tmp = File.createTempFile("onnx_model_", ".onnx");
                tmp.deleteOnExit();
                Files.copy(resource.getInputStream(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("从 classpath 提取模型到: {}", tmp.getAbsolutePath());
                return tmp.getAbsolutePath();
            } catch (IOException e) {
                log.error("无法从 classpath 加载模型: {}", cp, e);
                return null;
            }
        }
        return path;
    }

    /**
     * 读取 WAV，自动重采样到 TARGET_SR Hz，确保立体声。
     */
    private float[][] readAndResample(String filePath) throws Exception {
        File file = new File(filePath);
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        AudioFormat fmt = ais.getFormat();
        int srcSr = (int) fmt.getSampleRate();
        int srcCh = fmt.getChannels();
        int bps = fmt.getSampleSizeInBits() / 8;
        boolean bigEndian = fmt.isBigEndian();

        // 读取全部样本
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = ais.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        ais.close();
        byte[] raw = baos.toByteArray();
        int totalFrames = raw.length / (srcCh * bps);

        // 提取左右声道
        float[] srcL = new float[totalFrames];
        float[] srcR = new float[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            int off = i * srcCh * bps;
            srcL[i] = readSample(raw, off, bps, bigEndian);
            srcR[i] = srcCh >= 2 ? readSample(raw, off + bps, bps, bigEndian) : srcL[i];
        }

        // 重采样到 TARGET_SR
        if (srcSr != TARGET_SR) {
            log.info("重采样 {}Hz → {}Hz", srcSr, TARGET_SR);
            srcL = resample(srcL, srcSr, TARGET_SR);
            srcR = resample(srcR, srcSr, TARGET_SR);
        }

        return new float[][]{srcL, srcR};
    }

    /**
     * 线性插值重采样。
     */
    private float[] resample(float[] src, int srcSr, int dstSr) {
        double ratio = (double) dstSr / srcSr;
        int dstLen = (int) (src.length * ratio);
        float[] dst = new float[dstLen];
        for (int i = 0; i < dstLen; i++) {
            double srcIdx = i / ratio;
            int idx = (int) srcIdx;
            double frac = srcIdx - idx;
            float a = src[Math.min(idx, src.length - 1)];
            float b = src[Math.min(idx + 1, src.length - 1)];
            dst[i] = (float) (a + (b - a) * frac);
        }
        return dst;
    }

    private float readSample(byte[] buf, int off, int bps, boolean bigEndian) {
        if (bps == 2) {
            return readShort(buf, off, bigEndian) / 32768.0f;
        } else if (bps == 3) {
            return read24Bit(buf, off, bigEndian) / 8388608.0f;
        }
        throw new ServiceException("不支持的位深度: " + (bps * 8));
    }

    private double[] extractChunk(float[] src, int offset, int actualLen, int chunkLen) {
        double[] chunk = new double[chunkLen];
        for (int i = 0; i < actualLen; i++) {
            chunk[i] = src[offset + i];
        }
        // 超出部分零填充
        return chunk;
    }

    /**
     * 生成 fade-in/fade-out 窗口，用于 overlap-add。
     * 窗口边缘用 sin² 平滑过渡，中间保持 1.0。
     */
    private double[] makeFadeWindow(int chunkLen, int fadeLen) {
        double[] w = new double[chunkLen];
        for (int i = 0; i < chunkLen; i++) {
            if (i < fadeLen) {
                w[i] = Math.sin(Math.PI * i / (2.0 * fadeLen));
                w[i] *= w[i]; // sin² 保证 overlap 权重和为 1
            } else if (i >= chunkLen - fadeLen) {
                double x = Math.sin(Math.PI * (chunkLen - i) / (2.0 * fadeLen));
                w[i] = x * x;
            } else {
                w[i] = 1.0;
            }
        }
        return w;
    }

    private float[] buildInputTensor(double[][][] leftSpec, double[][][] rightSpec,
                                     int freqBins, int numFrames) {
        float[] data = new float[4 * freqBins * numFrames];
        for (int f = 0; f < freqBins; f++) {
            for (int t = 0; t < numFrames; t++) {
                data[0 * freqBins * numFrames + f * numFrames + t] = (float) leftSpec[f][t][0];
                data[1 * freqBins * numFrames + f * numFrames + t] = (float) leftSpec[f][t][1];
                data[2 * freqBins * numFrames + f * numFrames + t] = (float) rightSpec[f][t][0];
                data[3 * freqBins * numFrames + f * numFrames + t] = (float) rightSpec[f][t][1];
            }
        }
        return data;
    }

    /**
     * 峰值归一化：将最大绝对值缩放到 0.95（留余量防 clip）。
     */
    private void normalizePeak(float[] left, float[] right) {
        double peak = 0.0;
        for (int i = 0; i < left.length; i++) {
            peak = Math.max(peak, Math.abs(left[i]));
            peak = Math.max(peak, Math.abs(right[i]));
        }
        if (peak > 0.95) {
            float scale = (float) (0.95 / peak);
            for (int i = 0; i < left.length; i++) {
                left[i] *= scale;
                right[i] *= scale;
            }
            log.info("峰值归一化: max={}, scale={}", String.format("%.2f", peak), String.format("%.4f", scale));
        }
    }

    private void writeStereoWav(String path, float[] left, float[] right) throws IOException {
        int len = Math.min(left.length, right.length);
        AudioFormat format = new AudioFormat(TARGET_SR, 16, 2, true, false);
        byte[] pcm = new byte[len * 4];
        for (int i = 0; i < len; i++) {
            short l = clampShort(left[i] * 32767.0);
            short r = clampShort(right[i] * 32767.0);
            pcm[i * 4] = (byte) (l & 0xff);
            pcm[i * 4 + 1] = (byte) ((l >> 8) & 0xff);
            pcm[i * 4 + 2] = (byte) (r & 0xff);
            pcm[i * 4 + 3] = (byte) ((r >> 8) & 0xff);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
        AudioInputStream ais = new AudioInputStream(bais, format, len);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(path));
        ais.close();
    }

    private static short clampShort(double v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v)));
    }

    private static short readShort(byte[] buf, int offset, boolean bigEndian) {
        if (bigEndian) {
            return (short) (((buf[offset] & 0xff) << 8) | (buf[offset + 1] & 0xff));
        }
        return (short) (((buf[offset + 1] & 0xff) << 8) | (buf[offset] & 0xff));
    }

    private static int read24Bit(byte[] buf, int offset, boolean bigEndian) {
        int s;
        if (bigEndian) {
            s = ((buf[offset] & 0xff) << 16) | ((buf[offset + 1] & 0xff) << 8) | (buf[offset + 2] & 0xff);
        } else {
            s = ((buf[offset + 2] & 0xff) << 16) | ((buf[offset + 1] & 0xff) << 8) | (buf[offset] & 0xff);
        }
        if ((s & 0x800000) != 0) s |= 0xff000000;
        return s;
    }
}
