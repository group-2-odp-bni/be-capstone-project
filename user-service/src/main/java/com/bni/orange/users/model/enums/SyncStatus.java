package com.bni.orange.users.model.enums;

/**
 * Enum representing the synchronization status of user profiles
 * from authentication-service events.
 */
public enum SyncStatus {
    /**
     * Profile has been successfully synchronized from auth-service
     */
    SYNCED,

    /**
     * Profile is waiting to be synchronized from auth-service
     * This status indicates a potential issue if it persists
     */
    PENDING_SYNC,

    /**
     * Synchronization from auth-service failed
     * Requires manual investigation and retry
     */
    SYNC_FAILED
}
