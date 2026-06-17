-- ========================================
-- Karaoke MV / 歌手管理结构补丁
-- 适用于已经导入旧版 ruoyi.sql 的数据库
-- ========================================

ALTER TABLE karaoke_singer_detail
    ADD COLUMN region int DEFAULT 0 COMMENT '地区（0未知 1内地 2港台 3欧美 4日韩 5其他）' AFTER singer_avatar,
    ADD COLUMN gender int DEFAULT 2 COMMENT '性别（0男 1女 2未知）' AFTER region,
    ADD COLUMN birthday date DEFAULT NULL COMMENT '出生年月' AFTER gender,
    ADD COLUMN description varchar(500) DEFAULT NULL COMMENT '简介/描述' AFTER birthday,
    ADD COLUMN pinyin_initials varchar(20) DEFAULT NULL COMMENT '拼音首字母' AFTER description,
    ADD COLUMN sort_order int DEFAULT 0 COMMENT '排序号' AFTER pinyin_initials,
    ADD COLUMN status int DEFAULT 0 COMMENT '状态（0正常 1停用）' AFTER sort_order;

ALTER TABLE karaoke_songs_detail
    ADD COLUMN process int DEFAULT 0 COMMENT '处理进度（0-100）' AFTER status;

CREATE INDEX idx_karaoke_song_singer_song_id ON karaoke_song_singer_relation(song_id);
CREATE INDEX idx_karaoke_song_singer_singer_id ON karaoke_song_singer_relation(singer_id);
CREATE INDEX idx_karaoke_singer_name ON karaoke_singer_detail(singer_name);
CREATE INDEX idx_karaoke_songs_title ON karaoke_songs_detail(song_title);

-- ========================================
-- KTV 统计看板
-- ========================================

CREATE TABLE IF NOT EXISTS karaoke_song_play_stat (
    song_id BIGINT NOT NULL PRIMARY KEY COMMENT '歌曲ID',
    song_title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '歌曲名称',
    singer_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '歌手名称',
    play_count BIGINT NOT NULL DEFAULT 0 COMMENT '点歌次数',
    last_play_time DATETIME NULL COMMENT '最近点歌时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KTV歌曲点歌累计统计';

CREATE TABLE IF NOT EXISTS karaoke_song_play_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    song_id BIGINT NOT NULL COMMENT '歌曲ID',
    song_title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '歌曲名称',
    singer_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '歌手名称',
    device_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'TV设备ID',
    create_time DATETIME NOT NULL COMMENT '点歌时间',
    KEY idx_song_id (song_id),
    KEY idx_device_id (device_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KTV点歌日志';

INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'KTV管理', 0, 5, 'karaoke', NULL, '', 1, 0, 'M', '0', '0', '', 'guide', 'admin', NOW(), 'KTV管理目录'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = 'KTV管理' AND parent_id = 0);

INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'KTV统计', menu_id, 1, 'statistics/index', 'karaoke/statistics/index', '', 1, 0, 'C', '0', '0', 'karaoke:statistics:list', 'chart', 'admin', NOW(), 'KTV统计看板'
FROM sys_menu
WHERE menu_name = 'KTV管理' AND parent_id = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'karaoke:statistics:list');
