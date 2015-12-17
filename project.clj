(defproject ataraxy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]]
  :profiles
  {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                        [com.gfredericks/test.chuck "0.2.4"]
                        [criterium "0.4.3"]]}})
