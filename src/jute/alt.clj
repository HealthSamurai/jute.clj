(ns jute.alt
  (:require
   [fhirpath.core :as fp]
   [clojure.test :as t]
   [clojure.string :as str]))


(declare parse)

(mapv first [{:a 1} {:b 2}])

(defn parse-let [{l :$let b :$body}]
  (let [vars
        (reduce (fn [acc [k v]]
                  (conj acc
                        (symbol (str "localvar-" (name k)))
                        (parse v)))
                [] (if (map? l) l (mapv first l)))]
    `(with-local-vars ~vars ~(parse b))))

(defn parse-if [{i :$if t :$then e :$else}]
  `(if ~(parse i)
     ~(parse t)
     ~(parse e)))

(defn clear-map [m]
  (apply dissoc m (for [[k v] m :when (or (nil? v) (= [] v) (= {} v))] k)))

(defn parse-obj [expr]
  (list 'jute.alt/clear-map
        (reduce (fn [acc [k v]]
                  (assoc acc k (parse v))) {}
                expr)))

(defn parse-map [{m :$map as :$as b :$body}]
  `(mapv
    (fn [x#]
      (with-local-vars [~(symbol (str "localvar-" as)) x#]
        ~(parse b)))
    ~(parse m)))

(defn parse [expr]
  (cond
    (map? expr)
    (cond
      (contains? expr :$let) (parse-let expr)
      (contains? expr :$if) (parse-if expr)
      (contains? expr :$map) (parse-map expr)
      :else (parse-obj expr))

    (string? expr) (if (str/starts-with? expr "$")
                     (list (fp/parse (subs expr 1)) 'doc)
                     expr)
    :else expr))

(defn *compile [expr]
  (list 'fn ['doc] (parse expr)))

(defn compile [expr]
  (eval (*compile expr)))

(defn jute [expr target]
  ((compile expr) target))

(*compile {:$map "$ coll"
           :$as "x"
           :$body {:xx "$ %x + 1"}})

(*compile {:$let [{:a 1} {:b 2}]})

(*compile {:$let {:a 1 :b 2}})
