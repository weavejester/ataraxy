(defproject ataraxy "0.4.0"
  :description "A data-driven Ring routing and destructuring library"
  :url "https://github.com/weavejester/ataraxy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring/ring-core "1.6.1"]]
  :profiles
  {:dev {:dependencies [[criterium "0.4.3"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0-alpha6"]]}}

  :aliases
  {"test-all" ["with-profile" "default:+1.10" "test"]})
