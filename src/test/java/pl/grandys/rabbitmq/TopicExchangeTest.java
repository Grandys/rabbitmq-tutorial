package pl.grandys.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Code for tutorial 5- Topics
 * Producer randomly selects binding keys - "logs.green" OR "logs.blue" OR "nlogs" and sends them to the "logs-topics" exchange
 * Consumer 1 is subscribed on binging key "logs.green", consumer 2 on "logs.green".
 * Consumer 3 is subscribed on wildcard pattern logs.*, and it will receive all the with the "logs" prefix
 * Messages with routing key "nlogs" will be discarded
 */
class TopicExchangeTest {

    final static Logger logger = Logger.getLogger(TopicExchangeTest.class);

    private static final String EXCHANGE_NAME = "logs-topic";
    private static final String FIRST_ROUTING_BINDING = "logs.green";
    private static final String SECOND_ROUTING_BINDING = "logs.blue";
    private static final String EMPTY_ROUTING_BINDING = "nlogs";
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
            createConsumer(connections, "Consumer 1", FIRST_ROUTING_BINDING);
            createConsumer(connections, "Consumer 2", SECOND_ROUTING_BINDING);
            createConsumer(connections, "Consumer 3", "logs.*");
            Thread.sleep(120 * 1000);
        }

    }

    void runPublisher(Connection connections, String publisherName) {
        executor.submit(() -> {
            try {
                Channel channel = connections.createChannel();
                channel.exchangeDeclare(EXCHANGE_NAME, "topic");
                while (true) {
                    String message = "Logs " + UUID.randomUUID() + " from publisher " + publisherName;

                    var bingingKeys = new java.util.ArrayList<>(List.of(FIRST_ROUTING_BINDING, SECOND_ROUTING_BINDING, EMPTY_ROUTING_BINDING));
                    Collections.shuffle(bingingKeys);
                    String bindingKey = bingingKeys.get(0);

                    channel.basicPublish(EXCHANGE_NAME, bindingKey, null, message.getBytes(StandardCharsets.US_ASCII));
                    logger.info("Sent out message " + message + " with binding key" + bindingKey);
                    Thread.sleep(200 * new Random().nextInt(10));
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });


    }

    private void createConsumer(Connection connections, String consumerName, String bindingKey) throws IOException {
        Channel channel = connections.createChannel();
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);

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
