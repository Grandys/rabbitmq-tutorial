package pl.grandys.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.MessageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Code for tutorials 1 & 2, Hello World and Work queues.
 * First test sends messages to the test_queue.
 * In second test consumers read messages from test_queue. When test is started, consumer 1 first connects to the queue, and consumes all messages.
 * When messages are published ex. via Management Console, messages are distributed with Round Robin.
 */
class SendTest {

    final static Logger logger = LogManager.getLogger(SendTest.class);

    private static final String QUEUE_NAME = "test_queue";
    private final ConnectionFactory connectionFactory = new ConnectionFactory();

    {
        connectionFactory.setHost("localhost");
    }

    @Test
    void should_send_message_to_queue() throws IOException, TimeoutException {

        try (var connections = connectionFactory.newConnection()) {
            Channel channel = connections.createChannel();
            channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of());
            for (int i = 0; i < 10; i++) {
                String message = "Hello rabbitMq! " + UUID.randomUUID() + ".".repeat(i);

                channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.US_ASCII));

                logger.info("Sent out message " + message);
            }
        }
    }

    @Test
    void should_receive_messages_from_queue() throws IOException, TimeoutException, InterruptedException {
        try (var connections = connectionFactory.newConnection()) {
            createConsumer(connections, "First");
            createConsumer(connections, "Second");
            Thread.sleep(10 * 1000);
        }

    }

    private void createConsumer(Connection connections, String consumerName) throws IOException {
        Channel channel = connections.createChannel();
        channel.basicQos(1);
        channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of());

        DeliverCallback callback = ((consumerTag, message) -> {
            var stringMessage = new String(message.getBody(), StandardCharsets.US_ASCII);
            logger.info("Received " + stringMessage + " by " + consumerName);
            long seconds = stringMessage.chars().filter(ch -> ch == '.').count();

            try {
                Thread.sleep(seconds * 100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Finished processing " + stringMessage + " by " + consumerName);
            long deliveryTag = message.getEnvelope().getDeliveryTag();
            logger.info("Acknowledging tag " + deliveryTag + "Thread name " + Thread.currentThread().getName());
            channel.basicAck(deliveryTag, false);
        });

        channel.basicConsume(QUEUE_NAME, false, callback, consumerTag -> {
        });
    }
}
