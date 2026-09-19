package com.scenary.place;

import java.util.Optional;

/**
 * 逆地理 provider SPI（docs/05 §6.5 E3-1）：place 包内门面，Controller/Service 不直接触碰
 * 具体供应商实现。实现必须自托管（用户坐标禁止外发第三方公有 API），并且任何
 * 未启用/超时/失败都返回空候选、绝不抛出到发布链路。
 */
public interface PlaceProvider {

    /** 供应商标识，随响应返回前端（契约 docs/02 §7B）。 */
    String id();

    /**
     * 坐标 → 人类可读地名候选。失败/无结果返回 empty，由调用方决定降级展示。
     */
    Optional<String> reverseGeocode(double latitude, double longitude);
}
