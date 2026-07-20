package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlatformInfoResponse {

    private String region;
    private boolean kafkaEnabled;
    private boolean avroEnabled;
    private String schemaVersion;
    private String schemaRegistryUrl;
    /** Active UI lottery type code: SSQ | FC3D */
    private String lotteryType;
    /** Display name, e.g. 福彩3D */
    private String lotteryTypeLabel;
}
