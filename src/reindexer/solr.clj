(ns reindexer.solr
  (:import [org.apache.solr.client.solrj SolrQuery]
           [org.apache.solr.client.solrj.impl HttpSolrServer]
           [org.apache.solr.common SolrInputDocument]))


(defn connect-to-cores
  "Given a config map, connects to a remote solr instance via http"
  [urls]
  (map #(HttpSolrServer. %) urls))

(defn query-solr
  "Given a solr cxn and a query, return a collection of results"
  [client query-string]
  (let [query    (doto (SolrQuery.) (.setQuery query-string))
        response (.query client query)]
    (.getResults response)))

(defn as-input-document
  "Given a clojure map with string keys, return a solr input document"
  [m]
  (let [document (SolrInputDocument.)]
    (doseq [[name value] m]
      (.addField document name value))
    document))

(defn update-in-cores
  "Given an array of connections and document maps, update documents in the colls"
  [clients document-maps]
  (let [documents (map as-input-document document-maps)]
    (doseq [c clients]
      (.add c documents)
      (.commit c))))




;; Some notes
;; Had to tread carefully because solrj ifaces are always changing, thankfully
;; the javadocs + sorta current guides kinda helped:
;; * https://lucene.apache.org/solr/5_5_0//solr-solrj/org/apache/solr/client/solrj/package-summary.html
;; * https://lucene.apache.org/solr/guide/6_6/using-solrj.html
;; * http://www.solrtutorial.com/solrj-tutorial.html

; Have to add a localtunnel to my ssh config for a remote box:
;; LocalForward 8081 solr:8080

;; some REPL testing: I usually send forms one by one with C-c M-p
;; but you can also just eval the whole thing with C-c M-e at the end of the
;; do block.

(comment
  (do
    (def clients (connect-to-cores ["http://127.0.0.1:8081/solr440/products"]))
    (def results (query-solr (first clients) "id:111"))
    (println (get (first results) "product_name"))
                                        ; => "blowPro Blow Up Thickening Mist
    (def doc (first results))
    ;; the document is a SolrDocument instance, so, mutable
    (.setField doc "product_name" "Luis's magical product")
    (.get doc "product_name")
    (def doc-map (zipmap (keys doc) (vals doc)))
    ; notice it's a clojure map now:
    (get doc-map "product_name")
    ;; one interesting to notice is that trying to print new-doc
    ;; to the REPL raises an exception. Gotta inspect it lightly
    (def new-doc (as-input-document doc-map))
    (.keySet new-doc)
    (.get new-doc "product_name")
    ;; notice that "title_sort" isn't supposed to be here, but that's a testing
    ;; issue, so we just dissoc:
    (def doc-maps [(dissoc doc-map "title_sort")])
    (update-in-cores clients doc-maps)))
