ALTER TABLE product
    ADD COLUMN developer_id VARCHAR(64) NULL COMMENT '创建者开发者ID，NULL表示官方Skill';

CREATE INDEX idx_product_developer_id ON product (developer_id);
