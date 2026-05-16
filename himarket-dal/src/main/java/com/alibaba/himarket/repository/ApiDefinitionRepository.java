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

import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.support.enums.ApiType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiDefinitionRepository extends BaseRepository<ApiDefinition, Long> {

    Optional<ApiDefinition> findByApiDefinitionId(String apiDefinitionId);

    /**
     * Find API definition names that match the specified type and names, and are still bound to
     * existing products through product references.
     *
     * @param type the API definition type
     * @param names the API definition names to match
     * @return the distinct API definition names still bound to existing products
     */
    @Query(
            """
            SELECT DISTINCT definition.name
            FROM ApiDefinition definition
            JOIN ProductRef productRef ON productRef.apiDefinitionId = definition.apiDefinitionId
            JOIN Product product ON product.productId = productRef.productId
            WHERE definition.type = :type
              AND definition.name IN :names
            """)
    List<String> findLinkedNamesByTypeAndNameIn(
            @Param("type") ApiType type, @Param("names") Collection<String> names);
}
