package com.ruoyi.karaoke.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.karaoke.domain.KaraokeSingerDetail;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.domain.KaraokeSongsDetailVO;
import com.ruoyi.karaoke.mapper.KaraokeSongsDetailMapper;
import com.ruoyi.karaoke.model.SplitAudioDTO;
import com.ruoyi.karaoke.service.IKaraokeSongSingerRelationService;
import com.ruoyi.karaoke.service.IKaraokeSingerDetailService;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import com.ruoyi.karaoke.service.VocalSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 歌曲管理Service业务层处理
 *
 * @author ruoyi
 * @date 2024-11-05
 */
@Service
public class KaraokeSongsDetailServiceImpl extends ServiceImpl<KaraokeSongsDetailMapper, KaraokeSongsDetail> implements IKaraokeSongsDetailService {
    private static final Logger log = LoggerFactory.getLogger(KaraokeSongsDetailServiceImpl.class);

    public static final LinkedList<KaraokeSongsDetail> songList = new LinkedList<>();
    private static final String DEFAULT_QUEUE_KEY = "default";
    private static final Map<String, LinkedList<KaraokeSongsDetail>> deviceSongListMap = new ConcurrentHashMap<>();
    private static final int STATUS_PROCESSING = 0;
    private static final int STATUS_READY = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_PENDING = 3;
    private final ConcurrentLinkedQueue<ParseTask> parseQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean parsing = new AtomicBoolean(false);

    @Value("${ruoyi.profile}")
    private String sysUploadPath;

    @Autowired
    private AudioVideoProcessor processor;

    @Autowired
    private VocalSeparator vocalSeparator;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Autowired
    private IKaraokeSongSingerRelationService songSingerRelationService;

    @Autowired
    private IKaraokeSingerDetailService singerDetailService;

    private static final Pattern pattern = Pattern.compile("(.*?)-(.*)\\.[a-zA-Z0-9]+$");

    @Override
    public KaraokeSongsDetail nextSong(Long songId) {
        return nextSong(DEFAULT_QUEUE_KEY, songId);
    }

    @Override
    public KaraokeSongsDetail nextSong(String deviceId, Long songId) {
        if (null != songId) {
            return getById(songId);
        }

        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            if (queue.isEmpty()) {
                return null;
            }
            return queue.pop();
        }
    }

    @Override
    public Boolean uploadMV(String filePath, String fileName, String inputSongTitle, List<Long> singerIds, Long userId) {
        String songName = getFileName(fileName);
        String singerName = "";
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            songName = matcher.group(1);
            singerName = matcher.group(2);
        }
        if (StringUtils.isNotBlank(inputSongTitle)) {
            songName = inputSongTitle;
        }
        if (singerIds == null || singerIds.isEmpty()) {
            singerIds = findSingerIdsByNames(singerName);
        }
        if (singerIds != null && !singerIds.isEmpty()) {
            singerName = getSingerNames(singerIds);
        }

        String sysFilepath = sysUploadPath + filePath.replaceFirst("^/profile", "");
        String lowerPath = sysFilepath.toLowerCase();
        if (!lowerPath.endsWith(".mp4") && !lowerPath.endsWith(".mov") && !lowerPath.endsWith(".avi")) {
            throw new ServiceException("仅支持 MP4/MOV/AVI 格式");
        }

        // 立即插入记录，状态=待解析；用户可在列表中批量选择后再解析。
        KaraokeSongsDetail songsDetail = new KaraokeSongsDetail();
        songsDetail.setSongTitle(songName);
        songsDetail.setSingerName(singerName);
        songsDetail.setSourceVideoPath(filePath);
        songsDetail.setStatus(STATUS_PENDING);
        songsDetail.setProcess(0);
        songsDetail.setCreateTime(LocalDateTime.now());
        songsDetail.setCreateBy(userId);
        songsDetail.setUpdateTime(LocalDateTime.now());
        songsDetail.setUpdateBy(userId);
        songsDetail.insert();
        songSingerRelationService.saveRelations(songsDetail.getId(), singerIds);

        return true;
    }

    @Override
    public Boolean parseMV(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> {
                    KaraokeSongsDetail song = baseMapper.selectById(id);
                    if (song == null) {
                        return;
                    }
                    song.setStatus(STATUS_PENDING);
                    song.setProcess(0);
                    song.setUpdateTime(LocalDateTime.now());
                    song.setUpdateBy(userId);
                    baseMapper.updateById(song);
                    parseQueue.add(new ParseTask(id, userId));
                });
        startParseWorker();
        return true;
    }

    private void startParseWorker() {
        if (!parsing.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ParseTask task;
                while ((task = parseQueue.poll()) != null) {
                    KaraokeSongsDetail song = baseMapper.selectById(task.songId);
                    if (song == null) {
                        continue;
                    }
                    String sysFilepath = sysUploadPath + song.getSourceVideoPath().replaceFirst("^/profile", "");
                    processMv(song, sysFilepath, task.userId);
                }
            } finally {
                parsing.set(false);
                if (!parseQueue.isEmpty()) {
                    startParseWorker();
                }
            }
        }, executor);
    }

    private void processMv(KaraokeSongsDetail songsDetail, String sysFilepath, Long userId) {
        try {
            songsDetail.setStatus(STATUS_PROCESSING);
            songsDetail.setProcess(0);
            songsDetail.setVideoPath(null);
            songsDetail.setAccompanimentPath(null);
            songsDetail.setVocalsPath(null);
            songsDetail.setUpdateTime(LocalDateTime.now());
            songsDetail.setUpdateBy(userId);
            baseMapper.updateById(songsDetail);

            String uploadPath = sysUploadPath;
            String videoPathForAudio = sysFilepath;
            String videoPathForVideo = sysFilepath;
            boolean isAvi = sysFilepath.toLowerCase().endsWith(".avi");

            // 阶段1: AVI → MP4 (5%)
            if (isAvi) {
                String fileNameNoExt = getFileName(sysFilepath);
                String mp4Path = uploadPath + "/mv/" + fileNameNoExt + ".mp4";
                processor.convertAviToMp4(sysFilepath, mp4Path);
                videoPathForAudio = mp4Path;
                videoPathForVideo = mp4Path;
                updateProgress(songsDetail, 5);
            }

            // 阶段2: 提取音频 → WAV (10-15%)
            String fileNameNoExt = getFileName(videoPathForAudio);
            String wavFilePath = uploadPath + "/mv/" + fileNameNoExt + ".wav";
            processor.extractAudio(videoPathForAudio, wavFilePath);
            updateProgress(songsDetail, 15);

            // 阶段3: 生成无声视频 (15-25%)
            String ext = isAvi ? ".mp4" : videoPathForVideo.substring(videoPathForVideo.lastIndexOf('.'));
            String finalVideoPath = uploadPath + "/mv/" + fileNameNoExt + "-final" + ext;
            processor.removeAudioTrack(videoPathForVideo, finalVideoPath);
            updateProgress(songsDetail, 25);

            // 阶段4: 人声分离 (25-95%)
            updateProgress(songsDetail, 26);
            SplitAudioDTO splitResult = vocalSeparator.separate(wavFilePath,
                    progress -> {
                        int mapped = 25 + progress * 70 / 100; // 25→95
                        updateProgress(songsDetail, mapped);
                    });
            // 保留原始提取的 WAV，方便排查问题
            log.info("原始音频提取完成: {}", wavFilePath);

            String accompPath = splitResult.getAccompanimentFilePath();
            String vocalsPath = splitResult.getVocalsFilePath();
            updateProgress(songsDetail, 96);

            convertToPlayableAudio(wavFilePath);
            String playableAccompPath = convertToPlayableAudio(accompPath);
            String playableVocalsPath = convertToPlayableAudio(vocalsPath);
            updateProgress(songsDetail, 98);

            songsDetail.setVideoPath("/profile" + finalVideoPath.replace(uploadPath, ""));
            songsDetail.setAccompanimentPath("/profile" + playableAccompPath.replace(uploadPath, ""));
            songsDetail.setVocalsPath("/profile" + playableVocalsPath.replace(uploadPath, ""));
            songsDetail.setStatus(STATUS_READY);
            songsDetail.setProcess(100);

        } catch (Throwable e) {
            songsDetail.setStatus(STATUS_FAILED);
            songsDetail.setProcess(0);
            log.error("异步处理失败: {}", e.getMessage(), e);
        }

        songsDetail.setUpdateTime(LocalDateTime.now());
        songsDetail.setUpdateBy(userId);
        baseMapper.updateById(songsDetail);
    }

    private String convertToPlayableAudio(String wavPath) {
        if (StringUtils.isBlank(wavPath)) {
            return wavPath;
        }
        String m4aPath = wavPath.replaceFirst("(?i)\\.wav$", ".m4a");
        if (m4aPath.equals(wavPath)) {
            m4aPath = wavPath + ".m4a";
        }
        try {
            return processor.convertWavToM4a(wavPath, m4aPath);
        } catch (Exception e) {
            log.warn("音轨转 M4A 失败，回退使用 WAV: {}", wavPath, e);
            return wavPath;
        }
    }

    private void updateProgress(KaraokeSongsDetail songsDetail, int progress) {
        songsDetail.setProcess(progress);
        songsDetail.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(songsDetail);
    }

    @Override
    public Boolean addSong(Long songId) {
        return addSong(DEFAULT_QUEUE_KEY, songId);
    }

    @Override
    public Boolean addSong(String deviceId, Long songId) {
        KaraokeSongsDetail newSong = baseMapper.selectById(songId);
        if (newSong == null) {
            return false;
        }
        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            queue.add(newSong);
        }
        return true;
    }

    @Override
    public Boolean removeSong(Long songId) {
        return removeSong(DEFAULT_QUEUE_KEY, songId);
    }

    @Override
    public Boolean removeSong(String deviceId, Long songId) {
        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            Optional<KaraokeSongsDetail> songToRemove = queue.stream()
                    .filter(song -> song != null && Objects.equals(song.getId(), songId))
                    .findFirst();

            if (songToRemove.isPresent()) {
                queue.remove(songToRemove.get());
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public Boolean upSong(Long songId) {
        return upSong(DEFAULT_QUEUE_KEY, songId);
    }

    @Override
    public Boolean upSong(String deviceId, Long songId) {
        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            if (queue.isEmpty()) {
                return false;
            }
            int songIndex = -1;
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i) != null && Objects.equals(queue.get(i).getId(), songId)) {
                    songIndex = i;
                    break;
                }
            }

            if (songIndex == -1) {
                return false;
            }
            if (songIndex > 0) {
                KaraokeSongsDetail song = queue.remove(songIndex);
                queue.add(songIndex - 1, song);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public Boolean topSong(Long songId) {
        return topSong(DEFAULT_QUEUE_KEY, songId);
    }

    @Override
    public Boolean topSong(String deviceId, Long songId) {
        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            if (queue.isEmpty()) {
                return false;
            }
            int songIndex = -1;
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i) != null && Objects.equals(queue.get(i).getId(), songId)) {
                    songIndex = i;
                    break;
                }
            }

            if (songIndex == -1) {
                return false;
            }
            KaraokeSongsDetail song = queue.remove(songIndex);
            queue.addFirst(song);
            return true;
        }
    }

    @Override
    public List<KaraokeSongsDetail> listSongs() {
        return listSongs(DEFAULT_QUEUE_KEY);
    }

    @Override
    public List<KaraokeSongsDetail> listSongs(String deviceId) {
        LinkedList<KaraokeSongsDetail> queue = queue(deviceId);
        synchronized (queue) {
            return new LinkedList<>(queue);
        }
    }

    private LinkedList<KaraokeSongsDetail> queue(String deviceId) {
        String key = StringUtils.isBlank(deviceId) ? DEFAULT_QUEUE_KEY : deviceId.trim();
        if (DEFAULT_QUEUE_KEY.equals(key)) {
            return songList;
        }
        return deviceSongListMap.computeIfAbsent(key, item -> new LinkedList<>());
    }

    private static class ParseTask {
        private final Long songId;
        private final Long userId;

        private ParseTask(Long songId, Long userId) {
            this.songId = songId;
            this.userId = userId;
        }
    }

    private String getFileName(String filePath) {
        String regex = "([^/]+)(?=\\.[^.]+$)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(filePath);

        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new ServiceException("获取文件名匹配失败");
    }

    @Override
    public void updateSongWithRelations(KaraokeSongsDetail songsDetail, List<Long> singerIds) {
        if (singerIds != null) {
            songsDetail.setSingerName(getSingerNames(singerIds));
        }
        // 更新歌曲基本信息
        updateById(songsDetail);
        // 更新歌手关联
        if (singerIds != null) {
            songSingerRelationService.saveRelations(songsDetail.getId(), singerIds);
        }
    }

    @Override
    public List<KaraokeSongsDetailVO> listWithSingers(String songTitle, String singerName, Integer status) {
        LambdaQueryWrapper<KaraokeSongsDetail> queryWrapper = new LambdaQueryWrapper<KaraokeSongsDetail>()
                .like(StringUtils.isNotBlank(songTitle), KaraokeSongsDetail::getSongTitle, songTitle)
                .like(StringUtils.isNotBlank(singerName), KaraokeSongsDetail::getSingerName, singerName)
                .eq(status != null, KaraokeSongsDetail::getStatus, status)
                .orderByDesc(KaraokeSongsDetail::getId);
        List<KaraokeSongsDetail> list = list(queryWrapper);

        // 转VO并填充歌手信息
        List<KaraokeSongsDetailVO> voList = new java.util.ArrayList<>();
        for (KaraokeSongsDetail song : list) {
            KaraokeSongsDetailVO vo = new KaraokeSongsDetailVO();
            org.springframework.beans.BeanUtils.copyProperties(song, vo);

            List<Long> singerIds = songSingerRelationService.getSingerIdsBySongId(song.getId());
            if (singerIds != null && !singerIds.isEmpty()) {
                String singerNames = getSingerNames(singerIds);
                vo.setSingerNames(singerNames);
                vo.setSingerIds(singerIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            } else {
                vo.setSingerNames(StringUtils.defaultString(song.getSingerName()));
                vo.setSingerIds("");
            }
            voList.add(vo);
        }
        return voList;
    }

    private List<Long> findSingerIdsByNames(String singerName) {
        if (StringUtils.isBlank(singerName)) {
            return new java.util.ArrayList<>();
        }
        String[] names = singerName.split("[,，/、&和]+");
        List<String> cleanNames = java.util.Arrays.stream(names)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (cleanNames.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<KaraokeSingerDetail> singers = singerDetailService.list(
                new LambdaQueryWrapper<KaraokeSingerDetail>()
                        .in(KaraokeSingerDetail::getSingerName, cleanNames)
                        .eq(KaraokeSingerDetail::getStatus, 0)
        );
        return singers.stream().map(KaraokeSingerDetail::getId).collect(Collectors.toList());
    }

    private String getSingerNames(List<Long> singerIds) {
        if (singerIds == null || singerIds.isEmpty()) {
            return "";
        }
        List<KaraokeSingerDetail> singers = singerDetailService.listByIds(singerIds);
        return singers.stream().map(KaraokeSingerDetail::getSingerName).collect(Collectors.joining(","));
    }
}
