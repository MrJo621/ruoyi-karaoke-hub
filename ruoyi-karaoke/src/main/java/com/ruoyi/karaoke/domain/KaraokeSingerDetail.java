package com.ruoyi.karaoke.domain;


import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 歌手对象 karaoke_singer_detail
 *
 * @author ruoyi
 * @date 2024-11-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("karaoke_singer_detail")
public class KaraokeSingerDetail extends Model<KaraokeSingerDetail> {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 歌手名称 */
    private String singerName;

    /** 头像路径 */
    private String singerAvatar;

    /** 地区（0未知 1内地 2港台 3欧美 4日韩 5其他） */
    private Integer region;

    /** 性别（0未知 1男 2女） */
    private Integer gender;

    /** 出生年月 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /** 简介/描述 */
    private String description;

    /** 拼音首字母（用于搜索） */
    private String pinyinInitials;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（0正常 1停用） */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 创建者 */
    private Long createBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 更新者 */
    private Long updateBy;

}
