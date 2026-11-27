package com.golf.erp.common.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 그린피 정책 조회(fee 모듈이 구현). 라운드 체크인 등 다른 모듈은 이 인터페이스로만 요금을 묻는다.
 */
public interface GreenFeeQuoter {
	/** 해당 골프장·일시에 적용되는 1인 그린피(기본 요금 - 할인). 맞는 정책이 없으면 empty */
	Optional<BigDecimal> perPlayerFee(long golfCourseId, LocalDateTime playAt);
}
