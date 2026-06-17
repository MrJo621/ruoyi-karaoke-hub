package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatStrategyService implements SongClientStrategyInterface {

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        return console;
    }
}