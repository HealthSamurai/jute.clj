(ns user
  (:require [cider-nrepl.main]))

(defn start-nrepl []
  (cider-nrepl.main/init
   ["refactor-nrepl.middleware/wrap-refactor"
    "cider.nrepl/cider-middleware"]))

(defn start-all []
  (start-nrepl))

(defn -main [& args]
  (start-nrepl))
