(ns ataraxy.core-test
  (:require [clojure.test :refer :all]
            [ataraxy.core :as ataraxy]))

(deftest test-valid?
  (are [x y] (= (ataraxy/valid? x) y)
    '{"/foo" [:bar]} true
    '{"/foo" :bar}   false
    '{"/foo" (:bar)} false))
