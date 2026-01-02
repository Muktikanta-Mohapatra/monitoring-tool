package com.monitoring.logforwarder.websocket;

public class SubscriptionLimitCheckResult {
    private final boolean allowed;
    private final int currentCount;
    private final int maxCount;

    private SubscriptionLimitCheckResult(boolean allowed, int currentCount, int maxCount) {
        this.allowed = allowed;
        this.currentCount = currentCount;
        this.maxCount = maxCount;
    }

    public static SubscriptionLimitCheckResult allowed(int maxCount) {
        return new SubscriptionLimitCheckResult(true, 0, maxCount);
    }

    public static SubscriptionLimitCheckResult exceeded(int currentCount, int maxCount) {
        return new SubscriptionLimitCheckResult(false, currentCount, maxCount);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public ErrorMessage toErrorMessage(String requestId) {
        return WebSocketErrorBuilder.subscriptionLimitError(currentCount, maxCount, requestId);
    }
}
