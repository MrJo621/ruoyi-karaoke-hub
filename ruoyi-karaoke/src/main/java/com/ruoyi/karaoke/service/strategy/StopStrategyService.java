package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.stereotype.Service;
/**
 *暂停
 *
 * @author jo
 */
@Service
public class StopStrategyService implements SongClientStrategyInterface {

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        return console;
    }
}
