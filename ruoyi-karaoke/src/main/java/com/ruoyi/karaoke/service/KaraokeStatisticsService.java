package com.ruoyi.karaoke.service;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.karaoke.domain.KaraokeSongsDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KaraokeStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(KaraokeStatisticsService.class);
    private static final int ONLINE_SECONDS = 45;
    private final Map<String, ClientStatus> clients = new ConcurrentHashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IKaraokeSongsDetailService songsDetailService;

    @PostConstruct
    public void initTables() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS karaoke_song_play_stat (" +
                    "song_id BIGINT NOT NULL PRIMARY KEY," +
                    "song_title VARCHAR(255) NOT NULL DEFAULT ''," +
                    "singer_name VARCHAR(255) NOT NULL DEFAULT ''," +
                    "play_count BIGINT NOT NULL DEFAULT 0," +
                    "last_play_time DATETIME NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS karaoke_song_play_log (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "song_id BIGINT NOT NULL," +
                    "song_title VARCHAR(255) NOT NULL DEFAULT ''," +
                    "singer_name VARCHAR(255) NOT NULL DEFAULT ''," +
                    "device_id VARCHAR(128) NOT NULL DEFAULT ''," +
                    "create_time DATETIME NOT NULL," +
                    "KEY idx_song_id (song_id)," +
                    "KEY idx_device_id (device_id)," +
                    "KEY idx_create_time (create_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("KTV统计表自动初始化失败，请手动执行 sql/karaoke_manage_upgrade.sql: {}", e.getMessage());
        }
    }

    public void recordClientHeartbeat(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return;
        }
        ClientStatus status = clients.computeIfAbsent(deviceId.trim(), ClientStatus::new);
        status.touch();
    }

    public void recordSongRequest(String deviceId, Long songId) {
        if (songId == null) {
            return;
        }
        KaraokeSongsDetail song = songsDetailService.getById(songId);
        if (song == null) {
            return;
        }
        String safeDeviceId = StringUtils.isBlank(deviceId) ? "" : deviceId.trim();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO karaoke_song_play_log(song_id, song_title, singer_name, device_id, create_time) VALUES (?, ?, ?, ?, ?)",
                song.getId(), nvl(song.getSongTitle()), nvl(song.getSingerName()), safeDeviceId, Timestamp.valueOf(now));
        jdbcTemplate.update("INSERT INTO karaoke_song_play_stat(song_id, song_title, singer_name, play_count, last_play_time) " +
                        "VALUES (?, ?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE song_title = VALUES(song_title), singer_name = VALUES(singer_name), " +
                        "play_count = play_count + 1, last_play_time = VALUES(last_play_time)",
                song.getId(), nvl(song.getSongTitle()), nvl(song.getSingerName()), Timestamp.valueOf(now));
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("onlineCount", onlineClients().size());
        data.put("clientCount", clients.size());
        data.put("totalPlayCount", queryLong("SELECT COALESCE(SUM(play_count), 0) FROM karaoke_song_play_stat"));
        data.put("songCount", queryLong("SELECT COUNT(1) FROM karaoke_songs_detail"));
        data.put("readySongCount", queryLong("SELECT COUNT(1) FROM karaoke_songs_detail WHERE status = 1"));
        data.put("singerCount", queryLong("SELECT COUNT(1) FROM karaoke_singer_detail"));
        return data;
    }

    public List<Map<String, Object>> clientList() {
        List<ClientStatus> values = new ArrayList<>(clients.values());
        Collections.sort(values, Comparator.comparing(ClientStatus::getLastSeen).reversed());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClientStatus status : values) {
            Map<String, Object> row = new HashMap<>();
            row.put("deviceId", status.deviceId);
            row.put("online", status.online());
            row.put("lastSeen", status.lastSeen);
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> songRank(int limit) {
        return jdbcTemplate.queryForList("SELECT song_id songId, song_title songTitle, singer_name singerName, " +
                "play_count playCount, last_play_time lastPlayTime FROM karaoke_song_play_stat " +
                "ORDER BY play_count DESC, last_play_time DESC LIMIT ?", normalizeLimit(limit));
    }

    public List<Map<String, Object>> singerRank(int limit) {
        List<Map<String, Object>> songs = jdbcTemplate.queryForList("SELECT singer_name singerName, play_count playCount FROM karaoke_song_play_stat");
        Map<String, Long> counter = new HashMap<>();
        for (Map<String, Object> song : songs) {
            String names = String.valueOf(song.get("singerName") == null ? "" : song.get("singerName"));
            long count = number(song.get("playCount"));
            for (String name : names.split("[,，/、& ]+")) {
                String trimmed = name.trim();
                if (trimmed.length() == 0) {
                    continue;
                }
                counter.put(trimmed, counter.getOrDefault(trimmed, 0L) + count);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> item : counter.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("singerName", item.getKey());
            row.put("playCount", item.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(number(b.get("playCount")), number(a.get("playCount"))));
        int size = Math.min(normalizeLimit(limit), rows.size());
        return new ArrayList<>(rows.subList(0, size));
    }

    public List<Map<String, Object>> trend(int days) {
        int value = Math.max(1, Math.min(days, 90));
        LocalDate start = LocalDate.now().minusDays(value - 1L);
        List<Map<String, Object>> raw = jdbcTemplate.queryForList("SELECT DATE(create_time) day, COUNT(1) playCount " +
                "FROM karaoke_song_play_log WHERE create_time >= ? GROUP BY DATE(create_time)",
                Timestamp.valueOf(start.atStartOfDay()));
        Map<String, Long> grouped = new HashMap<>();
        for (Map<String, Object> row : raw) {
            grouped.put(String.valueOf(row.get("day")), number(row.get("playCount")));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < value; i++) {
            String day = start.plusDays(i).toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put("playCount", grouped.getOrDefault(day, 0L));
            rows.add(row);
        }
        return rows;
    }

    private List<ClientStatus> onlineClients() {
        List<ClientStatus> rows = new ArrayList<>();
        for (ClientStatus status : clients.values()) {
            if (status.online()) {
                rows.add(status);
            }
        }
        return rows;
    }

    private Long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 10 : limit, 100));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private static class ClientStatus {
        private final String deviceId;
        private LocalDateTime lastSeen;

        ClientStatus(String deviceId) {
            this.deviceId = deviceId;
            touch();
        }

        void touch() {
            lastSeen = LocalDateTime.now();
        }

        LocalDateTime getLastSeen() {
            return lastSeen;
        }

        boolean online() {
            return lastSeen != null && lastSeen.isAfter(LocalDateTime.now().minusSeconds(ONLINE_SECONDS));
        }
    }
}
