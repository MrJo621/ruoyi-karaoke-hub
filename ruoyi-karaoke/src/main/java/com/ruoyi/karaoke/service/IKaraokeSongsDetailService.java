package com.ruoyi.karaoke.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.domain.KaraokeSongsDetailVO;

import java.util.List;

/**
 * 【请填写功能名称】Service接口
 *
 * @author ruoyi
 * @date 2024-11-05
 */
public interface IKaraokeSongsDetailService extends IService<KaraokeSongsDetail> {

    KaraokeSongsDetail nextSong(Long songId);

    KaraokeSongsDetail nextSong(String deviceId, Long songId);

    Boolean uploadMV(String filePath, String fileName, String songTitle, List<Long> singerIds, Long userId);

    Boolean parseMV(List<Long> ids, Long userId);

    Boolean addSong(Long songId);

    Boolean addSong(String deviceId, Long songId);

    Boolean removeSong(Long songId);

    Boolean removeSong(String deviceId, Long songId);

    Boolean upSong(Long songId);

    Boolean upSong(String deviceId, Long songId);

    Boolean topSong(Long songId);

    Boolean topSong(String deviceId, Long songId);

    List<KaraokeSongsDetail> listSongs();

    List<KaraokeSongsDetail> listSongs(String deviceId);

    /**
     * 更新歌曲信息（含歌手关联）
     */
    void updateSongWithRelations(KaraokeSongsDetail songsDetail, List<Long> singerIds);

    /**
     * 查询歌曲列表（带歌手名称）
     */
    List<KaraokeSongsDetailVO> listWithSingers(String songTitle, String singerName, Integer status);
}
