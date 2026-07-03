package com.alibaba.himarket.dto.params.skill;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSkillVersionParam {

    @Schema(
            description =
                    "Target version status. `reviewing` submits the version for review and"
                            + " triggers the Skill package scan; `online` publishes the version;"
                            + " `offline` takes the version offline")
    @Pattern(
            regexp = "reviewing|online|offline",
            message = "Status must be 'reviewing', 'online', or 'offline'")
    private String status;

    @Schema(
            description =
                    "Set this version as the latest version. Cannot be combined with status or"
                            + " author")
    private Boolean latest;

    @Schema(description = "Force-publish this version when status is `online`")
    private Boolean force;

    @Schema(
            description =
                    "Whether force publish should update the latest label. Requires force=true")
    private Boolean updateLatestLabel;

    @Schema(
            description =
                    "Version author. Blank value clears the author. Cannot be combined with status"
                            + " or latest")
    @Size(max = 64, message = "Author cannot exceed 64 characters")
    private String author;

    @AssertTrue(message = "Status, latest, or author must be specified")
    private boolean isUpdateSpecified() {
        return status != null || Boolean.TRUE.equals(latest) || author != null;
    }

    @AssertTrue(message = "Only one version operation can be specified")
    private boolean isSingleOperationValid() {
        int operationCount = 0;
        if (status != null) {
            operationCount++;
        }
        if (Boolean.TRUE.equals(latest)) {
            operationCount++;
        }
        if (author != null) {
            operationCount++;
        }
        return operationCount <= 1;
    }

    @AssertTrue(message = "Force publish requires status 'online'")
    private boolean isForcePublishValid() {
        return !Boolean.TRUE.equals(force) || "online".equals(status);
    }

    @AssertTrue(message = "updateLatestLabel requires force=true")
    private boolean isUpdateLatestLabelValid() {
        return !Boolean.TRUE.equals(updateLatestLabel) || Boolean.TRUE.equals(force);
    }
}
