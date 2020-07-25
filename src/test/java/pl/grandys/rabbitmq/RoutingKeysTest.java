package pl.grandys.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Example for tutorial no 4
 * Publishers 1-3 in random intervals 200-2000mS, are publishing messages with with routing keys "green" or "blue".
 * Consumers 1 receives messages with routing key "green", both consumer 2 and 3 receive messages with routing key "blue".
 */
class RoutingKeysTest {

    final static Logger logger = Logger.getLogger(RoutingKeysTest.class);

    private static final String EXCHANGE_NAME = "logs-routed";
    private static final String FIRST_ROUTING_KEY = "green";
    private static final String SECOND_ROUTING_KEY = "blue";
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ConnectionFactory connectionFactory = new ConnectionFactory();

    {
        connectionFactory.setHost("localhost");
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
    }


    @Test
    void run() throws IOException, TimeoutException, InterruptedException {
        try (var connections = connectionFactory.newConnection()) {
            runPublisher(connections, "Publisher 1");
            runPublisher(connections, "Publisher 2");
            runPublisher(connections, "Publisher 3");
            createConsumer(connections, "Consumer 1", FIRST_ROUTING_KEY);
            createConsumer(connections, "Consumer 2", SECOND_ROUTING_KEY);
            createConsumer(connections, "Consumer 3", SECOND_ROUTING_KEY);
            Thread.sleep(120 * 1000);
        }

    }

    void runPublisher(Connection connections, String publisherName) {
        executor.submit(() -> {
            try {
                Channel channel = connections.createChannel();
                channel.exchangeDeclare(EXCHANGE_NAME, "direct");
                while (true) {
                    String message = "Logs " + UUID.randomUUID() + " from publisher " + publisherName;

                    String routingKey = new Random().nextInt() % 2 == 0 ? FIRST_ROUTING_KEY : SECOND_ROUTING_KEY;

                    channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.US_ASCII));

                    logger.info("Sent out message " + message);
                    Thread.sleep(200 * new Random().nextInt(10));
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });


    }

    private void createConsumer(Connection connections, String consumerName, String routingKey) throws IOException {
        Channel channel = connections.createChannel();
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        DeliverCallback callback = ((consumerTag, message) -> {
            var stringMessage = new String(message.getBody(), StandardCharsets.US_ASCII);
            logger.info("Received " + stringMessage + " by " + consumerName + " with routing key " + message.getEnvelope().getRoutingKey());
            long deliveryTag = message.getEnvelope().getDeliveryTag();
            channel.basicAck(deliveryTag, false);
        });

        channel.basicConsume(queueName, false, callback, consumerTag -> {
        });
    }
}
