package com.czertainly.rabbitmq.bootstrap.controller;

import com.czertainly.rabbitmq.bootstrap.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProxyProvisioningControllerIT {

    private static final String API_KEY = "poc-secret-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void provisionProxy_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_TEST_1"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionProxy_isIdempotent() throws Exception {
        String body = """
                {"proxyCode": "IT_IDEMPOTENT"}
                """;
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void decommissionProxy_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_DELETE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/proxies/IT_DELETE")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void decommissionProxy_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/proxies/NON_EXISTENT")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getInstallationInstructions_returnsHelmCommand() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "IT_INSTALL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/proxies/IT_INSTALL/installation")
                        .header("X-API-Key", API_KEY)
                        .param("format", "helm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command.shell").value(
                        "helm install proxy-IT_INSTALL oci://registry.example.com/charts/proxy --version 1.0.0 --set config.proxyCode=IT_INSTALL"));
    }

    @Test
    void getInstallationInstructions_returnsDockerComposeCommand() throws Exception {
        mockMvc.perform(get("/api/v1/proxies/SOME_PROXY/installation")
                        .header("X-API-Key", API_KEY)
                        .param("format", "docker-compose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command.shell").value(
                        "docker compose -f proxy-SOME_PROXY-compose.yaml up -d"));
    }

    @Test
    void anyRequest_returns401_withoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "NO_AUTH"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyRequest_returns401_withWrongApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/proxies")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proxyCode": "WRONG_AUTH"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
