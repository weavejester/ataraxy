(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]
            [ataraxy.error :as err]))

(deftest test-valid?
  (are [x y] (= (ataraxy/valid? x) y)
    '{"/foo" [:bar]}        true
    '{"/foo" :bar}          false
    '{"/foo" (:bar)}        false
    '{"/x" [:x], "/y" [:x]} false))

(deftest test-matches
  (testing "string routes"
    (let [routes '{"/foo" [:foo], "/bar" [:bar]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"}       [:foo]
        {:path-info "/bar"} [:bar]
        {:uri "/baz"}       [::err/unmatched-path])))

  (testing "list routing tables"
    (let [routes '("/foo" [:foo], "/bar" [:bar])]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"}       [:foo]
        {:path-info "/bar"} [:bar]
        {:uri "/baz"}       [::err/unmatched-path])))

  (testing "symbol routes"
    (let [routes '{x [:foo x]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "foo"}       [:foo "foo"]
        {:path-info "bar"} [:foo "bar"])))

  (testing "custom regexes"
    (let [routes '{^{:re #"\d\d"} x   [:foo x]
                   ^{:re #"\d\d\d"} y [:bar y]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "10"}     [:foo "10"]
        {:uri "1"}      [::err/unmatched-path]
        {:uri "bar"}    [::err/unmatched-path]
        {:uri "10/bar"} [::err/unmatched-path]
        {:uri "200"}    [:bar "200"]
        {:uri "1"}      [::err/unmatched-path])))

  (testing "keyword routes"
    (let [routes '{:get [:read], :put [:write]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get}    [:read]
        {:request-method :put}    [:write]
        {:request-method :delete} [::err/unmatched-method])))

  (testing "set routes"
    (let [routes '{#{x} [:foo x], #{y} [:bar y], #{z w} [:baz z w]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/" :query-params {"x" "quz"}}       [:foo "quz"]
        {:uri "/" :query-params {"y" "quz"}}       [:bar "quz"]
        {:uri "/" :query-params {"z" "a" "w" "b"}} [:baz "a" "b"]
        {:uri "/" :form-params {"x" "fp"}}         [:foo "fp"]
        {:uri "/" :multipart-params {"x" "mp"}}    [:foo "mp"]
        {:uri "/" :query-params {"z" "a"}}         [::err/missing-params '#{x}]
        {:uri "/"}                                 [::err/missing-params '#{x}])))

  (testing "map routes"
    (let [routes '{{{p :p} :params} [:p p], {{:keys [q]} :params} [:q q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:params {:p "page"}}    [:p "page"]
        {:params {:q "query"}}   [:q "query"]
        {:params {:z "invalid"}} [::err/missing-destruct '#{p}])))

  (testing "optional bindings"
    (let [routes '{["/p" #{?p}] [:p ?p]
                   ["/q" {{?q "q"} :query-params}] [:q ?q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/p", :query-params {"p" "page"}}  [:p "page"]
        {:uri "/p", :query-params {"q" "query"}} [:p nil]
        {:uri "/q", :query-params {"q" "query"}} [:q "query"]
        {:uri "/q", :query-params {"p" "page"}}  [:q nil]
        {:uri "/z", :query-params {"p" "page"}}  [::err/unmatched-path]
        {:uri "/z", :query-params {"q" "query"}} [::err/unmatched-path])))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" [:foo]})]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"} [:foo]
        {:uri "/bar"} [::err/unmatched-path])))

  (testing "vector routes"
    (let [routes '{["/foo/" foo]          [:foo foo]
                   [:get "/bar"]          [:bar]
                   ["/baz" #{baz}]        [:baz baz]
                   [:get "/x/" x "/y/" y] [:xy x y]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}                         [:foo "10"]
        {:request-method :get, :uri "/bar"}      [:bar]
        {:uri "/baz", :query-params {"baz" "2"}} [:baz "2"]
        {:request-method :get, :uri "/x/8/y/3a"} [:xy "8" "3a"]
        {:uri "/foo"}                            [::err/unmatched-path]
        {:request-method :put, :uri "/bar"}      [::err/unmatched-method]
        {:request-method :get, :uri "/x/44/y/"}  [::err/unmatched-path])))

  (testing "nested routes"
    (let [routes '{"/foo" {:get [:foo], ["/" id] {:get [:bar id]}}}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo"}    [:foo]
        {:request-method :get, :uri "/foo/10"} [:bar "10"]
        {:request-method :put, :uri "/foo"}    [::err/unmatched-method])))

  (testing "coercions"
    (let [routes '{["/foo/" id] [:foo ^int id]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo/10"} [:foo 10]
        {:request-method :get, :uri "/foo/xx"} [::err/failed-coercions '#{id}])))

  (testing "error results"
    (let [routes '{[:get "/foo/" id #{page}] [:foo id ^int page]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :put, :uri "/foo"}    [::err/unmatched-path]
        {:request-method :put, :uri "/foo/10"} [::err/unmatched-method]
        {:request-method :get, :uri "/foo/10"} [::err/missing-params '#{page}]
        {:request-method :get, :uri "/foo/10"
         :query-params {"page" "x"}}           [::err/failed-coercions '#{page}]))))

(deftest test-handler
  (testing "synchronous handler"
    (let [handler (ataraxy/handler
                   {:routes
                    '{[:get "/foo"]     [:foo]
                      [:get "/bar/" id] [:bar id]}
                    :handlers
                    {:foo
                     (constantly {:status 200, :headers {}, :body "foo"})
                     :bar
                     (fn [{[_ id] :ataraxy/result}]
                       {:status 200, :headers {}, :body (str "bar" id)})}})]
      (is (= (handler {:request-method :get, :uri "/foo"})
             {:status 200, :headers {}, :body "foo"}))
      (is (= (handler {:request-method :get, :uri "/bar/baz"})
             {:status 200, :headers {}, :body "barbaz"}))
      (is (= (handler {:request-method :get, :uri "/baz"})
             {:status  404
              :headers {"Content-Type" "text/plain; charset=UTF-8"}
              :body    "Not Found"}))
      (is (= (handler {:request-method :put, :uri "/foo"})
             {:status  405
              :headers {"Content-Type" "text/plain; charset=UTF-8"}
              :body    "Method Not Allowed"}))))

  (testing "asynchronous handler"
    (let [handler (ataraxy/handler
                   {:routes
                    '{[:get "/foo"]     [:foo]
                      [:get "/bar/" id] [:bar id]}
                    :handlers
                    {:foo
                     (fn [request respond raise]
                       (respond {:status 200, :headers {}, :body "foo"}))
                     :bar
                     (fn [{[_ id] :ataraxy/result} respond raise]
                       (respond {:status 200, :headers {}, :body (str "bar" id)}))}})]
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/foo"} respond raise)
        (is (= @respond {:status 200, :headers {}, :body "foo"})))
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/bar/baz"} respond raise)
        (is (= @respond {:status 200, :headers {}, :body "barbaz"})))
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/baz"} respond raise)
        (is (= @respond {:status  404
                         :headers {"Content-Type" "text/plain; charset=UTF-8"}
                         :body    "Not Found"})))
      (let [respond (promise), raise (promise)]
        (handler {:request-method :put, :uri "/foo"} respond raise)
        (is (= @respond {:status  405
                         :headers {"Content-Type" "text/plain; charset=UTF-8"}
                         :body    "Method Not Allowed"})))))

  (testing "middleware"
    (let [handler (ataraxy/handler
                   {:routes
                    '{"/foo" {:get ^:baz [:foo]}
                      "/bar" ^{:quz 9} {["/" id] {:get [:bar id]}}}
                    :handlers
                    {:foo
                     (constantly {:status 200, :headers {}, :body "foo"})
                     :bar
                     (fn [{[_ id] :ataraxy/result}]
                       {:status 200, :headers {}, :body (str "bar" id)})}
                    :middleware
                    {:baz
                     (fn [handler]
                       #(assoc-in (handler %) [:headers "X-Middle"] "baz"))
                     :quz
                     (fn [handler x]
                       #(assoc-in (handler %) [:headers "X-Middle"] (str "quz" x)))}})]
      (is (= (handler {:request-method :get, :uri "/foo"})
             {:status 200, :headers {"X-Middle" "baz"}, :body "foo"}))
      (is (= (handler {:request-method :get, :uri "/bar/10"})
             {:status 200, :headers {"X-Middle" "quz9"}, :body "bar10"}))
      (is (= (handler {:request-method :get, :uri "/baz"})
             {:status  404
              :headers {"Content-Type" "text/plain; charset=UTF-8"}
              :body    "Not Found"}))))

  (testing "route parameters"
    (let [handler (ataraxy/handler
                   {:routes
                    '{[:get "/user/" uid "/post/" pid] [:user-post uid pid]}
                    :handlers
                    {:user-post
                     (fn [{params :route-params}]
                       {:status 200, :headers {}, :body params})}})]
      (is (= (handler {:request-method :get, :uri "/user/alice/post/5"})
             {:status 200, :headers {}, :body {:uid "alice" :pid "5"}})))))
