package com.scenary.place;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * P12-E3 逆地理编码配置（application.yml scenary.place，环境变量 SCENARY_PLACE_*）。
 * 默认 provider 关闭：接口稳定返回空候选，前端保留手工输入（docs/05 §6.5 供应商决策）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "scenary.place")
public class PlaceProperties {

    /** 是否启用自托管 provider；false 时接口返回空候选（provider=none），无任何出网请求 */
    private boolean providerEnabled = false;

    /** 自托管 Nominatim/Photon 地址，仅内网可达（compose 内无公网映射） */
    private String providerUrl = "http://nominatim:8080";

    /** provider 超时（连接与读），超时按空候选降级 */
    private long providerTimeoutMs = 2000;

    /** geohash-5 缓存 TTL（天），契约默认 30 天 */
    private long cacheTtlDays = 30;
}
