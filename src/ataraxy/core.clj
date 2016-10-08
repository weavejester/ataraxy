(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:import java.util.UUID)
  (:require [clojure.set :as set]
            [clojure.spec :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.core.match :refer [match]]))

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

(s/def ::route-set
  (s/and set? (s/coll-of symbol?)))

(s/def ::route-single
  (s/or :keyword keyword?
        :string  string?
        :symbol  symbol?
        :map     map?
        :set     ::route-set))

(s/def ::route-multiple
  (s/and vector? (s/coll-of ::route-single)))

(s/def ::route
  (s/or :single   ::route-single
        :multiple ::route-multiple))

(s/def ::result
  (s/and vector? (s/cat :key keyword? :args (s/* symbol?))))

(s/def ::route-result
  (s/cat :route  ::route
         :result (s/or :result ::result :routes ::routing-table)))

(s/def ::routing-table
  (s/or :unordered (s/and map?  (s/* (s/spec ::route-result)))
        :ordered   (s/and list? (s/* ::route-result))))

(defn valid? [routes]
  (s/valid? ::routing-table routes))

(defn- coerce-symbol
  ([x]
   (coerce-symbol x nil))
  ([x default-tag]
   (cond
     (and (symbol? x) (:tag (meta x))) `(coerce ~x '~(:tag (meta x)))
     (and (symbol? x) default-tag)     `(coerce ~x '~default-tag)
     :else x)))

(defn- compile-result-vector
  ([{:keys [path-matched? path]} key]
   (if path-matched?
    `(if (= ~path "") [~key])
    [key]))
  ([{:keys [path-matched? path]} key args]
   (let [symbols (map gensym args)]
     `(let [~@(interleave symbols (map coerce-symbol args))]
        (if (and ~@(if path-matched? [`(= ~path "")]) ~@symbols)
          [~key ~@symbols])))))

(declare compile-routing-table)

(defn- compile-result [{:keys [path path-matched?] :as state} result]
  (match result
    [:result {:key k :args as}] (compile-result-vector state k as)
    [:result {:key k}]          (compile-result-vector state k)
    [:routes r]                 (compile-routing-table state r)))

(declare compile-routes)

(defmulti ^:private compile-route-part
  (fn [state route result] (first route)))

(defmethod compile-route-part :keyword [{:keys [request] :as state} [_ kw] cont]
  `(if (= (:request-method ~request) ~kw)
     ~(cont state)))

(defmethod compile-route-part :string [{:keys [path] :as state} [_ s] cont]
  `(if (str/starts-with? ~path ~s)
     (let [~path (subs ~path ~(count s))]
       ~(cont (assoc state :path-matched? true)))))

(defmethod compile-route-part :symbol [{:keys [path] :as state} [_ sym] cont]
  (let [re (re-pattern (str "(" (:re (meta sym) "[^/]+") ")(.*)"))]
    `(if-let [[~sym ~path] (next (re-matches ~re ~path))]
       ~(cont (assoc state :path-matched? true)))))

(defmethod compile-route-part :map [{:keys [request] :as state} [_ m] cont]
  `(match [~request] [~m] ~(cont state) :else nil))

(defn- compile-routes [state routes result]
  (if (seq routes)
    (compile-route-part state (first routes) #(compile-routes % (rest routes) result))
    (compile-result state result)))

(defn- compile-entry [state {:keys [route result]}]
  (match route
    [:multiple rs] (compile-routes state rs result)
    [:single   r]  (compile-routes state [r] result)))

(defn- compile-routing-table [state [_ table]]
  `(or ~@(map #(compile-entry state %) table)))

(defn- compile-matches [routes]
  (let [request (gensym "request")
        path    (gensym "path")
        state   {:request request, :path path}
        table   (s/conform ::routing-table routes)]
    `(fn [~request]
       (let [~path (or (:path-info ~request) (:uri ~request))]
         ~(compile-routing-table state table)))))

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
       ~@(mapcat compile-result-match (flatten-routes routes))
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
