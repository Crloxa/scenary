package com.scenary.place;

/**
 * Geohash 编码（docs/05 §6.5 E3）：逆地理缓存按 geohash-5（≈4.9km 网格）聚合，
 * 网格内查询共享缓存，避免逐坐标打爆 provider。算法为标准的二分区间交替编码，
 * 不引入第三方依赖（01 §3.1 依赖冻结线）。
 */
public final class Geohash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int[] BITS = {16, 8, 4, 2, 1};

    private Geohash() {
    }

    public static String encode(double latitude, double longitude, int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("precision must be 1~12");
        }
        double latLow = -90, latHigh = 90;
        double lngLow = -180, lngHigh = 180;
        StringBuilder hash = new StringBuilder(precision);
        boolean even = true;
        int bit = 0;
        int ch = 0;
        while (hash.length() < precision) {
            if (even) {
                double mid = (lngLow + lngHigh) / 2;
                if (longitude >= mid) {
                    ch |= BITS[bit];
                    lngLow = mid;
                } else {
                    lngHigh = mid;
                }
            } else {
                double mid = (latLow + latHigh) / 2;
                if (latitude >= mid) {
                    ch |= BITS[bit];
                    latLow = mid;
                } else {
                    latHigh = mid;
                }
            }
            even = !even;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }
}
