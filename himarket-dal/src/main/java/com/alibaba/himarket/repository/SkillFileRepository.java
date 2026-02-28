/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.SkillFile;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SkillFileRepository extends BaseRepository<SkillFile, Long> {

    /**
     * Find all skill files by product ID
     *
     * @param productId the product ID
     * @return list of skill files
     */
    List<SkillFile> findByProductId(String productId);

    /**
     * Find a skill file by product ID and path
     *
     * @param productId the product ID
     * @param path the file path
     * @return the skill file if found
     */
    Optional<SkillFile> findByProductIdAndPath(String productId, String path);

    /**
     * Delete all skill files by product ID
     *
     * @param productId the product ID
     */
    @Transactional
    void deleteByProductId(String productId);
}
