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

