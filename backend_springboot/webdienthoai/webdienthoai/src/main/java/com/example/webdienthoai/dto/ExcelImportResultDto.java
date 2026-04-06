package com.example.webdienthoai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResultDto {

    private int totalRows;
    private int successCount;
    private int errorCount;

    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        /** Số dòng trên file Excel (1 = hàng đầu tiên của sheet). */
        private int row;
        private String message;
    }
}
