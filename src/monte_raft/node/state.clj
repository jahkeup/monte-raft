(ns monte-raft.node.state
  (:require [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.socket :as socket]))

(def ^:dynamic state
  "This is the state that the consensus is operating for." nil)

(def ^:dynamic transient-state
  "The in-between state that is pending confirmation and committal." nil)

(def ^:dynamic cluster "Cluster node addresses"
  (atom []))

(def ^:dynamic node-options
  "Node options that are used to configure certain aspects"
  (atom {}))

(def ^:dynamic global-nodes-state
  "YES BAD BAD BAD, we're using this for statstics." (atom {}))

(def ^:dynamic heartbeat-failure
  "Channel recieves message on a heartbeat failure." (atom false))

(def ^:dynamic confirmed
  "Has the transient state been confirmed?" (atom false))


(defn state-worker
  "Go thead designed to run in the background until a message is sent
  over the stop-chan channel. "
  [{:keys [update-socket stop-chan check-period]}]
  (until-message-from stop-chan
    ;; Potential problems here in the future if the windows don't align..
    (if-let [new-state (socket/receive-str-timeout
                         update-socket check-period)]
      ;; Blindly set transient state to the received state.
      (reset! transient-state new-state)))
  :exiting)

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
