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
 * Code for tutorial 3- fanout. 
 * Consumers 1 and 2 receive all messages produced by producers 1-3.
 */
class PubSubTest {

    final static Logger logger = Logger.getLogger(PubSubTest.class);
    
    private static final String EXCHANGE_NAME = "logs";
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
            runConsumer(connections, "Consumer 1");
            runConsumer(connections, "Consumer 2");
            Thread.sleep(120 * 1000);
        }

    }

    void runPublisher(Connection connections, String publisherName) {
        executor.submit(() -> {
            try {
                Channel channel = connections.createChannel();
                channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
                while (true) {
                    String message = "Logs " + UUID.randomUUID() + " from publisher " + publisherName;

                    channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.US_ASCII));

                    logger.info("Sent out message " + message);
                    Thread.sleep(200 * new Random().nextInt(10));
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void runConsumer(Connection connections, String consumerName) throws IOException {
        Channel channel = connections.createChannel();
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        DeliverCallback callback = ((consumerTag, message) -> {
            var stringMessage = new String(message.getBody(), StandardCharsets.US_ASCII);
            logger.info("Received " + stringMessage + " by " + consumerName);
            long deliveryTag = message.getEnvelope().getDeliveryTag();
            channel.basicAck(deliveryTag, false);
        });

        channel.basicConsume(queueName, false, callback, consumerTag -> {
        });
    }
}
