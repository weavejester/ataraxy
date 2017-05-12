(ns ataraxy.error)

(def errors
  {::unmatched-path   0
   ::unmatched-method 1
   ::missing-params   2
   ::missing-destruct 3})

(defn error-result? [result]
  (contains? errors (first result)))
