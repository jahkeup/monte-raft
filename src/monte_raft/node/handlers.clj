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
  "Handle ELECT command" [reply-sock elect-node {:keys [state] :as config}]
  (let [response (if @(:elected state) "NOVOTE"
                     (do (reset! (:elected state) elect-node) "VOTE"))]
    (respond reply-sock response)))

(defn handle-follow
  "Handle FOLLOW command"
  [reply-sock new-leader-id {:keys [state] :as config}]
  (if (contains? @node-state/cluster new-leader-id)
    (do (reset! (:leader-id state) new-leader-id)
        (respond reply-sock (format "FOLLOWING %s" new-leader-id)))
    (respond reply-sock "INVALID NODE ID")))

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

(defn handle-term
  "Handle TERM command"
  ([reply-sock {:keys [state] :as config}]
     (respond reply-sock (str "TERM " @(:term state))))
  ([reply-sock new-term {:keys [state] :as config}]
     (respond reply-sock (str "TERM " (reset! (:term state) new-term)))))

(defn handle-unknown-message [reply-sock]
  (respond reply-sock "BAD COMMAND"))

(def cmd-handlers "Command handler mapping"
  {:ping handle-ping
   :confirm handle-confirm
   :commit handle-commit})
