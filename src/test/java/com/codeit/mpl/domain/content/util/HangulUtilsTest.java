package com.codeit.mpl.domain.content.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HangulUtilsTest {

    @Test
    @DisplayName("extractChosung converts Korean syllables into initial consonants")
    void testExtractChosung() {
        assertThat(HangulUtils.extractChosung("인터스텔라")).isEqualTo("ㅇㅌㅅㅌㄹ");
        assertThat(HangulUtils.extractChosung("영화")).isEqualTo("ㅇㅎ");
        assertThat(HangulUtils.extractChosung("Stranger Things")).isEqualTo("Stranger Things");
        assertThat(HangulUtils.extractChosung("")).isEqualTo("");
        assertThat(HangulUtils.extractChosung(null)).isEqualTo("");
    }
}
