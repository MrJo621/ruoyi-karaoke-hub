package com.ruoyi.karaoke.service.impl;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * STFT/ISTFT 音频预处理工具，基于 JTransforms FFT。
 * 支持去 Nyquist bin 模式（UVR MDX-Net 模型常用：n_fft=4096 → 2048 bins）。
 */
public class StftUtils {

    private StftUtils() {}

    /**
     * 单声道 STFT，输出全部 freqBins (= nFft/2+1，含 Nyquist)。
     */
    public static double[][][] stft(double[] samples, int nFft, int hopLength) {
        return stft(samples, nFft, hopLength, nFft / 2 + 1);
    }

    /**
     * 单声道 STFT，指定输出频率 bin 数。
     * freqBins = nFft/2 时去掉 Nyquist bin（模型期望 2048 输入的场景）。
     */
    public static double[][][] stft(double[] samples, int nFft, int hopLength, int freqBins) {
        double[] window = hann(nFft);
        int numFrames = Math.max(1, (samples.length - nFft) / hopLength + 1);
        boolean dropNyquist = freqBins == nFft / 2;

        double[][][] result = new double[freqBins][numFrames][2];
        DoubleFFT_1D fft = new DoubleFFT_1D(nFft);
        double[] frameData = new double[nFft];

        for (int frame = 0; frame < numFrames; frame++) {
            int offset = frame * hopLength;
            int copyLen = Math.min(nFft, samples.length - offset);
            for (int i = 0; i < nFft; i++) {
                frameData[i] = (i < copyLen) ? samples[offset + i] * window[i] : 0.0;
            }

            fft.realForward(frameData);

            result[0][frame][0] = frameData[0]; // DC
            result[0][frame][1] = 0.0;

            if (dropNyquist) {
                for (int k = 1; k < freqBins; k++) {
                    result[k][frame][0] = frameData[2 * k];
                    result[k][frame][1] = frameData[2 * k + 1];
                }
            } else {
                result[freqBins - 1][frame][0] = frameData[1]; // Nyquist
                result[freqBins - 1][frame][1] = 0.0;
                for (int k = 1; k < freqBins - 1; k++) {
                    result[k][frame][0] = frameData[2 * k];
                    result[k][frame][1] = frameData[2 * k + 1];
                }
            }
        }
        return result;
    }

    /**
     * 单声道 ISTFT，假定输入含 Nyquist bin (freqBins = nFft/2+1)。
     */
    public static double[] istft(double[][][] spec, int nFft, int hopLength) {
        return istft(spec, nFft, hopLength, nFft / 2 + 1);
    }

    /**
     * 单声道 ISTFT，可指定输入 bin 数。dropNyquist 时 Nyquist bin 补 0。
     */
    public static double[] istft(double[][][] spec, int nFft, int hopLength, int freqBins) {
        double[] window = hann(nFft);
        int numFrames = spec[0].length;
        int outputLen = (numFrames - 1) * hopLength + nFft;
        boolean dropNyquist = freqBins == nFft / 2;
        int fullBins = nFft / 2 + 1;

        double[] output = new double[outputLen];
        double[] windowSum = new double[outputLen];
        DoubleFFT_1D fft = new DoubleFFT_1D(nFft);
        double[] frameData = new double[nFft];

        for (int frame = 0; frame < numFrames; frame++) {
            frameData[0] = spec[0][frame][0];
            if (dropNyquist) {
                frameData[1] = 0.0; // Nyquist 补零
                for (int k = 1; k < freqBins; k++) {
                    frameData[2 * k] = spec[k][frame][0];
                    frameData[2 * k + 1] = spec[k][frame][1];
                }
            } else {
                frameData[1] = spec[fullBins - 1][frame][0];
                for (int k = 1; k < fullBins - 1; k++) {
                    frameData[2 * k] = spec[k][frame][0];
                    frameData[2 * k + 1] = spec[k][frame][1];
                }
            }

            fft.realInverse(frameData, false);

            int offset = frame * hopLength;
            for (int i = 0; i < nFft; i++) {
                double w = window[i];
                output[offset + i] += frameData[i] * w;
                windowSum[offset + i] += w * w;
            }
        }

        for (int i = 0; i < outputLen; i++) {
            if (windowSum[i] > 1e-10) {
                output[i] /= windowSum[i];
            }
        }
        return output;
    }

    public static double[] hann(int size) {
        double[] w = new double[size];
        for (int i = 0; i < size; i++) {
            w[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (size - 1)));
        }
        return w;
    }
}
