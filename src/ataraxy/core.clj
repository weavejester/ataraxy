(ns ataraxy.core
  (:require [clojure.spec :as s]
            [clojure.core.match :refer [match]]))

(s/def ::route-set
  (s/and set? (s/coll-of symbol?)))

(s/def ::route-single
  (s/or :method  keyword?
        :path    (s/or :string string? :symbol symbol?)
        :params  (s/and set? (s/coll-of symbol?))
        :request map?))

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
  (parse-routing-table {} (s/conform ::routing-table routes)))
