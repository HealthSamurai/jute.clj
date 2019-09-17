(ns jute.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as cset]
            [instaparse.core :as insta]
            [clojure.string :as str])
  #?(:clj (:gen-class)))

(defn- to-string [v]
  (if (keyword? v) (name v) (str v)))

(defn- to-decimal [v]
  (if (string? v)
    (try
      #?(:clj (Float/parseFloat v)
         :cljs (js/parseFloat v))
      (catch #?(:clj Exception :cljs js/Object) e nil))
    v))

(def standard-fns
  {:join str/join       ;; deprecated
   :joinStr str/join
   :splitStr (fn [s re & [limit]] (str/split s (re-pattern re) (or limit 0)))
   :substring subs      ;; deprecated
   :substr    subs
   :concat concat
   :merge merge
   :str to-string
   :range range
   :toString to-string
   :hash hash
   :toInt (fn [v] (if (string? v)
                    (try
                      #?(:clj (java.lang.Long/parseLong v)
                         :cljs (js/parseInt v))
                      (catch #?(:clj Exception :cljs js/Object) e nil))
                    v))

   :toDecimal to-decimal           ;; deprecated
   :toDec to-decimal

   :now (fn [] #?(:clj (new java.util.Date)
                  :cljs (new js/Date)))
   :groupBy group-by
   :len count
   :println println})

;; operator precedence:
;; unary ops & not op
;; mult, div and reminder
;; plus / minus
;; comparison (le, ge, etc)
;; equal, not equal
;; logical and
;; logical or

(def expression-parser
  (insta/parser
   "
<root> = <('$' #'\\s+')?> expr

<expr>
  = or-expr
  | and-expr
  | equality-expr
  | comparison-expr
  | additive-expr
  | multiplicative-expr
  | unary-expr
  | terminal-expr

<parens-expr>
  = <'('> expr <')'>

comparison-expr
  = comparison-expr ('>' | '<' | '>=' | '<=') additive-expr | additive-expr

additive-expr
  = additive-expr  ('+' | '-') multiplicative-expr | multiplicative-expr

multiplicative-expr
  = multiplicative-expr ('*' | '/' | '%') unary-expr | unary-expr

and-expr
  = and-expr <'&&'> equality-expr | equality-expr

or-expr
  = or-expr <'||'> and-expr | and-expr

equality-expr
  = equality-expr ('=' | '!=') comparison-expr | comparison-expr

unary-expr
  = ('+' | '-' | '!') expr | terminal-expr

<terminal-expr>
  = bool-literal / num-literal / string-literal / null-literal/ fn-call / path / parens-expr

fn-call
  = #'[a-zA-Z_]+' <'('> fn-call-args <')'>

<fn-call-args>
  = (expr (<','> expr) *)?

(* PATHS *)

path
  = path-head (<'.'> path-component)*
  | parens-expr (<'.'> path-component)+

<path-head>
  = #'[a-zA-Z_][a-zA-Z_0-9]*'
  | '@'
  | parens-expr
  | fn-call

<path-component>
  = #'[a-zA-Z_0-9]+'
  | path-predicate
  | path-deep-wildcard
  | path-wildcard
  | parens-expr

path-predicate
  = <'*'> parens-expr

path-deep-wildcard
  = <'**'>

path-wildcard
  = <'*'>

(* LITERALS *)

num-literal
  = #'[0-9]+' ('.' #'[0-9]'*)?

bool-literal
  = 'true' !path-head | 'false' !path-head

null-literal
  = 'null' !path-head

string-literal
  = <'\"'> #'[^\"]*'  <'\"'>
"
   :auto-whitespace :standard))

(declare compile*)

(defn- eval-node [n scope]
  (if (fn? n) (n scope) n))

(defn- compile-map-directive [node options]
  (let [compiled-map (compile* (:$map node) options)
        var-name (keyword (or (:$as node) "this"))
        compiled-body (compile* (:$body node) options)]

    (fn [scope]
      (let [coll (eval-node compiled-map scope)]
        (mapv (if (map? coll)
                (fn [[k v]] (eval-node compiled-body (assoc scope var-name {:key k :value v})))
                (fn [item] (eval-node compiled-body (assoc scope var-name item))))
              coll)))))

(defn- compile-reduce-directive [node options]
  (let [compiled-reduce (compile* (:$reduce node) options)
        [acc-name alias] (:$as node)
        compiled-start (compile* (:$start node) options)
        compiled-body (compile* (:$body node) options)]

    (when (or (nil? acc-name) (nil? alias))
      (let [err "Please provide $as attribute (array) for an $reduce directive"]
        (throw #?(:clj (IllegalArgumentException. err)
                  :cljs (js/Error. err)))))

    (let [acc-name (keyword acc-name)
          alias (keyword alias)]
      (fn [scope]
        (let [coll (eval-node compiled-reduce scope)
              start (eval-node compiled-start scope)]
          (reduce (if (map? coll)
                    (fn [acc [k v]]
                      (eval-node compiled-body
                                 (assoc scope alias {:key k :value v}
                                        acc-name acc)))

                    (fn [acc item]
                      (eval-node compiled-body
                                 (assoc scope alias item acc-name acc))))
                  start coll))))))

(defn- compile-if [node options]
  (let [compiled-if (compile* (:$if node) options)
        compiled-then (if (contains? node :$then)
                        (compile* (:$then node) options)
                        (compile* (dissoc node :$if :$else) options))
        compiled-else (compile* (:$else node) options)]

    (if (fn? compiled-if)
      (fn [scope]
        (if (eval-node compiled-if scope)
          (eval-node compiled-then scope)
          (eval-node compiled-else scope)))

      (if compiled-if
        (fn [scope] (eval-node compiled-then scope))
        (fn [scope] (eval-node compiled-else scope))))))

(defn- compile-let [node options]
  (let [lets (:$let node)
        compiled-locals (mapv (fn [[k v]] [k (compile* v options)])
                              (if (vector? lets)
                                (mapv (fn [i] [(first (keys i)) (first (vals i))]) lets)
                                (mapv (fn [[k v]] [k v]) lets)))

        compiled-body (compile* (:$body node) options)]

    (fn [scope]
      (let [new-scope (reduce (fn [acc [n v]]
                                (assoc acc n (eval-node v acc)))
                              scope compiled-locals)]
        (eval-node compiled-body new-scope)))))

(defn- compile-fn-directive [node options]
  (let [arg-names (map keyword (:$fn node))
        compiled-body (compile* (:$body node) options)]

    (fn [scope]
      (fn [& args]
        (let [new-scope (merge scope (zipmap arg-names args))]
          (eval-node compiled-body new-scope))))))

(defn- compile-switch-directive [node options]
  (let [compiled-node (reduce-kv (fn [m k v] (assoc m k (compile* v options))) {} node)]

    (fn [scope]
      (let [v (eval-node (:$switch compiled-node) scope)
            v-kw (keyword (str v))]
        (eval-node (get compiled-node v-kw (:$default compiled-node)) scope)))))

(defn- compile-call-directive [node options]
  (let [fn-name (keyword (:$call node))
        args (compile* (:$args node) options)]

    (fn [scope]
      (if-let [fun (or (and (fn? (get scope fn-name)) (get scope fn-name))
                       (get standard-fns (keyword fn-name)))]
        (let [arg-values (eval-node args scope)]
          (apply fun arg-values))

        (let [err (str "Cannot find function " fn-name " in the current scope")]
          (throw #?(:clj (IllegalArgumentException. err)
                    :cljs (js/Error. err))))))))

(def directives
  {:$if compile-if
   :$let compile-let
   :$map compile-map-directive
   :$reduce compile-reduce-directive
   :$call compile-call-directive
   :$switch compile-switch-directive
   :$fn compile-fn-directive})

(defn- compile-map [node options]
  (let [directives (merge directives (:directives options))
        directive-keys (cset/intersection (set (keys node))
                                          (set (keys directives)))]
    (when (> (count directive-keys) 1)
      (let [err (str "More than one directive found in the node "
                     (pr-str node)
                     ". Found following directive keys: "
                     (pr-str directive-keys))]
        (throw #?(:clj (IllegalArgumentException. err)
                  :cljs (js/Error. err)))))

    (if (empty? directive-keys)
      (let [result (reduce (fn [acc [key val]]
                             (let [compiled-val (compile* val options)]
                               (-> acc
                                   (assoc-in [:result key] compiled-val)
                                   (assoc :dynamic? (or (:dynamic? acc) (fn? compiled-val))))))
                           {:dynamic? false :result {}} node)]

        (if (:dynamic? result)
          (if (:discard-map-when-everything-evaluated-to-null options)
            (let [dynamic-keys (filter #(fn? (get (:result result) %))
                                       (keys (:result result)))]
              (fn [scope]
                (let [res (reduce (fn [acc [key val]]
                                    (assoc acc key (eval-node val scope)))
                                  {} (:result result))]
                  (if (every? #(nil? (get res %)) dynamic-keys)
                    nil
                    res))))

            (fn [scope]
              (reduce (fn [acc [key val]]
                        (assoc acc key (eval-node val scope)))
                      {} (:result result))))

          (:result result)))

      ((get directives (first directive-keys)) node options))))

(defn- compile-vector [node options]
  (let [result (mapv #(compile* % options) node)]
    (if (some fn? result)
      (fn [scope]
        (mapv #(eval-node % scope) result))

      result)))

(def operator-to-fn
  {"+" (fn [a b] (if (string? a) (str a b) (+ a b)))
   "-" clojure.core/-
   "*" clojure.core/*
   "%" clojure.core/rem
   "=" clojure.core/=
   "!=" clojure.core/not=
   "!" clojure.core/not
   ">" clojure.core/>
   "<" clojure.core/<
   ">=" clojure.core/>=
   "<=" clojure.core/<=
   "/" clojure.core//})

(declare compile-expression-ast)

(defn- compile-expr-expr [ast]
  (compile-expression-ast (second ast)))

(defn- compile-op-expr [[_ left op right]]
  (if right
    (if-let [f (operator-to-fn op)]
      (let [compiled-left (compile-expression-ast left)
            compiled-right (compile-expression-ast right)]

        (if (or (fn? compiled-left) (fn? compiled-right))
          (fn [scope] (f (eval-node compiled-left scope) (eval-node compiled-right scope)))
          (f compiled-left compiled-right)))

      (let [err (str "Cannot guess operator for: " op)]
        (throw #?(:clj (RuntimeException. err)
                  :cljs (js/Error. err)))))

    (compile-expression-ast left)))

(defn- compile-fn-call [[_ fn-name & args]]
  (let [compiled-args (mapv compile-expression-ast args)
        f (get standard-fns (keyword fn-name))]
    (fn [scope]
      (let [f (or f (get scope (keyword fn-name)))]
        (assert (and f (fn? f)) (str "Unknown function: " fn-name))
        (apply f (mapv #(eval-node % scope) compiled-args))))))

(defn- compile-and-expr [[_ left right]]
  (if right
    (let [compiled-left (compile-expression-ast left)
          compiled-right (compile-expression-ast right)]
      (if (or (fn? compiled-left) (fn? compiled-right))
        (fn [scope] (and (eval-node compiled-left scope) (eval-node compiled-right scope)))
        (and compiled-left compiled-right)))

    (compile-expression-ast left)))

(defn- compile-or-expr [[_ left right]]
  (if right
    (let [compiled-left (compile-expression-ast left)
          compiled-right (compile-expression-ast right)]
      (if (or (fn? compiled-left) (fn? compiled-right))
        (fn [scope] (or (eval-node compiled-left scope) (eval-node compiled-right scope)))
        (or compiled-left compiled-right)))

    (compile-expression-ast left)))

(defn- compile-unary-expr [ast]
  (if (= 2 (count ast))
    (compile-expression-ast (last ast))

    (let [f (operator-to-fn (second ast))
          operand (compile-expression-ast (last ast))]
      (fn [scope] (f (eval-node operand scope))))))

(defn- compile-num-literal [ast]
  #?(:clj (read-string (apply str (rest ast)))
     :cljs (js/parseFloat (apply str (rest ast)))))

(defn- compile-null-literal [ast]
  nil)

(defn- compile-bool-literal [[_ v]]
  (= v "true"))

(defn- compile-string-literal [[_ v]]
  v)

(defn- expand-wildcard [v]
  (if (sequential? v)
    v
    (if (map? v)
      (vals v)
      nil)))

(defn- compile-path-component [cmp idx]
  (cond
    (string? cmp) (if (re-matches #"^\d+$" cmp)
                    #?(:clj (read-string cmp)
                       :cljs (js/parseInt cmp))

                    (keyword cmp))
    (vector? cmp)
    (let [[t arg] cmp]
      (cond
        (= :path-wildcard t) [(fn [val scope is-multiple?]
                                (if is-multiple?
                                  (remove nil? (mapcat expand-wildcard val))
                                  (remove nil? (expand-wildcard val))))
                              true]

        (= :path-predicate t) (let [compiled-pred (compile-expression-ast arg)]
                                [(fn [val scope is-multiple?]
                                   (vec (filter #(compiled-pred (assoc scope :this %)) val)))
                                 true])

        :else

        (if (= 0 idx)
          [(let [compiled-expr (compile-expression-ast cmp)]
             (fn [val scope is-multiple?]
               (compiled-expr scope)))
           false]
          [(let [compiled-expr (compile-expression-ast cmp)]
             (fn [val scope is-multiple?]
               (let [result (compiled-expr scope)
                     result (if (string? result) (keyword result) result)]
                 (if is-multiple?
                   (remove nil? (map #(get % result) val))
                   (get val result)))))
           false])))))

(def path-root (keyword "@"))

(defn- compile-path [[_ & path-comps]]
  (let [compiled-comps (map-indexed (fn [idx itm] (compile-path-component itm idx))
                                    path-comps)]
    (fn [scope]
      (loop [[cmp & tail] compiled-comps
             val scope
             is-multiple? false
             idx 0]
        (let [next-val
              (if (= path-root cmp)
                val

                (if (vector? cmp)
                  ((first cmp) val scope is-multiple?)

                  (if is-multiple?
                    (remove nil? (map #(get % cmp) val))
                    (get val cmp))))]

          (if (empty? tail)
            next-val
            (recur tail next-val (or is-multiple?
                                     (and (vector? cmp)
                                          (second cmp)))
                   (inc idx))))))))

(def expressions-compile-fns
  {:expr compile-expr-expr
   :additive-expr compile-op-expr
   :multiplicative-expr compile-op-expr
   :and-expr compile-and-expr
   :or-expr compile-or-expr
   :equality-expr compile-op-expr
   :comparison-expr compile-op-expr
   :unary-expr compile-unary-expr
   :num-literal compile-num-literal
   :null-literal compile-null-literal
   :bool-literal compile-bool-literal
   :string-literal compile-string-literal
   :fn-call compile-fn-call
   :path compile-path})

(defn- compile-expression-ast [ast]
  (if-let [compile-fn (get expressions-compile-fns (first ast))]
    (compile-fn ast)
    (let [err (str "Cannot find compile function for node " ast)]
      (throw #?(:clj (RuntimeException. err)
                :cljs (js/Error. err))))))

(defn failure? [x]
  (if (insta/failure? x)
    (let [err (pr-str (insta/get-failure x))]
      (throw #?(:clj (RuntimeException. err)
                :cljs (js/Error. err))))
    x))

(defn- compile-string [node options]
  (if (.startsWith node "$fp")
    ;; (fhirpath/compile (subs node 3))
    (throw #?(:clj (RuntimeException. "Fhirpath expressions are not supported yet")
              :cljs (js/Error. "Fhirpath expressions are not supported yet")))

    (if (.startsWith node "$")
      (-> node
          (expression-parser)
          failure?
          (first)
          (compile-expression-ast))

      node)))

(defn compile* [node options]
  (cond
    (nil? node)     nil
    (map? node)     (compile-map node options)
    (string? node)  (compile-string node options)
    (seqable? node) (compile-vector node options)
    :else node))

(defn compile
  "Compiles JUTE template into invocabe function."
  [node & [options]]

  (let [result (compile* node (or options {}))]
    (if (fn? result)
      result
      (constantly result))))

(defn drop-blanks [n]
  (let [res (cond
              (and (string? n) (str/blank? n)) nil
              (map? n)
              (let [nn (reduce (fn [acc [k v]]
                                 (let [nv (drop-blanks v)]
                                   (if (nil? nv)
                                     acc
                                     (assoc acc k nv))))
                               {} n)]
                (if (empty? nn) nil nn))

              (sequential? n)
              (let [nn (remove nil? (map drop-blanks n))]
                (if (empty? nn) nil nn))

              :else n)]

    res))

(comment
  (expression-parser "splitStr(s, \" \").0" )

  )
