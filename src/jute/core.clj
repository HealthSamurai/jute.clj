(ns jute.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as cset]
            [instaparse.core :as insta]
            [fhirpath.core :as fhirpath]))

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
  = bool-literal / num-literal / string-literal / path / parens-expr

(* PATHS *)

path
  = path-head (<'.'> path-component)*
  | parens-expr (<'.'> path-component)+
  | '@' (<'.'> path-component)*

<path-head>
  = #'[a-zA-Z_]+'

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

string-literal
  = <'\"'> #'[^\"]*'  <'\"'>
"
   :auto-whitespace :standard))

(def template
  {:arithmetic-result "$ 2 + 3 * foo.bar"

   :const-result {:a 12
                  :b []
                  :c true}

   :let-example {:$let [{:local "$ foo.bar"}]
                 :$body {:abc "$ local + 5"}}

   :if-result {:$if "$ foo.baz = 42 && foo.bar = 42"
               :$then "there is a foo.baz in the scope and it equals 42"
               :$else "there is no foo.baz in the scope"}})

(declare compile*)

(defn- eval-node [n scope]
  (if (fn? n) (n scope) n))

(defn- compile-if [node]
  (let [compiled-if (compile* (:$if node))
        compiled-then (compile* (:$then node))
        compiled-else (compile* (:$else node))]

    (if (fn? compiled-if)
      (fn [scope]
        (if (eval-node compiled-if scope)
          (eval-node compiled-then scope)
          (eval-node compiled-else scope)))

      (if compiled-if
        compiled-then
        compiled-else))))

(defn- compile-let [node]
  (let [lets (:$let node)
        compiled-locals (mapv (fn [[k v]] [k (compile* v)])
                              (if (vector? lets)
                                (mapv (fn [i] [(first (keys i)) (first (vals i))]) lets)
                                (mapv (fn [[k v]] [k v]) lets)))

        compiled-body (compile* (:$body node))]

    (fn [scope]
      (let [new-scope (reduce (fn [acc [n v]]
                                (assoc acc n (eval-node v acc)))
                              scope compiled-locals)]
        (eval-node compiled-body new-scope)))))

(def directives
  {:$if compile-if
   :$let compile-let})

(defn- compile-map [node]
  (let [directive-keys (cset/intersection (set (keys node)) (set (keys directives)))]
    (when (> (count directive-keys) 1)
      (throw (IllegalArgumentException. (str "More than one directive found in node "
                                             (pr-str node)
                                             ". Found following directive keys: "
                                             (pr-str directive-keys)))))

    (if (empty? directive-keys)
      (let [result (reduce (fn [acc [key val]]
                             (let [compiled-val (compile* val)]
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
  (let [result (mapv compile* node)]
    (if (some fn? result)
      (fn [scope]
        (mapv #(eval-node % scope) result))

      result)))

(def operator-to-fn
  {"+" clojure.core/+
   "-" clojure.core/-
   "*" clojure.core/*
   "%" clojure.core/rem
   "=" clojure.core/=
   "!=" clojure.core/not=
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

      (throw (RuntimeException. (str "Cannot guess operator for: " op))))

    (compile-expression-ast left)))

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
  (read-string (apply str (rest ast))))

(defn- compile-bool-literal [[_ v]]
  (= v "true"))

(defn- compile-string-literal [[_ v]]
  v)

(defn- compile-path-component [cmp]
  (cond
    (string? cmp) (keyword cmp)
    (vector? cmp)
    (let [[t arg] cmp]
      (cond
        (= :wildcard t) (fn [scope])))))

(defn- compile-path [[_ & path-comps]]
  (let [compiled-comps (mapv compile-path-component path-comps)]
    (fn [scope]
      (loop [[cmp & tail] compiled-comps
             scope scope]
        (let [next-scope (if (fn? cmp) (cmp scope) (get scope cmp))]
          (if (empty? tail)
            next-scope
            (recur tail next-scope)))))))

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
   :bool-literal compile-bool-literal
   :string-literal compile-string-literal
   :path compile-path})

(defn- compile-expression-ast [ast]
  (if-let [compile-fn (get expressions-compile-fns (first ast))]
    (compile-fn ast)
    (throw (RuntimeException. (str "Cannot find compile function for node " ast)))))

(defn- compile-string [node]
  (if (.startsWith node "$fp")
    (fhirpath/compile (subs node 3))

    (if (.startsWith node "$")
      (-> node
          (expression-parser)
          (first)
          (compile-expression-ast))

      node)))

(defn compile* [node]
  (cond
    (map? node) (compile-map node)
    (vector? node) (compile-vector node)
    (string? node) (compile-string node)
    :else node))

(defn compile
  "Compiles JUTE template into invocabe function."
  [node]

  (let [result (compile* node)]
    (if (fn? result)
      result
      (constantly result))))
