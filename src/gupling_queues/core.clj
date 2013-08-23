(ns gulping-queues.core
  (:require [clojure.core.async :refer [chan go >! <! <!! timeout]]))

(defprotocol Touch
  (touch [this]))

(defprotocol SpatialEmpty
  (totally-empty? [this])
  (front-empty? [this tol])
  (back-empty? [this tol]))

(defprotocol Peekable
  (front-peek [this])
  (back-peek [this]))

(defprotocol Offerable
  (offer! [this x len])
  (offer!! [this x len]))

(defprotocol Gulpable
  (gulp! [this x velocity buf pause])
  (gulp!! [this x velocity buf pause]))

(defprotocol Consumable
  (take! [this])
  (take!! [this]))

(extend-protocol Touch
  clojure.lang.Ref
  (touch [x] (dosync (commute x identity)))
  
  clojure.lang.Agent
  (touch [x] (send x identity)))

(defn back-of-p [p]
  (+ (:front p) (:length p)))

(defn put-at-back! [p distance]
  (send p (fn [p] (assoc p :front (- distance (:length p))))))

(defn occupy-space [q p distance]
  (alter q conj (put-at-back! p distance))
  nil)

(defn ref-offer! [q distance x len]
  (let [p (agent {:x x :length len})
        result (dosync
         (if-let [tail (last @q)]
           (let [room (- distance (+ (:front @tail) (:length @tail)))]
             (if (<= (:length @p) room)
               (occupy-space q p distance)
               tail))
           (occupy-space q p distance)))]
    (await p)
    result))

(defn move-distance [p velocity]
  (let [space-left (:front p)]
    (if (> space-left velocity)
      velocity
      space-left)))

(defn step [p velocity]
  (assoc p :front (- (:front p) (move-distance p velocity))))

(defn watch-x-for-motion [me x ch]
  (add-watch x me
             (fn [_ _ old new]
               (when-not (= old new)
                 (go (>! ch true))))))

(defn advance [p velocity pause]
  (prn p)
  (send p step velocity)
  (<!! (timeout pause))
  (await p))

(defn find-pointer [q x]
  (first (filter (fn [p] (= (:x @p) x)) @q)))

(defn ref-gulp! [q x velocity buf pause]
  (go (let [p (find-pointer q x)]
        (while (> (:front @p) 0)
          (let [preceeding-pos (dec (.indexOf @q p))]
            (if-not (neg? preceeding-pos)
              (let [preceeding-p (nth @q preceeding-pos)]
                (if (<= (- (:front @p) (back-of-p @preceeding-p)) buf)
                  (let [ch (chan)]
                    (watch-x-for-motion preceeding-p p ch)
                    (touch preceeding-p)
                    (<!! ch)
                    (remove-watch preceeding-p p velocity pause))
                  (advance p velocity pause)))
              (advance p velocity pause)))))))

(defn ref-gulp!! [q x velocity buf pause]
  (let [p (find-pointer q x)]
    (while (> (:front @p) 0)
      (let [preceeding-pos (dec (.indexOf @q p))]
        (if-not (neg? preceeding-pos)
          (let [preceeding-p (nth @q preceeding-pos)]
            (if (<= (- (:front @p) (back-of-p @preceeding-p)) buf)
              (let [ch (chan)]
                (watch-x-for-motion preceeding-p p ch)
                (touch preceeding-p)
                (<!! ch)
                (remove-watch preceeding-p p velocity pause))
              (advance p velocity pause)))
          (advance p velocity pause))))))

(defn ref-take! [q]
  (dosync
   (let [head (first @q)]
     (alter q rest)
     (send head dissoc :front)
     head)))

(defn ref-front-peek [q]
  (first @q))

(defn ref-back-peek [q]
  (last @q))

(defn ref-totally-empty? [q]
  (empty? @q))

(defn ref-front-empty? [q tol]
  (or (empty? @q) (>= (:front (deref (first @q))) tol)))

(defn ref-back-empty? [q tol]
  (or (empty? @q) (<= (:front (deref (last @q))) tol)))

(deftype CoordinatedGulpingQueue [line capacity]
  Offerable
  (offer! [this x len] (ref-offer! line capacity x len))
  (offer!! [this x len])

  Gulpable
  (gulp! [this x velocity buf pause]
    (ref-gulp! line x velocity buf pause))
  (gulp!! [this x velocity buf pause]
    (ref-gulp!! line x velocity buf pause))

  Consumable
  (take!! [this])
  
  Peekable
  (front-peek [this] (ref-front-peek line))
  (back-peek [this] (ref-back-peek line))
  
  SpatialEmpty
  (totally-empty? [this] (ref-totally-empty? line))
  (front-empty? [this tol] (ref-front-empty? line tol))
  (back-empty? [this tol] (ref-back-empty? line tol))

  clojure.lang.Seqable
  (seq [this] (deref line))
  
  clojure.lang.IRef
  (addWatch [this key cb] (add-watch line key cb))
  (removeWatch [this key] (remove-watch line key))
  
  clojure.lang.IDeref
  (deref [this] @line)

  Touch
  (touch [this] (touch line)))

(defn coord-g-queue [capacity]
  (CoordinatedGulpingQueue. (ref []) capacity))

