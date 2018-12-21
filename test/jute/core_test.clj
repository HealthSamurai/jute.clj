(ns jute.core-test
  (:require [clojure.test :refer :all]
            [jute.core :refer :all]))

(deftest expressions-test
  (is (= false (compile* "$ true && false")))
  (is (= true (compile* "$ false || true")))
  (is (compile* "$ 42 > 3"))
  (is (compile* "$ 42 >= 42"))
  (is (compile* "$ 42 != true"))

  (is (= ((compile* "$ foo + 42") {:foo 5}) 47))
  ;;(is (= ((compile* "$ foo + 42") {:foo "a"}) "a42"))

  )

(run-tests)
