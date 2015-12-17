(ns ataraxy.core-test
  (:require [ataraxy.core :as ataraxy]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.gfredericks.test.chuck.generators :as gen2]
            [clojure.test :refer [deftest testing is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(deftest check-matches
  (checking "static routes" 10
    [key gen/keyword
     uri (gen2/string-from-regex #"(/[a-z])+")]
    (is (= (ataraxy/matches {[uri] key} {:uri uri}) [key]))))

(deftest test-matches
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (is (= (ataraxy/matches routes {:uri "/foo"}) [:foo]))
      (is (= (ataraxy/matches routes {:uri "/bar"}) [:bar]))
      (is (nil? (ataraxy/matches routes {:uri "/baz"})))))

  (testing "compiled routes"
    (let [routes (ataraxy/compile '{"/foo" :foo})]
      (is (= (ataraxy/matches routes {:uri "/foo"}) [:foo]))
      (is (nil? (ataraxy/matches routes {:uri "/bar"})))))

  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (is (= (ataraxy/matches routes {:uri "/foo/10"})       [:foo "10"]))
      (is (= (ataraxy/matches routes {:uri "/foo/8/bar/3a"}) [:foobar "8" "3a"]))
      (is (nil? (ataraxy/matches routes {:uri "/foo"})))
      (is (nil? (ataraxy/matches routes {:uri "/foo/44/bar/"})))))

  (testing "methods"
    (let [routes '{(:get ["/foo/" id]) [:foo id]}]
      (is (= (ataraxy/matches routes {:request-method :get, :uri "/foo/10"}) [:foo "10"]))
      (is (nil? (ataraxy/matches routes {:request-method :post, :uri "/foo/10"})))))

  (testing "request destructuring"
    (let [routes '{(:get ["/search"] {:params {:q q}}) [:search q]}]
      (is (= (ataraxy/matches routes {:request-method :get
                                      :uri "/search"
                                      :params {:q "foobar"}})
             [:search "foobar"]))))

  (testing "partial routes"
    (let [routes '{("/foo" {:params {:q q}}) [:foo q]}]
      (is (= (ataraxy/matches routes {:uri "/foo", :params {:q "x"}})
             [:foo "x"])))))

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
