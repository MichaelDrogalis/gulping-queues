(ns gulping-queues.core
  (:require [clojure.core.reducers :as r]
            [clojure.core.async :refer [chan go >! <! timeout take! put!]]))

(def lane [{:id "Mike" :position 50 :buf 2}
           {:id "Dorrene" :position 51 :buf 5}])

(def ch (chan))

(defn slot [lane id]
  (let [indexed-lane (zipmap lane (range))]
    (second (first (filter (fn [[k v]] (= id (:id k))) indexed-lane)))))

(defn drive-forward [car position]
  (if (>= position 1)
    (assoc car :position (dec position))
    car))

(defn advance [old new {:keys [id position buf] :as car}]
  (let [my-slot (slot old id)]
    (if (zero? my-slot)
      (conj new (drive-forward car position))
      (let [target (nth old (dec my-slot))]
        (if (<= (- position (:position target)) buf)
          (conj new car)
          (conj new (drive-forward car position)))))))

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

