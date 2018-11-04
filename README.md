# Reindexer

Little infinite, resilient, logging loop that subscribes to a RabbitMQ queue and expects the messages to be fully-formed solr docs. If they are, it writes them without much ceremony to the configured Solr core (or cores). Meant to help bridge the gap between public web services that know how to derive the docs (but can't, and shouldn't, write to Solr directly due to network configuration, latency, etc.), and Solr. 

**Note**: If you don't want to read through approximately 100 pages of "why I did this for my team at work but then decided not to deploy it because I found an even less disruptive solution", you can skip to [How it works](#how-it-works-or-wouldve-worked)

## History

At work, we have batch jobs that update our Solr indexes for product data on a fixed schedule. However, sometimes people update products and need to see the changes immediately (or at least much earlier than the next invocation of the indexer jobs); as a bit of tech debt, we didn't build/design code that observes changes to products and triggers one-off Solr updates. For quite a bit, we've had to update products manually (with some scripts). 

There's a separate project that doesn't have these issues, and we're deploying it soon! For the time being, we needed to do something better than engineers running scripts to trigger index updates, but not a full-fledged refactoring of the guts of our e-commerce catalog management. We thought about a self serve "click a button, get a reindex" situation involving an API that gets that request, passes it on to a queue, and a consumer does the actual reindexing. The solutions we found in the rails ecosystem weren't compatible with our stack, and upgrading/shuffling things wasn't worth given that the new replacement project didn't have this issue by design. 

In a "let's pay down this tech debt" frenzy, I decided to look for something we _could_ build with our current stack, with minimal operational complexity. A little jar that used established Java libraries to talk to RabbitMQ (already in use by other projects), and Solr (itself written in Java) seemed like a good idea. Except for the Java part. Enter this little project.

I coded the whole thing on a Sunday, added some logging refinements on a Monday, bounced the idea off a few people on a Tuesday--with good feedback! Decided to kill it on a Wednesday.

Why? I realized, as explained below, it didn't _full_ solve the problem (there was still quite a bit of business logic and database munging that would need to be done, and that was already done in the context of our Ruby apis). So I decided to just write a queue consumer _there_ and not only leverage existing infrastructure, but also an existing codebase. It isn't as pithy as this or as elegant as more modern Ruby on Rails approaches (which is why I somehow didn't see that solution at first!), but it'll get the job done with even fewer moving parts than this.

However, I'm happy with the java interop experience and to know that this is yet another quick win (like my XML [mockery](https://github.com/lfborjas/mockery) service) for my team that Clojure could help with. This time however, I didn't _actually_ need to compromise the stability of our internal ecosystem, so I'm leaving this as an open source reference project, for perhaps others (or future me) who may be resource constrained when solving a problem and/or want to leverage the power of the JVM without the J part of the acronym if it can be helped.

## Design principles

* Introduce the least amount of operational complexity: we already have Java in our servers, and we already have RabbitMQ and Solr. A Jar that reads its config from the environment and logs to STDOUT checks those boxes.
* Have no proprietary business logic: just pick up messages from a queue, translate to Solr documents and put them on Solr; no secret sauce (or coupling!) on how to derive product information (allows to open source and also to consume different representations in the future).
* Be deployable on a server meant for batch jobs and other internal tooling (where, coincidentally, RMQ and Solr reside). 

## How this was _not_ deployed in the end: counterpoints to the design principles

* Even though this was conceived to be easy to sneak in without creating much operational debt as an explicit design principle (it's a little jar that includes all of its dependencies and only needs some env vars to be set), it's still requiring _some debt_: it's more code to maintain, and only me and one other engineer know Clojure at work. My initial impetus was that, if we wanted something like this on our existing Ruby codebases, we'd need to introduce Redis and upgrade the target Rails service to be compatible with Sidekiq. These seemed like larger operational costs to pay for something that would only be needed for a few more months until we introduced a new service with event-based reindexing built-in. 
* I didn't want the crazy business logic that prepares products for Solr here, because it's proprietary, coupled to our e-commerce system and, frankly, a lot. However, after doing profiling on the API that would've created the messages for this lil daemon to consume, it dawned on me that, yes, putting those documents on a queue solves the "web servers shouldn't write to Solr" problem, but the majority of the heavy lifting happens _when_ computing the documents, not when writing to solr. So all the work that should be done asynchronously to not overtax the web servers _is_ in the business logic. 

Given the above, I decided to bite the bullet and write a Ruby queue consumer in the web api itself using [sneakers](https://github.com/jondot/sneakers/tree/v1.0.4/lib/sneakers) (a version compatible with our current production stack) that would, like this Clojure daemon, pick up reindex product messages from the queue, but, since it resides in the same repository of our product service, also have access to all the models that derive documents--thus allowing the web-facing API to just publish ids as messages and delegating the heavy lifting of constructing the documents to the queue consumer, as it should be.

## How it works (or would've worked):

Due to our old versions of rabbitmq and solr (and java!), I had to use very specific, and ancient, versions of java libraries and couldn't leverage more general-purpose clojure wrappers for RabbitMQ ([langohr](http://clojurerabbitmq.info/)) and solr (there's [flux](https://github.com/mwmitchell/flux) but it's... okay).

It expects JSON payloads on a very specific rabbitmq queue, and will add documents on a very specific Solr core (or set thereof).

It writes to a log file in its dir (`reindexer.log`), which uses log4j's [`RollingFileAppender`](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/RollingFileAppender.html) to keep its filesystem footprint small (keeps up to 20 1MB log files). For now it writes some INFO log messages when it's about to reindex, and it'll skip queue payloads that cause exceptions (dutifully logging their stacktraces, but not requeing). It's meant to chug along pretty much forever, unless an exception happens outside of the reindexing function--in which case it'll neatly close its connection to RMQ before dying.

For example:

If you manually put in this message (which is a full product representation):

```json
[{"slug":"blow-up-thickening-mist","is_men":false,"is_women":true,"is_searchable":true,"updated_at":"2018-10-22T02:11:34Z","id":"111","magento_id":"111","url":"/shop/blow-up-thickening-mist","title":"HELLO FROM UBERJAR","product_name":"blowPro Blow Up Thickening Mist","sku":"BLOW-MIS-THICK-FZ","type":"simple","family":"default","product_notes":"","ship_with_box":0,"presale_only":0,"mes_title":"","mes_short_description":"","mes_full_description":"HERES THE CHANGES","mes_size":"","mes_additional_notes":"","is_changeable_subscription":0,"is_lifestyle":null,"mes_group_id":"","key_ingredients":"","great_for":"YOUR HAIR","meta_title":"Blow Up Thickening Mist ","meta_keyword":"","meta_description":"","images":"{\"pdp\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/384x384/163b81649b7ef7bc8a00b0066e59ae0a/t/e/test-image-2-1.jpg\",\"width\":384,\"height\":384,\"is_placeholder\":false},\"box_list\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/200x176/163b81649b7ef7bc8a00b0066e59ae0a/t/e/test-image-2-1.jpg\",\"width\":200,\"height\":176,\"is_placeholder\":false},\"box_feature\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/640x528/163b81649b7ef7bc8a00b0066e59ae0a/t/e/test-image-2-1.jpg\",\"width\":640,\"height\":528,\"is_placeholder\":false}}","media_s":"[{\"format\":\"image\",\"type\":\"primary\",\"position\":1,\"sizes\":{\"large\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/384x384/163b81649b7ef7bc8a00b0066e59ae0a/t/e/test-image-2-1.jpg\",\"width\":384,\"height\":384,\"is_placeholder\":false},\"small\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/96x96/163b81649b7ef7bc8a00b0066e59ae0a/t/e/test-image-2-1.jpg\",\"width\":96,\"height\":96,\"is_placeholder\":false},\"xlarge\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/small_image/1500x1700/b38cf51ec77170b109c5e310157197eb/t/e/test-image-2-1.jpg\",\"width\":1500,\"height\":1700,\"is_placeholder\":false}}},{\"format\":\"image\",\"type\":\"swatch\",\"position\":5,\"sizes\":{\"large\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/thumbnail/384x384/163b81649b7ef7bc8a00b0066e59ae0a/b/l/blow_blowup_900x900.jpg\",\"width\":384,\"height\":384,\"is_placeholder\":false},\"small\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/thumbnail/96x96/163b81649b7ef7bc8a00b0066e59ae0a/b/l/blow_blowup_900x900.jpg\",\"width\":96,\"height\":96,\"is_placeholder\":false},\"xlarge\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/thumbnail/900x900/b38cf51ec77170b109c5e310157197eb/b/l/blow_blowup_900x900.jpg\",\"width\":900,\"height\":900,\"is_placeholder\":false}}},{\"format\":\"image\",\"type\":\"sample\",\"sizes\":{\"large\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/sample_image/384x384/163b81649b7ef7bc8a00b0066e59ae0a/images/catalog/product/placeholder/sample_image.jpg\",\"width\":384,\"height\":384,\"is_placeholder\":true},\"small\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/sample_image/96x96/163b81649b7ef7bc8a00b0066e59ae0a/images/catalog/product/placeholder/sample_image.jpg\",\"width\":96,\"height\":96,\"is_placeholder\":true},\"xlarge\":{\"url\":\"https://www.luis-beta.dev.birchbox.com/shop/media/catalog/product/cache/1/sample_image/b38cf51ec77170b109c5e310157197eb/images/catalog/product/placeholder/sample_image.jpg\",\"width\":262,\"height\":262,\"is_placeholder\":true}}}]","ingredients":[],"ingredients_md":"","brand":"blowPro","brand_id":97,"description":"Flat, limp hair? New York\u2019s blow dry experts have you covered. blowPro's thickening mist is the ultimate way to pump up hair without weighing it down \u2014 perfect for daily use and a must for voomy up-dos.","description_md":"\u003Cp\u003EFlat, limp hair? New York\u2019s blow dry experts have you covered. blowPro's thickening mist is the ultimate way to pump up hair without weighing it down \u2014 perfect for daily use and a must for voomy up-dos.\u003C/p\u003E","short_description":"Give ho-hum hair a boost with this lightweight thickening mist. ","expert_take":"Revive flat hair with blowPro Faux Dry Shampoo.","expert_take_md":"\u003Cp\u003ERevive flat hair with \u003Ca href=\"/shop/blow-faux-dry-shampoo\"\u003EblowPro Faux Dry Shampoo\u003C/a\u003E.\u003C/p\u003E","how_it_works":"","how_to_use":"Spritz lightly on clean, damp hair in sections and blow-dry in upward motions for all over lift.","how_to_use_md":"\u003Cp\u003ESpritz lightly on clean, damp hair in sections and blow-dry in upward motions for all over lift.\u003C/p\u003E\r\n","details_html":"\u003Ch2\u003EBirchbox Breakdown\u003C/h2\u003E\n    \u003Cp\u003EFlat, limp hair? New York\u2019s blow dry experts have you covered. blowPro's thickening mist is the ultimate way to pump up hair without weighing it down \u2014 perfect for daily use and a must for voomy up-dos.\u003C/p\u003E\n    \u003Ch2\u003EHow To Use\u003C/h2\u003E\n    \u003Cp\u003ESpritz lightly on clean, damp hair in sections and blow-dry in upward motions for all over lift.\u003C/p\u003E\n    \u003Ch2\u003EEditor's Tip\u003C/h2\u003E\n    \u003Cp\u003ERevive flat hair with \u003Ca href=\"birchbox://view/products/107\"\u003EblowPro Faux Dry Shampoo\u003C/a\u003E.\u003C/p\u003E\n","size":"8.5 oz","brand_ids":[97],"related_product_ids":[],"is_discontinued":false,"rating":"3.4","rating_weighted":"4.2615","rating_purchased":"3.3333","rating_sampled":"0.0","weighted_rating_sf":"4.2615","max_sale_qty":50,"review_count":14,"in_stock":true,"is_free_shipping":false,"is_reviewable":true,"is_favoritable":true,"is_exclusive":false,"is_visible_in_catalog":true,"is_hazmat":true,"price":"21.0","currency":"USD","blurb":"","upc":"","weight":"1.0","availability":"available","swatch_hex_code":"","category_names":["Hair","Birchbox: December 2010","Birchbox: January 2011 Welcome","Birchbox: March 2011","March's Box - 2011","$25 and Under","Styling Products","Protective Sprays","Texture \u0026 Beach Sprays"],"category_position_6":24,"category_position_101":1,"category_position_157":0,"category_position_194":1,"category_position_196":1,"category_position_217":1,"category_position_580":25,"category_position_589":20,"category_position_697":16,"category_position_3372":13,"category_position_3386":12,"brand_position_97":0,"category_urls":["/","//shop","//shop/march-box","//shop/hair","//shop/hair/treatments","//shop/hair/treatments/protective-sprays","//shop/hair/styling-products-1","//shop/hair/styling-products-1/texture-beach-sprays","//shop/featured","//shop/featured/25-and-under-1","//shop/featured/25-and-under-1/hair","//shop/birchbox-1","//shop/birchbox-1/march-2011","//shop/birchbox-1/march-2011/birchbox-march-2011-bb6","//shop/birchbox-1/march-2011/birchbox-march-2011-bb4","//shop/birchbox-1/january-2011","//shop/birchbox-1/january-2011/birchbox-january-2011-welcomeb","//shop/birchbox-1/apr-dec-2010","//shop/birchbox-1/apr-dec-2010/birchbox-december-2010-volume"],"category_ids":["2","8","280","101","277","196","194","275","157","6","698","3372","697","3386","217","15","580","589"]}]

```

(Ideally an API puts the messages, but let's say it's from RabbitMQ's admin interface):

![image](https://user-images.githubusercontent.com/82133/47277035-e5f3dc00-d589-11e8-99b6-40bb01d3683f.png)


It'll consume it and chuck it onto solr:

![image](https://user-images.githubusercontent.com/82133/47277021-b80e9780-d589-11e8-966f-3b3459135a04.png)

If you tail its log, you may see this (notice that, since `map` is lazy, this first message will open the connections to the solr cores):

```
[2018-10-23 01:06:06,111][INFO][reindexer.core] About to index: 111
[2018-10-23 01:06:06,120][INFO][org.apache.solr.client.solrj.impl.HttpClientUtil] Creating new http client, config:maxConnections=128&maxConnectionsPerHost=32&followRedirects=false
```

And if you send a bad payload, it'll log that error (in this case, malformed JSON) and keep chugging along:

```
[2018-10-23 01:05:30,376][ERROR][reindexer.core] Error reindexing, skipping!
java.lang.Exception: JSON error (expected false)
	at clojure.data.json$_read.invokeStatic(json.clj:215)
	at clojure.data.json$_read.invoke(json.clj:177)
	at clojure.data.json$read.invokeStatic(json.clj:272)
	at clojure.data.json$read.doInvoke(json.clj:228)
	at clojure.lang.RestFn.invoke(RestFn.java:410)
	at clojure.lang.AFn.applyToHelper(AFn.java:154)
	at clojure.lang.RestFn.applyTo(RestFn.java:132)
	at clojure.core$apply.invokeStatic(core.clj:648)
	at clojure.core$apply.invoke(core.clj:641)
	at clojure.data.json$read_str.invokeStatic(json.clj:278)
	at clojure.data.json$read_str.doInvoke(json.clj:274)
	at clojure.lang.RestFn.invoke(RestFn.java:410)
	at reindexer.core$json_reindexer$fn__409.invoke(core.clj:26)
	at reindexer.rabbitmq$consume_delivery.invokeStatic(rabbitmq.clj:47)
	at reindexer.rabbitmq$consume_delivery.invoke(rabbitmq.clj:42)
	at reindexer.rabbitmq$consume_forever.invokeStatic(rabbitmq.clj:55)
	at reindexer.rabbitmq$consume_forever.invoke(rabbitmq.clj:50)
	at reindexer.rabbitmq$subscribe_to_queue.invokeStatic(rabbitmq.clj:68)
	at reindexer.rabbitmq$subscribe_to_queue.invoke(rabbitmq.clj:62)
	at clojure.core$partial$fn__4759.invoke(core.clj:2515)
	at reindexer.core$_main.invokeStatic(core.clj:40)
	at reindexer.core$_main.doInvoke(core.clj:34)
	at clojure.lang.RestFn.invoke(RestFn.java:397)
	at clojure.lang.AFn.applyToHelper(AFn.java:152)
	at clojure.lang.RestFn.applyTo(RestFn.java:132)
	at reindexer.core.main(Unknown Source)

```


## Usage

Ideally, you should grab the standalone uberjar and chuck it on a server:

    $ java -jar uberjar/reindexer-0.1.0-SNAPSHOT-standalone.jar

However, it needs some env vars; see the example shell script for the env variables that it expects to pick up, so you can use `run.sh.example` as a basis for something like:

    $ ./reindexer.sh


## Development

See the `comment` blocks at the end of each of the source files for some thoughts on interactive development (inspired by Stuart Halloway's ["running with scissors" talk](https://www.youtube.com/watch?v=Qx0-pViyIDU))

I'm using `leiningen` for project management, Emacs + CIDER for interactive development, and a couple of ssh tunnels to my development ec2 instance to leverage its rabbitmq and solr (need to have an ssh session for these to be picked up, natch):

```

Host new-dev
  Hostname luis.dev.birchbox.com
  ForwardAgent yes
  User luis
  LocalForward 5673 rabbitmq:5672
  LocalForward 8081 solr:8080
```
