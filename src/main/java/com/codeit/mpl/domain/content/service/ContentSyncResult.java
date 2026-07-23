package com.codeit.mpl.domain.content.service;

public record ContentSyncResult(
        int createdCount,
        int updatedCount,
        int skippedCount
) {

    public static ContentSyncResult empty() {
        return new ContentSyncResult(0, 0, 0);
    }

    public ContentSyncResult plus(
            ContentSyncResult other
    ) {
        return new ContentSyncResult(
                createdCount + other.createdCount,
                updatedCount + other.updatedCount,
                skippedCount + other.skippedCount
        );
    }
}