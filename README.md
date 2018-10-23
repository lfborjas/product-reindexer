# Reindexer

Little clojure project meant to be run on old servers for a few months, as a JAR compiled for java 6. Due to our old versions of rabbitmq and solr (and java!), I had to use very specific, and ancient, versions of java libraries and couldn't leverage more general-purpose clojure wrappers for RabbitMQ ([langohr](http://clojurerabbitmq.info/)) and solr (there's [flux](https://github.com/mwmitchell/flux) but it's... okay).

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
