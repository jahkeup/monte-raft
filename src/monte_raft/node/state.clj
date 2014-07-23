(ns monte-raft.node.state
  (:require [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.socket :as socket]))

(defn make-node-options [num]
  {:control-binding (format "inproc://node-%s-control" num)
   :leader-binding "inproc://system-leader"
   :publish-binding "inproc://system-state-updates"
   :kill-codes
   {:control (keyword (format "node-%s-control-worker" num))
    :leader (keyword (format "node-%s-leader-worker" num))
    :state (keyword (format "node-%s-state-worker" num))}})

(defn -make-node-cluster-map
  "Generate a map of 1-x nodes (for use in cluster)"
  [x]
  (reduce
    #(assoc %1 (keyword (format "node-%s" %2)) (make-node-options %2))
    {} (range 1 (inc x))))

;;
;; This algorithm is very stateful, make sure to bind these. In the
;; future these should be moved into the function arguments, but for
;; the time being bind in the main node function and go.
;;

(def ^:dynamic node-id
  "The node identifier"
  nil)

(def ^:dynamic state
  "This is the state that the consensus is operating for." nil)

(def ^:dynamic transient-state
  "The in-between state that is pending confirmation and committal." nil)

(def cluster "Cluster node addresses"
  (atom (-make-node-cluster-map 3)))

(def ^:dynamic global-nodes-state
  "YES BAD BAD BAD, we're using this for statstics." (atom {}))

(def ^:dynamic confirmed
  "Has the transient state been confirmed?" nil)

(def ^:dynamic term
  "The current term of the system" nil)

(defn confirmable? "Is the current state confirmable?" []
  (not (nil? @transient-state)))

(defn confirmed! "Mark transient state confirmed" []
  (reset! confirmed true))

(defn confirmed? "Has the current transient state been confirmed?" []
  (true? @confirmed))

(defn commit!
  "Terminate state chain, update state and clear transient and
  confirmation flag" []
  (reset! state @transient-state)
  (reset! confirmed false)
  (reset! transient-state nil))
