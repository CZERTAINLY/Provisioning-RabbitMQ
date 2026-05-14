package com.czertainly.rabbitmq.bootstrap.controller;

import com.czertainly.rabbitmq.bootstrap.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.security.api-key-enabled=true",
        "app.security.api-key=" + QueueProvisioningControllerIT.API_KEY
})
class QueueProvisioningControllerIT {

    public static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void provisionQueue_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-basic",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.it-q-basic",
                                  "properties": { "x-expires": 60000 }
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionQueue_isIdempotent() throws Exception {
        String body = """
                {
                  "name": "it-q-idempotent",
                  "exchange": "czertainly-proxy",
                  "routingKey": "proxymessage.*.it-q-idempotent",
                  "properties": { "x-expires": 60000 }
                }
                """;

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void provisionQueue_returns409_whenQueueExistsWithDifferentArguments() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-conflict",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.it-q-conflict",
                                  "properties": { "x-expires": 60000 }
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-conflict",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.it-q-conflict",
                                  "properties": { "x-expires": 120000 }
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void provisionQueue_returns400_whenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteQueue_returns204_forExistingQueue() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-to-delete",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.it-q-to-delete"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/queues/it-q-to-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQueue_returns204_evenWhenQueueDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/v1/queues/nonexistent-queue")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQueue_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "it-q-double-delete",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.it-q-double-delete"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/queues/it-q-double-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/queues/it-q-double-delete")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void anyRequest_returns401_withoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "core-0",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyRequest_returns401_withWrongApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/queues")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "core-0",
                                  "exchange": "czertainly-proxy",
                                  "routingKey": "proxymessage.*.core-0"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
