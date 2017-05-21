(ns ataraxy.response-test
  (:require [ataraxy.handler :as handler]
            [ataraxy.response :as resp]
            [clojure.test :refer :all]))

(deftest test-content
  (is (= (handler/default {:ataraxy/result [::resp/ok "<h1>Hello World</h1>"]})
         {:status  200
          :headers {"Content-Type" "text/html; charset=UTF-8"}
          :body    "<h1>Hello World</h1>"})))

(deftest test-redirects
  (is (= (handler/default {:ataraxy/result [::resp/created "/new" "foo"]})
         {:status  201
          :headers {"Content-Type" "text/html; charset=UTF-8"
                    "Location"     "/new"}
          :body    "foo"}))
  (is (= (handler/default {:ataraxy/result [::resp/found "/redirect"]})
         {:status 302, :headers {"Location" "/redirect"}, :body nil}))
  (is (= (handler/default {:ataraxy/result [::resp/temporary-redirect "/redirect"]})
         {:status 307, :headers {"Location" "/redirect"}, :body nil})))

