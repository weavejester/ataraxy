(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

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
      (is (nil? (ataraxy/matches routes {:request-method :post, :uri "/foo/10"}))))))

(deftest test-generate
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (is (= (ataraxy/generate routes :foo) {:uri "/foo"}))
      (is (= (ataraxy/generate routes :bar) {:uri "/bar"}))
      (is (nil? (ataraxy/generate routes :baz)))))
  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (is (= (ataraxy/generate routes [:foo "10"])        {:uri "/foo/10"}))
      (is (= (ataraxy/generate routes [:foobar "8" "3a"]) {:uri "/foo/8/bar/3a"} ))
      (is (nil? (ataraxy/generate routes [:bar]))))))
