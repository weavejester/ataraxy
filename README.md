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

    [ataraxy "0.1.0-SNAPSHOT"]


## Usage

### Static routes

Ataraxy's syntax starts with a map.

```clojure
(def routes '{"/foo" [:foo]})
```

This maps the static **route** `"/foo"` to the **result**, `[:foo]`.

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

Note that the generated request map may not be complete.


### Nested routes

Routes may be arbitrarily nested, by specifying a route map as the
result, instead of a vector.

```clojure
{"/foo"
 {"/bar" [:foobar]
  "/baz" [:foobaz]}})
```

This will match the URIs "/foo/bar" and "/foo/baz".


### Parameterized routes

A vector can be used to construct a parameterized route.

```clojure
{["/foo/" id] [:foo id]})
```

This will match URIs like "/foo/1" and "/foo/bar". The part of the
route specified by `id` is carried over to the output:

```clojure
(def routes '{["/foo/" id] [:foo id]})

(ataraxy/matches routes {:uri "/foo/123"})
=> [:foo "123"]

(ataraxy/generate routes [:foo "456"])
=> {:uri "/foo/456"}
```


### Request methods

A keyword route denotes a request method.

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


### Arbitrary matches

A map route allows for arbitrary matching and destructuring, using the
syntax defined in [core.match][].

[core.match]: https://github.com/clojure/core.match

```clojure
{{:query-params {"q" q}} [:query q]})
```

This route will match any request with a query parameter named "q". As
with keyword routes, map routes are often nested:

```clojure
{"/search"
 {:get
  {{:query-params {"q" q}} [:search q]}}})
```


## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
