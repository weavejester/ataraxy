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
      (is (= (ataraxy/generate routes :foo) {:uri "/foo"}))
      (is (= (ataraxy/generate routes :bar) {:uri "/bar"}))
      (is (nil? (ataraxy/generate routes :baz)))))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" :foo})]
      (is (= (ataraxy/generate routes [:foo])  {:uri "/foo"}))
      (is (nil? (ataraxy/generate routes [:bar])))))

  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (is (= (ataraxy/generate routes [:foo "10"])        {:uri "/foo/10"}))
      (is (= (ataraxy/generate routes [:foobar "8" "3a"]) {:uri "/foo/8/bar/3a"} ))
      (is (nil? (ataraxy/generate routes [:bar])))))

  (testing "methods"
    (let [routes '{(:get ["/foo/" id]) [:foo id]}]
      (is (= (ataraxy/generate routes [:foo "10"]) {:request-method :get, :uri "/foo/10"}))
      (is (nil? (ataraxy/generate routes [:bar "10"])))))

  (testing "request restructuring"
    (let [routes '{(:get ["/search"] {:params {:q q}}) [:search q]}]
      (is (= (ataraxy/generate routes [:search "foobar"])
             {:request-method :get
              :uri "/search"
              :params {:q "foobar"}}))))

  (testing "partial routes"
    (let [routes '{("/foo" {:params {:q q}}) [:foo q]}]
      (is (= (ataraxy/generate routes [:foo "x"])
             {:uri "/foo", :params {:q "x"}})))))
