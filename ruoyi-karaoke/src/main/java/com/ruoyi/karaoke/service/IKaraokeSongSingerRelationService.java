package com.ruoyi.karaoke.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.karaoke.domain.KaraokeSongSingerRelation;

import java.util.List;

/**
 * 【请填写功能名称】Service接口
 *
 * @author ruoyi
 * @date 2024-11-05
 */
public interface IKaraokeSongSingerRelationService extends IService<KaraokeSongSingerRelation> {

    /**
     * 根据歌曲ID查询歌手ID列表
     */
    List<Long> getSingerIdsBySongId(Long songId);

    /**
     * 保存歌曲歌手关联
     */
    void saveRelations(Long songId, List<Long> singerIds);

    /**
     * 删除歌曲歌手关联
     */
    void deleteBySongId(Long songId);
}
