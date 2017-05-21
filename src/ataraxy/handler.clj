(ns ataraxy.handler
  "A namespace that contains the default handler for Ataraxy routes.")

(defmulti sync-default
  "A synchronous Ring handler, defined as a multimethod that dispatches off the
  :ataraxy/result key in the requst map."
  (fn [request] (first (:ataraxy/result request))))

(defmulti async-default
  "An asynchronous Ring handler, defined as a multimethod that dispatches off
  the :ataraxy/result key in the requst map."
  (fn [request _ _] (first (:ataraxy/result request))))

(defmethod async-default :default [request respond raise]
  (respond (sync-default request)))

(defn default
  "The default handler for Ataraxy routes. Extend by using the sync-default and
  async-default multimethods."
  ([request] (sync-default request))
  ([request respond raise] (async-default request respond raise)))
