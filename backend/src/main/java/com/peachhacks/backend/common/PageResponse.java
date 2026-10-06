package com.peachhacks.backend.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record PageResponse<T>(List<T> items, long total, int page, int size) {

	public static final int MAX_SIZE = 200;

	public static Pageable pageable(int page, int size) {
		return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
	}

	public static <S, T> PageResponse<T> of(Page<S> result, Pageable pageable, Function<S, T> mapper) {
		return new PageResponse<>(result.getContent().stream().map(mapper).toList(), result.getTotalElements(),
				pageable.getPageNumber(), pageable.getPageSize());
	}

}
