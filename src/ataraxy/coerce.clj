(ns ataraxy.coerce)

(defn ->int [s]
  (try (Long/parseLong s) (catch NumberFormatException _)))

(defn ->uuid [s]
  (try (java.util.UUID/fromString s) (catch IllegalArgumentException _)))

(def default-coercers
  {'int  ->int
   'uuid ->uuid})
