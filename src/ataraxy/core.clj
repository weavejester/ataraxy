(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.core.match :refer [match]]))

(defn- re-quote [s]
  (java.util.regex.Pattern/quote s))

(defn- compile-pattern [[_ path _]]
  (str/join (map #(if (string? %) (re-quote %) "([^/]+)") path)))

(defn- join-patterns [patterns]
  (re-pattern (str/join "|" (map #(str "()" %) patterns))))

(defn- add-path-binding [bindings route]
  (let [binds      (into [""] (filter symbol? route))
        inc-blanks (repeat (count binds) '_)
        new-blanks (repeat (count (first bindings)) '_)]
    (-> (mapv #(into % inc-blanks) bindings)
        (conj (into (vec new-blanks) binds)))))

(defn- compile-path-bindings [routes]
  (->> (map second routes)
       (reduce add-path-binding [])
       (map (partial into '[_]))))

(defn- compile-bindings [routes]
  (map (fn [path [method _ request]] [method path request])
       (compile-path-bindings routes)
       routes))

(defn- compile-matches [routes]
  (let [routes   (seq routes)
        pattern  (join-patterns (map compile-pattern (keys routes)))
        bindings (compile-bindings (keys routes))
        clauses  (interleave bindings (vals routes))]
    `(fn [request#]
       (match [(:request-method request#)
               (re-matches ~pattern (:uri request#))
               request#]
         ~@clauses
         ~'[_ _ _] nil))))

(defn- compile-request [[method path request]]
  (merge
   (if (not= method '_) `{:request-method ~method})
   (if (not= path '_ )  `{:uri (str ~@path)})
   (if (not= request '_) request)))

(defn- compile-generate [routes]
  (let [result (gensym "result")]
    `(fn [~result]
       (case (first ~result)
         ~@(mapcat
            (fn [[route [result-key & args]]]
              [result-key `(let [[~@args] (rest ~result)] ~(compile-request route))])
            routes)
         nil))))

(derive clojure.lang.IPersistentVector ::vector)
(derive clojure.lang.IPersistentList ::list)
(derive clojure.lang.IPersistentMap ::map)
(derive clojure.lang.Keyword ::keyword)
(derive clojure.lang.Symbol ::symbol)
(derive java.lang.String ::string)
(derive java.lang.Object ::any)

(defn- normalize-route-list [[a b c :as route]]
  (condp #(isa? %2 %1) (mapv type route)
    [::keyword]         (list a '_ '_)
    [::string]          (list '_ [a] '_)
    [::vector]          (list '_ a '_)
    [::map]             (list '_ '_ a)
    [::any ::string]    (list a [b] '_)
    [::keyword ::map]   (list a '_ b)
    [::string ::map]    (list '_ [a] b)
    [::vector ::map]    (list '_ a b)
    [::symbol ::map]    (list '_ a b)
    [::any ::any]       (list a b '_)
    [::any ::any ::any] (list a b c)))

(defn- normalize-route [route]
  (condp #(isa? %2 %1) (type route)
    ::keyword (list route '_ '_)
    ::string  (list '_ [route] '_)
    ::vector  (list '_ route '_)
    ::map     (list '_ '_ route)
    ::list    (normalize-route-list route)))

(defn- normalize-result [route]
  (condp #(isa? %2 %1) (type route)
    ::keyword [route]
    ::vector  route))

(defn normalize [routes]
  (into {} (for [[route result] routes]
             [(normalize-route route)
              (normalize-result result)])))

(defprotocol Routes
  (-matches [routes request])
  (-generate [routes result]))

(defn compile [routes]
  (let [routes   (normalize routes)
        matches  (eval (compile-matches routes))
        generate (eval (compile-generate routes))]
    (reify Routes
      (-matches [_ request] (matches request))
      (-generate [_ result] (generate result)))))

(defn matches [routes request]
  (if (satisfies? Routes routes)
    (-matches routes request)
    (-matches (compile routes) request)))

(defn generate [routes result]
  (let [result (normalize-result result)]
    (if (satisfies? Routes routes)
      (-generate routes result)
      (-generate (compile routes) result))))
