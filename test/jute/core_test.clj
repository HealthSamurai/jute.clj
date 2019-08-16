(ns jute.core-test
  (:refer-clojure :exclude [compile])
  (:require [clojure.test :refer :all]
            [jute.core :refer :all]
            [yaml.core :as yaml]))

(deftest expressions-test
  (is (= false (compile* "$ true && false" {})))
  (is (= true (compile* "$ false || true" {})))
  (is (compile* "$ 42 > 3" {}))
  (is (compile* "$ 42 >= 42" {}))
  (is (compile* "$ 42 != true" {}))

  (is (= ((compile* "$ foo + 42" {}) {:foo 5}) 47))
  (is (= ((compile* "$ foo + 42" {}) {:foo "a"}) "a42")))

(deftest if-directive-test
  (let [suite (yaml/from-file "spec/if_directive.yml" true)]
    (doseq [{:keys [desc scope template result]} (:tests suite)]
      (testing desc
        (is (= result ((compile template) scope)))))))

(deftest let-directive-test
  (let [suite (yaml/from-file "spec/let_directive.yml" true)]
    (doseq [{:keys [desc scope template result]} (:tests suite)]
      (testing desc
        (is (= ((compile template) scope) result))))))

(deftest map-directive-test
  (let [suite (yaml/from-file "spec/map_directive.yml" true)]
    (doseq [{:keys [desc scope template result]} (:tests suite)]
      (testing desc
        (is (= ((compile template) scope) result))))))

(deftest fn-directive-test
  (let [suite (yaml/from-file "spec/fn_directive.yml" true)]
    (doseq [{:keys [desc scope template result]} (:tests suite)]
      (testing desc
        (is (= ((compile template) scope) result))))))

(deftest expressions-test
  (let [suite (yaml/from-file "spec/expressions.yml" true)]
    (doseq [{:keys [desc scope template result]} (:tests suite)]
      (testing desc
        (is (= ((compile template) scope) result))))))

