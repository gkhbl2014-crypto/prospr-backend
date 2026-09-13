package com.prospr.app.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportPreviewResponse {

    private List<ImportPreviewRow> rows;
    private int totalParsed;
}
