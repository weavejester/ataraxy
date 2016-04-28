(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:import java.util.UUID)
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.core.match :refer [match]]
            [miner.herbert :as herbert]))

(defmulti coerce
  (fn [from to] [(type from) to]))

(defmethod coerce [String 'Int] [x _]
  (try (Long/parseLong x) (catch NumberFormatException _)))

(defmethod coerce [String 'Nat] [x _]
  (if-let [x (coerce x 'Int)] (if (>= x 0) x)))

(defmethod coerce [String 'UUID] [x _]
  (try (UUID/fromString x) (catch IllegalArgumentException _)))

(defmethod coerce [Number 'String] [x _] (str x))
(defmethod coerce [UUID   'String] [x _] (str x))
(defmethod coerce [String 'String] [x _] x)

(defmulti check
  (fn [x type] type))

(defmethod check 'Int    [x _] (integer? x))
(defmethod check 'Nat    [x _] (and (integer? x) (>= x 0)))
(defmethod check 'UUID   [x _] (instance? UUID x))
(defmethod check 'String [x _] (string? x))

(def schema
  '(grammar routing-table
     regex         (class java.util.regex.Pattern)
     binding-list  (list sym (* kw any))
     binding       (or sym binding-list)
     route-map     {(or str kw) (or sym route-map)}
     route-vec     (vec (+ (or str binding)))
     route         (or kw str route-vec route-map)
     result        (vec kw binding*)
     routing-table {route (or result routing-table)}))

(derive clojure.lang.IPersistentVector ::vector)
(derive clojure.lang.IPersistentMap ::map)
(derive clojure.lang.Keyword ::keyword)
(derive java.lang.String ::string)

(defn- build-metadata [keyvals]
  (let [m (apply hash-map keyvals)]
    (cond-> m (:- m) (assoc :tag (:- m)))))

(defn- bindings->symbols [routes]
  (walk/postwalk
   #(if (list? %)
      (with-meta (first %) (build-metadata (rest %)))
      %)
   routes))

(defn valid? [routes]
  (herbert/conforms? schema routes))

(declare compile-conditions)

(defmulti ^:private compile-result
  (fn [state result] (type result)))

(defn- coerce-symbol
  ([x]
   (coerce-symbol x nil))
  ([x default-tag]
   (cond
     (and (symbol? x) (:tag (meta x))) `(coerce ~x '~(:tag (meta x)))
     (and (symbol? x) default-tag)     `(coerce ~x '~default-tag)
     :else x)))

(defmethod compile-result ::vector [{:keys [path path-matched?]} [kw & args]]
  (let [symbols (map gensym args)
        result  `(let [~@(interleave symbols (map coerce-symbol args))]
                   (if (and ~@symbols)
                     [~kw ~@symbols]))]
    (if path-matched?
      `(if (= ~path "") ~result)
      result)))

(defmethod compile-result ::map [state result]
  (compile-conditions state result))

(defmulti ^:private compile-clause
  (fn [state route] (type (first route))))

(defmethod compile-clause ::keyword [{:keys [request] :as state} [route result]]
  `(if (= (:request-method ~request) ~route)
    ~(compile-result state result)))

(defmethod compile-clause ::map [{:keys [request] :as state} [route result]]
  `(match [~request]
     [~route] ~(compile-result state result)
     :else  nil))

(defmethod compile-clause ::string [state [route result]]
  (compile-clause state [[route] result]))

(defn- compile-regex-part [value]
  (cond
    (string? value) (java.util.regex.Pattern/quote value)
    (symbol? value) (str "(" (:re (meta value) "[^/]+") ")")))

(defn- compile-regex [route]
  (re-pattern (str (str/join (map compile-regex-part route)) "(.*)")))

(defn- compile-groups [route path]
  `[~'_ ~@(filter symbol? route) ~path])

(defmethod compile-clause ::vector [{:keys [path] :as state} [route result]]
  (let [groups (compile-groups route path)
        regex  (compile-regex route)]
    `(if-let [~groups (re-matches ~regex ~path)]
       ~(compile-result (assoc state :path-matched? true) result))))

(defn- compile-conditions [state routes]
  `(or ~@(for [route routes]
           (compile-clause state (bindings->symbols route)))))

(defn- compile-matches [routes]
  (let [request (gensym "request")
        path    (gensym "path")]
    `(fn [~request]
       (let [~path (or (:path-info ~request) (:uri ~request))]
         ~(compile-conditions {:request request, :path path} routes)))))

(defn- flatten-routes [routes]
  (mapcat
   (fn [[route result]]
     (if (map? result)
       (for [[route' result'] (flatten-routes result)]
         [(into [route] route') result'])
       [[[route] result]]))
   routes))

(defn- route->request [route]
  (cond
    (keyword? route) {:request-method route}
    (vector? route)  {:uri (mapv #(coerce-symbol % 'String) route)}
    (string? route)  {:uri [route]}
    (map? route)     (walk/postwalk coerce-symbol route)))

(defn- merge-requests [a b]
  (cond
    (and (map? a) (map? b))   (merge-with merge-requests a b)
    (and (coll? a) (coll? b)) (into a b)
    :else b))

(defn- compile-request-uri [request]
  (if-let [uri (:uri request)]
    (assoc request :uri `(str ~@uri))
    request))

(defn- guard-symbol [s]
  (if (and (symbol? s) (:tag (meta s)))
    `(~s :guard #(check % '~(:tag (meta s))))
    s))

(defn- compile-result-match [[route [kw & args]]]
  [(into [kw] (map guard-symbol) args)
   (->> route
        (map route->request)
        (reduce merge-requests)
        (compile-request-uri))])

(defn- compile-generate [routes]
  `(fn [result#]
     (match result#
       ~@(->> routes
              (bindings->symbols)
              (flatten-routes)
              (mapcat compile-result-match))
       :else nil)))

(defprotocol Routes
  (-matches [routes request])
  (-generate [routes result]))

(defmacro compile* [routes]
  {:pre [(valid? routes)]}
  `(let [matches#  ~(compile-matches routes)
         generate# ~(compile-generate routes)]
     (reify Routes
       (-matches [_ request#] (matches# request#))
       (-generate [_ result#] (generate# result#)))))

(defn compile [routes]
  (eval `(compile* ~routes)))

(defn matches [routes request]
  (if (satisfies? Routes routes)
    (-matches routes request)
    (-matches (compile routes) request)))

(defn generate [routes result]
  (if (satisfies? Routes routes)
    (-generate routes result)
    (-generate (compile routes) result)))
