package com.codeit.mpl.infra.common;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import com.codeit.mpl.domain.content.service.ElasticsearchSyncService;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistContent;
import com.codeit.mpl.domain.curating.repository.PlaylistContentRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistRepository;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ContentRepository contentRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistContentRepository playlistContentRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchSyncService elasticsearchSyncService;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.name}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        if (admin == null) {
            admin = userRepository.save(User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .name(adminName)
                    .role(UserRole.ADMIN)
                    .build());
            log.info("Initialized Admin User: {}", adminEmail);
        }

        // 1-2. Dummy Users Initialization (Woody, Buzz, Jessie, Rex, Slinky)
        List<User> dummyUsers = new ArrayList<>();
        String[] dummyNames = {"우디", "버즈", "제시", "렉스", "슬링키"};
        String[] dummyEmails = {"woody@mopl.io", "buzz@mopl.io", "jessie@mopl.io", "rex@mopl.io", "slinky@mopl.io"};

        for (int i = 0; i < dummyEmails.length; i++) {
            String email = dummyEmails[i];
            String name = dummyNames[i];
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = userRepository.save(User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("mopl1!"))
                        .name(name)
                        .role(UserRole.USER)
                        .build());
                log.info("Initialized Dummy User: {}", email);
            }
            dummyUsers.add(user);
        }

        // 2. Dummy Data Seeding (only if database has no contents)
        if (contentRepository.count() == 0) {
            log.info("Seeding dummy data...");

            // Create Contents
            Content inception = Content.create(
                    admin,
                    ContentType.MOVIE,
                    "인셉션 (Inception)",
                    "크리스토퍼 놀란 감독의 SF 액션 블록버스터. 타인의 꿈에 들어가 생각을 심는 인셉션 작전의 이야기.",
                    "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&auto=format&fit=crop",
                    "https://www.youtube.com/watch?v=YoHD9XEInc0",
                    List.of("SF", "액션", "스릴러", "명작")
            );

            Content interstellar = Content.create(
                    admin,
                    ContentType.MOVIE,
                    "인터스텔라 (Interstellar)",
                    "세계 각국의 정부와 경제가 붕괴한 미래, 인류의 구원을 위해 시공간의 틈을 찾아 떠나는 우주 여행가들의 이야기.",
                    "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&auto=format&fit=crop",
                    "https://www.youtube.com/watch?v=zSWdZAib5nY",
                    List.of("SF", "우주", "감동", "모험")
            );

            Content breakingBad = Content.create(
                    admin,
                    ContentType.TVSERIES,
                    "브레이킹 배드 (Breaking Bad)",
                    "시한부 판정을 받은 평범한 고등학교 화학 교사가 가족의 미래를 위해 마약 제조에 뛰어들며 벌어지는 범죄 스릴러.",
                    "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=500&auto=format&fit=crop",
                    "https://www.youtube.com/watch?v=HhesaQXLuRY",
                    List.of("드라마", "범죄", "스릴러", "명작")
            );

            Content strangerThings = Content.create(
                    admin,
                    ContentType.TVSERIES,
                    "기묘한 이야기 (Stranger Things)",
                    "인디애나주의 작은 마을에서 한 소년이 흔적도 없이 사라지자, 가족과 친구들이 미스터리한 힘을 가진 소녀와 함께 소년을 찾아 나서는 모험담.",
                    "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&auto=format&fit=crop",
                    "https://www.youtube.com/watch?v=b9EkMc79ZSU",
                    List.of("SF", "판타지", "스릴러", "레트로")
            );

            Content sonHighlight = Content.create(
                    admin,
                    ContentType.SPORT,
                    "손흥민 토트넘 핫스퍼 골 모음",
                    "프리미어리그 토트넘 핫스퍼의 캡틴 손흥민 선수의 환상적인 원더골 및 어시스트 하이라이트 영상.",
                    "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&auto=format&fit=crop",
                    "https://www.youtube.com/watch?v=2e49c7o6G4c",
                    List.of("스포츠", "축구", "손흥민", "하이라이트")
            );

            contentRepository.saveAll(List.of(inception, interstellar, breakingBad, strangerThings, sonHighlight));
            log.info("Seeded 5 contents.");

            // Create Playlists
            Playlist sfMasterpieces = playlistRepository.save(new Playlist(
                    admin,
                    "최고의 SF 영화 추천",
                    "우주와 시공간, 무의식을 다루는 지적이고 흥미진진한 SF 명작 영화 모음집입니다."
            ));

            Playlist sportsCollection = playlistRepository.save(new Playlist(
                    admin,
                    "주말에 보기 좋은 스포츠 하이라이트",
                    "가슴이 웅장해지는 한국 선수들의 해외 리그 활약상을 모았습니다."
            ));
            log.info("Seeded 2 playlists.");

            // Link Contents to Playlists
            playlistContentRepository.save(new PlaylistContent(sfMasterpieces, inception));
            playlistContentRepository.save(new PlaylistContent(sfMasterpieces, interstellar));
            playlistContentRepository.save(new PlaylistContent(sportsCollection, sonHighlight));
            log.info("Linked contents to playlists.");

            // Create Reviews
            reviewRepository.save(new Review(admin, inception, "인셉션은 정말 영화 역사에 남을 명작입니다. 꿈속의 꿈이라는 설정이 소름 돋아요.", 5));
            reviewRepository.save(new Review(admin, interstellar, "놀란 감독의 우주적 상상력과 한스 짐mer의 음악이 어우러진 최고의 SF 영화입니다.", 4));
            reviewRepository.save(new Review(admin, sonHighlight, "주말 예능 축구는 언제나 최고입니다. 손흥민 최고!", 5));
            log.info("Seeded 3 reviews.");
        }

        // 3. Sync existing database contents with Elasticsearch on startup
        syncDatabaseWithElasticsearch();
    }

    private void syncDatabaseWithElasticsearch() {
        log.info("Starting database contents sync with Elasticsearch...");
        try {
            elasticsearchSyncService.ensureIndexWithMapping();
            long dbCount = contentRepository.count();
            if (dbCount == 0) {
                log.info("No content in database to sync.");
                return;
            }

            List<Content> allContents = contentRepository.findAll();
            List<ContentDocument> documents = allContents.stream()
                    .map(ContentDocument::from)
                    .toList();

            // OpenSearch의 http.max_content_length(기본 100MB) 초과로 413 오류가 발생하는 것을 방지하기 위해
            // 전체를 한 번에 보내지 않고 BATCH_SIZE 건씩 나눠서 인덱싱한다.
            int batchSize = 100;
            int totalSynced = 0;
            for (int i = 0; i < documents.size(); i += batchSize) {
                List<ContentDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
                contentSearchRepository.saveAll(batch);
                totalSynced += batch.size();
                log.info("Elasticsearch sync progress: {}/{}", totalSynced, documents.size());
            }
            log.info("Successfully synced {} contents to Elasticsearch.", totalSynced);
        } catch (Exception e) {
            log.error("Failed to sync database contents with Elasticsearch on startup", e);
        }
    }
}

