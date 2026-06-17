package com.ruoyi.karaoke.service.strategy;

import cn.hutool.json.JSONUtil;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 添加歌曲到歌单
 *
 * @author jo
 */
@Service
public class AddSongsStrategyService implements SongClientStrategyInterface {
    @Autowired
    private IKaraokeSongsDetailService songsDetailService;

    @Override
    public WebSocketConsole deal(WebSocketConsole console) {
        Boolean b = songsDetailService.addSong(Long.valueOf(console.getData()));
        console.setMessage(b ? "添加成功！" : "添加失败！");
        List<KaraokeSongsDetail> karaokeSongsDetails = songsDetailService.listSongs();
        console.setData(JSONUtil.toJsonPrettyStr(karaokeSongsDetails));
        //TODO 考虑给歌曲的点个次数+1 方便后续热门排行
        return console;
    }
}
