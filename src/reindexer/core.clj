(ns reindexer.core
  (:gen-class)
  (:require [reindexer.rabbitmq :as rmq]
            [reindexer.solr :as solr]
            [clojure.data.json :as json]))

(defn build-config
  "Grabs config from the environment"
  []
  {:username "luis"
   :password "hunter2"
   :virtual-host "/birchbox-event-bus"
   :host "127.0.0.1"
   :port 5673
   :queue-name "reindex"
   :exchange-name "reindex-events"
   :routing-key "product_reindex"
   :solr-urls (clojure.string/split "http://127.0.0.1:8081/solr440/products" #",")})

(defn json-reindexer
  "Takes a config and returns a closed-over fn that can update solr cores from JSON"
  [config]
  (let [clients (solr/connect-to-cores (:solr-urls config))]
    (fn [message-body]
      (let [json-maps (json/read-str message-body)]
            (solr/update-in-cores clients json-maps)))))

(defn -main
  "Infinite loop that picks up reindex messages and sends them to solr"
  [& args]
  (let [config       (build-config)
        sub-forever  (partial rmq/subscribe-to-queue config)
        processor    (json-reindexer config)]
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
    "Helps with repl debuggin"
    [config]
    (fn [message-body]
      (clojure.pprint/pprint (json/read-str message-body))))
  (let [config       (build-config)
        sub-forever  (partial rmq/subscribe-to-queue config)
        ; can also send json-consumer if you're playing around in
        ; the REPL
        processor    (json-reindexer config)]
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

