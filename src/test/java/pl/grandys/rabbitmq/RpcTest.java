package pl.grandys.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static com.rabbitmq.client.AMQP.BasicProperties;

/**
 * Code for tutorial 6, RPC
 * Caller creates single callback queue, and sends message to the callee with generated correlation id.
 * Callee receives message from caller, and responds with the message with correlationId to the queue specified in replyTo property.
 * Callee acknowledges message after response is successfully send to the caller. It's at-least-once delivery.
 */
class RpcTest {

    final static Logger logger = LogManager.getLogger(RpcTest.class);

    private static final String QUEUE_NAME = "logs-rpc";
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ConnectionFactory connectionFactory = new ConnectionFactory();

    {
        connectionFactory.setHost("localhost");
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
    }


    @Test
    void should_receive_messages_from_queue() throws IOException, TimeoutException, InterruptedException {
        try (var connections = connectionFactory.newConnection()) {
            runCaller(connections);
            runCallee(connections);
            Thread.sleep(120 * 1000);
        }

    }

    void runCaller(Connection connections) {
        executor.submit(() -> {
            try {
                Channel channel = connections.createChannel();
                channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                String callbackQueueName = channel.queueDeclare().getQueue();

                while (true) {
                    String request = "RPC Request " + UUID.randomUUID();
                    String correlationId = UUID.randomUUID().toString();
                    BasicProperties publisherProperties = new BasicProperties.Builder()
                            .replyTo(callbackQueueName)
                            .correlationId(correlationId)
                            .build();

                    DeliverCallback callback = ((consumerTag, responseMessage) -> {
                        var stringResponseMessage = new String(responseMessage.getBody(), StandardCharsets.US_ASCII);
                        logger.info("Caller received response message "
                                + stringResponseMessage + " on request "
                                + request + "with correlation id "
                                + responseMessage.getProperties().getCorrelationId());
                    });

                    logger.info("Caller sends " + request + " with correlationId " + correlationId);
                    channel.basicConsume(callbackQueueName, true, callback, consumerTag -> {
                    });

                    channel.basicPublish("", QUEUE_NAME, publisherProperties, request.getBytes(StandardCharsets.US_ASCII));
                    Thread.sleep(200 * new Random().nextInt(10));
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });


    }

    private void runCallee(Connection connections) throws IOException {
        Channel channel = connections.createChannel();

        DeliverCallback callback = ((consumerTag, message) -> {
            var stringMessage = new String(message.getBody(), StandardCharsets.US_ASCII);
            String correlationId = message.getProperties().getCorrelationId();
            logger.info("Callee received " + stringMessage + " with correlation id " + correlationId);
            long deliveryTag = message.getEnvelope().getDeliveryTag();


            String response = "RPC response " + UUID.randomUUID().toString();
            BasicProperties producerProperties =
                    new BasicProperties
                            .Builder()
                            .correlationId(correlationId)
                            .build();
            logger.info("Callee responds  " + response + " with correlation id " + correlationId);
            channel.basicPublish("", message.getProperties().getReplyTo(), producerProperties, response.getBytes(StandardCharsets.US_ASCII));

            channel.basicAck(deliveryTag, false);
        });

        channel.basicConsume(QUEUE_NAME, false, callback, consumerTag -> {
        });
    }
}
