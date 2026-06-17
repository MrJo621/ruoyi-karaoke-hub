package com.ruoyi.karaoke.service.strategy;


import cn.hutool.json.JSONUtil;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 切歌
 *
 * @author jo
 */
@Service
public class SongCutStrategyService implements SongClientStrategyInterface {
    @Autowired
    private IKaraokeSongsDetailService songsDetailService;
    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        // 切歌的话把下一首歌的内容返回
        KaraokeSongsDetail songsDetail = songsDetailService.nextSong(null);
        console.setData(JSONUtil.toJsonPrettyStr(songsDetail));
        console.setMessage(null == songsDetail ? "没有下一首歌了" : "");
        return console;    }
}
