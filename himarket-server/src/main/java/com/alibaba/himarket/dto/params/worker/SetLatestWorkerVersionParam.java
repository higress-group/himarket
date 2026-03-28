package com.alibaba.himarket.dto.params.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetLatestWorkerVersionParam {

    @NotBlank(message = "Version cannot be blank")
    private String version;
}
