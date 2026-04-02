package com.example.webdienthoai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R2PresignServiceTest {

    @Test
    void normalizeR2Endpoint_stripsBucketPath() {
        String in = "https://a0fbfef35f171fc3bd858423bf5cafed.r2.cloudflarestorage.com/springr2-student";
        assertThat(R2PresignService.normalizeR2Endpoint(in))
                .isEqualTo("https://a0fbfef35f171fc3bd858423bf5cafed.r2.cloudflarestorage.com");
    }

    @Test
    void normalizeR2Endpoint_keepsHostOnly() {
        String in = "https://a0fbfef35f171fc3bd858423bf5cafed.r2.cloudflarestorage.com";
        assertThat(R2PresignService.normalizeR2Endpoint(in)).isEqualTo(in);
    }
}
