package com.golf.erp.billing;

/** 수납 수단(round_payment.payment_method). PREPAID = 예약 시스템에서 이미 결제한 금액을 옮겨 적은 것 */
public enum PaymentMethod {
	CASH, CARD, BANK_TRANSFER, KAKAO_PAY, NAVER_PAY, PREPAID, ETC
}
