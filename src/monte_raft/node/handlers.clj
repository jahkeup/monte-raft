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

(defmacro respond
  ([msg]
     `(socket/send-str-timeout socket/node-control-socket
        socket/default-timeout ~msg))
  ([sock msg]
     `(socket/send-str-timeout ~sock
        socket/default-timeout ~msg)))

(defn handle-ping
  "Handle a PING command" [reply-sock]
  (let [response (first (msgs/responses-for :ping))]
    (respond reply-sock response)))

(defn handle-elect
  "Handle ELECT command" [reply-sock])

(defn handle-confirm
  "Handle CONFIRM command" [reply-sock]
  (if (node-state/confirmable?)
    (do (node-state/confirmed!)
        (respond reply-sock :confirmed))
    (respond reply-sock :resend)))

(defn handle-commit
  "Handle COMMIT command" [reply-sock]
  (if (node-state/confirmed?)
    (do (node-state/commit!)
        (respond reply-sock :committed))
    (respond reply-sock :retry)))
