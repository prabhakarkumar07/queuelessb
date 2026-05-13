package com.queueless.entity;

/**
 * Roles for staff members assigned to a shop.
 * RECEPTIONIST: Can manage queue and walk-ins.
 * PROVIDER: Can serve and manage their own queue.
 * MANAGER: Full access to shop settings, analytics, and staff management.
 */
public enum StaffRole {
    RECEPTIONIST,
    PROVIDER,
    MANAGER
}
