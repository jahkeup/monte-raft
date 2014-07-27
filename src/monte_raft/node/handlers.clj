(ns monte-raft.node.handlers
  (:require [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]))

(defmacro respond
  ([msg]
     `(socket/send-str-timeout socket/node-control-socket
        socket/default-timeout ~msg))
  ([sock msg]
     `(socket/send-str-timeout ~sock
        socket/default-timeout ~msg)))

(defn handle-ping
  "Handle a PING command" [reply-sock _]
  (let [response (first (msgs/responses-for :ping))]
    (respond reply-sock response)))

(defn handle-elect
  "Handle ELECT command" [reply-sock _])

(defn handle-confirm
  "Handle CONFIRM command" [reply-sock {:keys [state] :as config}]
  (if (node-state/confirmable? state)
    (do (node-state/confirmed! state)
        (respond reply-sock :confirmed))
    (respond reply-sock :unconfirmed)))

(defn handle-commit
  "Handle COMMIT command" [reply-sock {:keys [state] :as config}]
  (if (node-state/confirmed? state)
    (do (node-state/commit! state)
        (respond reply-sock :committed))
    (respond reply-sock :uncommitted)))


(def cmd-handlers "Command handler mapping"
  {:ping handle-ping
   :elect handle-elect
   :confirm handle-confirm
   :commit handle-commit})
