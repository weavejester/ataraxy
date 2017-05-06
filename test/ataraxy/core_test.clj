(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-valid?
  (are [x y] (= (ataraxy/valid? x) y)
    '{"/foo" [:bar]} true
    '{"/foo" :bar}   false
    '{"/foo" (:bar)} false))

(deftest test-matches
  (testing "string routes"
    (let [routes '{"/foo" [:foo], "/bar" [:bar]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"}       [:foo]
        {:path-info "/bar"} [:bar]
        {:uri "/baz"}       [:ataraxy/not-found])))

  (testing "list routing tables"
    (let [routes '("/foo" [:foo], "/bar" [:bar])]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"}       [:foo]
        {:path-info "/bar"} [:bar]
        {:uri "/baz"}       [:ataraxy/not-found])))

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
        {:uri "1"}      [:ataraxy/not-found]
        {:uri "bar"}    [:ataraxy/not-found]
        {:uri "10/bar"} [:ataraxy/not-found]
        {:uri "200"}    [:bar "200"]
        {:uri "1"}      [:ataraxy/not-found])))

  (testing "keyword routes"
    (let [routes '{:get [:read], :put [:write]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get}    [:read]
        {:request-method :put}    [:write]
        {:request-method :delete} [:ataraxy/not-found])))

  (testing "set routes"
    (let [routes '{#{x} [:foo x], #{y} [:bar y], #{z w} [:baz z w]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/" :query-params {"x" "quz"}}       [:foo "quz"]
        {:uri "/" :query-params {"y" "quz"}}       [:bar "quz"]
        {:uri "/" :query-params {"z" "a" "w" "b"}} [:baz "a" "b"]
        {:uri "/" :form-params {"x" "fp"}}         [:foo "fp"]
        {:uri "/" :multipart-params {"x" "mp"}}    [:foo "mp"]
        {:uri "/" :query-params {"z" "a"}}         [:ataraxy/not-found]
        {:uri "/"}                                 [:ataraxy/not-found])))

  (testing "map routes"
    (let [routes '{{{p :p} :params} [:p p], {{:keys [q]} :params} [:q q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:params {:p "page"}}    [:p "page"]
        {:params {:q "query"}}   [:q "query"]
        {:params {:z "invalid"}} [:ataraxy/not-found])))

  (testing "optional bindings"
    (let [routes '{["/p" #{?p}] [:p ?p]
                   ["/q" {{?q "q"} :query-params}] [:q ?q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/p", :query-params {"p" "page"}}  [:p "page"]
        {:uri "/p", :query-params {"q" "query"}} [:p nil]
        {:uri "/q", :query-params {"q" "query"}} [:q "query"]
        {:uri "/q", :query-params {"p" "page"}}  [:q nil]
        {:uri "/z", :query-params {"p" "page"}}  [:ataraxy/not-found]
        {:uri "/z", :query-params {"q" "query"}} [:ataraxy/not-found])))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" [:foo]})]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"} [:foo]
        {:uri "/bar"} [:ataraxy/not-found])))

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
        {:uri "/foo"}                            [:ataraxy/not-found]
        {:request-method :put, :uri "/bar"}      [:ataraxy/not-found]
        {:request-method :get, :uri "/x/44/y/"}  [:ataraxy/not-found])))

  (testing "nested routes"
    (let [routes '{"/foo" {:get [:foo], ["/" id] {:get [:foo id]}}}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo"}    [:foo]
        {:request-method :get, :uri "/foo/10"} [:foo "10"]
        {:uri "/foo"}                          [:ataraxy/not-found]))))

(deftest test-handler
  (testing "synchronous handler"
    (let [handler (ataraxy/handler
                   '{[:get "/foo"]     [:foo]
                     [:get "/bar/" id] [:bar id]}
                   {:foo
                    (constantly {:status 200, :headers {}, :body "foo"})
                    :bar
                    (fn [{[_ id] :ataraxy/result}]
                      {:status 200, :headers {}, :body (str "bar" id)})
                    :ataraxy/not-found
                    (constantly nil)})]
      (is (= (handler {:request-method :get, :uri "/foo"})
             {:status 200, :headers {}, :body "foo"}))
      (is (= (handler {:request-method :get, :uri "/bar/baz"})
             {:status 200, :headers {}, :body "barbaz"}))
      (is (nil? (handler {:request-method :get, :uri "/baz"})))))

  (testing "asynchronous handler"
    (let [handler (ataraxy/handler
                   '{[:get "/foo"]     [:foo]
                     [:get "/bar/" id] [:bar id]}
                   {:foo
                    (fn [request respond raise]
                      (respond {:status 200, :headers {}, :body "foo"}))
                    :bar
                    (fn [{[_ id] :ataraxy/result} respond raise]
                      (respond {:status 200, :headers {}, :body (str "bar" id)}))
                    :ataraxy/not-found
                    (fn [request respond raise] (respond nil))})]
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/foo"} respond raise)
        (is (= @respond {:status 200, :headers {}, :body "foo"})))
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/bar/baz"} respond raise)
        (is (= @respond {:status 200, :headers {}, :body "barbaz"})))
      (let [respond (promise), raise (promise)]
        (handler {:request-method :get, :uri "/baz"} respond raise)
        (is (nil? @respond))))))
