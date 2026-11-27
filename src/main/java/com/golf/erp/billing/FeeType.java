package com.golf.erp.billing;

/** 부대 요금 종류(service_fee.fee_type). 같은 이름의 {@link ChargeType} 으로 부과된다. */
public enum FeeType {
	CADDIE_FEE, CART_FEE, RENTAL, LOCKER, ETC;

	public ChargeType toChargeType() {
		return ChargeType.valueOf(name());
	}
}
