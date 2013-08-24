(ns gulping-queues.core
  (:require [clojure.core.async :refer [chan go >! <! <!! timeout]]))

(defprotocol Touch
  (touch [this]))

(defprotocol SpatiallyEmpty
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

(defn watch-p-for-motion [me x ch]
  (add-watch x me
             (fn [_ _ old new]
               (when-not (= old new)
                 (go (>! ch true))))))

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

(defn ref-offer!! [q distance x len]
  (let [blocker (ref-offer! q distance x len)]
    (if blocker
      (let [ch (chan)]
        (watch-p-for-motion x blocker ch)
        (touch blocker)
        (<!! ch)
        (remove-watch blocker x)
        (recur q distance x len)))))

(defn move-distance [p velocity]
  (let [space-left (:front p)]
    (if (> space-left velocity)
      velocity
      space-left)))

(defn step [p velocity]
  (assoc p :front (- (:front p) (move-distance p velocity))))

(defn advance [p velocity pause]
  (send p step velocity)
  (<!! (timeout pause))
  (await p))

(defn find-pointer [q x]
  (first (filter (fn [p] (= (:x @p) x)) @q)))

(defn ref-gulp!! [q x velocity buf pause]
  (let [p (find-pointer q x)]
    (while (> (:front @p) 0)
      (let [preceeding-pos (dec (.indexOf @q p))]
        (if-not (neg? preceeding-pos)
          (let [preceeding-p (nth @q preceeding-pos)
                space (- (:front @p) (back-of-p @preceeding-p))]
            (if (<= space buf)
              (let [ch (chan)]
                (watch-p-for-motion preceeding-p p ch)
                (touch preceeding-p)
                (<!! ch)
                (remove-watch preceeding-p p velocity pause))
              (advance p (min velocity buf) pause)))
          (advance p velocity pause))))))

(defn ref-gulp! [q x velocity buf pause]
  (go (ref-gulp!! q x velocity buf pause)))

(defn ref-take! [q]
  (dosync
   (if-let [head (first @q)]
     (do (alter q rest)
         (:x (deref head))))))

(defn ref-front-peek [q]
  (:x (deref (first @q))))

(defn ref-back-peek [q]
  (:x (deref (last @q))))

(defn ref-totally-empty? [q]
  (empty? @q))

(defn ref-front-empty? [q tol]
  (or (empty? @q) (>= (:front (deref (first @q))) tol)))

(defn ref-back-empty? [q tol]
  (or (empty? @q) (<= (:front (deref (last @q))) tol)))

(deftype CoordinatedGulpingQueue [line capacity]
  Offerable
  (offer! [this x len] (ref-offer! line capacity x len))
  (offer!! [this x len] (ref-offer!! line capacity x len))

  Gulpable
  (gulp! [this x velocity buf pause]
    (ref-gulp! line x velocity buf pause))
  (gulp!! [this x velocity buf pause]
    (ref-gulp!! line x velocity buf pause))

  Consumable
  (take! [this] (ref-take! line))
  (take!! [this])
  
  Peekable
  (front-peek [this] (ref-front-peek line))
  (back-peek [this] (ref-back-peek line))
  
  SpatiallyEmpty
  (totally-empty? [this] (ref-totally-empty? line))
  (front-empty? [this tol] (ref-front-empty? line tol))
  (back-empty? [this tol] (ref-back-empty? line tol))

  clojure.lang.Seqable
  (seq [this] (seq (deref line)))
  
  clojure.lang.IRef
  (addWatch [this key cb] (add-watch line key cb))
  (removeWatch [this key] (remove-watch line key))
  
  clojure.lang.IDeref
  (deref [this] (map deref (deref line)))

  Touch
  (touch [this] (touch line)))

(defn coord-g-queue [capacity]
  (CoordinatedGulpingQueue. (ref []) capacity))

