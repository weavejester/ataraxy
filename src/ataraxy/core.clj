(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as specs]
            [clojure.string :as str]))

(s/def ::route-set
  (s/and set? (s/coll-of symbol?)))

(s/def ::route-single
  (s/or :method   keyword?
        :path     (s/or :string string? :symbol symbol?)
        :params   (s/and set? (s/coll-of symbol?))
        :destruct ::specs/map-binding-form))

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

(defn- parse-single-route [context [type value]]
  (update context type (fnil conj []) value))

(defn- parse-route [context [type route]]
  (case type
    :single   (parse-single-route context route)
    :multiple (reduce parse-single-route context route)))

(declare parse-routing-table)

(defn- parse-result [context [type result]]
  (case type
    :routes (parse-routing-table context result)
    :result [[context result]]))

(defn- parse-route-result [context {:keys [route result]}]
  (-> context
      (parse-route route)
      (parse-result result)))

(defn- parse-routing-table [context [_ routes]]
  (mapcat (partial parse-route-result context) routes))

(defn parse [routes]
  {:pre [(valid? routes)]}
  (parse-routing-table {} (s/conform ::routing-table routes)))

(defn- compile-match-result [{:keys [key args]}]
  `[~key ~@args])

(defn- find-symbols [x]
  (cond
    (coll? x)   (mapcat find-symbols x)
    (symbol? x) (list x)))

(defn- compile-match-destruct [request destructs next-form]
  (if (some? destructs)
    `(let [~@(mapcat #(vector % request) destructs)]
       (if (and ~@(find-symbols destructs))
         ~next-form))
    next-form))

(defn- compile-match-params [request params next-form]
  (if (some? params)
    (let [params (apply set/union params)]
      `(let [{:strs [~@params]} (merge (:query-params ~request)
                                       (:form-params ~request)
                                       (:multipart-params ~request))]
         (if (and ~@params)
           ~next-form)))
    next-form))

(defn- path-symbols [path]
  (into '[_] (comp (map second) (filter symbol?)) path))

(defn- path-part-regex [[_ part]]
  (if (string? part)
    (java.util.regex.Pattern/quote part)
    (str "(" (:re (meta part) "[^/]+") ")")))

(defn- path-regex [path]
  (re-pattern (str/join (map path-part-regex path))))

(defn- compile-match-path [request path next-form]
  (if (some? path)
    `(let [path-info# (or (:path-info ~request) (:uri ~request))]
       (if-let [~(path-symbols path) (re-matches ~(path-regex path) path-info#)]
         ~next-form))
    next-form))

(defn- compile-match-method [request method next-form]
  (if (some? method)
    `(if (= ~(first method) (:request-method ~request)) ~next-form)
    next-form))

(defn- compile-match-route [request [{:keys [method path params destruct]} result]]
  (->> (compile-match-result result)
       (compile-match-destruct request destruct)
       (compile-match-params request params)
       (compile-match-path request path)
       (compile-match-method request method)))

(defn compile-match [routes]
  (let [request (gensym "request")]
    `(fn [~request]
       (or ~@(map (partial compile-match-route request) (parse routes))
           [:ataraxy/not-found]))))

(defprotocol Routes
  (-matches [routes request]))

(defmacro compile* [routes]
  {:pre [(valid? routes)]}
  `(let [matches# ~(compile-match routes)]
     (reify Routes
       (-matches [_ request#] (matches# request#)))))

(defn compile [routes]
  (eval `(compile* ~routes)))

(defn matches [routes request]
  (if (satisfies? Routes routes)
    (-matches routes request)
    (-matches (compile routes) request)))

(defn result-keys [routes]
  (->> (parse routes)
       (map (comp :key second))
       (cons :ataraxy/not-found)))

(defn- assoc-result [request result]
  (assoc request :ataraxy/result result))

(defn handler [routes handler-map]
  {:pre [(set/subset? (set (result-keys routes)) (set (keys handler-map)))]}
  (let [routes (compile routes)]
    (fn
      ([request]
       (let [result  (matches routes request)
             handler (handler-map (first result))]
         (handler (assoc-result request result))))
      ([request respond raise]
       (let [result  (matches routes request)
             handler (handler-map (first result))]
         (handler (assoc-result request result) respond raise))))))
