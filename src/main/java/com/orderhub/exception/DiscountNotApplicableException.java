package com.orderhub.exception;

import lombok.Getter;

@Getter
public class DiscountNotApplicableException extends BusinessRuleException {

    public enum Reason {
        UNKNOWN_CODE,
        INACTIVE,
        NOT_YET_VALID,
        EXPIRED,
        USAGE_LIMIT_REACHED,
        BELOW_MINIMUM_ORDER_AMOUNT
    }

    private final String code;
    private final Reason reason;

    public DiscountNotApplicableException(String code, Reason reason, String message) {
        super(message);
        this.code = code;
        this.reason = reason;
    }
}
