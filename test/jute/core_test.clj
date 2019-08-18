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

(deftest documentation-examples-test
  (testing "The first example from README.md"
    (let [template {:type "book"
                    :author "$ book.author.name"
                    :title "$ book.title"
                    :content {:$map "$ book.chapters.*(this.type = \"content\")"
                              :$as "ch"
                              :$body "$ ch.content"}
                    :content2 "$ book.chapters.*(this.type = \"content\").content"}

          scope {:book {:author {:name "M. Soloviev"
                                 :title "PHD"
                                 :gender "m"}
                        :title "Approach to Cockroach"
                        :chapters [{:type "preface"
                                    :content "A preface chapter"}
                                   {:type "content"
                                    :content "Chapter 1"}
                                   {:type "content"
                                    :content "Chapter 2"}
                                   {:type "content"
                                    :content "Chapter 3"}
                                   {:type "afterwords"
                                    :content "Afterwords"}]}}]

      (is (= ((compile template) scope)
             {:type "book"
              :author "M. Soloviev"
              :title "Approach to Cockroach"
              :content ["Chapter 1" "Chapter 2" "Chapter 3"]
              :content2 ["Chapter 1" "Chapter 2" "Chapter 3"]})))))
