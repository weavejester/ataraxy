(ns ataraxy.core
  "The core Ataraxy namespace. Includes functions for compiling and matching
  routes, and for building a Ring handler function."
  (:refer-clojure :exclude [compile])
  (:require [ataraxy.coerce :as coerce]
            [ataraxy.error :as err]
            [ataraxy.handler :as handler]
            [ataraxy.response :as response]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as specs]
            [clojure.string :as str]))

(s/def ::with-meta
  (s/conformer (fn [v] [v (meta v)])))

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

(s/def ::result-with-meta
  (s/and ::with-meta (s/cat :value ::result, :meta (s/nilable map?))))

(s/def ::route-result
  (s/cat :route  ::route
         :result (s/or :result ::result-with-meta
                       :routes ::routing-table-with-meta)))

(defn- conformed-results [[_ routes]]
  (mapcat (fn [{[k {v :value}] :result}]
            (case k
              :result [v]
              :routes (conformed-results v)))
          routes))

(defn- distinct-result-keys? [routing-table]
  (apply distinct? (map :key (conformed-results routing-table))))

(s/def ::routing-table
  (s/and (s/or :unordered (s/and map?  (s/* (s/spec ::route-result)))
               :ordered   (s/and list? (s/* ::route-result)))
         distinct-result-keys?))

(s/def ::routing-table-with-meta
  (s/and ::with-meta (s/cat :value ::routing-table, :meta (s/nilable map?))))

(defn valid?
  "Return true if the routes data structure is valid."
  [routes]
  (s/valid? ::routing-table-with-meta routes))

(defn- parse-single-route [context [type value]]
  (update context type (fnil conj []) value))

(defn- parse-route [context [type route]]
  (case type
    :single   (parse-single-route context route)
    :multiple (reduce parse-single-route context route)))

(declare parse-routing-table)

(defn- update-meta [context meta]
  (cond-> context meta (update :meta (fnil conj []) meta)))

(defn- parse-result [context [type result]]
  (case type
    :routes (parse-routing-table context result)
    :result [(-> context
                 (update-meta (:meta result))
                 (assoc :result (:value result)))]))

(defn- parse-route-result [context {:keys [route result]}]
  (-> context
      (parse-route route)
      (parse-result result)))

(defn- parse-routing-table [context {[_ routes] :value, meta :meta}]
  (mapcat (partial parse-route-result (update-meta context meta)) routes))

(defn parse
  "Parse the routes into an ordered sequence of maps that's more covenient to
  work with. Each map represents a route. Nested routes are flattened."
  [routes]
  {:pre [(valid? routes)]}
  (parse-routing-table {} (s/conform ::routing-table-with-meta routes)))

(defn- compile-coercion [coercers sym]
  (if-let [tag (-> sym meta :tag)]
    [sym `((~coercers '~tag) ~sym)]))

(defn- missing-symbol-set [symbols]
  `(-> #{} ~@(for [sym symbols] `(cond-> (not ~sym) (conj '~sym)))))

(defn- compile-match-result [{:keys [key args]} meta coercers result-form]
  (let [coercions (into {} (keep (partial compile-coercion coercers) args))]
    `(let [~@(apply concat coercions)]
       (if (and ~@(keys coercions))
         ~(result-form (into [key] args))
         ~(result-form [::err/failed-coercions (missing-symbol-set args)])))))

(defn- optional-binding? [sym]
  (str/starts-with? (name sym) "?"))

(defn- param-name [sym]
  (if (optional-binding? sym)
    (subs (name sym) 1)
    (name sym)))

(defn- find-symbols [x]
  (cond
    (coll? x)   (mapcat find-symbols x)
    (symbol? x) (list x)))

(defn- compile-match-destruct [request destructs result-form next-form]
  (if (some? destructs)
    (let [mandatory (remove optional-binding? (find-symbols destructs))]
      `(let [~@(mapcat #(vector % request) destructs)]
         (if (and ~@mandatory)
           ~next-form
           ~(result-form [::err/missing-destruct (missing-symbol-set mandatory)]))))
    next-form))

(defn- param-match-binding [request param]
  (let [key (param-name param)]
    [param `(or (get (:query-params ~request) ~key)
                (get (:form-params ~request) ~key)
                (get (:multipart-params ~request) ~key))]))

(defn- compile-match-params [request params result-form next-form]
  (if (some? params)
    (let [params    (apply set/union params)
          mandatory (remove optional-binding? params)]
      `(let [~@(mapcat (partial param-match-binding request) params)]
         (if (and ~@mandatory)
           ~next-form
           ~(result-form [::err/missing-params (missing-symbol-set mandatory)]))))
    next-form))

(defn- path-symbols [path]
  (into [] (comp (map second) (filter symbol?)) path))

(defn- path-part-regex [[_ part]]
  (if (string? part)
    (java.util.regex.Pattern/quote part)
    (str "(" (:re (meta part) "[^/]+") ")")))

(defn- path-regex [path]
  (re-pattern (str/join (map path-part-regex path))))

(defn- compile-route-params [route-params path]
  (if-let [syms (seq (path-symbols path))]
    `(assoc ~route-params ~@(mapcat (juxt keyword identity) syms))
    route-params))

(defn- compile-match-path [request path route-params result-form next-form]
  (if (some? path)
    `(let [path-info# (or (:path-info ~request) (:uri ~request))]
       (if-let [~(path-symbols path) (next (re-matches ~(path-regex path) path-info#))]
         (let [~route-params ~(compile-route-params route-params path)]
           ~next-form)
         ~(result-form [::err/unmatched-path])))
    next-form))

(defn- compile-match-method [request method result-form next-form]
  (if (some? method)
    `(if (= ~(first method) (:request-method ~request))
       ~next-form
       ~(result-form [::err/unmatched-method]))
    next-form))

(defn- compile-match-route
  [request coercers {:keys [method path params destruct result meta]}]
  (let [route-params (gensym "route-params")
        result-form  (fn [result] {:route-params route-params, :result result})]
    `(let [~route-params {}]
       ~(->> (compile-match-result result meta coercers result-form)
             (compile-match-destruct request destruct result-form)
             (compile-match-params request params result-form)
             (compile-match-method request method result-form)
             (compile-match-path request path route-params result-form)))))

(defn ^:no-doc best-match [a b]
  (if-let [fa (err/errors (first (:result a)))]
    (if-let [fb (err/errors (first (:result b)))]
      (if (>= fa fb) a b)
      b)
    a))

(defn- compile-match-route-seq [request match coercers routes]
  (if (seq routes)
    (let [route (first routes)]
      `(let [~match (best-match ~match ~(compile-match-route request coercers route))]
         (if (err/error-result? (:result ~match))
           ~(compile-match-route-seq request match coercers (rest routes))
           ~match)))
    match))

(defn ^:no-doc compile-match [routes coercers]
  (let [request (gensym "request")
        match   (gensym "match")]
    `(fn [~request]
       (let [~match {:result [::err/unmatched-path]}]
         ~(compile-match-route-seq request match coercers (parse routes))))))

(defprotocol ^:no-doc Routes
  (-matches [routes request]))

(defmacro ^:no-doc compile* [routes coercers]
  {:pre [(valid? routes)]}
  (let [coercers-sym (gensym "coercers")]
    `(let [~coercers-sym ~(into {} (for [[k v] coercers] `['~k ~v]))
           matches#      ~(compile-match routes coercers-sym)]
       (reify Routes
         (-matches [_ request#] (matches# request#))))))

(defn compile
  "Compile a data structure of routes into an object for performance."
  ([routes] (compile routes {}))
  ([routes coercers]
   (if (satisfies? Routes routes)
     routes
     (eval `(compile* ~routes ~(merge coerce/default-coercers coercers))))))

(defn matches
  "Check if any route matches the supplied request. If a route does match, the
  associated result vector is returned."
  [routes request]
  (:result (-matches (compile routes) request)))

(defn result-keys [routes]
  (map (comp :key :result) (parse routes)))

(defn- assoc-match [request result route-params]
  (assoc request
         :ataraxy/result result
         :route-params   route-params))

(defn- result-metadata [routes]
  (->> (parse routes)
       (map (juxt (comp :key :result) #(apply merge (:meta %))))
       (into {})))

(defn- wrap-handler [handler handler-key metadata-map middleware-map]
  (reduce
   (fn [handler [k v]]
     (if-let [middleware (middleware-map k)]
       (if (true? v) (middleware handler) (middleware handler v))
       handler))
   handler
   (sort-by key (metadata-map handler-key))))

(defn- wrap-handler-map [handler-map routes middleware-map]
  (let [metadata-map (result-metadata routes)
        wrap-handler (fn [[k h]] (wrap-handler h k metadata-map middleware-map))]
    (into {} (map (juxt key wrap-handler) handler-map))))

(defn- apply-handler
  ([get-handler request]
   (let [handler  (get-handler request)
         response (handler request)]
     (if (vector? response)
       (recur get-handler (assoc request :ataraxy/result response))
       response)))
  ([get-handler request respond raise]
   (let [handler (get-handler request)]
     (handler request
              #(if (vector? %)
                (apply-handler get-handler (assoc request :ataraxy/result %) respond raise)
                (respond %))
              raise))))

(defn handler
  "Create a handler from a data structure of routes and a map of result keys to
  handler functions. If no handler matches, the :default handler is used. If no
  default handler is set, the ataraxy.handler/default function is used.

  Optionally, maps of middleware and coercer functions can also be supplied.
  Middleware is applied to any result with metadata matching the key in the
  middleware map. Coercers are applied to any symbol in the result that are
  tagged with the corresponding key in the coercers map.

  By default coercers for int and uuid are included."
  [{:keys [routes handlers middleware coercers]}]
  {:pre [(set/subset? (set (result-keys routes)) (set (keys handlers)))]}
  (let [handlers    (wrap-handler-map handlers routes middleware)
        default     (:default handlers handler/default)
        get-handler (fn [req] (handlers (first (:ataraxy/result req)) default))
        routes      (compile routes coercers)]
    (fn
      ([request]
       (let [{:keys [result route-params]} (-matches routes request)
             request (assoc-match request result route-params)]
         (apply-handler get-handler request)))
      ([request respond raise]
       (let [{:keys [result route-params]} (-matches routes request)
             request (assoc-match request result route-params)]
         (apply-handler get-handler request respond raise))))))
