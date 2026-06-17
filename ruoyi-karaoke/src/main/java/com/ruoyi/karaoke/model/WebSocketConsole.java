package com.ruoyi.karaoke.model;

import com.ruoyi.karaoke.enums.ConsoleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketConsole {

    private String code;
    // 返回的数据包
    private String data;
    // 返回的提示信息
    private String message;
}
