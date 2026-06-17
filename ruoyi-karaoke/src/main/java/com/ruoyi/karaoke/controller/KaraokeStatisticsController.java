package com.ruoyi.karaoke.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.karaoke.service.KaraokeStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/karaoke/statistics")
public class KaraokeStatisticsController extends BaseController {
    @Autowired
    private KaraokeStatisticsService statisticsService;

    @GetMapping("/overview")
    public AjaxResult overview() {
        return AjaxResult.success(statisticsService.overview());
    }

    @GetMapping("/clients")
    public AjaxResult clients() {
        return AjaxResult.success(statisticsService.clientList());
    }

    @GetMapping("/song/rank")
    public AjaxResult songRank(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return AjaxResult.success(statisticsService.songRank(limit));
    }

    @GetMapping("/singer/rank")
    public AjaxResult singerRank(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return AjaxResult.success(statisticsService.singerRank(limit));
    }

    @GetMapping("/trend")
    public AjaxResult trend(@RequestParam(value = "days", defaultValue = "14") Integer days) {
        return AjaxResult.success(statisticsService.trend(days));
    }
}
