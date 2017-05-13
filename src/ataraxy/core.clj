(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [ataraxy.error :as err]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as specs]
            [clojure.string :as str]))

(defn- meta-conformer [spec]
  (s/conformer #(let [val (s/conform spec %)]
                  (if (= val ::s/invalid)
                    ::s/invalid
                    {:value val, :meta (meta %)}))))

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
  (meta-conformer (s/and vector? (s/cat :key keyword? :args (s/* symbol?)))))

(s/def ::route-result
  (s/cat :route  ::route
         :result (s/or :result ::result :routes ::routing-table)))

(s/def ::routing-table
  (meta-conformer (s/or :unordered (s/and map?  (s/* (s/spec ::route-result)))
                        :ordered   (s/and list? (s/* ::route-result)))))

(defn valid? [routes]
  (s/valid? ::routing-table routes))

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
    :result [[(update-meta context (:meta result)) (:value result)]]))

(defn- parse-route-result [context {:keys [route result]}]
  (-> context
      (parse-route route)
      (parse-result result)))

(defn- parse-routing-table [context {[_ routes] :value, meta :meta}]
  (mapcat (partial parse-route-result (update-meta context meta)) routes))

(defn parse [routes]
  {:pre [(valid? routes)]}
  (parse-routing-table {} (s/conform ::routing-table routes)))

(defn- compile-match-result [{:keys [key args]}]
  `[~key ~@args])

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

(defn- missing-symbol-set [symbols]
  `(-> #{} ~@(for [sym symbols] `(cond-> (not ~sym) (conj '~sym)))))

(defn- compile-match-destruct [request destructs next-form]
  (if (some? destructs)
    (let [mandatory (remove optional-binding? (find-symbols destructs))]
      `(let [~@(mapcat #(vector % request) destructs)]
         (if (and ~@mandatory)
           ~next-form
           [::err/missing-destruct ~(missing-symbol-set mandatory)])))
    next-form))

(defn- param-match-binding [request param]
  (let [key (param-name param)]
    [param `(or (get (:query-params ~request) ~key)
                (get (:form-params ~request) ~key)
                (get (:multipart-params ~request) ~key))]))

(defn- compile-match-params [request params next-form]
  (if (some? params)
    (let [params    (apply set/union params)
          mandatory (remove optional-binding? params)]
      `(let [~@(mapcat (partial param-match-binding request) params)]
         (if (and ~@mandatory)
           ~next-form
           [::err/missing-params ~(missing-symbol-set mandatory)])))
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
         ~next-form
         [::err/unmatched-path]))
    next-form))

(defn- compile-match-method [request method next-form]
  (if (some? method)
    `(if (= ~(first method) (:request-method ~request))
       ~next-form
       [::err/unmatched-method])
    next-form))

(defn- compile-match-route [request [{:keys [method path params destruct]} result]]
  (->> (compile-match-result result)
       (compile-match-destruct request destruct)
       (compile-match-params request params)
       (compile-match-method request method)
       (compile-match-path request path)))

(defn best-result [a b]
  (if-let [fa (err/errors (first a))]
    (if-let [fb (err/errors (first b))]
      (if (>= fa fb) a b)
      b)
    a))

(defn- compile-match-route-seq [request result routes]
  (if (seq routes)
    (let [route (first routes)]
      `(let [~result (best-result ~result ~(compile-match-route request route))]
         (if (err/error-result? ~result)
           ~(compile-match-route-seq request result (rest routes))
           ~result)))
    result))

(defn compile-match [routes]
  (let [request (gensym "request")
        result  (gensym "result")]
    `(fn [~request]
       (let [~result [::err/unmatched-path]]
         ~(compile-match-route-seq request result (parse routes))))))

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
  (map (comp :key second) (parse routes)))

(defn- assoc-result [request result]
  (assoc request :ataraxy/result result))

(defn handler [routes handler-map]
  {:pre [(set/subset? (set (result-keys routes)) (set (keys handler-map)))]}
  (let [routes  (compile routes)
        default (:default handler-map err/default-handler)]
    (fn
      ([request]
       (let [result  (matches routes request)
             handler (handler-map (first result) default)]
         (handler (assoc-result request result))))
      ([request respond raise]
       (let [result  (matches routes request)
             handler (handler-map (first result) default)]
         (handler (assoc-result request result) respond raise))))))
