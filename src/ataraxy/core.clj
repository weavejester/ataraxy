(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.core.match :refer [match]]))

(defn- re-quote [s]
  (java.util.regex.Pattern/quote s))

(defn- compile-pattern [route]
  (str/join (map #(if (string? %) (re-quote %) "([^/]+)") route)))

(defn- join-patterns [patterns]
  (re-pattern (str/join "|" (map #(str "()" %) patterns))))

(defn- add-route-binding [bindings route]
  (let [binds      (into [""] (filter symbol? route))
        inc-blanks (repeat (count binds) '_)
        new-blanks (repeat (count (first bindings)) '_)]
    (-> (mapv #(into % inc-blanks) bindings)
        (conj (into (vec new-blanks) binds)))))

(defn- compile-bindings [routes]
  (->> (reduce add-route-binding [] routes)
       (map (partial into '[_]))))

(defn- compile-matches [routes]
  (let [routes   (seq routes)
        pattern  (join-patterns (map compile-pattern (keys routes)))
        bindings (compile-bindings (keys routes))
        clauses  (interleave bindings (vals routes))]
    `(fn [request#]
       (match (re-matches ~pattern (:uri request#))
         ~@clauses
         ~'_ nil))))

(defn- compile-generate [routes]
  (let [result (gensym "result")]
    `(fn [~result]
       (case (first ~result)
         ~@(mapcat
            (fn [[route [result-key & args]]]
              [result-key `(let [[~@args] (rest ~result)] {:uri (str ~@route)})])
            routes)
         nil))))

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
