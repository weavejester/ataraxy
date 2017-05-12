(ns ataraxy.error)

(def errors
  {::unmatched-path   0
   ::unmatched-method 1
   ::missing-params   2
   ::missing-destruct 3})

(defn error-result? [result]
  (contains? errors (first result)))

(def responses
  {::unmatched-path   {:status 404, :body "Not Found"}
   ::unmatched-method {:status 405, :body "Method Not Allowed"}
   ::missing-params   {:status 400, :body "Bad Request"}
   ::missing-destruct {:status 400, :body "Bad Request"}})

(def ^:private common-headers
  {"Content-Type" "text/plain; charset=UTF-8"})

(defn default-handler
  ([request]
   (let [key  (first (:ataraxy/result request))
         resp (responses key ::unmatched-path)]
     (assoc resp :headers common-headers)))
  ([request respond raise]
   (respond (default-handler request))))
