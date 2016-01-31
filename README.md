# Ataraxy

A **experimental** data-driven routing and destructuring library for
[Ring][]. Experimental means the syntax should be considered unstable.

[ring]: https://github.com/ring-clojure/ring


## Rationale

There are several data-driven routing libraries for Ring, such as
[bidi][], [Silk][] and [gudu][]. Ataraxy differs from them because
it not only seeks to match a route, it also destructures the
incoming request.

In this sense it is similar to [Compojure][], in that the idea is to
remove extraneous information. However, while Compojure is designed to
use chains of functions, Ataraxy defines its functionality through a
declarative data structure.

[bidi]: https://github.com/juxt/bidi
[silk]: https://github.com/DomKM/silk
[gudu]: https://github.com/thatismatt/gudu
[compojure]: https://github.com/weavejester/compojure


## Installation

Add the following dependency to your `project.clj` file:

    [ataraxy "0.0.1"]


## Overview

Ataraxy uses a data structure to tell it how to route and destructure
requests.

```clojure
(def routes '{"/foo" [:foo]})
```

We can match a request map to a result with `matches`:

```clojure
(ataraxy/matches routes {:uri "/foo"})
=> [:foo]
```

And we can generate a request map from a result with `generate`:

```clojure
(ataraxy/generate routes [:foo])
=> {:uri "/foo"}
```

Note that the generated request map may not be complete. Ataraxy fills
in as much as it is able.


## Syntax

Ataraxy generates routes from a **routing table**, which is a Clojure
map.

The keys of the map are **routes**, and the data type used defines a
way of matching and destructuring a request.

The values are either **results** or nested routing tables. Results
are always vectors, beginning with a keyword.

Here's the formal definition of the syntax:

```
routing-table = {route (result | routing-table)}

route         = keyword | string | vector-route | map-route
result        = [keyword symbol*]

vector-route  = [(string | symbol)+]
map-route     = {(keyword | string) (symbol | map-route)}
```


### Strings routes

The simplest form of routes are strings. These match against the
`:uri` or `:path-info` keys on a Ring request map.

```clojure
{"/foo" [:foo]
 "/bar" [:bar]}
```

This example will match the URIs "/foo" and "/bar".


### Nested routing tables

As discussed earlier, routing tables may be arbitrarily nested, by
specifying a map as the result, instead of a vector.

```clojure
{"/foo"
 {"/bar" [:foobar]
  "/baz" [:foobaz]}})
```

This will match the URIs "/foo/bar" and "/foo/baz".


### Keyword routes

A keyword route will match the request method.

```clojure
{:get [:get-any]})
```

This route will match any request with the GET method. Because this is
such a broad match, a keyword route is often nested:

```clojure
{"/foo"
 {:get  [:get-foo]
  :post [:post-foo]}})
```

This route will match a GET or a POST request to "/foo".


### Vector routes

A vector can be used to construct a parameterized route. Strings in
the vector are matched verbatim. Symbols match any character that
isn't "/". Symbols may be used to carry information from the route to
the result and vice versa.

```clojure
{["/foo/" id] [:foo id]})
```

This will match URIs like "/foo/1" and "/foo/bar". The part of the
route specified by `id` is carried over to the result.

For example:

```clojure
(def routes '{["/foo/" id] [:foo id]})

(ataraxy/matches routes {:uri "/foo/123"})
=> [:foo "123"]

(ataraxy/generate routes [:foo "456"])
=> {:uri "/foo/456"}
```

Note that the `id` binding works both ways.


### Map routes

A map route allows for arbitrary matching and destructuring, using a
subset of the [core.match][] syntax. Currently only maps, symbols,
strings and keywords are supported.

As with vector routes, symbols may be used to carry information
between the route and result.

[core.match]: https://github.com/clojure/core.match

```clojure
{{:query-params {"q" q}} [:query q]}
```

This route will match any request with a query parameter named "q".

```clojure
(def routes '{{:query-params {"q" q}} [:query q]})

(ataraxy/matches routes {:query-params {"q" "foo"}})
=> [:query "foo"]

(ataraxy/generate routes [:query "bar"])
=> {:query-params {"q" "bar"}}
```

As with keyword routes, map routes are often nested:

```clojure
{"/search"
 {:get
  {{:query-params {"q" q}} [:search q]}}})
```


## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
