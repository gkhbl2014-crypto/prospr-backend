package com.prospr.app.dto.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyResponse {

    private UUID id;
    private String familyName;
}
