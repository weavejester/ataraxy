(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-matches
  (let [routes (ataraxy/compile {"/foo" :foo, "/bar" :bar})]
    (is (= (ataraxy/matches routes {:uri "/foo"}) :foo))
    (is (= (ataraxy/matches routes {:uri "/bar"}) :bar))
    (is (nil? (ataraxy/matches routes {:uri "/baz"})))))

(deftest test-generate
  (let [routes (ataraxy/compile {"/foo" :foo, "/bar" :bar})]
    (is (= (ataraxy/generate routes :foo) {:uri "/foo"}))
    (is (= (ataraxy/generate routes :bar) {:uri "/bar"}))
    (is (nil? (ataraxy/generate routes :baz)))))
