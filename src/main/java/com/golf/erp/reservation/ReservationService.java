package com.golf.erp.reservation;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class ReservationService {
	private final ReservationMapper mapper;

	public ReservationService(ReservationMapper mapper) {
		this.mapper = mapper;
	}

	public Map<String, Object> search(String keyword, String status, int page, int size) {
		if (page < 0 || size < 1 || size > 100)
			throw new IllegalArgumentException("page는 0 이상, size는 1~100이어야 합니다.");
		return Map.of("items", mapper.search(keyword, status, page * size, size), "page", page, "size", size, "total",
				mapper.count(keyword, status));
	}
}
