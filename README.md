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


## Syntax

Ataraxy's syntax starts with a map.

```clojure
{"/foo" [:foo]}
```

This maps the static **route** `"/foo"` to the **result**, `[:foo]`.

We can match a request map (or a partial one) to a result with
`matches`:

```clojure
(ataraxy/matches '{"/foo" [:foo]} {:uri "/foo"})
=> [:foo]
```

And we can generate a request map from a result with `generate`:

```clojure
(ataraxy/generate '{"/foo" [:foo]} [:foo])
=> {:uri "/foo"}
```


## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
