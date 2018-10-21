(ns reindexer.core
  (:gen-class))

(defn -main
  "Infinite loop that picks up reindex messages and sends them to solr"
  [& args]
  (println "Hello, World!"))
