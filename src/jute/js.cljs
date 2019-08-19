(ns jute.js
  (:require [jute.core :as j]))

(defn ^:export compile [template & [options]]
  (let [compiled (j/compile (js->clj template :keywordize-keys true)
                            (js->clj options :keywordize-keys true))]
    (fn [scope]
      (clj->js (compiled scope)))))

(defn ^:export drop-blanks [n]
  (j/drop-blanks (js->clj n :keywordize-keys true)))
