package com.lottery.feed;

public class FeedFetchException extends RuntimeException {

    private final String errorType;
    private final int httpStatus;

    public FeedFetchException(String message, String errorType) {
        this(message, errorType, 0);
    }

    public FeedFetchException(String message, String errorType, int httpStatus) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
