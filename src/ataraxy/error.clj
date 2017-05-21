(ns ataraxy.error
  (:require [ataraxy.handler :as handler]
            [ataraxy.response :as response]))

(def errors
  {::unmatched-path   0
   ::unmatched-method 1
   ::missing-params   2
   ::missing-destruct 3
   ::failed-coercions 4})

(defn error-result? [result]
  (contains? errors (first result)))

(defmethod handler/sync-default ::unmatched-path [_]
  [::response/not-found "Not Found"])

(defmethod handler/sync-default ::unmatched-method [_]
  [::response/method-not-allowed "Method Not Allowed"])

(defmethod handler/sync-default ::missing-params [_]
  [::response/bad-request "Bad Request"])

(defmethod handler/sync-default ::missing-destruct [_]
  [::response/bad-request "Bad Request"])

(defmethod handler/sync-default ::failed-coercions [_]
  [::response/bad-request "Bad Request"])
