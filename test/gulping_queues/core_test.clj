(ns gulping-queues.core-test
  (:require [midje.sweet :refer :all]
            [gulping-queues.core :refer :all]))

(let [lane [{:id "Mike"} {:id "Dorrene"} {:id "Kristen"}]]
  (fact (slot lane "Mike")      => 0)
  (fact (slot lane "Dorrene")   => 1)
  (fact (slot lane "Kristen")   => 2)
  (fact (slot lane "Dan") => nil))

(fact (back-of-car {:front 0 :len 1})  => 1)
(fact (back-of-car {:front 0 :len 5})  => 5)
(fact (back-of-car {:front 7 :len 10}) => 17)
(fact (back-of-car {:front 9 :len 1})  => 10)

(fact (drive-forward {:front 20} 5)  => {:front 15})
(fact (drive-forward {:front 20} 20) => {:front 0})
(fact (drive-forward {:front 30} 40) => {:front 0})
(fact (drive-forward {:front 0} 10)  => {:front 0})

(fact (drive-watching-forward
       {:id "Dorrene" :front 10 :len 1 :buf 1}
       {:id "Mike" :front 5 :len 1 :buf 3} 5)
      => {:id "Dorrene" :front 7 :len 1 :buf 1})

(fact (drive-watching-forward
       {:id "Dorrene" :front 7 :len 1 :buf 1}
       {:id "Mike" :front 5 :len 1 :buf 3} 1)
      => {:id "Dorrene" :front 7 :len 1 :buf 1})

(fact (drive-watching-forward
       {:id "Kristen" :front 15 :len 5 :buf 0}
       {:id "Mike" :front 5 :len 5 :buf 3} 5)
      => {:id "Kristen" :front 10 :len 5 :buf 0})

(fact (drive-watching-forward
       {:id "Kristen" :front 15 :len 5 :buf 1}
       {:id "Mike" :front 5 :len 5 :buf 3} 5)
      => {:id "Kristen" :front 11 :len 5 :buf 1})

(fact (advance 1 [{:id "Mike" :front 5 :buf 3}] [] {:id "Mike" :front 5 :buf 3})
      => [{:id "Mike" :front 4 :buf 3}])

(fact (advance 3 [{:id "Mike" :front 5 :buf 3}] [] {:id "Mike" :front 5 :buf 3})
      => [{:id "Mike" :front 2 :buf 3}])

(fact (advance 5 [{:id "Mike"    :front 5 :len 1 :buf 3}
                  {:id "Dorrene" :front 10 :len 1 :buf 1}]
               [] {:id "Dorrene" :front 10 :len 1 :buf 1})
      => [{:id "Dorrene" :front 7 :len 1 :buf 1}])

(fact (advance 1 [{:id "Mike"  :front 5 :len 1 :buf 3}
                  {:id "Benti" :front 7 :len 1 :buf 1}]
               [] {:id "Benti" :front 7 :len 1 :buf 1})
      => [{:id "Benti" :front 7 :len 1 :buf 1}])

(fact (produce-next-lane-state ["south" []]) => {"south" []})
(fact (produce-next-lane-state ["south" [{:id "Mike" :front 10}]])
      => {"south" [{:id "Mike" :front 9}]})

(let [fns (light-transition->fns {:state-diff {:x [:green]} :ticks 1})]
  (fact (reductions #(%2 %1) {:x [:red]} fns)
        => [{:x [:red]} {:x [:green]}]))

(let [fns (light-transition->fns {:state-diff {:x [:green]} :ticks 2})]
  (fact (reductions #(%2 %1) {:x [:red]} fns)
        => [{:x [:red]} {:x [:green]} {:x [:green]}]))

(let [fns (light-transition->fns {:state-diff {:x [:green]} :ticks 2})]
  (fact (reductions #(%2 %1) {:x [:red]} fns)
        => [{:x [:red]} {:x [:green]} {:x [:green]}]))

(let [light {:state 0 :fns [inc dec]}]
  (fact (produce-next-light-state light) => {:state 1 :fns [dec inc]}))

