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

(defmulti ^:private compile-match-clause
  (fn [[route result]] [(type route) (type result)]))

(defmethod compile-match-clause [::string ::keyword] [[route result]]
  (let [matching (gensym (name result))]
    {:bindings [matching], :result result}))

(defmethod compile-match-clause [::string ::vector] [[route result]]
  (let [matching (gensym (name (first result)))]
    {:bindings [matching], :result result}))

(defmethod compile-match-clause [::vector ::keyword] [[route result]]
  (let [matching (gensym (name result))
        groups   (map gensym (filter symbol? route))]
    {:bindings (into [matching] groups), :result result}))

(defmethod compile-match-clause [::vector ::vector] [[route result]]
  (let [matching (gensym (name (first result)))
        params   (filter symbol? route)
        groups   (map gensym params)
        mapping  (zipmap params groups)]
    {:bindings (into [matching] groups)
     :result   (mapv #(mapping % %) result)}))

(defn- compile-matches [routes]
  (let [routes   (seq routes)
        pattern  (join-patterns (map compile-pattern (keys routes)))
        clauses  (map compile-match-clause routes)
        bindings (mapcat :bindings clauses)
        conds    (mapcat (juxt (comp first :bindings) :result) clauses)]
    `(fn [request#]
       (let [[~@bindings] (rest (re-matches ~pattern (:uri request#)))]
         (cond ~@conds)))))

(defmulti ^:private compile-result-case
  (fn [_ [route result]] [(type route) (type result)]))

(defmethod compile-result-case [::string ::keyword] [_ [route result]]
  [result {:uri route}])

(defmethod compile-result-case [::string ::vector] [_ [route [kw & _]]]
  [kw {:uri route}])

(defmethod compile-result-case [::vector ::keyword] [_ [route result]]
  [result {:uri `(str ~@route)}])

(defmethod compile-result-case [::vector ::vector] [result-sym [route [kw & args]]]
  [kw `(let [[~@args] (rest ~result-sym)] {:uri (str ~@route)})])

(defn- compile-generate [routes]
  (let [result (gensym "result")
        cases  (mapcat (partial compile-result-case result) routes)]
    `(fn [~result]
       (case (if (vector? ~result) (first ~result) ~result)
         ~@cases
         nil))))

(defn compile [routes]
  (let [matches*  (eval (compile-matches routes))
        generate* (eval (compile-generate routes))]
    (reify Routes
      (matches [_ request] (matches* request))
      (generate [_ result] (generate* result)))))
