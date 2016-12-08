(ns ataraxy.core
  (:require [clojure.spec :as s]))

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
