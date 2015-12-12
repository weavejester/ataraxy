(ns ataraxy.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as set]))

(defprotocol Routes
  (matches [routes request])
  (generate [routes outcome]))

(defn compile [routes]
  (let [lookup (set/map-invert routes)]
    (reify Routes
      (matches [_ request]
        (routes (:uri request)))
      (generate [_ outcome]
        (if-let [uri (lookup outcome)]
          {:uri uri})))))
