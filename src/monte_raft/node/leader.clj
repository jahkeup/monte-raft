(ns monte-raft.leader
  (:require [monte-raft.node.state :as node-state]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :refer [until-message-from]]
            [zeromq.zmq :as zmq]))

(def ^:dynamic leader-id
  "The node id of the current leader" nil)

(def ^:dynamic leader-remote
  "The leader's remote publishing address (connects directly for commands)"
  nil)

(defn is-leader?
  "Is the other-node the same as the leader process?"
  [other-node-id]
  (and (not (nil? leader-id))
    (= other-node-id leader-id)))

(defn leader-worker
  "Go thread used to manage the system. Establishes heartbeat
  messages, state consensus, and handles all client interactions. Node
  sub-worker"
  [leader-id context pub-binding]
  (until-message-from
    ))
