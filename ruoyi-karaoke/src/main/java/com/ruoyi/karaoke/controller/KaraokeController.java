package com.ruoyi.karaoke.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.karaoke.domain.KaraokeSingerDetail;
import com.ruoyi.karaoke.domain.KaraokeSongSingerRelation;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import com.ruoyi.karaoke.domain.KaraokeSongsDetailVO;
import com.ruoyi.karaoke.service.IKaraokeSongSingerRelationService;
import com.ruoyi.karaoke.service.IKaraokeSingerDetailService;
import com.ruoyi.karaoke.service.IKaraokeSongsDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.ruoyi.common.utils.PageUtils.startPage;


@RestController
@RequestMapping("/karaoke/manage")
public class KaraokeController extends BaseController {

    @Autowired
    private IKaraokeSongsDetailService songsDetailService;

    @Autowired
    private IKaraokeSingerDetailService singerDetailService;

    @Autowired
    private IKaraokeSongSingerRelationService songSingerRelationService;

    @GetMapping("/list/mv")
    public TableDataInfo list(@RequestParam(value = "songTitle", required = false) String songTitle,
                              @RequestParam(value = "singerName", required = false) String singerName,
                              @RequestParam(value = "status", required = false) Integer status) {
        startPage();
        List<KaraokeSongsDetailVO> list = songsDetailService.listWithSingers(songTitle, singerName, status);
        return getDataTable(list);
    }

    @PostMapping("/upload/mv")
    public AjaxResult uploadMV(@RequestBody String fileJson) {
        JSONObject file = JSONUtil.parseObj(fileJson);
        String filePath = file.getStr("fileName");
        String fileName = file.getStr("originalFilename");
        String songTitle = file.getStr("songTitle");
        List<Long> singerIds = parseSingerIds(file.get("singerIds"));

        Boolean res = songsDetailService.uploadMV(filePath, fileName, songTitle, singerIds, getUserId());

        return AjaxResult.success(res);
    }

    @PostMapping("/upload/mv/batch")
    public AjaxResult uploadMVBatch(@RequestBody String fileJson) {
        JSONArray files = JSONUtil.parseArray(fileJson);
        int success = 0;
        for (Object item : files) {
            JSONObject file = JSONUtil.parseObj(item);
            String filePath = file.getStr("fileName");
            String fileName = file.getStr("originalFilename");
            String songTitle = file.getStr("songTitle");
            List<Long> singerIds = parseSingerIds(file.get("singerIds"));
            if (StringUtils.isBlank(filePath) || StringUtils.isBlank(fileName)) {
                continue;
            }
            if (songsDetailService.uploadMV(filePath, fileName, songTitle, singerIds, getUserId())) {
                success++;
            }
        }
        return AjaxResult.success("导入成功：" + success + " 个MV");
    }

    @PostMapping("/parse/mv")
    public AjaxResult parseMV(@RequestBody Long[] ids) {
        if (ids == null || ids.length == 0) {
            return AjaxResult.error("请选择要解析的MV");
        }
        songsDetailService.parseMV(Arrays.asList(ids), getUserId());
        return AjaxResult.success("已加入解析队列");
    }

    @PostMapping("/update/mv")
    public AjaxResult updateMV(@RequestBody KaraokeSongsDetailVO vo) {
        // 解析歌手ID列表（前端传入的是逗号分隔的字符串）
        List<Long> singerIds = parseSingerIds(vo.getSingerIds());
        // 转回实体类
        KaraokeSongsDetail songsDetail = new KaraokeSongsDetail();
        org.springframework.beans.BeanUtils.copyProperties(vo, songsDetail);
        songsDetail.setUpdateBy(getUserId());
        songsDetail.setUpdateTime(java.time.LocalDateTime.now());
        songsDetailService.updateSongWithRelations(songsDetail, singerIds);
        return AjaxResult.success();
    }

    @GetMapping("/list/singer")
    public TableDataInfo listSinger(KaraokeSingerDetail query) {
        startPage();
        List<KaraokeSingerDetail> list = singerDetailService.list(
                new LambdaQueryWrapper<KaraokeSingerDetail>()
                        .like(StringUtils.isNotBlank(query.getSingerName()), KaraokeSingerDetail::getSingerName, query.getSingerName())
                        .like(StringUtils.isNotBlank(query.getPinyinInitials()), KaraokeSingerDetail::getPinyinInitials, query.getPinyinInitials())
                        .eq(query.getRegion() != null, KaraokeSingerDetail::getRegion, query.getRegion())
                        .eq(query.getGender() != null, KaraokeSingerDetail::getGender, query.getGender())
                        .eq(query.getStatus() != null, KaraokeSingerDetail::getStatus, query.getStatus())
                        .orderByAsc(KaraokeSingerDetail::getSortOrder)
                        .orderByDesc(KaraokeSingerDetail::getId)
        );
        return getDataTable(list);
    }

    @PostMapping("/singer")
    public AjaxResult addSinger(@RequestBody KaraokeSingerDetail singer) {
        singer.setCreateBy(getUserId());
        singer.setCreateTime(java.time.LocalDateTime.now());
        singerDetailService.save(singer);
        return AjaxResult.success();
    }

    @PutMapping("/singer")
    public AjaxResult updateSinger(@RequestBody KaraokeSingerDetail singer) {
        singer.setUpdateBy(getUserId());
        singer.setUpdateTime(java.time.LocalDateTime.now());
        singerDetailService.updateById(singer);
        return AjaxResult.success();
    }

    @DeleteMapping("/singer/{ids}")
    public AjaxResult deleteSinger(@PathVariable Long[] ids) {
        singerDetailService.removeByIds(Arrays.asList(ids));
        songSingerRelationService.remove(new LambdaQueryWrapper<KaraokeSongSingerRelation>()
                .in(KaraokeSongSingerRelation::getSingerId, Arrays.asList(ids)));
        return AjaxResult.success();
    }

    @DeleteMapping("/mv/{ids}")
    public AjaxResult deleteMV(@PathVariable Long[] ids) {
        songsDetailService.removeByIds(Arrays.asList(ids));
        Arrays.stream(ids).forEach(songSingerRelationService::deleteBySongId);
        return AjaxResult.success();
    }

    private List<Long> parseSingerIds(Object singerIdsValue) {
        if (singerIdsValue == null) {
            return null;
        }
        if (singerIdsValue instanceof JSONArray) {
            JSONArray singerIdsArray = (JSONArray) singerIdsValue;
            return singerIdsArray.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }
        String singerIdsStr = String.valueOf(singerIdsValue);
        if (StringUtils.isBlank(singerIdsStr)) {
            return null;
        }
        return Arrays.stream(singerIdsStr.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

}
