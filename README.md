# 모두의 플리 (MOPL) 🎬🍿

[![codecov](https://codecov.io/gh/alpha-lens/sb09-mpl-team01/graph/badge.svg)](https://codecov.io/gh/alpha-lens/sb09-mpl-team01)

> **영화, 드라마, 스포츠 콘텐츠 평점 및 실시간 같이 보기 큐레이션 플랫폼**
> 
> 이 레포지토리는 글로벌 대규모 트래픽 처리를 염두에 두고 설계된 **[모두의 플리 (MOPL)]** 백엔드 애플리케이션입니다. 
> 콘텐츠 정보의 효율적인 배치 수집, 검색 최적화, 실시간 소켓 통신 및 세션 상태 관리가 구현되어 있습니다.

---

## 👥 팀원 및 기여 파트 (Contributors)

| <img src="https://github.com/geumbi1.png" width="120px;" alt="geumbi1"/> | <img src="https://github.com/parksh3070.png" width="120px;" alt="parksh3070"/> | <img src="https://github.com/by15622.png" width="120px;" alt="by15622"/> | <img src="https://github.com/alpha-lens.png" width="120px;" alt="alpha-lens"/> |
| :---: |:------------------------------------------------------------------------------:| :---: | :---: |
| **[geumbi1](https://github.com/geumbi1)** |                **[parksh3070](https://github.com/parksh3070)**                 | **[by15622](https://github.com/by15622)** | **[alpha_lens](https://github.com/alpha-lens)** |
| **사용자 관리 & AWS** |                          **콘텐츠 데이터 관리 & AWS**                          | **콘텐츠 평가/큐레이팅 & 프로필** | **실시간 같이보기 & 알림** |
| JWT/OAuth2 인증 인가 <br> 어드민 계정/권한 관리 <br> AWS ECS 인프라 구축 | Spring Batch 외부 API 수집 <br> AWS 인프라/배포 설정 <br> 콘텐츠 정렬 및 검색  | 평점/리뷰 CRUD <br> 큐레이션 플레이리스트 <br> 사용자 프로필 & 팔로우 | WebSocket STOMP 실시간 채팅 <br> 시청 세션 하트비트 생명주기 <br> SSE 실시간 알림 <br> OpenSearch 한글 검색 엔진 |

---

## 🛠️ 백엔드 기술 아키텍처

- **Language & Framework**: Java 17 / Spring Boot 4.1.0 (Spring Security, Spring Batch, Data JPA)
- **Database & Search**: PostgreSQL (주 DB), H2 (테스트용 DB), Elasticsearch v7.10.2 (Nori 형태소 분석기 기반 풀텍스트 검색 및 동기화)
- **Cache & Message Broker**: Redis (세션/캐시 저장소), Apache Kafka (분산 메시징 및 이벤트 핸들링)
- **Communication**: WebSocket/STOMP (실시간 채팅 & 하트비트 세션), Server-Sent Events (SSE 알림 발송)
- **Infrastructure**: AWS ECS (다중 서버 스케일아웃), AWS OpenSearch, AWS ElastiCache, Confluent Cloud, Nginx

---

## 📂 프로젝트 구조

```text
src/main/java/com/codeit/mpl/
├── domain/                      # 비즈니스 도메인 레이어
│   ├── user/                    # 인증/인가 및 회원/어드민 기능
│   ├── profile/                 # 프로필 관리 및 유저 팔로우
│   ├── content/                 # 콘텐츠 정보 수집(Open API, Batch) 및 검색(Elasticsearch)
│   ├── curating/                # 플레이리스트 큐레이팅 및 구독 관리
│   ├── review/                  # 콘텐츠 평점 및 리뷰
│   ├── notification/            # SSE 기반 알림 리스트 관리
│   ├── conversation/            # 1:1 대화방(DM) 관리
│   └── chat/                    # 웹소켓 기반 실시간 시청 세션 및 채팅
└── infra/                       # 인프라스트럭처 및 공통 기술 레이어
    ├── common/                  # 커서 기반 페이징 유틸리티 등 공통 클래스
    ├── config/                  # 애플리케이션 및 시큐리티 설정
    ├── security/                # JWT 발급, 검증 및 인증 필터
    ├── elasticsearch/           # Elasticsearch 색인 및 검색 연동
    ├── kafka/                   # 카프카 토픽 설정 및 이벤트 리스너
    ├── redis/                   # 레디스 커넥션 설정
    ├── websocket/               # 웹소켓 STOMP 채널 및 인터셉터
    ├── sse/                     # SSE 커넥션 및 데이터 발송(SseEmitter)
    └── storage/                 # AWS S3 파일 업로드 및 이미지 URL 변환
```

---

## 🔗 구현 기능 및 주요 API 엔드포인트

### 1. 인증 및 사용자 관리 (`domain/user`)
- **인증 인가**: JWT(Access / Refresh Token)를 발급하며, 쿠키를 통한 CSRF 방어(`XSRF-TOKEN` / `X-XSRF-TOKEN`)를 수행합니다.
- **동시 로그인 제한**: 동일 계정이 중복 로그인 시 이전 세션을 Redis 상에서 무효화하여 강제 로그아웃을 처리합니다.
- **임시 비밀번호**: 메일 서비스를 이용해 3분 만료 기한의 임시 비밀번호를 발급 및 로그인 검증합니다.
- **소셜 로그인**: Google 및 Kakao OAuth2 인증을 연동하고 실패 시 에러 정보를 포함하여 리다이렉트합니다.
- **어드민 권한 관리**: 사용자 상태 잠금 및 권한(`ADMIN`/`USER`)을 동적으로 수정하며, 즉시 세션을 끊어 자동 로그아웃되도록 강제합니다.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/sign-in` | 로그인 (Form UrlEncoded 요청) |
| `POST` | `/api/auth/sign-out` | 로그아웃 (Redis 토큰 무효화 및 Refresh 쿠키 삭제) |
| `POST` | `/api/auth/refresh` | Refresh Token을 통한 JWT 갱신 |
| `POST` | `/api/auth/reset-password` | 만료 시간(3분) 임시 비밀번호 발급 및 메일 발송 |
| `GET` | `/api/auth/csrf-token` | 프론트엔드 CSRF 처리를 위한 토큰 획득 |
| `POST` | `/api/users` | 일반 회원가입 |
| `GET` | `/api/users` | 사용자 목록 조회 (커서 기반 페이징) |
| `GET` | `/api/users/{userId}` | 특정 사용자 프로필 조회 |
| `PATCH` | `/api/users/{userId}` | 프로필 정보 수정 및 S3 이미지 업로드 (Multipart) |
| `PATCH` | `/api/users/{userId}/role` | 사용자 권한(Role) 변경 `[ADMIN]` |
| `PATCH` | `/api/users/{userId}/locked` | 계정 잠금/해제 처리 `[ADMIN]` |
| `PATCH` | `/api/users/{userId}/password` | 로그인 사용자 본인의 비밀번호 수정 |

---

### 2. 콘텐츠 수집 & 검색 (`domain/content`)
- **수집 배치 (Spring Batch)**: TMDB(영화/TV) 및 Sports DB(스포츠) Open API 데이터를 연동하여 초기 및 주기적(새벽 3시) 수집 배치를 수행합니다.
- **Elasticsearch 동기화**: DB에 저장된 콘텐츠 데이터를 Elasticsearch로 이중화하여 Full-Text Search를 지원합니다. 관리자 수동 색인 보정 및 비동기 Reindexing 기능이 포함됩니다.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/contents` | 콘텐츠 목록 조회 (커서 페이징, `keywordLike` 검색, `typeEqual` 필터) |
| `GET` | `/api/contents/{contentId}` | 단일 콘텐츠 상세 정보 조회 |
| `POST` | `/api/contents` | 콘텐츠 수동 등록 `[ADMIN]` |
| `PATCH` | `/api/contents/{contentId}` | 콘텐츠 정보 수정 `[ADMIN]` |
| `DELETE` | `/api/contents/{contentId}` | 콘텐츠 삭제 `[ADMIN]` |
| `GET` | `/api/contents/external/search` | Open API 콘텐츠 실시간 검색 `[ADMIN]` |
| `POST` | `/api/contents/external/import` | 외부 Open API 검색 항목 수동 임포트 `[ADMIN]` |
| `POST` | `/api/admin/content-batches/initial` | 서비스 최초 구동용 대량 수집 배치 실행 `[ADMIN]` |
| `POST` | `/api/admin/content-batches/daily` | 일일 수집 배치 수동 트리거 테스트 `[ADMIN]` |
| `GET` | `/api/admin/es-sync/diff` | DB ↔ Elasticsearch 데이터 개수 및 ID 불일치 확인 `[ADMIN]` |
| `POST` | `/api/admin/es-sync/diff` | 데이터 불일치 건만 선택적으로 부분 동기화 `[ADMIN]` |
| `POST` | `/api/admin/es-sync/reindex` | DB 전체 데이터를 Elasticsearch로 비동기 재색인 `[ADMIN]` |

---

### 3. 평점 리뷰 & 플레이리스트 큐레이션 (`domain/review`, `domain/curating`)
- **평점 및 리뷰**: 유저들이 미디어 콘텐츠에 별점 및 감상평을 작성하고, 해당 목록을 커서 페이징으로 조회합니다.
- **큐레이션 플리**: 자신만의 플레이리스트를 생성하고 콘텐츠들을 추가/삭제하며, 타 유저의 플리를 구독할 수 있습니다.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/reviews` | 특정 콘텐츠에 대한 리뷰 및 평점 작성 |
| `PATCH` | `/api/reviews/{reviewId}` | 리뷰/평점 수정 |
| `DELETE` | `/api/reviews/{reviewId}` | 리뷰 삭제 |
| `GET` | `/api/reviews` | 콘텐츠별 리뷰 목록 조회 (커서 페이징) |
| `POST` | `/api/playlists` | 새로운 큐레이션 플레이리스트 생성 |
| `GET` | `/api/playlists` | 플레이리스트 목록 조회 (키워드 검색, 작성자/구독자 필터 지원) |
| `GET` | `/api/playlists/{playlistId}` | 플레이리스트 상세 및 포함된 콘텐츠 목록 조회 |
| `PATCH` | `/api/playlists/{playlistId}` | 플레이리스트 메타 데이터 수정 |
| `DELETE` | `/api/playlists/{playlistId}` | 플레이리스트 삭제 |
| `POST` | `/api/playlists/{playlistId}/contents/{contentId}` | 플레이리스트 내에 콘텐츠 추가 |
| `DELETE` | `/api/playlists/{playlistId}/contents/{contentId}` | 플레이리스트 내의 특정 콘텐츠 삭제 |
| `POST` | `/api/playlists/{playlistId}/subscription` | 플레이리스트 구독 (신규 콘텐츠 추가 시 알림 유발) |
| `DELETE` | `/api/playlists/{playlistId}/subscription` | 플레이리스트 구독 취소 |

---

### 4. 팔로우 & 1:1 DM (`domain/profile`, `domain/conversation`)
- **팔로우**: 관심 사용자를 팔로우하여 상대방의 시청 정보 및 신규 플레이리스트 등록 등의 활동 피드를 알림으로 받습니다.
- **1:1 대화방**: 사용자간 1:1 DM 방을 개설하여 대화 메시지를 기록 및 실시간으로 채팅합니다.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/follows` | 사용자 팔로우 신청 |
| `DELETE` | `/api/follows/{followId}` | 언팔로우 (팔로우 해제) |
| `GET` | `/api/follows/followed-by-me` | 특정 유저에 대한 나의 팔로우 여부 및 정보 조회 |
| `GET` | `/api/follows/count` | 해당 사용자를 팔로우하는 총 팔로워 수 조회 |
| `GET` | `/api/conversations` | 현재 참여 중인 1:1 대화방 목록 조회 |
| `POST` | `/api/conversations` | 신규 1:1 대화방 개설 |
| `GET` | `/api/conversations/{conversationId}` | 대화방 정보 상세 조회 |
| `GET` | `/api/conversations/with` | 상대 유저 ID 기반으로 개설된 대화방 존재 유무 확인 |
| `GET` | `/api/conversations/{conversationId}/direct-messages` | 대화방의 메시지 내역 목록 조회 (커서 페이징) |
| `POST` | `/api/conversations/{conversationId}/direct-messages/{directMessageId}/read` | 수신한 메시지 읽음(Read) 처리 |

---

### 5. 실시간 같이 보기 & 알림 통신 (`domain/chat`, `infra/sse`)
- **실시간 시청 세션**: WebSocket STOMP 연결을 유지하며, 유저의 하트비트 세션(`touchSession`)을 기록하고 현재 동일 콘텐츠를 시청하고 있는 동시 시청자 정보 및 시청자 수를 반환합니다.
- **SSE 실시간 알림**: `GET /api/sse` 엔드포인트를 통해 연결을 맺고 실시간 알림(팔로우, DM 수신, 구독 플리 업데이트 등)과 실시간 DM 신규 메시지를 SSE Emitter로 Push 받습니다.

#### HTTP 엔드포인트
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/sse` | 실시간 이벤트 스트림(SSE) 구독 (Last-Event-ID 복구 지원) |
| `GET` | `/api/users/{watcherId}/watching-sessions` | 특정 유저가 현재 시청 중인 실시간 콘텐츠 조회 |
| `GET` | `/api/contents/{contentId}/watching-sessions` | 특정 콘텐츠의 실시간 시청자 목록 조회 (커서 페이징) |
| `GET` | `/api/notifications` | 사용자의 알림 내역 조회 (SSE 전송 완료 내역 목록) |
| `DELETE` | `/api/notifications/{notificationId}` | 개별 알림 내역 삭제 |

#### WebSocket STOMP 엔드포인트 (`/ws` 커넥트 연결 후 사용)
| Action | Destination | Description |
| :--- | :--- | :--- |
| `SUBSCRIBE` | `/sub/contents/{contentId}/chat` | 특정 콘텐츠 같이 보기 채팅방 수신 채널 |
| `SEND` | `/pub/contents/{contentId}/chat` | 같이 보기 실시간 채팅 메시지 송신 |
| `SUBSCRIBE` | `/sub/conversations/{conversationId}/direct-messages` | 특정 DM 대화방 실시간 수신 채널 |
| `SEND` | `/pub/conversations/{conversationId}/direct-messages` | DM 채팅 메시지 송신 (보낸 뒤 수신자에게 SSE Push 트리거) |
| `SEND` | `/pub/contents/{contentId}/watch/heartbeat` | 콘텐츠 시청 세션 생명주기 유지를 위한 주기적 하트비트 전송 |

---

## 🏃‍♂️ 로컬 개발 환경 구동 방법

### 1. 외부 인프라스트럭처 기동 (Docker Compose)
로컬에 사전에 정의된 데이터베이스 및 검색 엔진, 메시징 큐 인프라들을 도커 컴포즈로 실행합니다.
```bash
docker-compose up -d
```
> ※ Elasticsearch 실행 후 Nori 한글 분석기 플러그인(`analysis-nori`)을 컨테이너 내부에 수동 또는 스크립트로 다운로드한 후 컨테이너를 재시작해야 원활한 동작이 가능합니다.

### 2. 환경 변수 설정
프로젝트 루트 폴더에 `.env` 파일을 생성한 후 데이터베이스 접속 정보, OAuth 클라이언트 키, JWT 비밀키 등을 작성합니다. (환경 변수 규격은 `.env.example`을 참조하세요.)

### 3. Gradle 빌드 및 실행
```bash
# JaCoCo 테스트 커버리지 리포트 생성 및 테스트 빌드
./gradlew clean build

# 로컬 WAS 실행 (기본 포트: 8080)
./gradlew bootRun
```
WAS 가동 후 브라우저에서 `http://localhost:8080/swagger-ui/index.html`에 접속하면, 전체 API 스펙 확인 및 API 직접 테스트(Swagger)가 가능합니다.

---

## 📊 테스트 커버리지 관리
- 프로젝트 전체 테스트 커버리지를 **80% 이상** 유지하는 것을 목표로 합니다.
- Gradle 빌드 시 `jacocoTestReport` 태스크가 자동으로 실행되어 `build/reports/jacoco/test/jacocoTestReport.xml` 경로에 XML 커버리지 리포트가 기록되며, GitHub CI 워크플로우 완료 후 Codecov로 자동 전송됩니다.
