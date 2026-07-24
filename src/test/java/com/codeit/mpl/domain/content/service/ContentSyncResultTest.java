package com.codeit.mpl.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentSyncResult 단위 테스트")
class ContentSyncResultTest {

    @Test
    @DisplayName("empty는 모든 집계값이 0인 결과를 반환한다")
    void empty_returnsZeroResult() {
        // when
        ContentSyncResult result =
                ContentSyncResult.empty();

        // then
        assertThat(result.createdCount())
                .isZero();

        assertThat(result.updatedCount())
                .isZero();

        assertThat(result.skippedCount())
                .isZero();

        assertThat(result)
                .isEqualTo(
                        new ContentSyncResult(
                                0,
                                0,
                                0
                        )
                );
    }

    @Test
    @DisplayName("plus는 생성, 수정, 건너뜀 개수를 각각 합산한다")
    void plus_addsEveryCount() {
        // given
        ContentSyncResult first =
                new ContentSyncResult(
                        2,
                        3,
                        4
                );

        ContentSyncResult second =
                new ContentSyncResult(
                        5,
                        6,
                        7
                );

        // when
        ContentSyncResult result =
                first.plus(second);

        // then
        assertThat(result.createdCount())
                .isEqualTo(7);

        assertThat(result.updatedCount())
                .isEqualTo(9);

        assertThat(result.skippedCount())
                .isEqualTo(11);

        assertThat(result)
                .isEqualTo(
                        new ContentSyncResult(
                                7,
                                9,
                                11
                        )
                );
    }

    @Test
    @DisplayName("plus는 기존 결과를 변경하지 않고 새로운 결과를 반환한다")
    void plus_doesNotModifyOriginalResult() {
        // given
        ContentSyncResult original =
                new ContentSyncResult(
                        1,
                        2,
                        3
                );

        ContentSyncResult other =
                new ContentSyncResult(
                        4,
                        5,
                        6
                );

        // when
        ContentSyncResult result =
                original.plus(other);

        // then
        assertThat(original)
                .isEqualTo(
                        new ContentSyncResult(
                                1,
                                2,
                                3
                        )
                );

        assertThat(result)
                .isNotSameAs(original);

        assertThat(result)
                .isEqualTo(
                        new ContentSyncResult(
                                5,
                                7,
                                9
                        )
                );
    }

    @Test
    @DisplayName("빈 결과를 더하면 기존 값과 동일한 값을 가진 새 결과를 반환한다")
    void plusEmpty_returnsSameValues() {
        // given
        ContentSyncResult original =
                new ContentSyncResult(
                        3,
                        2,
                        1
                );

        // when
        ContentSyncResult result =
                original.plus(
                        ContentSyncResult.empty()
                );

        // then
        assertThat(result)
                .isEqualTo(original);

        assertThat(result)
                .isNotSameAs(original);
    }

    @Test
    @DisplayName("빈 결과에 다른 결과를 더하면 다른 결과의 값이 반환된다")
    void emptyPlusResult_returnsOtherValues() {
        // given
        ContentSyncResult other =
                new ContentSyncResult(
                        7,
                        8,
                        9
                );

        // when
        ContentSyncResult result =
                ContentSyncResult
                        .empty()
                        .plus(other);

        // then
        assertThat(result)
                .isEqualTo(other);

        assertThat(result)
                .isNotSameAs(other);
    }
}

