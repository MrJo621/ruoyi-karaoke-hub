package com.ruoyi.karaoke.domain;


import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 【请填写功能名称】对象 karaoke_songs_detail
 * 
 * @author ruoyi
 * @date 2024-11-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("karaoke_songs_detail")
public class KaraokeSongsDetail extends Model<KaraokeSongsDetail> {
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 歌曲名称 */

    private String songTitle;

    private String singerName;

    /** 原视频文件路径 */

    private String sourceVideoPath;

    /** 无声视频文件路径 */

    private String videoPath;

    /** 伴奏文件路径 */

    private String accompanimentPath;

    /** 人声音轨路径 */

    private String vocalsPath;

    /** 处理状态（0未处理/处理中 1处理成功 2处理失败） */

    private Integer status;

    /** 处理进度（0-100） */
    private Integer process;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 创建者 */
    private Long createBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 更新者 */
    private Long updateBy;

}
