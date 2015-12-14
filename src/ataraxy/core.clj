(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defprotocol Routes
  (matches [routes request])
  (generate [routes result]))

(derive clojure.lang.IPersistentVector ::vector)
(derive clojure.lang.Keyword ::keyword)
(derive java.lang.String ::string)

(defn- re-quote [s]
  (java.util.regex.Pattern/quote s))

(defmulti ^:private compile-pattern type)

(defmethod compile-pattern ::string [route]
  (re-quote route))

(defmethod compile-pattern ::vector [route]
  (str/join (map #(if (string? %) (re-quote %) "([^/]+)") route)))

(defn- join-patterns [patterns]
  (re-pattern (str/join "|" (map #(str "(" % ")") patterns))))

(defmulti ^:private compile-clause (partial mapv type))

(defmethod compile-clause [::string ::keyword] [[route result]]
  (let [matching (gensym (name result))]
    {:bindings [matching], :result result}))

(defmethod compile-clause [::string ::vector] [[route result]]
  (let [matching (gensym (name (first result)))]
    {:bindings [matching], :result result}))

(defmethod compile-clause [::vector ::keyword] [[route result]]
  (let [matching (gensym (name result))
        groups   (map gensym (filter symbol? route))]
    {:bindings (into [matching] groups), :result result}))

(defmethod compile-clause [::vector ::vector] [[route result]]
  (let [matching (gensym (name (first result)))
        params   (filter symbol? route)
        groups   (map gensym params)
        mapping  (zipmap params groups)]
    {:bindings (into [matching] groups)
     :result   (mapv #(mapping % %) result)}))

(defn- compile-matches [routes]
  (let [routes   (seq routes)
        pattern  (join-patterns (map compile-pattern (keys routes)))
        clauses  (map compile-clause routes)
        bindings (mapcat :bindings clauses)
        conds    (mapcat (juxt (comp first :bindings) :result) clauses)]
    `(fn [request#]
       (let [[~@bindings] (rest (re-matches ~pattern (:uri request#)))]
         (cond ~@conds)))))

(defn- compile-generate [routes]
  (let [lookup (set/map-invert routes)]
    (fn [result]
      (if-let [uri (lookup result)]
        {:uri uri}))))

(defn compile [routes]
  (let [matches*  (eval (compile-matches routes))
        generate* (compile-generate routes)]
    (reify Routes
      (matches [_ request] (matches* request))
      (generate [_ result] (generate* result)))))
