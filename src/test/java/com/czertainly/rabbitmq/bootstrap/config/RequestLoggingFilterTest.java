package com.czertainly.rabbitmq.bootstrap.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void missingCorrelationId_generatesUuidAndSetsResponseHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String correlationId = response.getHeader(RequestLoggingFilter.CORRELATION_ID_HEADER);
        assertThat(correlationId)
                .isNotNull()
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void presentCorrelationId_propagatedToResponseHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/proxies");
        request.addHeader(RequestLoggingFilter.CORRELATION_ID_HEADER, "my-test-id-123");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.CORRELATION_ID_HEADER))
                .isEqualTo("my-test-id-123");
    }

    @Test
    void mdcIsSetDuringFilterAndClearedAfter() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        request.addHeader(RequestLoggingFilter.CORRELATION_ID_HEADER, "corr-id-for-mdc");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        String[] mdcDuringFilter = new String[1];
        doAnswer(invocation -> {
            mdcDuringFilter[0] = MDC.get(RequestLoggingFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        assertThat(mdcDuringFilter[0]).isEqualTo("corr-id-for-mdc");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }

    @Test
    void filterChainIsInvoked() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/proxies");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(any(), any());
    }
}
