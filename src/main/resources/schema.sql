-- 1. 유저 테이블 (users)
CREATE TABLE IF NOT EXISTS users (
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

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_temporary_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL' CHECK (provider IN ('LOCAL', 'GOOGLE', 'KAKAO'));

-- 소셜 로그인 제공자가 주는 고유 ID(예: 카카오 회원번호). 이메일을 안 주는 제공자는
-- 이메일 대신 이걸로 기존 회원을 식별해야, 프로필(닉네임 등)이 바뀌어도 같은 계정으로 인식된다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_provider_provider_id
    ON users (provider, provider_id) WHERE provider_id IS NOT NULL;

-- 2. 콘텐츠 테이블 (contents)
CREATE TABLE IF NOT EXISTS contents (
                          id UUID PRIMARY KEY NOT NULL,
                          creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                          title VARCHAR(255) NOT NULL,
                          description TEXT,
                          thumbnail_url TEXT,
                          content_url TEXT,
                          external_id VARCHAR(100),
                          source_type VARCHAR(50),
                          type VARCHAR(20) NOT NULL CHECK (type IN ('MOVIE', 'TVSERIES', 'SPORT')),
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_contents_creator
                              FOREIGN KEY (creator_id)
                              REFERENCES users(id)
                              ON DELETE RESTRICT

);

ALTER TABLE contents
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);

ALTER TABLE contents
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);

----------기존TMDB 데이터를 영화와 tv로 분리
UPDATE contents
SET source_type = 'TMDB_MOVIE'
WHERE source_type = 'TMDB'
    AND type = 'MOVIE';

UPDATE contents
SET source_type = 'TMDB_TV'
WHERE source_type = 'TMDB'
  AND type = 'TVSERIES';

-- 외부 콘텐츠 중복 저장 방지
CREATE UNIQUE INDEX IF NOT EXISTS uk_contents_source_external
    ON contents (source_type, external_id)
    WHERE source_type IS NOT NULL
    AND external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contents_created_at
    ON contents (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_contents_type
    ON contents (type);

CREATE INDEX IF NOT EXISTS idx_contents_title
    ON contents (title);

ALTER TABLE contents
    ADD COLUMN IF NOT EXISTS watcher_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE contents
    ADD COLUMN IF NOT EXISTS average_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0;

ALTER TABLE contents
    ADD COLUMN IF NOT EXISTS review_count INTEGER NOT NULL DEFAULT 0;

--  콘텐츠 태그 테이블
-- 3. 콘텐츠 태그 테이블
CREATE TABLE IF NOT EXISTS content_tags (
    content_id UUID NOT NULL,
    tag VARCHAR(255) NOT NULL,

    CONSTRAINT fk_content_tags_content
    FOREIGN KEY (content_id)
    REFERENCES contents(id)
    ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_content_tags_content_id
    ON content_tags (content_id);
-- 3. 리뷰 테이블 (reviews)
CREATE TABLE IF NOT EXISTS reviews (
                         id UUID PRIMARY KEY NOT NULL,
                         content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                         author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         text TEXT NOT NULL,
                         rating INTEGER NOT NULL DEFAULT 0 CHECK (rating >= 0 AND rating <= 5),
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT uk_review_author_content UNIQUE (author_id, content_id)
);


-- 4. 플레이리스트 테이블 (Playlist)
CREATE TABLE IF NOT EXISTS playlists (
                           id UUID PRIMARY KEY NOT NULL,
                           owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                           title VARCHAR(255) NOT NULL,
                           description TEXT,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. 플레이리스트-콘텐츠 매핑 테이블 (PlaylistContent)
CREATE TABLE IF NOT EXISTS playlist_contents (
                                   id UUID PRIMARY KEY NOT NULL,
                                   playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                                   content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT uk_playlist_content UNIQUE (playlist_id, content_id)
);

ALTER TABLE playlist_contents
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 6. 플레이리스트 구독 테이블 (PlaylistSubscription)
CREATE TABLE IF NOT EXISTS playlist_subscriptions (
                                        id UUID PRIMARY KEY NOT NULL,
                                        playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                                        subscriber_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT uk_playlist_subscriber UNIQUE (playlist_id, subscriber_id)
);

ALTER TABLE playlist_subscriptions
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 7. 팔로우 테이블 (Follow)
CREATE TABLE IF NOT EXISTS follows (
                         id UUID PRIMARY KEY NOT NULL,
                         follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT uk_follower_followee UNIQUE (follower_id, followee_id),
                         CONSTRAINT chk_not_self_follow CHECK (follower_id <> followee_id)
);

ALTER TABLE follows
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 8. 대화방 테이블 (Conversation)
CREATE TABLE IF NOT EXISTS conversation (
                               id UUID PRIMARY KEY NOT NULL,
                               user1_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               user2_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT uk_conversation_users UNIQUE (user1_id, user2_id),
                               CONSTRAINT chk_ordered_users CHECK (user1_id < user2_id)
);

-- 9. 다이렉트 메시지 테이블 (DM)
CREATE TABLE IF NOT EXISTS direct_message (
                     id UUID PRIMARY KEY NOT NULL,
                     conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
                     sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                     receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                     content TEXT NOT NULL,
                     is_read BOOLEAN NOT NULL DEFAULT FALSE,
                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. 시청 세션 테이블 (WatchingSession)
CREATE TABLE IF NOT EXISTS watching_sessions (
                                   id UUID PRIMARY KEY NOT NULL,
                                   user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   content_id UUID NOT NULL REFERENCES contents(id) ON DELETE CASCADE,
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_watching_sessions_watcher
                                       FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE,

                                   CONSTRAINT fk_watching_sessions_content
                                        FOREIGN KEY (content_id)
                                        REFERENCES contents(id)
                                        ON DELETE CASCADE

);

-- 기존에 생성된 watching_sessions 테이블 보정
ALTER TABLE watching_sessions
    ADD COLUMN IF NOT EXISTS created_at
    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE watching_sessions
    ADD COLUMN IF NOT EXISTS updated_at
    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 11. 알림 시스템 (Notification Management)
CREATE TABLE IF NOT EXISTS notifications (
                               id UUID PRIMARY KEY,
                               receiver_id UUID NOT NULL,
                               sender_id UUID,
                               level VARCHAR(50) NOT NULL,
                               type VARCHAR(50),
                               target_id UUID,
                               title VARCHAR(255) NOT NULL,
                               content TEXT NOT NULL,
                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (receiver_id) REFERENCES users(id),
                               FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_receiver_read ON notifications (receiver_id, is_read, created_at DESC);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type VARCHAR(50);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS target_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_receiver_type_target
    ON notifications (receiver_id, type, target_id);