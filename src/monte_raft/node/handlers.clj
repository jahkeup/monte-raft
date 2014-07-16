(ns monte-raft.node.handlers
  (:require [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]))


(declare handle-ping handle-term handle-confirm handle-commit
  handle-elect)

(def cmd-handlers "Command handler mapping"
  {:ping handle-ping
   :elect handle-elect
   :confirm handle-confirm
   :commit handle-commit})

(defn handle-ping
  "Handle a PING command" []
  (let [response (first (msgs/responses-for :ping))]
    (socket/send-str-timeout socket/control-socket socket/default-timeout)))

(defn handle-elect
  "Handle ELECT command" [])

(defn handle-confirm
  "Handle CONFIRM command" [])

(defn handle-commit
  "Handle COMMIT command" [])
