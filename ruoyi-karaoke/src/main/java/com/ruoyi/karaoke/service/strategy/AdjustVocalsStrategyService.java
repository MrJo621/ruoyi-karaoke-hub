package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.stereotype.Service;

/**
 * 调节人声
 *
 * @author jo
 */
@Service
public class AdjustVocalsStrategyService implements SongClientStrategyInterface {

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        return console;
    }
}
