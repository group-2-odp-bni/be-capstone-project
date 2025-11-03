package com.bni.orange.transaction.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private PageMetadata page;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMetadata {
        private int number;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean first;
        private boolean last;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .page(PageMetadata.builder()
                .number(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .first(page.isFirst())
                .last(page.isLast())
                .build())
            .build();
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return PageResponse.<T>builder()
            .content(List.of())
            .page(PageMetadata.builder()
                .number(page)
                .size(size)
                .totalPages(0)
                .totalElements(0)
                .first(true)
                .last(true)
                .build())
            .build();
    }
}
