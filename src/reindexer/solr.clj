(ns reindexer.solr
  (:import [org.apache.solr.client.solrj SolrQuery]
           [org.apache.solr.client.solrj.impl HttpSolrClient]
           [org.apache.solr.common SolrInputDocument]))


;; String urlString = "http://localhost:8983/solr/techproducts";
;; SolrClient solr = new HttpSolrClient.Builder(urlString).build();
; Have to add a localtunnel to my ssh config for a remote box:
;; LocalForward 8081 solr:8080

(defn connect-to-cores
  "Given a config map, connects to a remote solr instance via http"
  [urls]
  (map #(HttpSolrClient. %) urls))

(defn query-solr
  "Given a solr url and a query, do a simple select on it"
  [client query-string]
  (let [query    (doto (SolrQuery.) (.setQuery query-string))
        response (.query client query)]
    (.getResults response)))

(defn as-input-document
  "Given a clojure map, return a solr input document"
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

(comment
  (do
    (def clients (connect-to-cores ["http://localhost:8081/solr440/products"]) )
    (def results (query-solr (first clients) "id:111"))
    (println (get (first results) "product_name"))
                                        ; => "blowPro Blow Up Thickening Mist
    ;; the document is a SolrDocument instance, so, mutable
    (.setField doc "product_name" "Luis's magical product")
    (def doc-map (zipmap (keys doc) (vals doc)))
    ;; one interesting to notice is that trying to print new-doc
    ;; to the REPL raises an exception. Gotta inspect it lightly
    (def new-doc (as-input-document doc-map))
    (.keySet new-doc)
    (.get new-doc "product_name")
    (def doc-maps [doc-map])
    ))
