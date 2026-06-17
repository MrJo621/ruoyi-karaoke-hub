package com.ruoyi.karaoke.service.impl;

import com.ruoyi.common.exception.ServiceException;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.jcodec.codecs.aac.AACDecoder;
import org.jcodec.codecs.wav.WavOutput;
import org.jcodec.common.AudioCodecMeta;
import org.jcodec.common.AudioFormat;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.DataReader;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.AudioBuffer;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 纯 Java 音视频处理工具，基于 JCodec。支持 MP4/MOV/AVI 格式。
 */
@Component
public class AudioVideoProcessor {

    private static final Logger log = LoggerFactory.getLogger(AudioVideoProcessor.class);

    // RIFF/FOURCC constants (little-endian as read by DataReader)
    private static final int FOURCC_RIFF = 0x46464952;
    private static final int FOURCC_AVI  = 0x20495641;
    private static final int FOURCC_LIST = 0x5453494c;
    private static final int FOURCC_HDRL = 0x6c726468;
    private static final int FOURCC_MOVI = 0x69766f6d;
    private static final int FOURCC_STRL = 0x6c727473;
    private static final int FOURCC_STRH = 0x68727473;
    private static final int FOURCC_STRF = 0x66727473;
    private static final int FOURCC_AUDS = 0x73647561;

    /**
     * 使用 JAVE2/FFmpeg 提取音频为 16-bit PCM WAV。
     * FFmpeg 的 AAC/MP3 解码器比 JCodec 可靠，不会丢帧导致卡顿。
     */
    public String extractAudio(String videoPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(videoPath);
        File target = new File(outputPath);

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("pcm_s16le");

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("wav");
        attrs.setAudioAttributes(audio);
        // 不设置 video attributes → FFmpeg 自动禁用视频输出

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(source), target, attrs);
        return outputPath;
    }

    /**
     * 将分离后的 WAV 转为 Android / H5 都稳定支持的 AAC(M4A) 音轨。
     * uni-app APP 端播放大体积 PCM WAV 容易出现无声、音量控制失效等问题。
     */
    public String convertWavToM4a(String wavPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(wavPath);
        File target = new File(outputPath);

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("aac");
        audio.setBitRate(192000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("ipod");
        attrs.setAudioAttributes(audio);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(source), target, attrs);
        return outputPath;
    }

    /**
     * 判断是否为 MP4/MOV 格式（使用 MP4Demuxer 管线）。
     */
    public boolean isMp4Mov(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov");
    }

    /**
     * 判断是否为 AVI 格式。
     */
    public boolean isAvi(String path) {
        return path.toLowerCase().endsWith(".avi");
    }

    // ==================== MP4/MOV 处理 ====================

    /**
     * 从 MP4/MOV 视频中提取 AAC 音频轨道并保存为 WAV 格式。
     */
    public String extractAudioMp4(String videoPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(videoPath);
        SeekableByteChannel ch = null;
        MP4Demuxer demuxer = null;
        WavOutput wavOut = null;

        try {
            ch = NIOUtils.readableChannel(source);
            demuxer = MP4Demuxer.createMP4Demuxer(ch);

            List<DemuxerTrack> audioTracks = demuxer.getAudioTracks();
            if (audioTracks.isEmpty()) {
                throw new ServiceException("视频中没有找到音频轨道: " + videoPath);
            }

            DemuxerTrack audioTrack = audioTracks.get(0);
            DemuxerTrackMeta meta = audioTrack.getMeta();
            AudioCodecMeta audioMeta = meta.getAudioCodecMeta();
            ByteBuffer codecPrivate = meta.getCodecPrivate();

            String codec = meta.getCodec().name();
            if (!"aac".equalsIgnoreCase(codec)) {
                throw new ServiceException("不支持的音频编码格式: " + codec + "，仅支持 AAC");
            }

            int sampleRate = audioMeta.getSampleRate();
            int channels = audioMeta.getChannelCount();

            AACDecoder aacDecoder = new AACDecoder(codecPrivate != null ? codecPrivate : ByteBuffer.allocate(0));

            AudioFormat af = new AudioFormat(sampleRate, 16, channels, true, false);
            SeekableByteChannel wavCh = NIOUtils.writableChannel(new File(outputPath));
            wavOut = new WavOutput(wavCh, af);

            Packet pkt;
            while ((pkt = audioTrack.nextFrame()) != null) {
                ByteBuffer compressed = pkt.getData();
                ByteBuffer pcmBuf = ByteBuffer.allocate(4608 * 2);
                AudioBuffer decoded = aacDecoder.decodeFrame(compressed, pcmBuf);
                if (decoded != null && decoded.getData().hasRemaining()) {
                    wavOut.write(decoded.getData());
                }
            }
        } finally {
            if (wavOut != null) { try { wavOut.close(); } catch (Exception ignored) {} }
            if (demuxer != null) { try { demuxer.close(); } catch (Exception ignored) {} }
            if (ch != null) { try { ch.close(); } catch (Exception ignored) {} }
        }

        return outputPath;
    }

    /**
     * 使用 JAVE2/FFmpeg 流拷贝移除音频轨道（-c:v copy -an，不重编码）。
     */
    public String removeAudioTrack(String videoPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(videoPath);
        File target = new File(outputPath);

        VideoAttributes video = new VideoAttributes();
        video.setCodec(VideoAttributes.DIRECT_STREAM_COPY);  // 流拷贝，不重编码

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp4");
        attrs.setVideoAttributes(video);
        // 不设置 AudioAttributes → FFmpeg 自动加 -an 去掉音频

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(source), target, attrs);
        return outputPath;
    }

    /**
     * 优化 MP4 文件用于流式播放（将 moov atom 移到文件头部）。
     */
    public String optimizeForStreaming(String videoPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(videoPath);
        MovieBox movie = MP4Util.parseMovie(source);
        MP4Util.writeMovieToFile(new File(outputPath), movie);

        return outputPath;
    }

    // ==================== AVI 处理 ====================

    /**
     * 使用 JAVE2 将 AVI 转换为 MP4 (H.264 + AAC)。
     */
    public String convertAviToMp4(String aviPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(aviPath);
        File target = new File(outputPath);

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("aac");
        audio.setBitRate(128000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        VideoAttributes video = new VideoAttributes();
        video.setCodec("h264");
        video.setBitRate(1600000);
        video.setFrameRate(30);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp4");
        attrs.setAudioAttributes(audio);
        attrs.setVideoAttributes(video);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(source), target, attrs);
        return outputPath;
    }

    /**
     * 从 AVI 文件中提取 PCM 音频并保存为 WAV。
     * AVI 音频通常是未压缩 PCM，直接封装为 WAV 即可。
     */
    public String extractAudioFromAvi(String videoPath, String outputPath) throws Exception {
        ensureDir(outputPath);
        deleteFile(outputPath);

        File source = new File(videoPath);
        SeekableByteChannel ch = null;

        try {
            ch = NIOUtils.readableChannel(source);
            DataReader dr = DataReader.createDataReader(ch, ByteOrder.LITTLE_ENDIAN);

            // --- 解析 RIFF 头 ---
            if (dr.readInt() != FOURCC_RIFF) {
                throw new ServiceException("不是有效的 AVI 文件: 缺少 RIFF 标识");
            }
            int fileSize = dr.readInt();
            if (dr.readInt() != FOURCC_AVI) {
                throw new ServiceException("不是有效的 AVI 文件: 缺少 AVI 标识");
            }

            long endPos = 12 + (fileSize & 0xffffffffL);

            // --- 第一遍: 定位 hdrl 和 movi ---
            long hdrlDataStart = -1, hdrlDataEnd = -1;
            long moviDataStart = -1, moviDataEnd = -1;

            while (dr.position() < endPos) {
                int fourcc = dr.readInt();
                int size = dr.readInt();
                long chunkDataStart = dr.position();

                if (fourcc == FOURCC_LIST) {
                    int listType = dr.readInt();
                    if (listType == FOURCC_HDRL) {
                        hdrlDataStart = dr.position();
                        hdrlDataEnd = chunkDataStart + size;
                    } else if (listType == FOURCC_MOVI) {
                        moviDataStart = dr.position();
                        moviDataEnd = chunkDataStart + size;
                    }
                }

                long skipTo = chunkDataStart + (size & 0xffffffffL);
                if ((size & 1) == 1) skipTo++; // word alignment
                dr.setPosition(skipTo);
            }

            if (hdrlDataStart < 0) throw new ServiceException("AVI 文件中找不到 'hdrl' 头信息");
            if (moviDataStart < 0) throw new ServiceException("AVI 文件中找不到 'movi' 数据区");

            // --- 第二遍: 从 hdrl 读取音频格式 ---
            int sampleRate = 0;
            short channels = 0;
            short bitsPerSample = 0;
            boolean isMp3 = false;
            boolean audioFound = false;

            dr.setPosition(hdrlDataStart);
            while (dr.position() < hdrlDataEnd) {
                int fourcc = dr.readInt();
                int size = dr.readInt();
                long chunkStart = dr.position();

                if (fourcc == FOURCC_LIST) {
                    int listType = dr.readInt();
                    boolean isAudioStream = false;
                    long strlEnd = chunkStart + size;

                    while (dr.position() < strlEnd) {
                        int scc = dr.readInt();
                        int ssz = dr.readInt();
                        long sStart = dr.position();

                        if (scc == FOURCC_STRH) {
                            isAudioStream = (dr.readInt() == FOURCC_AUDS);
                            dr.setPosition(sStart + 4);
                        } else if (scc == FOURCC_STRF && isAudioStream) {
                            short fmtTag = dr.readShort();
                            channels = dr.readShort();
                            sampleRate = dr.readInt();
                            dr.readInt(); // nAvgBytesPerSec
                            dr.readShort(); // nBlockAlign

                            if (fmtTag == 1) { // PCM
                                bitsPerSample = dr.readShort();
                                audioFound = true;
                            } else if (fmtTag == 0x0055) { // MP3
                                bitsPerSample = 16; // JLayer decodes to 16-bit PCM
                                isMp3 = true;
                                audioFound = true;
                            } else {
                                throw new ServiceException("不支持的 AVI 音频编码格式: 0x" + Integer.toHexString(fmtTag));
                            }
                            break;
                        }
                        dr.setPosition(sStart + ssz);
                    }
                    if (audioFound) break;
                }
                dr.setPosition(chunkStart + size);
            }

            if (!audioFound) {
                throw new ServiceException("AVI 文件中未找到支持的音频流");
            }

            // --- 第三遍: 从 movi 提取音频数据 ---
            ByteArrayOutputStream audioOs = new ByteArrayOutputStream();
            dr.setPosition(moviDataStart);

            while (dr.position() < moviDataEnd) {
                int fourcc = dr.readInt();
                int size = dr.readInt();

                String tag = fourccToString(fourcc);
                if (tag.endsWith("wb")) {
                    byte[] chunk = new byte[size];
                    dr.readFully(chunk);
                    audioOs.write(chunk);
                    if ((size & 1) == 1) dr.readByte();
                } else {
                    long skip = size;
                    if ((size & 1) == 1) skip++;
                    dr.skipBytes((int) skip);
                }
            }

            byte[] rawAudio = audioOs.toByteArray();
            byte[] pcmData;
            int actualSampleRate = sampleRate;
            int actualChannels = channels;

            if (isMp3) {
                PcmResult result = decodeMp3ToPcm(rawAudio, sampleRate, channels);
                pcmData = result.data;
                actualSampleRate = result.sampleRate;
                actualChannels = result.channels;
            } else {
                pcmData = rawAudio;
            }

            // --- 写入 WAV ---
            AudioFormat af = new AudioFormat(actualSampleRate, bitsPerSample, actualChannels, true, false);
            SeekableByteChannel wavCh = NIOUtils.writableChannel(new File(outputPath));
            WavOutput wavOut = new WavOutput(wavCh, af);
            wavOut.write(ByteBuffer.wrap(pcmData));
            wavOut.close();

            return outputPath;
        } finally {
            if (ch != null) { try { ch.close(); } catch (Exception ignored) {} }
        }
    }

    // ==================== 工具方法 ====================

    public void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            if (!file.delete()) {
                log.warn("无法删除文件: {}", path);
            }
        }
    }

    private void ensureDir(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static String fourccToString(int fourcc) {
        return "" + (char) (fourcc & 0xff)
                + (char) ((fourcc >> 8) & 0xff)
                + (char) ((fourcc >> 16) & 0xff)
                + (char) ((fourcc >> 24) & 0xff);
    }

    private static class PcmResult {
        final byte[] data;
        final int sampleRate;
        final int channels;
        PcmResult(byte[] data, int sampleRate, int channels) {
            this.data = data;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    /**
     * 使用 JLayer 将 MP3 音频数据解码为 PCM (16-bit little-endian)。
     * 从第一帧 Header 检测实际采样率和声道数，优先于 AVI 头信息。
     */
    private PcmResult decodeMp3ToPcm(byte[] mp3Data, int fallbackSampleRate, int fallbackChannels) throws Exception {
        ByteArrayInputStream bais = null;
        Bitstream bitstream = null;
        ByteArrayOutputStream pcmOs = new ByteArrayOutputStream();

        int detectedSampleRate = fallbackSampleRate;
        int detectedChannels = fallbackChannels;
        boolean firstFrame = true;

        try {
            bais = new ByteArrayInputStream(mp3Data);
            bitstream = new Bitstream(bais);
            Decoder decoder = new Decoder();

            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = output.getBuffer();
                int len = output.getBufferLength();
                int srcChannels = output.getChannelCount();

                if (firstFrame) {
                    detectedSampleRate = header.frequency();
                    detectedChannels = srcChannels;
                    firstFrame = false;
                    if (detectedSampleRate != fallbackSampleRate || detectedChannels != fallbackChannels) {
                        log.info("检测到实际音频格式: {}Hz {}声道 (AVI声明: {}Hz {}声道)",
                                detectedSampleRate, detectedChannels, fallbackSampleRate, fallbackChannels);
                    }
                }

                byte[] framePcm = new byte[len * 2];
                for (int i = 0; i < len; i++) {
                    short s = samples[i];
                    framePcm[i * 2] = (byte) (s & 0xff);
                    framePcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                }

                pcmOs.write(framePcm);
                bitstream.closeFrame();
            }
        } finally {
            if (bitstream != null) { try { bitstream.close(); } catch (Exception ignored) {} }
            if (bais != null) { try { bais.close(); } catch (Exception ignored) {} }
        }

        return new PcmResult(pcmOs.toByteArray(), detectedSampleRate, detectedChannels);
    }
}
