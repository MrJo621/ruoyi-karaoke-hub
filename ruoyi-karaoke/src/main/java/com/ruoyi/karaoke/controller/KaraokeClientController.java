package com.ruoyi.karaoke.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.model.WebSocketConsole;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import com.ruoyi.karaoke.service.KaraokeStatisticsService;
import com.ruoyi.karaoke.service.strategy.SongStrategyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

import static com.ruoyi.karaoke.controller.WebSocketServer.webSocketSet;
import static com.ruoyi.karaoke.enums.ConsoleType.ADD_SONGS;
import static com.ruoyi.karaoke.enums.ConsoleType.CUT_SONG;
import static com.ruoyi.karaoke.enums.ConsoleType.REMOVE_SONG;
import static com.ruoyi.karaoke.enums.ConsoleType.TOP_SONG;


@RestController
@RequestMapping("/karaoke/client")
public class KaraokeClientController extends BaseController {

    @Autowired
    private IKaraokeSongsDetailService songsDetailService;
    @Autowired
    private SongStrategyContext songStrategyContext;
    @Autowired
    private KaraokeStatisticsService statisticsService;

    @GetMapping("/mv/list")
    public TableDataInfo mvList(@RequestParam(value = "searchQuery", required = false) String searchQuery,
                                @RequestParam(value = "region", required = false) Integer region,
                                @RequestParam(value = "gender", required = false) Integer gender) {
        startPage();

        LambdaQueryWrapper<KaraokeSongsDetail> wrapper = new LambdaQueryWrapper<KaraokeSongsDetail>()
                .and(StringUtils.isNotBlank(searchQuery), item -> item
                        .like(KaraokeSongsDetail::getSongTitle, searchQuery)
                        .or()
                        .like(KaraokeSongsDetail::getSingerName, searchQuery))
                .eq(KaraokeSongsDetail::getStatus, 1);

        if (region != null) {
            wrapper.inSql(KaraokeSongsDetail::getId,
                    "select relation.song_id from karaoke_song_singer_relation relation " +
                            "join karaoke_singer_detail singer on singer.id = relation.singer_id " +
                            "where singer.region = " + region + " and singer.status = 0");
        }
        if (gender != null) {
            wrapper.inSql(KaraokeSongsDetail::getId,
                    "select relation.song_id from karaoke_song_singer_relation relation " +
                            "join karaoke_singer_detail singer on singer.id = relation.singer_id " +
                            "where singer.gender = " + gender + " and singer.status = 0");
        }

        List<KaraokeSongsDetail> list = songsDetailService.list(wrapper.orderByDesc(KaraokeSongsDetail::getId));
        return getDataTable(list);
    }

    @GetMapping("/song/add")
    public AjaxResult addSong(@RequestParam("songId") Long songId,
                              @RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        if (StringUtils.isNotBlank(deviceId)) {
            if (songsDetailService.addSong(deviceId, songId)) {
                statisticsService.recordSongRequest(deviceId, songId);
                return AjaxResult.success("添加成功");
            }
            return AjaxResult.error("添加失败");
        }
        WebSocketConsole console = songStrategyContext.deal(new WebSocketConsole(){{
            setCode(ADD_SONGS.getCode());
            setData(String.valueOf(songId));
        }});
        statisticsService.recordSongRequest(deviceId, songId);
        broadcastConsole(console);
        return AjaxResult.success(console.getMessage());
    }

    @GetMapping("/song/remove")
    public AjaxResult removeSong(@RequestParam("songId") Long songId,
                                 @RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        if (StringUtils.isNotBlank(deviceId)) {
            return songsDetailService.removeSong(deviceId, songId)
                    ? AjaxResult.success("删除成功")
                    : AjaxResult.error("删除失败");
        }
        WebSocketConsole console = songStrategyContext.deal(new WebSocketConsole(){{
            setCode(REMOVE_SONG.getCode());
            setData(String.valueOf(songId));
        }});
        broadcastConsole(console);
        return AjaxResult.success(console.getMessage());
    }

    @GetMapping("/song/top")
    public AjaxResult topSong(@RequestParam("songId") Long songId,
                              @RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        if (StringUtils.isNotBlank(deviceId)) {
            return songsDetailService.topSong(deviceId, songId)
                    ? AjaxResult.success("置顶成功")
                    : AjaxResult.error("置顶失败");
        }
        WebSocketConsole console = songStrategyContext.deal(new WebSocketConsole(){{
            setCode(TOP_SONG.getCode());
            setData(String.valueOf(songId));
        }});
        broadcastConsole(console);
        return AjaxResult.success(console.getMessage());
    }

    @GetMapping("/song/cut")
    public AjaxResult cutSong(@RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        if (StringUtils.isNotBlank(deviceId)) {
            KaraokeSongsDetail song = songsDetailService.nextSong(deviceId, null);
            if (song == null) {
                return AjaxResult.error("没有下一曲了");
            }
            return AjaxResult.success("切歌成功", JSONUtil.toJsonStr(song));
        }
        WebSocketConsole console = songStrategyContext.deal(new WebSocketConsole(){{
            setCode(CUT_SONG.getCode());
        }});
        return AjaxResult.success(console.getMessage(), console.getData());
    }

    private void broadcastConsole(WebSocketConsole console) {
        for (WebSocketServer item : webSocketSet) {
            try {
                item.sendMessage(JSONUtil.toJsonPrettyStr(console));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @GetMapping("/song/list")
    public AjaxResult listSong(@RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        return AjaxResult.success(songsDetailService.listSongs(deviceId));
    }

    @GetMapping("/heartbeat")
    public AjaxResult heartbeat(@RequestParam(value = "deviceId", required = false) String deviceId) {
        touch(deviceId);
        return AjaxResult.success();
    }

    private void touch(String deviceId) {
        if (StringUtils.isNotBlank(deviceId)) {
            statisticsService.recordClientHeartbeat(deviceId);
        }
    }
}
