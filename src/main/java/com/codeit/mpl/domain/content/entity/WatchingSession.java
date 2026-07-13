package com.codeit.mpl.domain.content.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "watching_sessions")
public class WatchingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "watcher_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_watching_sessions_watcher")
    )
    private User watcher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "content_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_watching_sessions_content")
    )
    private Content content;

    public WatchingSession(User watcher, Content content) {
        this.watcher = watcher;
        this.content = content;
    }
}