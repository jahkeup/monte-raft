(ns monte-raft.node.timer)

;;
;; This file contains timers/timeouts used for different events to
;; take place. (all times are in msec)

(defn static-timeout
  "Static timeout to be used for scenarios"
  []
  2000)

(defn random-timeout
  "Generate a timer with a random value between start and end"
  [start end]
  (let [rand-timeout (+ start (rand-int end))]
    (if (> rand-timeout end) end
        rand-timeout)))

