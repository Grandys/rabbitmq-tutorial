# RabbitMQ tutorial
Example code for [RabbitMQ tutorial](https://www.rabbitmq.com/getstarted.html)
# Contents:
* Tests which are actually a running examples from tutorials
* Docker compose running official [RabbitMQ image](https://hub.docker.com/_/rabbitmq)
* Schema for RabbitMQ automatically 
# How to run project
* `docker-compose up` will run RabbitMQ server with management console. When server is running, you can log in with credentials `guest/guest` under the address [http://localhost:8080/](http://localhost:8080/)
* Run individual test from package `pl.grandys.rabbitmq` and observe different behaviours in the console logs