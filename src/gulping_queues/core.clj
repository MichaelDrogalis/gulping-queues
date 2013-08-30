(ns gulping-queues.core
  (:require [clojure.core.reducers :as r]
            [clojure.core.async :refer [chan go >! >!! <! <!! timeout take! put! alts!!]]))

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

(defn drain-channel
  ([ch] (drain-channel ch []))
  ([ch result]
     (let [x (take! ch identity)]
       (if (nil? x)
         result
         (recur ch (conj result x))))))

(defn drive [my-lane]
  (let [result (future (r/reduce (partial advance my-lane) [] my-lane))]
    (prn @result)
    (Thread/sleep 200)
    (recur @result)))

