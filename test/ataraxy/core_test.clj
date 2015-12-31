(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-matches
  (testing "string routes"
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

  (testing "vector routes"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}       [:foo "10"]
        {:uri "/foo/8/bar/3a"} [:foobar "8" "3a"]
        {:uri "/foo"}          nil
        {:uri "/foo/44/bar/"}  nil)))

  (testing "list routes with methods"
    (let [routes '{(:get ["/foo/" id])   [:get-foo id]
                   (:put ["/foo/" id])   [:put-foo id]
                   (method ["/bar/" id]) [:bar method id]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo/10"}  [:get-foo "10"]
        {:request-method :put, :uri "/foo/10"}  [:put-foo "10"]
        {:request-method :get, :uri "/bar/20"}  [:bar :get "20"]
        {:request-method :post, :uri "/bar/12"} [:bar :post "12"]
        {:request-method :post, :uri "/foo/10"} nil)))

  (testing "list routes with paths"
    (let [routes '{(["/foo/" id]) [:foo id]
                   ("/bar")       [:bar]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:uri "/foo/10"}                    [:foo "10"]
        {:uri "/bar"}                       [:bar]
        {:request-method :get, :uri "/bar"} [:bar]
        {:uri "/bar", :params {:q "q"}}     [:bar])))

  (testing "list routes with request maps"
    (let [routes '{(:get ["/foo"] {:params {:x x}}) [:foo x]
                   ("/bar"        {:params {:y y}}) [:bar y]
                   (["/baz/" x]   {:params {:z z}}) [:baz x z]}]
      (are [req res] (= (ataraxy/matches routes req) res)
        {:request-method :get, :uri "/foo", :params {:x "x"}} [:foo "x"]
        {:uri "/bar", :params {:y "y"}}   [:bar "y"]
        {:uri "/baz/4", :params {:z "z"}} [:baz "4" "z"]
        {:uri "/foo", :params {:x "x"}}   nil))))

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
