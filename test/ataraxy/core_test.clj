(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-matches
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (is (= (ataraxy/matches routes {:uri "/foo"}) [:foo]))
      (is (= (ataraxy/matches routes {:uri "/bar"}) [:bar]))
      (is (nil? (ataraxy/matches routes {:uri "/baz"})))))
  (testing "parameters"
    (let [routes '{["/foo/" x]           [:foo x]
                   ["/foo/" x "/bar/" y] [:foobar x y]}]
      (is (= (ataraxy/matches routes {:uri "/foo/10"})       [:foo "10"]))
      (is (= (ataraxy/matches routes {:uri "/foo/8/bar/3a"}) [:foobar "8" "3a"]))
      (is (nil? (ataraxy/matches routes {:uri "/foo"})))
      (is (nil? (ataraxy/matches routes {:uri "/foo/44/bar/"}))))))

(deftest test-generate
  (testing "static routes"
    (let [routes '{"/foo" :foo, "/bar" :bar}]
      (is (= (ataraxy/generate routes :foo) {:uri "/foo"}))
      (is (= (ataraxy/generate routes :bar) {:uri "/bar"}))
      (is (nil? (ataraxy/generate routes :baz))))))
