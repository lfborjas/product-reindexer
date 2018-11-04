(ns reindexer.core
  (:gen-class)
  (:require [reindexer.rabbitmq :as rmq]
            [reindexer.solr :as solr]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as s]))

(defn build-config
  "Grabs config from the environment"
  []
  {:username (System/getenv "RMQ_USERNAME")
   :password (System/getenv "RMQ_PASSWORD")
   :virtual-host (System/getenv "RMQ_VHOST")
   :host (System/getenv "RMQ_HOST")
   :port (Integer/parseInt (System/getenv "RMQ_PORT"))
   :queue-name (System/getenv "RMQ_QUEUE_NAME")
   :exchange-name (System/getenv "RMQ_EXCHANGE_NAME")
   :routing-key (System/getenv "RMQ_ROUTING_KEY")
   :solr-urls (clojure.string/split (System/getenv "SOLR_URLS") #",")})

(defn reindexer
  "Takes core connections and an envelope, reindexes"
  [clients json-message]
  (let [to-index    (get json-message "to_index" [])
        remove-ids  (get json-message "to_remove" [])
        index-ids   (map #(get % "id") to-index)]
    (log/info (str "About to index: " (s/join "," index-ids)))
    (solr/update-in-cores clients to-index)
    (log/info (str "About to remove: " (s/join "," remove-ids)))
    (solr/remove-from-cores clients to-remove)))

(defn solr-consumer
  "Takes a config and returns a closed-over fn that can update solr cores from JSON"
  [config]
  (let [solr-urls      (:solr-urls config)
        product-cores  (solr/connect-to-cores solr-urls :products)
        category-cores (solr/connect-to-cores solr-urls :categories)
        brand-cores    (solr/connect-to-cores solr-urls :brands)]
    (fn [message-body]
      (try (let [json-message (json/read-str message-body)
                 core         (get json-message "core")]
             (log/info (str "Received reindex message (" core ")"))
             (case core
               "products"   (reindexer product-cores json-message)
               "categories" (reindexer category-cores json-message)
               "brands"     (reindexer brand-cores json-message)
               (log/warn (str "Unknown core: " core))))
           (catch Exception e
             (log/error e "Error reindexing, skipping!"))))))

(defn -main
  "Infinite loop that picks up reindex messages and sends them to solr"
  [& args]
  (let [config       (build-config)
        sub-forever  (partial rmq/subscribe-to-queue config)
        processor    (solr-consumer config)]
    (sub-forever processor)))

;; ======= DEV NOTES FOLLOW =================================

;; I chuck these s-exps from here to a running CIDER repl
;; with C-c M-p, useful to put the whole let in there and have the
;; "process" running on a terminal
(comment
  ; Ideally, all that -main does is pick up stuff from the env
  ; and then spins up two closures from the libs and lets
  ; them run forever
  (defn json-consumer
    "Helps with repl debugging"
    [config]
    (fn [message-body]
      (clojure.pprint/pprint (json/read-str message-body))))
  (defn json-exhauster [_] #(println %))
  (let [config       {:username "luis"
                      :password "hunter2"
                      :virtual-host "/birchbox-event-bus"
                      :host "127.0.0.1"
                      :port 5673
                      :queue-name "reindex"
                      :exchange-name "reindex-events"
                      :routing-key "product_reindex"
                      :solr-urls (clojure.string/split "http://127.0.0.1:8081/solr440" #",")}
        sub-forever  (partial rmq/subscribe-to-queue config)
        ; can also send json-exhauster/consumer if you're playing around in the REPL
        processor    (solr-consumer config)]
    (sub-forever processor)))

;; NOTES

; Had to do all this bullshit to produce some sort of workable JSON from rails:
;; coll = ProductCollection.new(ids: [111], indexable: true)
;; products = Solr::ProductCollectionDecorator.decorate(coll, preload_data: false, discontinued_only: false, context: {bust_caches: true})
;; batch_products = Solr::ProductCollectionDecorator.new(coll, products: products.products)
;; h = Solr::ProductCollectionSerializer.new(batch_products, root: false).serializable_hash[:products]
;; puts h.to_json # then copy-paste this verbatim in the rabbitmq env
;; h.as_json.first["brand_position_97"]
;; however, that last datum returned "" instead of 0,
;; causing the following error in the remote solr:
;; (from http://solr.luis-beta.dev.birchbox.com:8080/solr440/#/~logging)
;; null:org.apache.solr.common.SolrException: Error while creating field 'brand_position_97{type=sint,properties=indexed,stored,omitNorms,sortMissingLast}' from value ''

