(ns jute.alt
  (:require
   [fhirpath.core :as fp]
   [clojure.test :as t]
   [clojure.string :as str]))


(declare parse)

(defn parse-let [{l :$let b :$body}]
  (let [vars (reduce (fn [acc [[k v] & r]]
                       (conj acc
                             (symbol (name k))
                             (parse v)))
                     [] l)]
    (list 'let vars (parse b))))

(defn parse-if [{i :$if t :$then e :$else}]
  (list 'if (parse i) (parse t) (parse e)))

(defn clear-map [m]
  (apply dissoc m (for [[k v] m :when (or (nil? v) (= [] v) (= {} v))] k)))

(defn parse-map [expr]
  (list 'jute.alt/clear-map
        (reduce (fn [acc [k v]]
                  (assoc acc k (parse v))) {}
                expr)))

(defn parse [expr]
  (cond
    (map? expr)
    (cond
      (contains? expr :$let) (parse-let expr)
      (contains? expr :$if) (parse-if expr)
      :else (parse-map expr))

    (string? expr) (if (str/starts-with? expr "$")
                     (list (fp/parse (subs expr 1)) 'doc)
                     expr)
    :else expr))

(defn *compile [expr]
  (list 'fn ['doc] (parse expr)))

(defn compile [expr]
  (eval (*compile expr)))

(*compile
 {:$let [{:x 2}]
  :$body {:$if "$ a = 1"
          :$then 1
          :$else "$ b"}})

((compile
  {:$let [{:x 2}]
   :$body {:$if "$ a = 1"
           :$then 1
           :$else "$ b"}})
 {:a 2 :b "Hoho"})

(*compile {:a {:b "$ c"}})
((compile {:a {:b "$ c"}}) {:c 1})
