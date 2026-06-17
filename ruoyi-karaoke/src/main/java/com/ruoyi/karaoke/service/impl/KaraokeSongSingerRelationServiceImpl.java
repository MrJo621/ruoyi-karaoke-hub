package com.ruoyi.karaoke.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.karaoke.domain.KaraokeSongSingerRelation;
import com.ruoyi.karaoke.mapper.KaraokeSongSingerRelationMapper;
import com.ruoyi.karaoke.service.IKaraokeSongSingerRelationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 歌曲歌手关联 Service业务层处理
 *
 * @author ruoyi
 * @date 2024-11-05
 */
@Service
public class KaraokeSongSingerRelationServiceImpl extends ServiceImpl<KaraokeSongSingerRelationMapper, KaraokeSongSingerRelation> implements IKaraokeSongSingerRelationService {

    @Override
    public List<Long> getSingerIdsBySongId(Long songId) {
        List<KaraokeSongSingerRelation> list = list(new LambdaQueryWrapper<KaraokeSongSingerRelation>()
                .eq(KaraokeSongSingerRelation::getSongId, songId));
        return list.stream().map(KaraokeSongSingerRelation::getSingerId).collect(Collectors.toList());
    }

    @Override
    public void saveRelations(Long songId, List<Long> singerIds) {
        // 删除旧的关联
        deleteBySongId(songId);
        // 新增新的关联
        if (singerIds != null && !singerIds.isEmpty()) {
            List<KaraokeSongSingerRelation> relations = singerIds.stream().map(singerId -> {
                KaraokeSongSingerRelation relation = new KaraokeSongSingerRelation();
                relation.setSongId(songId);
                relation.setSingerId(singerId);
                return relation;
            }).collect(Collectors.toList());
            saveBatch(relations);
        }
    }

    @Override
    public void deleteBySongId(Long songId) {
        remove(new LambdaQueryWrapper<KaraokeSongSingerRelation>().eq(KaraokeSongSingerRelation::getSongId, songId));
    }
}
