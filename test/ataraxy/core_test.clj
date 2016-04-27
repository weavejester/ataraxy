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
        {:uri "/foo"} [:foo]
        {:path-info "/bar"} [:bar]
        {:uri "/baz"} nil)))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" [:foo]})]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"} [:foo]
        {:uri "/bar"} nil)))

  (testing "vector routes"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}       [:foo "10"]
        {:uri "/foo/8/bar/3a"} [:foobar "8" "3a"]
        {:uri "/foo"}          nil
        {:uri "/foo/44/bar/"}  nil)))

  (testing "custom regexes"
    (let [routes '{["/foo/" (x :re #"\d\d")]    [:foo x]
                   ["/bar/" ^{:re #"\d\d\d"} x] [:bar x]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}     [:foo "10"]
        {:uri "/foo/1"}      nil
        {:uri "/foo/bar"}    nil
        {:uri "/foo/10/bar"} nil
        {:uri "/bar/200"}    [:bar "200"]
        {:uri "/bar/1"}      nil)))

  (testing "keyword routes"
    (let [routes '{:get [:read], :put [:write]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get}    [:read]
        {:request-method :put}    [:write]
        {:request-method :delete} nil)))

  (testing "map routes"
    (let [routes '{{:params {:p p}} [:p p], {:params {:q q}} [:q q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:params {:p "page"}}    [:p "page"]
        {:params {:q "query"}}   [:q "query"]
        {:params {:z "invalid"}} nil)))

  (testing "nested routes"
    (let [routes '{"/foo" {:get [:foo], ["/" id] {:get [:foo id]}}}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo"}    [:foo]
        {:request-method :get, :uri "/foo/10"} [:foo "10"]
        {:uri "/foo"}                          nil)))

  (testing "types"
    (let [routes '{["/foo/" id] [:foo (id :tag UUID)]
                   ["/bar/" id] [:bar (id :- UUID)]
                   ["/baz/" id] [:baz ^Int id]}
          id     #uuid "8b82e52d-3c9f-44b8-8342-dfc29ca1c471"]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri (str "/foo/" id)} [:foo id]
        {:uri (str "/bar/" id)} [:bar id]
        {:uri (str "/baz/10")}  [:baz 10]
        {:uri "/foo/8b82e52d"}  nil
        {:uri "/bar/10"}        nil
        {:uri "/baz/xx"}        nil))))

(deftest test-generate
  (testing "static routes"
    (let [routes '{"/foo" [:foo], "/bar" [:bar]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo] {:uri "/foo"}
        [:bar] {:uri "/bar"}
        [:baz] nil)))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" [:foo]})]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo] {:uri "/foo"}
        [:bar] nil)))

  (testing "vector routes"
    (let [routes '{["/foo"]              [:foo]
                   ["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo "10"]        {:uri "/foo/10"}
        [:foobar "8" "3a"] {:uri "/foo/8/bar/3a"}
        [:foo]             {:uri "/foo"}
        [:foobar "8"]      nil
        [:baz "4" "5"]     nil)))

  (testing "custom regexes"
    (let [routes '{["/foo/" (x :re #"\d\d")] [:foo x]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo "10"] {:uri "/foo/10"}
        [:bar "10"] nil)))

  (testing "keyword routes"
    (let [routes '{:get [:read], :put [:write]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:read]   {:request-method :get}
        [:write]  {:request-method :put}
        [:delete] nil)))

  (testing "map routes"
    (let [routes '{{:params {:p p}} [:p p], {:params {:q q}} [:q q]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:p "page"]    {:params {:p "page"}}
        [:q "query"]   {:params {:q "query"}}
        [:z "invalid"] nil)))

  (testing "nested routes"
    (let [routes '{"/foo" {:get [:foo], ["/" id] {:get [:foo id]}}}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo]           {:request-method :get, :uri "/foo"}
        [:foo "10"]      {:request-method :get, :uri "/foo/10"}
        [:foo "10" "20"] nil
        [:bar "10"]      nil)))

  (testing "types"
    (let [routes '{["/foo/" id] [:foo (id :tag UUID)]
                   ["/bar/" id] [:bar ^UUID id]}
          id     #uuid "8b82e52d-3c9f-44b8-8342-dfc29ca1c471"]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo id]       {:uri (str "/foo/" id)}
        [:bar id]       {:uri (str "/bar/" id)}
        [:bar "x"]      nil
        [:bar (str id)] nil))))
