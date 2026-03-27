package com.alibaba.himarket.dto.params.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateWorkerVersionStatusParam {

    @NotBlank(message = "Status cannot be blank")
    @Pattern(regexp = "online|offline", message = "Status must be 'online' or 'offline'")
    private String status;
}
