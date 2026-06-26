-- 1. 유저 테이블 (users)
CREATE TABLE users (
                       id UUID PRIMARY KEY NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password TEXT NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       profile_image_url TEXT,
                       role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
                       is_locked BOOLEAN NOT NULL DEFAULT FALSE,
                       created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 콘텐츠 테이블 (contents)
CREATE TABLE contents (
                          id UUID PRIMARY KEY NOT NULL,
                          creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                          title VARCHAR(255) NOT NULL,
                          description TEXT,
                          thumbnail_url TEXT,
                          content_url TEXT,
                          type VARCHAR(20) NOT NULL CHECK (type IN ('movie', 'tvSeries', 'sport')),
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_tags (
                              content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                              tag VARCHAR(255) NOT NULL
);

-- 3. 리뷰 테이블 (reviews)
CREATE TABLE reviews (
                         id UUID PRIMARY KEY NOT NULL,
                         content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                         author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         text TEXT NOT NULL,
                         rating DOUBLE PRECISION NOT NULL DEFAULT 0.0 CHECK (rating >= 0.0 AND rating <= 5.0),
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. 플레이리스트 테이블 (Playlist)
CREATE TABLE playlists (
                           id UUID PRIMARY KEY NOT NULL,
                           owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                           title VARCHAR(255) NOT NULL,
                           description TEXT,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. 플레이리스트-콘텐츠 매핑 테이블 (PlaylistContent)
CREATE TABLE playlist_contents (
                                   id UUID PRIMARY KEY NOT NULL,
                                   playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                                   content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                                   CONSTRAINT uk_playlist_content UNIQUE (playlist_id, content_id)
);

-- 6. 플레이리스트 구독 테이블 (PlaylistSubscription)
CREATE TABLE playlist_subscriptions (
                                        id UUID PRIMARY KEY NOT NULL,
                                        playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                                        subscriber_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                        CONSTRAINT uk_playlist_subscriber UNIQUE (playlist_id, subscriber_id)
);

-- 7. 팔로우 테이블 (Follow)
CREATE TABLE follows (
                         id UUID PRIMARY KEY NOT NULL,
                         follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         CONSTRAINT uk_follower_followee UNIQUE (follower_id, followee_id),
                         CONSTRAINT chk_not_self_follow CHECK (follower_id <> followee_id)
);

-- 8. 대화방 테이블 (Conversation)
CREATE TABLE conversations (
                               id UUID PRIMARY KEY NOT NULL,
                               user1_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               user2_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT uk_conversation_users UNIQUE (user1_id, user2_id),
                               CONSTRAINT chk_different_users CHECK (user1_id <> user2_id)
);

-- 9. 다이렉트 메시지 테이블 (DM)
CREATE TABLE dms (
                     id UUID PRIMARY KEY NOT NULL,
                     conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                     sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                     receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                     content TEXT NOT NULL,
                     is_read BOOLEAN NOT NULL DEFAULT FALSE,
                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. 시청 세션 테이블 (WatchingSession)
CREATE TABLE watching_sessions (
                                   id UUID PRIMARY KEY NOT NULL,
                                   watcher_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11. 알림 시스템 (Notification Management)
CREATE TABLE notifications (
                               id UUID PRIMARY KEY,
                               receiver_id UUID NOT NULL,
                               sender_id UUID,
                               level VARCHAR(50) NOT NULL,
                               title VARCHAR(255) NOT NULL,
                               content TEXT NOT NULL,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (receiver_id) REFERENCES users(id),
                               FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_receiver_read ON notifications (receiver_id, is_read, created_at DESC);