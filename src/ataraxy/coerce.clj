(ns ataraxy.coerce
  "A namespace for data coercion functions.")

(defn ->int
  "Coerce a string into an integer."
  [s]
  (try (Long/parseLong s) (catch NumberFormatException _)))

(defn ->uuid
  "Coerce a string into a UUID."
  [s]
  (try (java.util.UUID/fromString s) (catch IllegalArgumentException _)))

(def default-coercers
  "The default coercer map that all routes have access to."
  {'int  ->int
   'uuid ->uuid})
