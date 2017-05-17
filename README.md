# Ataraxy

[![Build Status](https://travis-ci.org/weavejester/ataraxy.svg?branch=master)](https://travis-ci.org/weavejester/ataraxy)

A data-driven routing and destructuring library for [Ring][]. This
library is still being developed, so some functionality may change
before we hit version 1.0.0.

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


## Example

```clojure
{["/api" {uid :identity}]
 {"/products"
   {[:get]                [:products/list uid]
    [:get "/" pid]        [:products/get uid ^uuid pid]
    [:get "/search" #{q}] [:products/search uid q]
    [:post {body :body}]  [:products/new uid body]}}}
```


## Installation

Add the following dependency to your `project.clj` file:

    [ataraxy "0.2.1"]


## Routing

Ataraxy uses a data structure to tell it how to route and destructure
requests. See the following section on [syntax](#syntax) for details.

```clojure
(def routes '{"/foo" [:foo]})
```

We can match a request map to a result with `matches`:

```clojure
(ataraxy/matches routes {:uri "/foo"})
=> [:foo]
```

If Ataraxy cannot find a matching route, then `[:ataraxy/not-found]`
will be returned. This behavior may be extended in future to support
different types of failure.

For performance, we can also pre-compile the routing data:

```clojure
(def compiled-routes (ataraxy/compile routes))
```

The resulting object can be used in `matches` in the same way as the
raw data structure:

```clojure
(ataraxy/matches compiled-routes {:uri "/foo"})
=> [:foo]
```


## Handlers

Once we have our routes, it's likely we want to turn them into a Ring
handler function. Ataraxy has a function called `handler` for this
purpose:

```clojure
(defn foo [request]
  {:status 200, :headers {}, :body "Foo"})

(def handler
  (ataraxy/handler
   {:routes   routes
    :handlers {:foo foo}))
```

This function takes a map with three keys:

* `:routes`     - the routes to match
* `:handlers`   - a map of result keys to Ring handlers
* `:middleware` - a map of metadata keys to Ring middleware (optional)
* `:coercers`   - a map of symbols to coercer functions (optional)

The handler function is chosen by the key of the result. Two keys are
added to the request map passed to the handler:

* `:ataraxy/result` - contains the matched result
* `:route-params`   - a map of parameters matched in the path
                      (included for compatibility)

Middleware is chosen based on the metadata that is applied to the
result or to the containing routing table. For example:

```clojure
(defn wrap-example [handler value]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:header "X-Example"] value))))

(def handler
  (ataraxy/handler
   {:routes     {"/foo" ^:example [:foo]}
    :handlers   {:foo foo}
    :middleware {:example #(wrap-example % "test")}}))
```

This would add an `X-Example` header to the response of the
handler. We can also pass an argument to the handler by setting the
`:example` metadata key to something other than `true`:

```clojure
(def handler
  (ataraxy/handler
   {:routes     {"/foo" ^{:example "test"} [:foo]}
    :handlers   {:foo foo}
    :middleware {:example wrap-example}}))
```

Custom coercers can be added to the handler by specifying the
`:coercers` option. This is described in more detail in
the [coercers](#coercers) section.


## Syntax

Ataraxy generates routes from a **routing table**, which is a Clojure
map, or a list of alternating keys and values.

The keys of the table are **routes**, and the data type used defines a
way of matching and destructuring a request.

The values are either **results** or nested tables.

Here's a semi-formal definition of the syntax:

```
table  = {<route result>+} | (<route result>+)
route  = keyword | string | symbol | set | map | [route+]
result = table | [keyword symbol*]
```

### Results

Results are always vectors, beginning with a keyword, followed by zero
or more symbols. For example:

```clojure
[:foo id]
```

Results are paired with routes:

```clojure
{["/foo/" id] [:foo id]}
```

The symbols in the route are passed into the result.

The symbols in the result may be tagged with a type they should be
coerced into. For example:

```clojure
[:foo ^int id]
```

See the [coercers](#coercers) section for more detail.

### Keyword routes

A keyword will match the request method. For example:

```clojure
{:get [:foo]})
```

This route will match any request with the GET method.

### String routes

A string will match the `:path-info` or `:uri` key on the request. For
example:

```clojure
{"/foo" [:foo]
 "/bar" [:bar]}
```

This example will match the URIs "/foo" and "/bar".

### Symbol routes

Like strings, symbols match against the `:path-info` or `:uri` key on
the request. Unlike strings, they match on a regular expression, and
bind the string matched by the regular expression to the symbol.

By default the regex used is `[^/]+`. In other words, any character
except a forward slash. The regex can be changed by adding a `:re` key
to the symbol's metadata. For example:

```clojure
{^{:re #"/d.g"} w [:word w]}
```

This will match URIs like "/dog", "/dig" and "/dug", and add the
matched word to the result.

### Set routes

A set of symbols will match URL-encoded parameters of the same name.
For example:

```clojure
{#{q} [:query q]}
```

This will match any request with `q` as a parameter. For example,
"/search?q=foo".

By default, the parameters must be set for the route to match. If you
want the parameters to be optional, you can prefix them with a "?".

```clojure
{#{?q} [:query ?q]}
```

This works the same as the previous example, except that the route
still matches if `q` is `nil`.

### Map routes

A map will destructure the request. Any destructured symbol must not
be `nil` for the route to match. For example:

```clojure
{{{:keys [user]} :session} [:user user]}
```

This route will match any request map with a `:user` key in the
session.

As with set routes, symbols prefixed with a "?" are considered
optional and may be `nil`.


### Vector routes

A vector combines the behavior of multiple routing rules. For example:

```clojure
{[:get "/foo"] [:foo]}
```

This will match both the request method and the URI.

Strings and symbols will be combined in order, to allow complex paths
to be matched. For example:

```clojure
{[:get "/user/" name "/info"] [:get-user-info name]}
```

This will match URIs like "/user/alice/info" and pass the name "alice"
to the result.

### Nested tables

Nesting routing tables is an alternative way of combining routes.
Instead of a result vector, a map or list may be specified. For
example:

```clojure
{"/foo"
 {"/bar" [:foobar]
  "/baz" [:foobaz]}})
```

This will match the URIs "/foo/bar" and "/foo/baz".

You can also use nesting and vectors together:

```clojure
{["/user/" name]
 {:get [:get-user name]
  :put [:put-user name]}}
```


## Coercers

Coercers are functions that turn a string into a custom type. Any
symbol in the result can be tagged with a symbol associated with a
coercer function.

For example, it's common to want to change a parameter from a string
into an int:

```clojure
{[:get "/foo/" id] [:foo ^int id]}
```

The `int` and `uuid` coercers are included by default. We can easily
add our own, however:

```clojure
(defn ->float [s]
  (try (Double/parseDouble s) (catch NumberFormatException _)))

(def compiled-routes
  (ataraxy/compile
   '{[:get "/foo/" id] [:foo ^float id]}
   {'float ->float}))
```

And similarly to handlers:

```clojure
(def handler
  (ataraxy/handler
   {:routes   '{[:get "/foo/" id] [:foo ^float id]}
    :coercers {'float ->float}}))
```


## License

Copyright © 2017 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
