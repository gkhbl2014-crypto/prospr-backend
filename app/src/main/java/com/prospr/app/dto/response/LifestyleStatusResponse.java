package com.prospr.app.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LifestyleStatusResponse {

    private String status;
    private String message;
    private LocalDateTime updatedAt;
}
