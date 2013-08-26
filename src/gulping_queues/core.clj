(ns gulping-queues.core
 "Notation:  q - the queue
             x - an element on the queue
             p - a pointer, which is an agent

  q contains pointers, containing maps of {:x x :front val :length len}
  The inner queue consists of pointers.

  function! => non-blocking/asynchronous
  function!! => blocking"
  (:require [clojure.core.async :refer [chan go >! <! <!! timeout]]))

(defprotocol Touch
  "Applies the indentity function to a mutable type
   with the purpose of triggering watches."
  (touch [this]))

(defprotocol SpatiallyEmpty
  "Describes the emptiness of a queue from a perspective.
   tol is a tolerance in units. For example, front-empty with
   20 units returns true if no element exists in the first
   20 units of the queue."
  (totally-empty? [this])
  (front-empty? [this tol])
  (back-empty? [this tol]))

(defprotocol Peekable
  "Non-mutuating operations to view the head & tail."
  (front-peek [this])
  (back-peek [this]))

(defprotocol Offerable
  "Puts x on the queue if there is room at the tail.
   offer! returns nil on success, and an agent on failure.
   The agent can be watched, and successive attempts to offer
   can be made.

   offer!! blocks indefinitely until x is added to the tail."
  (offer! [this x len])
  (offer!! [this x len]))

(defprotocol Gulpable
  "Moves x from the tail with inertia towards the front of the
   queue. gulp!! blocks until x is at the front of the queue.
   gulp! returns immediately - its asynchronous variant."
  (gulp! [this x velocity buf pause])
  (gulp!! [this x velocity buf pause]))

(defprotocol Consumable
  "Take an element off the front of the queue."
  (take! [this])
  (take!! [this]))

(extend-protocol Touch
  clojure.lang.Ref
  (touch [x] (dosync (commute x identity)))
  
  clojure.lang.Agent
  (touch [x] (send x identity)))

(defn back-of-p
  "Determine the position of the backside of p."
  [p]
  (+ (:front p) (:length p)))

(defn occupy-space
  "Put a pointer at the back of the queue."
  [q p]
  (alter q conj p)
  nil)

(defn watch-p-for-motion
  "Add a watch to me, keyed by me.
   When pred returns true, put a value on the ch."
  [p me ch pred]
  (add-watch p me
             (fn [_ _ old new]
               (when (or (not= old new) (pred new))
                 (go (>! ch true))))))

(defn ref-offer!
  "Non-blocking offer."
  [q distance x len]
  (let [p (agent {:x x :length len :front (- distance len)})]
    (dosync
     (if-let [tail (last @q)]
       (let [room (- distance (back-of-p @tail))]
         (if (<= (:length @p) room)
           (occupy-space q p)
           tail))
       (occupy-space q p)))))

(defn ref-offer!!
  "Blocking offer."
  [q distance x len]
  (let [blocker (ref-offer! q distance x len)]
    (if blocker
      (let [ch (chan)]
        (watch-p-for-motion blocker x ch (fn [p] (>= (- distance (back-of-p p)) len)))
        (touch blocker)
        (<!! ch)
        (remove-watch blocker x)
        (recur q distance x len))
      nil)))

(defn move-distance [front step-len]
  (min front step-len))

(defn step [p velocity]
  (assoc p :front (- (:front p) (move-distance (:front p) velocity))))

(defn find-pointer
  "Given x, find its corresponding pointer in the queue."
  [q x]
  (first (filter (fn [p] (= (:x @p) x)) @q)))

(defn ref-gulp!
  "Gulp the pointer for x down to the head of the queue.
   Move at velocity units per pause ms. Require a buffering
   space of buf between x and the pointer in front of it, unless
   its at the front of the queue, in which case it moves all the
   way to the front."
  [q x velocity buf pause]
  (go
   (let [p (find-pointer q x)
         q-snapshot @q]
     (while (> (:front @p) 0)
       (let [preceeding-pos (dec (.indexOf q-snapshot p))]
         (if-not (neg? preceeding-pos)
           (let [preceeding-p (nth q-snapshot preceeding-pos)
                 preceeding-p-snapshot @preceeding-p
                 space (- (:front @p) (back-of-p preceeding-p-snapshot))]
             (cond (<= space buf)
                   (let [ch (chan)]
                     (watch-p-for-motion preceeding-p p ch
                                         (fn [el] (>= (- (:front @p) (back-of-p el)) buf)))
                     (touch preceeding-p)
                     (<! ch)
                     (remove-watch preceeding-p p))
                   (< space (+ buf velocity))
                   (do (send p step (- space buf))
                       (<! (timeout pause))
                       (await p))
                   :else (do (send p step velocity)
                             (<! (timeout pause))
                             (await p))))
           (do (send p step velocity)
               (<! (timeout pause))
               (await p))))))))

(defn ref-gulp!! [q x velocity buf pause]
  (<!! (ref-gulp! q x velocity buf pause)))

(defn ref-take! [q]
  (dosync (if-let [head (first @q)]
            (do (alter q rest)
                (send head assoc :front -1)
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
  (deref [this] (deref line))

  Touch
  (touch [this] (touch line)))

(defn coord-g-queue [capacity]
  (let [r (ref [])]
    (CoordinatedGulpingQueue. (ref []) capacity)))

;; A queue of 100 units.
(def q (coord-g-queue 100))

;; Put Mike on the queue. He takes up 10 units of space.
(offer!! q "Mike" 10) ; => nil, success

@q ; => [#<Agent@67467dbe: {:x "Mike", :length 10, :front 90}>]

;; Fail, Mike is in the way.
;; Get back the agent who blocks Timothy.
;; Can watch for changes.
(offer! q "Timothy" 5) ; => #<Agent@67467dbe: {:x "Mike", :length 10, :front 90}>

;; Move Mike to the front of the queue, at 5 units per 250 ms.
;; Requires 0 buffering room. Blocks until at the front.
(gulp!! q "Mike" 5 0 250)

@q ; => [#<Agent@67467dbe: {:x "Mike", :length 10, :front 0}>]

(offer! q "Timothy" 5) ; => nil, success

@q ; => [#<Agent@67467dbe: {:x "Mike", :length 10, :front 0}>
   ;;    #<Agent@5f8551ae: {:x "Timothy", :length 5, :front 95}>]

(take! q) ; => "Mike"

@q ; => (#<Agent@5f8551ae: {:x "Timothy", :length 5, :front 95}>)
   ;; Notably, there's a bug. Timothy didn't move to the front of the queue.

