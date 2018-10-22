(ns reindexer.rabbitmq
  (:import [com.rabbitmq.client
            Connection
            Channel
            ConnectionFactory
            QueueingConsumer]))

;; core methods (see usage at end of file)

(defn connect
  "Takes some config, returns a connection to RMQ"
  [{:keys [username password virtual-host host port]
    :as config}]
  (-> (doto (ConnectionFactory.)
        (.setUsername username)
        (.setPassword password)
        (.setVirtualHost virtual-host)
        (.setHost host)
        (.setPort port))
      (.newConnection)))

(defn setup-channel
  "Takes a connection and config and sets up a channel with:
  * a durable, non-autodelete exchange of 'direct' type
  * a durable, non-exclusive, non-autodelete queue with a well-known name
  Returns the channel"
  [cxn {:keys [queue-name exchange-name routing-key]
        :as config}]
  (let [channel (.createChannel cxn)]
    (.exchangeDeclare channel exchange-name "direct" true)
    (.queueDeclare channel queue-name true false false nil)
    (.queueBind channel queue-name exchange-name routing-key)
    channel))

(defn create-consumer
  "Takes a channel and queue name and creates a QueuingConsumer for it"
  [channel queue-name]
  (let [consumer (QueueingConsumer. channel)]
    (.basicConsume channel queue-name false consumer)
    consumer))

(defn consume-delivery
  "Takes a channel and a delivery, applies a processor, acks"
  [channel delivery processor]
  (let [delivery-tag (-> delivery .getEnvelope .getDeliveryTag)]
    (processor delivery)
    (.basicAck channel delivery-tag false)))

(defn consume-forever
  "Takes a channel/cxn, a consumer and a processor; consumes forever.
  Dies upon exception, closing the channel and connection"
  [connection channel consumer processor]
  (try (loop []
         (consume-delivery channel
                           (.nextDelivery consumer)
                           processor)
         (recur))
       (catch Exception e
         (.printStackTrace e)
         (str "Failed (or killed) - " (.getMessage e)))
       (finally (.close channel)
                (.close connection))))

(defn subscribe-to-queue
  "Takes some config and a processor, sets up stuff, consumes forever"
  [config processor]
  (let [connection (connect config)
        channel    (setup-channel connection config)
        consumer   (create-consumer channel (:queue-name config))]
    (consume-forever connection channel consumer processor)))


;; Convenience methods:

(defn process-string
  "Example delivery processor that just gets the body of the delivery
  and doesn't check for routing keys or anything"
  [delivery]
  (let [delivery-body (slurp (.getBody delivery))]
    delivery-body))

;; Some notes:

;; Followed this guide:
;; https://www.rabbitmq.com/api-guide.html#connecting
;; Notice that I had to set my local tunnel as follows:
;; Host new-dev
;;   Hostname luis-beta.dev.birchbox.com
;;   ForwardAgent yes
;;   User luis
;;   LocalForward 5673 rabbitmq:5672
;; As per the "service catalog": https"//birchbox.atlassian.net/wiki/spaces/TECH/pages/43155572/Service+Catalog#ServiceCatalog-examplesExamples"
;; was able to see my connections and stuff here: http://rabbitmq.luis-beta.dev.birchbox.com:15672/#/connections
;; There's some examples of producers and consumers in rubyland here:
;; https://github.com/birchbox/authentication_sdk/blob/ddac058ab6e8c5413cf58787f60dc724e74b3a53/lib/authentication_sdk/publisher.rb#L13-L16
;; https://github.com/birchbox/percolator/blob/master/lib/consumer/base_consumer.rb
;; and walked through here: https://birchbox.atlassian.net/wiki/spaces/TECH/pages/62586906/Lifecycle+user+API+session+to+asset+broker+visitor+id+entry

;; reindexer.rabbitmq> (def resp (.basicGet chan "amq.gen-hl_RMM1OkUd7q7iNLDob-w" true))
;; #'reindexer.rabbitmq/resp
;; reindexer.rabbitmq> (slurp (.getBody resp))
;; "{\"hello\":\"world\"}"
;; reindexer.rabbitmq> 

;; followed also: 
;; http://www.learningclojure.com/2011/02/rabbitmq-clojure-hello-world.html
;; https://www.rabbitmq.com/releases/rabbitmq-java-client/v2.1.1/rabbitmq-java-client-javadoc-2.1.1/com/rabbitmq/client/QueueingConsumer.html

 ;; // Create connection and channel.
 ;; ConnectionFactory factory = new ConnectionFactory();
 ;; Connection conn = factory.newConnection();
 ;; Channel ch1 = conn.createChannel();

 ;; // Declare a queue and bind it to an exchange.
 ;; String queueName = ch1.queueDeclare().getQueue();
 ;; ch1.queueBind(queueName, exchangeName, queueName);

 ;; // Create the QueueingConsumer and have it consume from the queue
 ;; QueueingConsumer consumer = new QueueingConsumer(ch1);
 ;; ch1.basicConsume(queueName, false, consumer);

 ;; // Process deliveries
 ;; while (/* some condition * /) {
 ;;     QueueingConsumer.Delivery delivery = consumer.nextDelivery();
 ;;     // process delivery
 ;;     ch1.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
;; }

;; More references for RMQ:

;; * https://www.rabbitmq.com/api-guide.html#connecting
;; * https://www.rabbitmq.com/releases/rabbitmq-java-client/v2.1.1/rabbitmq-java-client-javadoc-2.1.1/com/rabbitmq/client/QueueingConsumer.html
;; * https://www.cloudamqp.com/blog/2015-09-03-part4-rabbitmq-for-beginners-exchanges-routing-keys-bindings.html

;; example usage:
;; (you can use C-c M-p to throw sexps from here into the REPL)
;; as per: https://cider.readthedocs.io/en/latest/interactive_programming/

(comment
  (do (def cxn (connect))
      (def example-channel (setup-channel cxn
                                          {:queue-name "reindex"
                                           :exchange-name "product-reindexer"
                                           :routing-key "us.product"}))
      (def example-consumer (create-consumer example-channel "reindex"))
      (println "At this point, you'd use some tool to publish a message")
      (consume-delivery example-channel
                        (.nextDelivery example-consumer)
                        println)
      (println "The above call would block until there's a delivery,
                    and return hello world eventually")
      (println "Or you can e.g. consume up to 3 messages:")
      (let [cnt (atom 3)]
        (while (pos? @cnt)
          (do
            (consume-delivery example-channel
                              (.nextDelivery example-consumer)
                              #(println (str "received: "
                                             (process-string %))))
            (swap! cnt dec))))
      (println "Also cool to just consumer forever:")
      (consume-forever example-channel
                       example-consumer
                       #(println (str "got: "
                                      (process-string %))))
      ;; e.g.
      ;; => got: hello world 1
      ;; => got: hello world 2
      ;; ...
      (println "You probably killed the above; it closed the channel")
      (println "Let's try with some toy config:")
      (def config {:username "luis"
                   :password "hunter2"
                   :virtual-host "/birchbox-event-bus"
                   :host "127.0.0.1"
                   :port 5673
                   :queue-name "reindex"
                   :exchange-name "reindex-events"
                   :routing-key "product_reindex"})
      (defn print-message [d]
        (println (str "Received: " (process-string d))))
      (subscribe-to-queue config print-message)
      (.close cxn)))
