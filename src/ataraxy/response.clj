(ns ataraxy.response
  (:require [ataraxy.handler :as handler]))

(defmethod handler/sync-default ::ok [{[_ body] :ataraxy/result}]
  {:status 200, :headers {}, :body body})

(defmethod handler/sync-default ::bad-request [{[_ body] :ataraxy/result}]
  {:status 400, :headers {}, :body body})

(defmethod handler/sync-default ::not-found [{[_ body] :ataraxy/result}]
  {:status 404, :headers {}, :body body})

(defmethod handler/sync-default ::method-not-allowed [{[_ body] :ataraxy/result}]
  {:status 405, :headers {}, :body body})
