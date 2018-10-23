(defproject reindexer "0.1.0-SNAPSHOT"
  :description "Lil reindexing daemon"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]
                 [log4j "1.2.16"]
                 ; these older versions match our sad prod/dev java/solr
                 [com.rabbitmq/amqp-client "4.8.3"]
                  ; this bullshit is required by the http solr server:
                 [commons-logging/commons-logging "1.1.1"]
                 [org.apache.solr/solr-solrj "4.4.0"]]
  :main ^:skip-aot reindexer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
