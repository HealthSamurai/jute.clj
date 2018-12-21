(ns jute.alt-test
  (:require
   [jute.alt :refer :all]
   [clojure.test :refer :all]
   [clojure.string :as str]))


(deftest test-alt-jute

  (is (= {:a 2 :b {:c 3}}
         (jute {:a "$ a + 1"
                :b {:c "$ x + 1"}}
               {:a 1 :x 2})))

  (is (= {:res [{:xx 2} {:xx 3} {:xx 4}]}
         (jute {:res {:$map "$ coll"
                      :$as "x"
                      :$body {:xx "$ %x.a + 1"}}}
               {:coll [{:a 1}
                       {:a 2}
                       {:a 3}]})))

  (is (= {:b "str" :d 1}
         (jute {:a "$ ups" :b "str" :d "$ x"}
               {:x 1})))


  (is (= "else"
         (jute
          {:$let [{:x "else"}
                  {:y "then"}]
           :$body {:$if "$ a = 1"
                   :$then "$ %y" 
                   :$else "$ %x"}}
          {:a 2 :b "Hoho"})
         ))

  (is (= "then"
         (jute
          {:$let [{:x "else"}
                  {:y "then"}]
           :$body {:$if "$ a = 1"
                   :$then "$ %y" 
                   :$else "$ %x"}}
          {:a 1 :b "Hoho"})))
  

  )

