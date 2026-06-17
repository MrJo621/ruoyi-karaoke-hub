package com.ruoyi.karaoke.service.strategy;


import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import org.springframework.beans.factory.annotation.Autowired;

public interface SongClientStrategyInterface {

    WebSocketConsole deal(WebSocketConsole console);
}
