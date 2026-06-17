package com.ruoyi.karaoke.domain;


import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌曲歌手关联对象 karaoke_song_singer_relation
 *
 * @author ruoyi
 * @date 2024-11-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("karaoke_song_singer_relation")
public class KaraokeSongSingerRelation extends Model<KaraokeSongSingerRelation> {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 歌曲ID */
    private Long songId;

    /** 歌手ID */
    private Long singerId;

}
