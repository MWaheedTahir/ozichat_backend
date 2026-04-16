package com.ozichat.common;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CursorPagedResponse<T> {
    private final List<T> content;
    private final String nextCursor;
    private final String previousCursor;
    private final boolean hasMore;
    private final int limit;
}
