package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.stereotype.Service;

/**
 * 重新播放
 *
 * @author jo
 */
@Service
public class RestartStrategyService implements SongClientStrategyInterface {

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        return console;
    }
}
