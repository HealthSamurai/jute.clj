(ns jute.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as cset]))

(def template
  {:foo 12

   :const {:a 12
           :b []
           :c true}

   :bar {:$if false
         :$then "bar"
         :$else "baz"}})

(declare compile)

(defn- eval-node [n scope]
  (if (fn? n) (n scope) n))

(defn- compile-if [node]
  (let [compiled-if (compile (:$if node))
        compiled-then (compile (:$then node))
        compiled-else (compile (:$else node))]

    (if (fn? compiled-if)
      (fn [scope]
        (if (eval-node compiled-if scope)
          (eval-node compiled-then scope)
          (eval-node compiled-else scope)))

      (if compiled-if
        compiled-then
        compiled-else))))

(def directives
  {:$if compile-if})

(defn- compile-map [node]
  (let [directive-keys (cset/intersection (set (keys node)) (set (keys directives)))]
    (when (> (count directive-keys) 1)
      (throw (IllegalArgumentException. (str "More than one directive found in node "
                                             (pr-str node)
                                             ". Found following directive keys: "
                                             (pr-str directive-keys)))))

    (if (empty? directive-keys)
      (let [result (reduce (fn [acc [key val]]
                             (let [compiled-val (compile val)]
                               (-> acc
                                   (assoc-in [:result key] compiled-val)
                                   (assoc :dynamic? (or (:dynamic? acc) (fn? compiled-val))))))
                           {:dynamic? false :result {}} node)]
        (if (:dynamic? result)
          (fn [scope]
            (reduce (fn [acc [key val]]
                      (assoc acc key (eval-node val scope)))
                    {} (:result result)))

          (:result result)))

      ((get directives (first directive-keys)) node))))

(defn- compile-vector [node]
  (let [result (mapv compile node)]
    (if (some fn? result)
      (fn [scope]
        (mapv #(eval-node % scope) result))

      result)))

(defn- compile-primitive [node]
  node)

(defn compile
  "Compiles JUTE template into invocabe function."
  [node]

  (cond
    (map? node) (compile-map node)
    (vector? node) (compile-vector node)
    :else (compile-primitive node)))
