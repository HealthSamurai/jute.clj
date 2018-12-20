(ns jute.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.set :as cset]
            [instaparse.core :as insta]))

(def expression-parser
  (insta/parser
   "
<root> = <('$' #'\\s+')?> expr

<expr>
  = unary-expr
  | and-expr
  | or-expr
  | multiplicative-expr
  | additive-expr
  | path
  | num-literal
  | bool-literal
  | <'('> expr <')'>

additive-expr
  = expr <whitespace> ? ('+' | '-') <whitespace> ? expr

multiplicative-expr
  = expr <whitespace> ? ('*' | '/') <whitespace> ? expr

or-expr
  = expr <whitespace> ? <'|' '|'> <whitespace> ? expr

and-expr
  = expr <whitespace> ? <'&' '&'> <whitespace> ? expr

unary-expr
  = ('+' | '-' | '!') expr

(* PATHS *)

path
  = path-head (<'.'> path-component)*

<path-head>
  = #'[a-zA-Z_]+'

<path-component>
  = #'[a-zA-Z_0-9]+'

(* LITERALS *)

num-literal
  = #'[0-9]+' ('.' #'[0-9]'*)?

bool-literal
  = 't' | 'f'

(* STUFF *)

<whitespace>
  = (' ' | '\t' | '\n')+
"))

(def template
  {:arithmetic-result "$ 2 + 3 * foo.bar"

   :const-result {:a 12
                  :b []
                  :c true}

   :let-example {:$let [{:local "$ foo.bar"}]
                 :$body {:abc "$ local + 5"}}

   :if-result {:$if "$ foo.baz"
               :$then "there is a foo.baz in the scope"
               :$else "there is no foo.baz in the scope"}})

(defmulti compile-expr first)

(defmethod compile-expr :expr [node]
  (compile-expr (second node)))

(defmethod compile-expr :additive-expr [node]
  (compile-expr (second node)))

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
  (let [compiled-locals (doall (map (fn [v]
                                      [(first (keys v)) (compile* (first (vals v)))])
                                    (:$let node)))
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
   "/" clojure.core//})

;; (declare compile-expression-ast)

;; (defn- compile-expr-expr [ast]
;;   (compile-expression-ast (second ast)))

(defn- compile-additive-or-multiplicative-expr [[_ left op right]]
  (if right
    (let [f (operator-to-fn op)
          compiled-left (compile-expression-ast left)
          compiled-right (compile-expression-ast right)]
      (fn [scope] (f (eval-node compiled-left scope) (eval-node compiled-right scope))))

    (compile-expression-ast left)))

(defn- compile-unary-expr [ast]
  (if (= 2 (count ast))
    (compile-expression-ast (last ast))

    (let [f (operator-to-fn (second ast))
          operand (compile-expression-ast (last ast))]
      (fn [scope] (f (eval-node operand scope))))))

(defn- compile-num-literal [ast]
  (read-string (apply str (rest ast))))

(defn- compile-path [ast]
  (fn [scope] (get-in scope (map keyword (rest ast)))))

(def expressions-compile-fns
  {:expr compile-expr-expr
   :additive-expr compile-additive-or-multiplicative-expr
   :multiplicative-expr compile-additive-or-multiplicative-expr
   :unary-expr compile-unary-expr
   :num-literal compile-num-literal
   :path compile-path})

(defn- compile-expression-ast [ast]
  (let [compile-fn (get expressions-compile-fns (first ast))]
    (compile-fn ast)))

(defn- compile-string [node]
  (if (.startsWith node "$")
    (-> node
        (expression-parser)
        (first)
        (compile-expression-ast))

    node))

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
