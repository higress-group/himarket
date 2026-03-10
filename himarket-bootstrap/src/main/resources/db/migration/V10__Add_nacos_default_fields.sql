-- 新增默认实例标记和默认命名空间字段
ALTER TABLE nacos_instance ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE nacos_instance ADD COLUMN default_namespace VARCHAR(128) NOT NULL DEFAULT 'public';

-- 存量数据：将最早创建的实例标记为默认
UPDATE nacos_instance ni
INNER JOIN (SELECT MIN(id) AS min_id FROM nacos_instance) t ON ni.id = t.min_id
SET ni.is_default = 1;
