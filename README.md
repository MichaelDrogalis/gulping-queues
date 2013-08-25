# gulping-queues

Gulping Queues are a concurrent data structure with spatial and time semantics,
offering autononous reactive ability for elements. Useful for simulating lines of people.
I made the name and concept up.

Experimental, alpha, and not battled-scarred. Stay out.

## Usage

```clojure
;; Queue of capacity 100 units
(def q (coord-g-queue 100))

; Offer to add Mike to the queue, who takes up 10 units of space
(offer! q "Mike" 10) ; => nil, sucess

@q ; => [#<Agent@25bbf0b7: {:x "Mike", :length 10, :front 90}>]

;; Offer failed, not added to queue because "Mike" is in the way
;; Returns an agent to watch for changes
(offer! q "Kristen" 10) ; => #<Agent@25bbf0b7: {:x "Mike", :length 10, :front 90}>

;; Blocking gulp, moves Mike all the way to the 'front' of the queue.
;; Moves at 10 units per 200 ms, requiring a buffer of 3 units between itself
;; and any elements in front of it.
(gulp!! q "Mike" 10 3 200)

@q ; => [#<Agent@25bbf0b7: {:x "Mike", :length 10, :front 0}>]

;; Async gulp, returns immediately. 
(gulp! q "Kristen" 10 3 200)

@q ; => [#<Agent@25bbf0b7: {:x "Mike", :length 10, :front 0}>
   ;     #<Agent@1c9a4938: {:x "Kristen", :length 10, :front 13}]

(take! q) ; => "Mike"

(take! q) ; => "Kristen"
```

## License

Copyright © 2013 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
