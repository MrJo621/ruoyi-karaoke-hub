-- ========================================
-- 卡拉OK 歌手管理字典数据
-- ========================================

-- 1. 歌手地区字典 (karaoke_singer_region)
INSERT INTO sys_dict_type (dict_name, dict_type, status, remark, create_by, create_time, update_by, update_time)
SELECT '歌手地区', 'karaoke_singer_region', '0', '歌手所在地区', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'karaoke_singer_region');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 1, '未知', '0', 'karaoke_singer_region', '', 'Y', 'default', '0', '未知地区', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '0');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 2, '内地', '1', 'karaoke_singer_region', '', 'N', 'success', '0', '内地歌手', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '1');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 3, '港台', '2', 'karaoke_singer_region', '', 'N', 'warning', '0', '港台歌手', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '2');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 4, '欧美', '3', 'karaoke_singer_region', '', 'N', 'primary', '0', '欧美歌手', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '3');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 5, '日韩', '4', 'karaoke_singer_region', '', 'N', 'danger', '0', '日韩歌手', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '4');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, is_default, list_class, status, remark, create_by, create_time, update_by, update_time)
SELECT 6, '其他', '5', 'karaoke_singer_region', '', 'N', 'info', '0', '其他地区', 1, NOW(), 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'karaoke_singer_region' AND dict_value = '5');

-- 2. 性别字典 (sys_user_sex) - 若依自带，这里检查并补充
-- 若依默认已经有 0=男 1=女 2=未知，我们需要 0=未知 1=男 2=女
-- 如果你的 sys_user_sex 字典值不同，请手动调整 SQL

-- 3. 系统状态字典 (sys_normal_disable) - 若依自带 0=停用 1=正常
-- 不需要重复添加
