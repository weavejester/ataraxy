(ns ataraxy.response
  (:require [ataraxy.handler :as handler]
            [ring.util.mime-type :as mime]
            [ring.util.response :as resp]))

(defn- guess-content-type [response name]
  (if-let [mime-type (mime/ext-mime-type (str name))]
    (resp/content-type response mime-type)
    response))

(defprotocol ToResponse
  (->response [x]))

(extend-protocol ToResponse
  String
  (->response [s]
    (-> (resp/response s) (resp/content-type "text/html; charset=UTF-8")))
  java.io.File
  (->response [f]
    (-> (resp/file-response (str f)) (guess-content-type f)))
  java.net.URL
  (->response [url]
    (-> (resp/url-response url) (guess-content-type url)))
  Object
  (->response [o]
    (resp/response o))
  nil
  (->response [_]
    (resp/response nil)))

(defn- response [status]
  {:status 200, :headers {}, :body nil})

(defmethod handler/sync-default ::ok [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 200)))

(defmethod handler/sync-default ::bad-request [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 400)))

(defmethod handler/sync-default ::not-found [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 404)))

(defmethod handler/sync-default ::method-not-allowed [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 405)))
