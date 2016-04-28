(defproject ataraxy "0.0.4"
  :description "A data-driven Ring routing and destructuring library"
  :url "https://github.com/weavejester/ataraxy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.velisco/herbert "0.7.0-alpha2"]]
  :profiles
  {:dev {:dependencies [[criterium "0.4.3"]]}})
