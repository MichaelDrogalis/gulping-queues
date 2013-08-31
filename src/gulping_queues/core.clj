(ns gulping-queues.core
  (:require [clojure.core.reducers :as r]))

(def lanes {"north" {:state [] :entry (java.util.concurrent.LinkedBlockingQueue.)}
            "east"  {:state [] :entry (java.util.concurrent.LinkedBlockingQueue.)}
            "south" {:state [] :entry (java.util.concurrent.LinkedBlockingQueue.)}
            "west"  {:state [] :entry (java.util.concurrent.LinkedBlockingQueue.)}})

(defn slot [lane id]
  (let [indexed-lane (zipmap lane (range))
        matches (filter (fn [[k v]] (= id (:id k))) indexed-lane)]
    (second (first matches))))

(defn back-of-car [car]
  (+ (:front car) (:len car)))

(defn drive-forward [car speed]
  (assert (>= (:front car) 0))
  (let [new-front (- (:front car) speed)]
    (assoc car :front (max new-front 0))))

(defn drive-watching-forward [car target speed]
  (let [space-between (- (:front car) (back-of-car target) (:buf car))]
    (assoc car :front (- (:front car) (min speed space-between)))))

(defn advance [speed old new {:keys [id front buf] :as car}]
  (let [my-slot (slot old id)]
    (if (zero? my-slot)
      (conj new (drive-forward car speed))
      (let [target (nth old (dec my-slot))]
        (conj new (drive-watching-forward car target speed))))))

(defn produce-next-lane-state [[lane-id lane]]
  {lane-id (r/reduce (partial advance 1 lane) [] lane)})

(defn drive [lanes]
  (prn lanes)
  (let [result (apply merge (pmap produce-next-lane-state lanes))]
    (Thread/sleep 200)
    (recur result)))


'{:schedule/ident :shamrock-schedule
  :schedule/substitute {?w :standard ?x :standard ?y :standard ?z :standard}
  :schedule/sequence [{:states {?x [:green] ?z [:green]} :ticks 8}
                      {:states {?x [:yellow] ?z [:yellow]} :ticks 3}
                      {:states {?x [:red] ?z [:red]} :ticks 3}
                      {:states {?w [:green]  ?y [:green]} :ticks 8}
                      {:states {?w [:yellow] ?y [:yellow]} :ticks 3}
                      {:states {?w [:red] ?y [:red]} :ticks 3}]}

(defn light-transformation->fns [{:keys [state-diff ticks]}]
  (map (fn [_] (fn [light] (merge light state-diff))) (range ticks)))

(def fns
  (concat
   (light-transformation->fns {:state-diff {:x [:green] :z [:green]} :ticks 8})
   (light-transformation->fns {:state-diff {:x [:yellow] :z [:yellow]} :ticks 3})
   (light-transformation->fns {:state-diff {:x [:red] :z [:red]} :ticks 3})))

(def light {:w [:red] :x [:red] :y [:red] :z [:red]})

(reductions #(%2 %1) light fns)
