package com.codeit.mpl.domain.content.entity;

import com.codeit.mpl.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "watching_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingSession {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    public static WatchingSession create(Content content, User user) {
        WatchingSession session = new WatchingSession();
        session.content = content;
        session.user = user;
        session.startedAt = Instant.now();
        return session;
    }
}