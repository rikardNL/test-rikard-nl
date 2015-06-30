Setup
=====

Needs to have Cassandra running on localhost listening on port 9042 (or just
change this in Config.scala).This is true both for running tests and the
application.

Test: `activator test` or `sbt test` from the directory

Run: `activator run` or `sbt run` from the directory

POST to localhost:9000 with the url to create for under parameter: url

The url must be formatted with protocol and all to work:
* http://google.com is ok
* www.google.com isn't

GET to localhost:9000/{short url} will redirect to the save location or return
status 404 not found

Structure
=========

* Two conceptual layer - service layer and persistence layer
* Service layer stateless on top of Finagle
* Persistence layer a Cassandra cluster
* More important to optimize performance on lookup endpoint

Comments on data
----------------

* Denormalized data with one table per query (short_urls, long_urls)
* short_urls - for the lookup query
* long_urls - for query to check if long url already has a short url

Scaling and HA
==============

* Since the service layer is stateless we can scale it horizontally behind a
load balancer.
* A stateless service layer also allows us to destroy and recreate a node if it
malfunctions.
* Persistence layer is a Cassandra cluster which will scale horizontally.
* We achieve high availability through redundancy and scaling.

Weakness
========

What needs the most love in this design is the insert calls and the short url
generation

Short URL
---------

Not safely unique. Not even per node. Because parallel.

It may also not be what we're after in terms of style but that can be solved
with an injective function from 10 base to 62 base also shrinking the id
significantly.

Considered two options

* Generate from input url (hash or similar)
* Generate from a combination on where it was created and the time it was
created (pseudo type 1 uuid)

First option could cause collisions when using a hash. Need to use an injective
function. That would lose the possibilty of making a short url.

Current implementation follows in line with the seconds option an generates a
large Long. This could be shrunk to a alphanumeric value, like mentioned above,
using encoding. A better option would be using a uuid (available in Cassandra)
and encoding it using a higher base.

Inserts
-------

Assumably it is very bad to overwrite into the short_url table (the long_url
table doesn't matter as much)

Since the short URL generating function is not safely unique we cannot be sure
that what we're creating isn't already in the database.

Current way of solving this is using IF NOT EXISTS on the inserts into the
short_urls table and only if that succeeds insert into the long_urls table.
Using IF NOT EXISTS will incure a performance penalty that gets worse when we
scale horizontally assuming we need complete consistency.

Possible solution would be to use a short url generation function that is
guaranteed to be unique (mentioned above)

In the implementation if I fail to insert into short_urls I just try again
through a recursive call. This is expensive and can possibly cause the node to
run out of memory.

Other todos
-----------

As agreed upon this is done under a short time period hence the general poor
state of the code.

Here are some things that aren't great

* Safety around connecting to the Cassandra cluster
* Everything is objects
* Tests
