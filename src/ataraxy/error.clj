(ns ataraxy.error
  "A namespace that contains Ataraxy's standard error results, and adds default
  handler methods for each one."
  (:require [ataraxy.handler :as handler]
            [ataraxy.response :as response]))

(def errors
  "A map of errors to their relative priority."
  {::unmatched-path   0
   ::unmatched-method 1
   ::missing-params   2
   ::missing-destruct 3
   ::failed-coercions 4})

(defn error-result?
  "Return true if the result is an Ataraxy error result."
  [result]
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
