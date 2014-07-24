(ns monte-raft.node.leader-worker
  (:require [monte-raft.node.state :as node-state]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.worker :as worker]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]))

(def ^:dynamic leader-id
  "The node id of the current leader" nil)

(def ^:dynamic leader-remote
  "The leader's remote publishing address (connects directly for commands)"
  nil)

(defn is-leader?
  "Is the other-node the same as the leader process?"
  []
  (and (not (nil? leader-id))
    (= node-state/node-id leader-id)))

(defn publish-state [publisher state-str]
  (zmq/send-str publisher state-str))

(defn leader-worker
  "Go thread used to manage the system. Establishes heartbeat
  messages, state consensus, and handles all client interactions. Node
  sub-worker"
  [leader-id {:keys [publish-binding kill-codes] :as worker-config}]
  (log/tracef "Starting leader '%s' sending state updates on '%s'."
    leader-id publish-binding)
  (with-open [state-publisher (doto (zmq/socket socket/ctx :pub)
                                (zmq/bind publish-binding))]
    (log/trace "Leader worker started.")
    (worker/until-worker-terminate (kill-codes :leader)
      (Thread/sleep 10)))
  (log/trace "Leader exiting.")
  :terminated)


