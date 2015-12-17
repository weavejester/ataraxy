(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-matches
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"} [:foo]
        {:uri "/bar"} [:bar]
        {:uri "/baz"} nil)))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" :foo})]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo"} [:foo]
        {:uri "/bar"} nil)))

  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}       [:foo "10"]
        {:uri "/foo/8/bar/3a"} [:foobar "8" "3a"]
        {:uri "/foo"}          nil
        {:uri "/foo/44/bar/"}  nil)))

  (testing "methods"
    (let [routes '{(:get ["/foo/" id]) [:foo id]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo/10"}  [:foo "10"]
        {:request-method :post, :uri "/foo/10"} nil)))

  (testing "request destructuring"
    (let [routes '{(:get ["/find"] {:params {:q q}}) [:find q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/find", :params {:q "x"}}  [:find "x"]
        {:request-method :get, :uri "/found", :params {:q "x"}} nil)))

  (testing "partial routes"
    (let [routes '{("/foo"   {:params {:q q}}) [:foo q]
                   (["/bar"] {:params {:q q}}) [:bar q]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo", :params {:q "x"}} [:foo "x"]
        {:uri "/bar", :params {:q "x"}} [:bar "x"]
        {:uri "/baz", :params {:q "x"}} nil))))

(deftest test-generate
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (are [res req] (= (ataraxy/generate routes res) req)
        :foo {:uri "/foo"}
        :bar {:uri "/bar"}
        :baz nil)))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" :foo})]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo] {:uri "/foo"}
        [:bar] nil)))

  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo "10"]        {:uri "/foo/10"}
        [:foobar "8" "3a"] {:uri "/foo/8/bar/3a"}
        [:bar]             nil)))

  (testing "methods"
    (let [routes '{(:get ["/foo/" id]) [:foo id]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo "10"] {:request-method :get, :uri "/foo/10"}
        [:bar "10"] nil)))

  (testing "request restructuring"
    (let [routes '{(:get ["/find"] {:params {:q q}}) [:find q]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:find "x"]  {:request-method, :get :uri "/find", :params {:q "x"}}
        [:found "x"] nil)))

  (testing "partial routes"
    (let [routes '{("/foo"   {:params {:q q}}) [:foo q]
                   (["/bar"] {:params {:q q}}) [:bar q]}]
      (are [res req] (= (ataraxy/generate routes res) req)
        [:foo "x"] {:uri "/foo", :params {:q "x"}}
        [:bar "x"] {:uri "/bar", :params {:q "x"}}))))
