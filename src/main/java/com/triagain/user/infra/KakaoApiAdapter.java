package com.triagain.user.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.port.out.KakaoApiPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoApiAdapter implements KakaoApiPort {

    private static final Logger log = LoggerFactory.getLogger(KakaoApiAdapter.class);
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";  // 카카오 API 주소

    private final RestClient restClient;

    /** 카카오 Access Token → KAKAO 사용자 정보 조회 */
    @Override
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .header("Authorization", "Bearer " + kakaoAccessToken) // 토큰을 담아서!
                    .retrieve()
                    .onStatus(status -> status.value() == 401,
                            (req, res) -> { throw new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN); })
                    .onStatus(HttpStatusCode::isError,
                            (req, res) -> {
                                log.error("카카오 API 오류: status={}", res.getStatusCode());
                                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
                            })
                    .body(KakaoUserResponse.class);

            if (response == null || response.id() == null) {
                throw new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN);
            }

            return new KakaoUserInfo(
                    String.valueOf(response.id()),
                    response.extractNickname(),
                    response.extractEmail(),
                    response.extractProfileImageUrl()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("카카오 API 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }
}
