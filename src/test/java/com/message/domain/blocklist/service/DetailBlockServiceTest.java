package com.message.domain.blocklist.service;

import com.message.domain.blocklist.query.BlockCheckQuery;
import com.message.domain.blocklist.repository.BlocklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DetailBlockServiceTest {

    @InjectMocks
    private DetailBlockService detailBlockService;

    @Mock
    private BlocklistRepository blocklistRepository;

    private static final String BLOCKED_PHONE = "01099999999";
    private static final String BLOCKED_EMAIL = "blocked@example.com";
    private static final String NORMAL_PHONE = "01012345678";
    private static final String NORMAL_EMAIL = "normal@example.com";

    @Test
    @DisplayName("차단된 전화번호로 조회하면 true를 반환한다")
    void isBlocked_blockedPhone_returnsTrue() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(BLOCKED_PHONE, null);
        given(blocklistRepository.existsByCellPhone(BLOCKED_PHONE)).willReturn(true);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isTrue();
        verify(blocklistRepository).existsByCellPhone(BLOCKED_PHONE);
    }

    @Test
    @DisplayName("차단된 이메일로 조회하면 true를 반환한다")
    void isBlocked_blockedEmail_returnsTrue() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(null, BLOCKED_EMAIL);
        given(blocklistRepository.existsByEmail(BLOCKED_EMAIL)).willReturn(true);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isTrue();
        verify(blocklistRepository).existsByEmail(BLOCKED_EMAIL);
    }

    @Test
    @DisplayName("phone이 null이면 email만 차단 여부를 체크한다")
    void isBlocked_nullPhone_checksEmailOnly() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(null, BLOCKED_EMAIL);
        given(blocklistRepository.existsByEmail(BLOCKED_EMAIL)).willReturn(true);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isTrue();
        verify(blocklistRepository, never()).existsByCellPhone(any());
        verify(blocklistRepository).existsByEmail(BLOCKED_EMAIL);
    }

    @Test
    @DisplayName("email이 null이면 전화번호만 차단 여부를 체크한다")
    void isBlocked_nullEmail_checksPhoneOnly() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(BLOCKED_PHONE, null);
        given(blocklistRepository.existsByCellPhone(BLOCKED_PHONE)).willReturn(true);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isTrue();
        verify(blocklistRepository).existsByCellPhone(BLOCKED_PHONE);
        verify(blocklistRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("phone과 email이 모두 null이면 false를 반환한다")
    void isBlocked_bothNull_returnsFalse() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(null, null);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isFalse();
        verify(blocklistRepository, never()).existsByCellPhone(any());
        verify(blocklistRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("phone과 email 모두 차단되지 않은 경우 false를 반환한다")
    void isBlocked_neitherBlocked_returnsFalse() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(NORMAL_PHONE, NORMAL_EMAIL);
        given(blocklistRepository.existsByCellPhone(NORMAL_PHONE)).willReturn(false);
        given(blocklistRepository.existsByEmail(NORMAL_EMAIL)).willReturn(false);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isFalse();
        verify(blocklistRepository).existsByCellPhone(NORMAL_PHONE);
        verify(blocklistRepository).existsByEmail(NORMAL_EMAIL);
    }

    @Test
    @DisplayName("phone이 차단된 경우 email은 체크하지 않는다 (short-circuit)")
    void isBlocked_phoneBlocked_skipsEmailCheck() {
        // given
        BlockCheckQuery query = new BlockCheckQuery(BLOCKED_PHONE, BLOCKED_EMAIL);
        given(blocklistRepository.existsByCellPhone(BLOCKED_PHONE)).willReturn(true);

        // when
        boolean result = detailBlockService.isBlocked(query);

        // then
        assertThat(result).isTrue();
        verify(blocklistRepository).existsByCellPhone(BLOCKED_PHONE);
        verify(blocklistRepository, never()).existsByEmail(BLOCKED_EMAIL);
    }

    // Mockito any() 사용을 위한 static import 보완
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
