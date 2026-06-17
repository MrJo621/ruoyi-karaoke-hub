package com.ruoyi.karaoke.service.strategy;

import cn.hutool.json.JSONUtil;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 移除歌单
 *
 * @author jo
 */
@Service
public class RemoveSongStrategyService implements SongClientStrategyInterface {
    @Autowired
    private IKaraokeSongsDetailService songsDetailService;

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        Boolean b = songsDetailService.removeSong(Long.valueOf(console.getData()));
        console.setMessage(b ? "移除成功！" : "移除失败！");
        List<KaraokeSongsDetail> karaokeSongsDetails = songsDetailService.listSongs();
        console.setData(JSONUtil.toJsonPrettyStr(karaokeSongsDetails));
        return console;
    }
}
