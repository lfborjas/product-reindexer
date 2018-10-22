# Introduction to reindexer

Some random dev notes:

Things to keep in mind:

* Should compile for Java 6
* Shouldn't require anything new
* Should be able to connect to RMQ and Solr

## Development

Notes:

* https://www.rabbitmq.com/java-client.html (need to use an older version?)
  * http://www.rabbitmq.com/api-guide.html#connecting
* Some bullshit daemon: http://www.learningclojure.com/2011/02/rabbitmq-clojure-hello-world.html
* Some solr libs https://wiki.apache.org/solr/IntegratingSolr#Clojure (don't look super promising, this one's the less worse: https://github.com/mwmitchell/flux )
* Or may want to use solrj: https://wiki.apache.org/solr/Solrj#Adding_Data_to_Solr

Learnings:

* We use Solr 4.4.0 in our envs, so have to use solrj directly
* Looks like the most barebones "lib" for clojure only uses that: https://github.com/mikejs/clojure-solr/blob/master/src/clojure_solr.clj
* Same for AMPQ client: 5.x isn't compatible with java 6, and langohr (the cool clj lib for RMQ) isn't compatible with java 6 either, so have to use a 4.x version of the java client directly


There's a nontrivial amount of Ruby I'll need to replicate here:

* https://github.com/birchbox/product_api/blob/master/lib/solar/refresh.rb#L69-L72
* https://github.com/birchbox/product_api/blob/master/lib/solar/updater.rb
* https://github.com/birchbox/product_api/blob/master/lib/solar/base.rb

Also, make sure I can set up local tunnels to both my solr and rabbitmq in dev

The plan is:

* Set up those tunnels
* Write a function to be able to connect to RMQ and consume a message in an exchange
* Write another function (or set thereof?) to connect to Solr and I dunno, query?
* Be able to run a lil daemon that consumes RMQ messages
* Compile ^ to java 6, see if it works on a prod server and a prod RMQ
* How to harmlessly test writing to the solr core?
* Need to map JSON(?) objects to EDN, then write them to SOLR

Looks like we _can_ port forward RMQ and solr from dev boxes:

https://birchbox.atlassian.net/wiki/spaces/TECH/pages/43155572/Service+Catalog

Just need to hijack the birchbox event bus, perhaps?

Admin iface:

http://rabbitmq.luis-beta.dev.birchbox.com:15672/#/

(gave myself admin access, with a silly password)


## Some random reference links:

* http://www.solrtutorial.com/solrj-tutorial.html
* https://lucene.apache.org/solr/4_4_0//solr-solrj/org/apache/solr/client/solrj/SolrServer.html#add(java.util.Collection)
* https://github.com/mwmitchell/flux/tree/v0.4.0
* https://github.com/mikejs/clojure-solr/blob/master/project.clj
* https://cider.readthedocs.io/en/latest/interactive_programming/
* https://www.cloudamqp.com/blog/2015-09-03-part4-rabbitmq-for-beginners-exchanges-routing-keys-bindings.html
* https://www.baeldung.com/apache-solrj
* https://wiki.apache.org/solr/Solrj#SolrJ.2FSolr_cross-version_compatibility
* https://lucene.apache.org/solr/4_2_1/solr-solrj/org/apache/solr/common/SolrInputDocument.html
* https://wiki.apache.org/solr/UpdateXmlMessages
* https://lucene.apache.org/solr/4_2_1/solr-solrj/org/apache/solr/common/SolrDocument.html#setField(java.lang.String,%20java.lang.Object)
