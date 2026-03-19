ALTER TABLE `coding_session`
    ADD COLUMN `model_product_id` varchar(64) DEFAULT NULL,
    ADD COLUMN `model_name` varchar(128) DEFAULT NULL;
