package com.scenary.place;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 自托管 Nominatim provider（docs/05 §6.5）：坐标只流经内网容器。
 * 超时（连接/读同用 provider-timeout-ms）、HTTP 错误、海面 error 响应、解析失败
 * 一律折叠为空候选——发布链路绝不因逆地理失败被阻塞。
 */
@Component
public class NominatimProvider implements PlaceProvider {

    /** place_name 列宽（V1 VARCHAR(128)），display_name 兜底时截断对齐 */
    static final int MAX_NAME_LENGTH = 128;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NominatimProvider(PlaceProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getProviderTimeoutMs());
        factory.setReadTimeout((int) properties.getProviderTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getProviderUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "scenary/1.0 (reverse-geocode)")
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "nominatim";
    }

    @Override
    public Optional<String> reverseGeocode(double latitude, double longitude) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("zoom", 16)
                            .queryParam("accept-language", "zh-CN")
                            .build())
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(parseName(body));
        } catch (Exception e) {
            // 超时/网络/5xx 全部按空候选降级，不区分失败原因（docs/05 §6.5 降级语义）
            return Optional.empty();
        }
    }

    /** 供单测直接覆盖解析分支：优先短地名 name，退回 display_name 并截断到列宽 */
    String parseName(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("error")) {
                return null;
            }
            String name = node.path("name").asText("");
            if (name.isBlank()) {
                name = node.path("display_name").asText("");
            }
            if (name.isBlank()) {
                return null;
            }
            return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        } catch (Exception e) {
            return null;
        }
    }
}
