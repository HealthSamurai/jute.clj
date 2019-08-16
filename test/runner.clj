(ns runner
  (:require [cognitect.test-runner]
            [clojure.test :as test])
  (:gen-class))

(defn -main [& args]
  (apply cognitect.test-runner/-main args))

