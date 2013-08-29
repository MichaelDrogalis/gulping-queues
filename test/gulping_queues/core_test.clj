(ns gulping-queues.core-test
  (:require [clojure.test :refer :all]
            [gulping-queues.core :refer :all]))

(deftest slot-test
  (let [lane [{:id "Mike"} {:id "Dorrene"} {:id "Kristen"}]]
    (is (= (slot lane "Mike") 0))
    (is (= (slot lane "Dorrene") 1))
    (is (= (slot lane "Kristen")) 2)
    (is (= (slot lane "not-found") nil))))

(deftest drive-forward-test
  (is (= (drive-forward {:front 20} 5) {:front 15}))
  (is (= (drive-forward {:front 20} 20) {:front 0}))
  (is (= (drive-forward {:front 30} 40) {:front 0}))
  (is (= (drive-forward {:front 0} 10) {:front 0})))

(deftest drive-watching-forward-test
  (is (= (drive-watching-forward {:id "Dorrene" :front 10 :buf 1}
                                 {:id "Mike" :front 5 :buf 3} 5)
         {:id "Dorrene" :front 6 :buf 1}))
  (is (= (drive-watching-forward {:id "Dorrene" :front 7 :buf 1}
                                 {:id "Mike" :front 5 :buf 3} 1)
         {:id "Dorrene" :front 6 :buf 1})))

(deftest advance-test
  (is (= (advance 1 [{:id "Mike" :front 5 :buf 3}] [] {:id "Mike" :front 5 :buf 3})
         [{:id "Mike" :front 4 :buf 3}]))

  (is (= (advance 3 [{:id "Mike" :front 5 :buf 3}] [] {:id "Mike" :front 5 :buf 3})
         [{:id "Mike" :front 2 :buf 3}]))

  (is (= (advance 5 [{:id "Mike" :front 5 :buf 3}
                     {:id "Dorrene" :front 10 :buf 1}]
                  [] {:id "Dorrene" :front 10 :buf 1})
         [{:id "Dorrene" :front 6 :buf 1}]))

  (is (= (advance 1 [{:id "Mike" :front 5 :buf 3}
                     {:id "Dorrene" :front 7 :buf 1}]
                  [] {:id "Dorrene" :front 7 :buf 1})
         [{:id "Dorrene" :front 6 :buf 1}])))

