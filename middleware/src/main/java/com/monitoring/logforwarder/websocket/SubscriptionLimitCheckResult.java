package com.monitoring.logforwarder.websocket;

/**
 * Result object for subscription limit validation.
 *
 * <p><b>Purpose:</b> Encapsulates the outcome of checking whether a user can create
 * additional subscriptions, including current and maximum counts.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
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
