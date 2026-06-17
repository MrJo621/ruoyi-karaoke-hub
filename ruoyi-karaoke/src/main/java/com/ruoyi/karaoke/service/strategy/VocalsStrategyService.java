package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.stereotype.Service;

/**
 * 开启原唱
 *
 * @author jo
 */
@Service
public class VocalsStrategyService implements SongClientStrategyInterface {

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        return console;
    }
}
