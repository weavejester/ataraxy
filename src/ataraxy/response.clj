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

(defmethod handler/sync-default ::continue [{[_] :ataraxy/result}]
  {:status 100, :headers {}, :body nil})

(defmethod handler/sync-default ::ok [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 200)))

(defmethod handler/sync-default ::created [{[_ url body] :ataraxy/result}]
  (-> (->response body) (resp/header "Location" url) (resp/status 201)))

(defmethod handler/sync-default ::accepted [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 202)))

(defmethod handler/sync-default ::no-content [{[_] :ataraxy/result}]
  {:status 204, :headers {}, :body nil})

(defmethod handler/sync-default ::moved-permanently [{[_ url] :ataraxy/result}]
  {:status 301, :headers {"Location" url}, :body nil})

(defmethod handler/sync-default ::found [{[_ url] :ataraxy/result}]
  {:status 302, :headers {"Location" url}, :body nil})

(defmethod handler/sync-default ::see-other [{[_ url] :ataraxy/result}]
  {:status 303, :headers {"Location" url}, :body nil})

(defmethod handler/sync-default ::not-modified [{[_] :ataraxy/result}]
  {:status 304, :headers {}, :body nil})

(defmethod handler/sync-default ::temporary-redirect [{[_ url] :ataraxy/result}]
  {:status 307, :headers {"Location" url}, :body nil})

(defmethod handler/sync-default ::permanent-redirect [{[_ url] :ataraxy/result}]
  {:status 308, :headers {"Location" url}, :body nil})

(defmethod handler/sync-default ::bad-request [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 400)))

(defmethod handler/sync-default ::unauthorized [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 401)))

(defmethod handler/sync-default ::forbidden [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 403)))

(defmethod handler/sync-default ::not-found [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 404)))

(defmethod handler/sync-default ::method-not-allowed [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 405)))

(defmethod handler/sync-default ::not-acceptable [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 406)))

(defmethod handler/sync-default ::request-timeout [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 408)))

(defmethod handler/sync-default ::conflict [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 409)))

(defmethod handler/sync-default ::gone [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 410)))

(defmethod handler/sync-default ::length-required [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 411)))

(defmethod handler/sync-default ::precondition-failed [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 412)))

(defmethod handler/sync-default ::payload-too-large [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 413)))

(defmethod handler/sync-default ::uri-too-long [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 414)))

(defmethod handler/sync-default ::unsupported-media-type [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 415)))

(defmethod handler/sync-default ::expectation-failed [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 417)))

(defmethod handler/sync-default ::precondition-required [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 428)))

(defmethod handler/sync-default ::too-many-requests [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 429)))

(defmethod handler/sync-default ::request-header-fields-too-large [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 431)))

(defmethod handler/sync-default ::unavailable-for-legal-reasons [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 451)))

(defmethod handler/sync-default ::internal-server-error [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 500)))

(defmethod handler/sync-default ::not-implemented [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 501)))

(defmethod handler/sync-default ::service-unavailable [{[_ body] :ataraxy/result}]
  (-> (->response body) (resp/status 502)))
