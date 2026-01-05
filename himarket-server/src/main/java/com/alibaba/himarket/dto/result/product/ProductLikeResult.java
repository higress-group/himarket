package com.alibaba.himarket.dto.result.product;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.ProductLike;
import com.alibaba.himarket.support.enums.LikeStatus;
import lombok.Data;

@Data
public class ProductLikeResult implements OutputConverter<ProductLikeResult, ProductLike> {
    private String likeId;

    private String productId;

    private String developerId;

    private String portalId;

    private LikeStatus status = LikeStatus.LIKED;
}
