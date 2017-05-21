(ns ataraxy.handler)

(defmulti sync-default
  (fn [request] (first (:ataraxy/result request))))

(defmulti async-default
  (fn [request _ _] (first (:ataraxy/result request))))

(defmethod async-default :default [request respond raise]
  (respond (sync-default request)))

(defn default
  ([request] (sync-default request))
  ([request respond raise] (async-default request respond raise)))
