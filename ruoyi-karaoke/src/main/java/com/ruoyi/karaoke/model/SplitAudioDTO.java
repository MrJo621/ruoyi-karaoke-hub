package com.ruoyi.karaoke.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人声/伴奏分离结果 DTO。
 * 存储分离后的伴奏和人声文件路径。
 *
 * @author jo
 */
@NoArgsConstructor
@Data
public class SplitAudioDTO {

    /** 伴奏（accompaniment）文件路径 */
    private String accompanimentFilePath;

    /** 人声（vocals）文件路径 */
    private String vocalsFilePath;

}
