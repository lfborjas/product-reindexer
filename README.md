# Reindexer

Things to keep in mind:

* Should compile for Java 6
* Shouldn't require anything new
* Should be able to connect to RMQ and Solr

## Development

References:

* https://www.rabbitmq.com/java-client.html (need to use an older version?)
  * http://www.rabbitmq.com/api-guide.html#connecting
* Some bullshit daemon: http://www.learningclojure.com/2011/02/rabbitmq-clojure-hello-world.html
* Some solr libs https://wiki.apache.org/solr/IntegratingSolr#Clojure (don't look super promising, this one's the less worse: https://github.com/mwmitchell/flux )
* Or may want to use solrj: https://wiki.apache.org/solr/Solrj#Adding_Data_to_Solr


## Usage

Need to set a few environment variables

    $ java -jar reindexer-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
