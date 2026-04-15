package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.exception.QueueAlreadyExistsException;
import com.czertainly.rabbitmq.bootstrap.model.QueueRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    @InjectMocks
    private QueueServiceImpl queueService;

    @Test
    void provisionQueue_declaresDurableQueueWithArguments() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0")
                .properties(Map.of("x-expires", 1800000));

        queueService.provisionQueue(request);

        var queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(queueCaptor.capture());
        Queue declared = queueCaptor.getValue();
        assertThat(declared.getName()).isEqualTo("core-0");
        assertThat(declared.isDurable()).isTrue();
        assertThat(declared.getArguments()).containsEntry("x-expires", 1800000);
    }

    @Test
    void provisionQueue_declaresBindingWithRoutingKeyAndExchange() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0");

        queueService.provisionQueue(request);

        var bindingCaptor = ArgumentCaptor.forClass(Binding.class);
        verify(rabbitAdmin).declareBinding(bindingCaptor.capture());
        Binding binding = bindingCaptor.getValue();
        assertThat(binding.getRoutingKey()).isEqualTo("proxymessage.*.core-0");
        assertThat(binding.getExchange()).isEqualTo("czertainly-proxy");
    }

    @Test
    void provisionQueue_withNullProperties_declaresQueueWithoutArguments() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0");
        // properties not set — defaults to null

        queueService.provisionQueue(request);

        var queueCaptor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(queueCaptor.capture());
        assertThat(queueCaptor.getValue().getArguments()).isNullOrEmpty();
    }

    @Test
    void provisionQueue_throwsQueueAlreadyExistsException_onPreconditionFailed() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpIOException(new IOException("PRECONDITION_FAILED - inequivalent arg 'x-expires' for queue 'core-0'")))
                .when(rabbitAdmin).declareQueue(any());

        assertThatThrownBy(() -> queueService.provisionQueue(request))
                .isInstanceOf(QueueAlreadyExistsException.class)
                .hasMessageContaining("core-0");
        verify(rabbitAdmin, never()).declareBinding(any());
    }

    @Test
    void provisionQueue_throwsQueueAlreadyExistsException_whenTopLevelMessageContainsPreconditionFailed() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpException("PRECONDITION_FAILED - inequivalent arg"))
                .when(rabbitAdmin).declareQueue(any());

        assertThatThrownBy(() -> queueService.provisionQueue(request))
                .isInstanceOf(QueueAlreadyExistsException.class);
    }

    @Test
    void provisionQueue_rethrowsOtherAmqpExceptions() {
        var request = new QueueRequest()
                .name("core-0")
                .exchange("czertainly-proxy")
                .routingKey("proxymessage.*.core-0");
        doThrow(new AmqpIOException(new IOException("CONNECTION_REFUSED")))
                .when(rabbitAdmin).declareQueue(any());

        assertThatThrownBy(() -> queueService.provisionQueue(request))
                .isInstanceOf(AmqpIOException.class);
    }

    @Test
    void deleteQueue_callsRabbitAdminWithName() {
        queueService.deleteQueue("core-0");

        verify(rabbitAdmin).deleteQueue("core-0");
    }

    @Test
    void deleteQueue_succeedsEvenWhenQueueNotFound() {
        when(rabbitAdmin.deleteQueue("nonexistent")).thenReturn(false);

        assertThatNoException().isThrownBy(() -> queueService.deleteQueue("nonexistent"));
    }
}
