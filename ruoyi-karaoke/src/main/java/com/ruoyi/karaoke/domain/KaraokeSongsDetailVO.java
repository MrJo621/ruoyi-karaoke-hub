package com.ruoyi.karaoke.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 歌曲VO（包含歌手信息）
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class KaraokeSongsDetailVO extends KaraokeSongsDetail {
    private static final long serialVersionUID = 1L;

    /** 歌手ID列表（逗号分隔） */
    private String singerIds;

    /** 歌手名称列表（逗号分隔） */
    private String singerNames;
}
