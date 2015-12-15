(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- re-quote [s]
  (java.util.regex.Pattern/quote s))

(defn- compile-pattern [route]
  (str/join (map #(if (string? %) (re-quote %) "([^/]+)") route)))

(defn- join-patterns [patterns]
  (re-pattern (str/join "|" (map #(str "(" % ")") patterns))))

(defn- compile-match-clause [[route result]]
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

(defn- compile-generate [routes]
  (let [result (gensym "result")]
    `(fn [~result]
       (let [~result (if (vector? ~result) ~result [~result])]
         (case (first ~result)
           ~@(mapcat
              (fn [[route [result-key & args]]]
                [result-key `(let [[~@args] (rest ~result)] {:uri (str ~@route)})])
              routes)
           nil)))))

(derive clojure.lang.IPersistentVector ::vector)
(derive clojure.lang.Keyword ::keyword)
(derive java.lang.String ::string)

(defmulti ^:private normalize-route type)

(defmethod normalize-route ::string [route] [route])
(defmethod normalize-route ::vector [route] route)

(defmulti ^:private normalize-result type)

(defmethod normalize-result ::keyword [route] [route])
(defmethod normalize-result ::vector  [route] route)

(defn normalize [routes]
  (into {} (for [[route result] routes]
             [(normalize-route route)
              (normalize-result result)])))

(defprotocol Routes
  (matches [routes request])
  (generate [routes result]))

(defn compile [routes]
  (let [routes'     (normalize routes)
        matches-fn  (eval (compile-matches routes'))
        generate-fn (eval (compile-generate routes'))]
    (reify Routes
      (matches [_ request] (matches-fn request))
      (generate [_ result] (generate-fn result)))))
