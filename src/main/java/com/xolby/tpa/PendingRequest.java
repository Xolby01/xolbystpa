package com.xolby.tpa;

import java.util.UUID;

public class PendingRequest {
    public enum Direction {
        SENDER_TO_TARGET, // /tpa : sender teleports to target
        TARGET_TO_SENDER  // /tpahere : target teleports to sender
    }

    private final UUID sender;
    private final UUID target;
    private final long createdAt;
    private final Direction direction;

    public PendingRequest(UUID sender, UUID target, long createdAt, Direction direction) {
        this.sender = sender;
        this.target = target;
        this.createdAt = createdAt;
        this.direction = direction;
    }

    public UUID getSender() { return sender; }
    public UUID getTarget() { return target; }
    public long getCreatedAt() { return createdAt; }
    public Direction getDirection() { return direction; }

    public boolean isExpired(long nowMs, int expireSeconds) {
        return nowMs - createdAt > expireSeconds * 1000L;
    }
}
